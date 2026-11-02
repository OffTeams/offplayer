package com.offplayer.app.youtube

import android.content.Context
import android.net.Uri
import com.offplayer.app.data.AppSettings
import com.offplayer.app.data.SponsorBlock
import com.offplayer.app.data.VideoMetaStore
import com.offplayer.app.download.Downloader
import com.offplayer.app.model.Chapter
import com.offplayer.app.model.VideoMeta
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamType
import kotlin.math.min

/** Готовый к воспроизведению ролик: адрес для плеера и сведения для экрана. */
data class YtStream(
    val youtubeId: String,
    /** id для видеоплеера: по нему находятся эпизоды и отрезки SponsorBlock. */
    val playerId: Long,
    val uri: Uri,
    val title: String,
    val channel: String,
    val channelId: String?,
    val thumbnail: String?,
    val width: Int,
    val height: Int,
)

/**
 * Потоки ролика. Основной путь — NewPipeExtractor (около секунды), запасной — yt-dlp
 * (медленнее, но справляется с тем, что требует входа: ролики «18+», для участников).
 * Реклама не воспроизводится: плеер получает только потоки самого ролика.
 * Видео и звук YouTube отдаёт раздельно — плеер склеивает их ([StreamMediaSourceFactory]).
 */
object YouTubeStreams {
    /** Ссылки YouTube живут около 6 часов; держим готовые полчаса, чтобы повторное открытие было мгновенным. */
    private const val CACHE_MS = 30 * 60 * 1000L
    private val cache = HashMap<String, Pair<Long, YtStream>>()
    private val ids = HashMap<Long, String>()

    /** id ролика YouTube по id в плеере (для «Скачать» и SponsorBlock). */
    fun youtubeIdOf(playerId: Long): String? = synchronized(ids) { ids[playerId] } ?: OnlineStore.item(playerId)?.ytId

    /**
     * id для плеера: отрицательный и далеко за пределами id файлов медиатеки (−1, −2, …),
     * а положительные заняты MediaStore.
     */
    fun playerIdOf(youtubeId: String): Long = -(1L shl 52) - (youtubeId.hashCode().toLong() and 0xffffffffL)

    fun isStream(playerId: Long?): Boolean = playerId != null && playerId <= -(1L shl 52)

    fun cached(youtubeId: String): YtStream? =
        synchronized(cache) { cache[youtubeId]?.takeIf { System.currentTimeMillis() - it.first < CACHE_MS }?.second }

    private class Resolved(val uri: Uri, val title: String, val channel: String, val channelId: String?, val width: Int, val height: Int, val chapters: List<Chapter>)

    suspend fun resolve(context: Context, video: YtVideo, withSponsorBlock: Boolean = true): YtStream = withContext(Dispatchers.IO) {
        YouTubeActions.prefetch(video.id)
        cached(video.id)?.let { return@withContext it }
        coroutineScope {
            val segments = if (withSponsorBlock) async { SponsorBlock.fetch(video.id) } else null
            val r = runCatching { fromNewPipe(video) }.getOrElse { first ->
                runCatching { fromYtDlp(context, video) }.getOrElse { throw first }
            }
            val playerId = playerIdOf(video.id)
            synchronized(ids) { ids[playerId] = video.id }
            VideoMetaStore.putTransient(
                playerId,
                VideoMeta(video.id, chapters = r.chapters, segments = segments?.await().orEmpty(), segmentsFetchedAt = System.currentTimeMillis()),
            )
            val stream = YtStream(
                youtubeId = video.id, playerId = playerId, uri = r.uri,
                title = r.title.ifEmpty { video.title }, channel = r.channel.ifEmpty { video.channel },
                channelId = r.channelId ?: video.channelId, thumbnail = video.thumbnail, width = r.width, height = r.height,
            )
            synchronized(cache) { cache[video.id] = System.currentTimeMillis() to stream }
            stream
        }
    }

    /** Качество, выбранное в настройках плеера (короткая сторона кадра), — по id ролика. */
    private val chosen = HashMap<String, Int>()

    fun chosenQuality(youtubeId: String): Int? = synchronized(chosen) { chosen[youtubeId] }

    /** Качества, между которыми можно переключиться (короткая сторона кадра, по убыванию); пусто — выбора нет. */
    fun qualities(youtubeId: String): List<Int> {
        val info = info(youtubeId) ?: return emptyList()
        if (info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM) return emptyList()
        return info.videoOnlyStreams
            .filter { playable(it) }
            .map { min(it.width, it.height) }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()
    }

    /** Тот же ролик в другом качестве: потоки берутся из уже полученных сведений, без нового запроса. */
    suspend fun withQuality(youtubeId: String, side: Int): YtStream = withContext(Dispatchers.IO) {
        val old = synchronized(cache) { cache[youtubeId]?.second } ?: error("No stream")
        val r = fromNewPipe(YtVideo(youtubeId, old.title, old.channel, old.channelId, null, "", old.thumbnail.orEmpty()), side)
        val stream = old.copy(uri = r.uri, width = r.width, height = r.height)
        synchronized(chosen) { chosen[youtubeId] = side }
        synchronized(cache) { cache[youtubeId] = System.currentTimeMillis() to stream }
        stream
    }

    /**
     * Поток видео, который телефон может воспроизвести: MP4 (H.264, AV1) и WebM (VP9) — выше 1080p YouTube
     * отдаёт только VP9 и AV1. AV1 — только если в телефоне есть его декодер.
     */
    private fun playable(s: org.schabi.newpipe.extractor.stream.VideoStream): Boolean {
        if (!s.isUrl || s.deliveryMethod != DeliveryMethod.PROGRESSIVE_HTTP) return false
        if (s.format != MediaFormat.MPEG_4 && s.format != MediaFormat.WEBM) return false
        return !(s.codec.orEmpty().startsWith("av01")) || hasAv1
    }

    /** Предпочтение кодеков при одинаковом качестве: H.264 надёжнее всего, затем VP9, затем AV1. */
    private fun codecScore(s: org.schabi.newpipe.extractor.stream.VideoStream): Int = s.codec.orEmpty().let {
        when {
            it.startsWith("avc1") -> 3
            it.startsWith("vp9") || it.startsWith("vp09") -> 2
            else -> 1
        }
    }

    private val hasAv1: Boolean by lazy {
        runCatching {
            android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos
                .any { !it.isEncoder && it.supportedTypes.any { t -> t.equals("video/av01", true) } }
        }.getOrDefault(false)
    }

    /** Ограничение качества — по короткой стороне кадра, чтобы вертикальные Shorts не играли в низком. */
    private fun maxSide(): Int = AppSettings.videoHeight.value.takeIf { it > 0 } ?: 1080

    /** User-Agent, с которым YouTube отдаёт поток: у ссылок разных клиентов свои требования. */
    private fun uaFor(url: String): String? = runCatching {
        when {
            YoutubeParsingHelper.isAndroidStreamingUrl(url) -> YoutubeParsingHelper.getAndroidUserAgent(null)
            YoutubeParsingHelper.isIosStreamingUrl(url) -> YoutubeParsingHelper.getIosUserAgent(null)
            else -> null
        }
    }.getOrNull()

    /** Сведения NewPipe о ролике (описание, лайки, аватар канала) — для панели «Описание и комментарии». */
    private val infos = HashMap<String, org.schabi.newpipe.extractor.stream.StreamInfo>()

    fun info(youtubeId: String): org.schabi.newpipe.extractor.stream.StreamInfo? = synchronized(infos) { infos[youtubeId] }

    /** Сведения о ролике: из кэша или новым запросом (если ролик открывался через yt-dlp). */
    suspend fun loadInfo(youtubeId: String): org.schabi.newpipe.extractor.stream.StreamInfo = withContext(Dispatchers.IO) {
        info(youtubeId) ?: NpSource.stream(youtubeId).also { synchronized(infos) { infos[youtubeId] = it } }
    }

    /** Адреса потоков ролика для ленивого воспроизведения (онлайн-плейлисты): видео, звук и User-Agent. */
    class Parts(val video: String, val audio: String, val ua: String?)
    private val parts = HashMap<String, Pair<Long, Parts>>()

    /** Вызывается из потока загрузки плеера в момент начала воспроизведения элемента. */
    fun partsBlocking(youtubeId: String): Parts {
        synchronized(parts) { parts[youtubeId]?.takeIf { System.currentTimeMillis() - it.first < CACHE_MS }?.let { return it.second } }
        val info = NpSource.stream(youtubeId)
        synchronized(infos) { infos[youtubeId] = info }
        val limit = maxSide()
        val v = info.videoOnlyStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format == MediaFormat.MPEG_4 && min(it.width, it.height) <= limit }
            .sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.VideoStream> { it.codec?.startsWith("avc1") == true }
                .thenByDescending { min(it.width, it.height) })
            .firstOrNull()?.content
            ?: info.videoStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }.maxByOrNull { min(it.width, it.height) }?.content
        val a = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format == MediaFormat.M4A }
            .sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
                .thenByDescending { it.averageBitrate })
            .firstOrNull()?.content ?: v ?: error("No playable stream")
        val p = Parts(v ?: a, a, uaFor(a))
        synchronized(ids) { ids[playerIdOf(youtubeId)] = youtubeId }
        // Эпизоды и SponsorBlock — как у ролика, открытого из ленты
        val chapters = info.streamSegments.mapIndexed { i, s ->
            val end = info.streamSegments.getOrNull(i + 1)?.startTimeSeconds?.toLong() ?: info.duration
            Chapter(s.startTimeSeconds * 1000L, end * 1000L, s.title)
        }
        val segments = runCatching { kotlinx.coroutines.runBlocking { SponsorBlock.fetch(youtubeId) } }.getOrNull().orEmpty()
        VideoMetaStore.putTransient(playerIdOf(youtubeId), VideoMeta(youtubeId, chapters = chapters, segments = segments, segmentsFetchedAt = System.currentTimeMillis()))
        synchronized(parts) { parts[youtubeId] = System.currentTimeMillis() to p }
        return p
    }

    /** side — качество, выбранное в плеере: поток именно такой высоты вместо лучшего в пределах настройки. */
    private fun fromNewPipe(video: YtVideo, side: Int? = null): Resolved {
        val info = (if (side != null) info(video.id) else null) ?: NpSource.stream(video.id)
        synchronized(infos) { infos[video.id] = info }
        val thumb = video.thumbnail
        // Субтитры: сначала авторские, затем автоматические; включаются кнопкой «CC» во время просмотра
        // NewPipe отдаёт ссылки на TTML; тот же адрес с fmt=vtt возвращает WebVTT, который понимает плеер
        val subs = info.subtitles
            .filter { it.isUrl }
            .sortedBy { it.isAutoGenerated }
            .distinctBy { it.languageTag + it.isAutoGenerated }
            .take(30)
            .map { s ->
                val name = s.displayLanguageName.ifEmpty { s.languageTag }
                val vtt = Uri.parse(s.content).buildUpon().clearQuery().apply {
                    val src = Uri.parse(s.content)
                    src.queryParameterNames.filter { it != "fmt" }.forEach { k -> src.getQueryParameters(k).forEach { appendQueryParameter(k, it) } }
                    appendQueryParameter("fmt", "vtt")
                }.build().toString()
                YtSubtitle(s.languageTag, if (s.isAutoGenerated) "$name (auto)" else name, vtt)
            }
            .let { list ->
                // Автоперевод на язык интерфейса (как «Перевести» на YouTube), если такой дорожки нет
                val ui = com.offplayer.app.L.ctx.resources.configuration.locales[0]
                val base = list.firstOrNull() ?: return@let list
                if (list.any { it.language.substringBefore('-').equals(ui.language, true) }) list
                else list + YtSubtitle(
                    ui.language,
                    "${ui.getDisplayLanguage(ui).replaceFirstChar { it.titlecase(ui) }} (${base.label} → auto)",
                    Uri.parse(base.url).buildUpon().appendQueryParameter("tlang", ui.language).build().toString(),
                )
            }
        val chapters = info.streamSegments.mapIndexed { i, s ->
            val end = info.streamSegments.getOrNull(i + 1)?.startTimeSeconds?.toLong() ?: info.duration
            Chapter(s.startTimeSeconds * 1000L, end * 1000L, s.title)
        }
        val channelId = info.uploaderUrl?.substringAfterLast("/channel/", "")?.takeIf { it.startsWith("UC") }
        val live = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
        if (live) {
            val hls = info.hlsUrl?.takeIf { it.isNotEmpty() } ?: error("No live stream")
            return Resolved(StreamMediaSourceFactory.singleUri(hls, uaFor(hls), thumb), info.name, info.uploaderName.orEmpty(), channelId, 16, 9, chapters)
        }
        val limit = maxSide()
        // H.264 в MP4 аппаратно декодируется на любом телефоне
        val video1 = info.videoOnlyStreams
            .filter { playable(it) && min(it.width, it.height) <= (side ?: limit) }
            // Лучшее качество в пределах настройки (или выбранное в плеере), при равном — более надёжный кодек
            .sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.VideoStream> { side != null && min(it.width, it.height) == side }
                .thenByDescending { min(it.width, it.height) }
                .thenByDescending { codecScore(it) }.thenByDescending { it.fps })
            .firstOrNull()
        // Звук — оригинальная дорожка (а не дубляж), лучшая по битрейту
        val audio = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format == MediaFormat.M4A }
            .sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
                .thenByDescending { it.averageBitrate })
            .firstOrNull()
        val uri = if (video1 != null && audio != null) {
            StreamMediaSourceFactory.mergedUri(video1.content, audio.content, uaFor(video1.content), thumb, subs)
        } else {
            val muxed = info.videoStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                .maxByOrNull { min(it.width, it.height) } ?: error("No playable stream")
            StreamMediaSourceFactory.singleUri(muxed.content, uaFor(muxed.content), thumb, subs)
        }
        return Resolved(uri, info.name, info.uploaderName.orEmpty(), channelId, video1?.width ?: 0, video1?.height ?: 0, chapters)
    }

    private suspend fun fromYtDlp(context: Context, video: YtVideo): Resolved {
        Downloader.ensureInit(context)
        val h = maxSide()
        val request = YoutubeDLRequest(video.url).apply {
            addOption("--dump-single-json")
            addOption("--no-playlist")
            addOption("--skip-download")
            // H.264 + AAC: аппаратно декодируются на любом телефоне; иначе — готовый поток со звуком
            addOption("-f", "bv*[height<=$h][vcodec^=avc1]+ba[ext=m4a]/bv*[height<=$h]+ba/b[height<=$h]/b")
            // После входа yt-dlp видит ролики «18+», для участников и не получает проверку «вы не бот»
            YouTubeAccount.cookiesFile(context)?.let { addOption("--cookies", it.absolutePath) }
        }
        val info = JSONObject(YoutubeDL.getInstance().execute(request).out)
        val req = info.optJSONArray("requested_formats")
        val ua = info.optJSONObject("http_headers")?.optString("User-Agent")
        val uri = if (req != null && req.length() >= 2) {
            val v = req.getJSONObject(0)
            val a = req.getJSONObject(1)
            StreamMediaSourceFactory.mergedUri(v.getString("url"), a.getString("url"), v.optJSONObject("http_headers")?.optString("User-Agent") ?: ua, video.thumbnail)
        } else {
            StreamMediaSourceFactory.singleUri(info.getString("url"), ua, video.thumbnail)
        }
        val chapters = info.optJSONArray("chapters")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val c = arr.optJSONObject(i) ?: return@mapNotNull null
                Chapter((c.optDouble("start_time") * 1000).toLong(), (c.optDouble("end_time") * 1000).toLong(), c.optString("title"))
            }
        }.orEmpty()
        return Resolved(uri, info.optString("title"), info.optString("channel"), info.optString("channel_id").takeIf { it.startsWith("UC") },
            info.optInt("width"), info.optInt("height"), chapters)
    }
}
