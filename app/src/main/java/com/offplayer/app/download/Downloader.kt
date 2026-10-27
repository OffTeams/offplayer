package com.offplayer.app.download

import com.offplayer.app.tr
import com.offplayer.app.trn
import com.offplayer.app.R

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.offplayer.app.data.LibraryStore
import com.offplayer.app.data.AppSettings
import com.offplayer.app.data.MediaRepository
import com.offplayer.app.data.PlaylistStore
import com.offplayer.app.data.SponsorBlock
import com.offplayer.app.data.VideoLibraryStore
import com.offplayer.app.data.VideoMetaStore
import com.offplayer.app.model.Chapter
import com.offplayer.app.ui.formatDuration
import com.offplayer.app.ui.formatSize
import com.offplayer.app.model.VideoMeta
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class DownloadKind { AUDIO, VIDEO }
enum class DownloadStatus { QUEUED, RUNNING, DONE, FAILED, CANCELED }

/** Дополнительные параметры загрузки. */
data class DownloadOptions(
    /** Скачать весь плейлист и сохранить его как список (видео) или микс (музыка). */
    val playlist: Boolean = false,
    /** Сохранить эпизоды (главы) видео. */
    val chapters: Boolean = true,
    /** Загрузить отрезки SponsorBlock для офлайн-пропуска. */
    val sponsorBlock: Boolean = true,
    /** Дубляж, который нужно добавить к оригинальной дорожке (коды языков). */
    val extraAudioLangs: List<String> = emptyList(),
    /** Субтитры, встраиваемые в видео (коды языков); пусто — без субтитров. */
    val subLangs: List<String> = emptyList(),
    /** Если обычных субтитров нет — брать автоматические. */
    val autoSubs: Boolean = false,
    /** Сохранить аудио во вкладку «Подкасты» (выбор делается до скачивания). */
    val podcast: Boolean = false,
)

/** Причина неудачи — от неё зависит, что предложить пользователю. */
enum class FailureKind {
    /** Нет сети — просто повторить. */
    NETWORK,
    /** Ролик приватный/удалён — повтор не поможет. */
    UNAVAILABLE,
    /** Возможно, устарел плагин загрузки — предложить «Обновить и повторить». */
    PLUGIN,
    /** Не помогло даже обновление плагина — просьба сообщить разработчику. */
    AFTER_UPDATE,
}

data class DownloadTask(
    val id: String,
    val url: String,
    val kind: DownloadKind,
    val audioFormat: String,
    val videoHeight: Int,
    val options: DownloadOptions = DownloadOptions(),
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val etaSec: Long = -1,
    val title: String? = null,
    val message: String? = null,
    val failure: FailureKind? = null,
    /** Перед загрузкой принудительно обновить плагин (после ошибки). */
    val forceUpdate: Boolean = false,
    /** Полный текст ошибки — для отчёта разработчику. */
    val errorDetails: String? = null,
    /** Что получилось в итоге — куда вести по нажатию на уведомление. */
    val result: DownloadResult? = null,
    /** Длинные аудио (> 5 мин): пользователь выбирает, в «Музыку» или в «Подкасты». */
    val longAudioIds: List<Long> = emptyList(),
)

/** Результат загрузки: трек, видео, микс или список (id элемента медиатеки или плейлиста). */
data class DownloadResult(val kind: Kind, val id: String) {
    enum class Kind { TRACK, VIDEO, MIX, LIST, PODCAST }
}

/** Очередь загрузок с YouTube / YouTube Music через yt-dlp. */
object Downloader {
    private val AUDIO_EXT = setOf("mp3", "m4a", "opus", "ogg", "aac", "flac", "wav", "webm")
    private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "mov")
    private val ID_SUFFIX = Regex("""\s\[[\w-]{6,}]$""")
    private val INDEX_PREFIX = Regex("""^\d{5}\s""")
    private val ITEM_REGEX = Regex("""Downloading (?:item|video) (\d+) of (\d+)""")
    private val PLAYLIST_REGEX = Regex("""Downloading playlist: (.+)$""")

    private val initMutex = Mutex()
    @Volatile private var initialized = false

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val _version = MutableStateFlow<String?>(null)
    val ytDlpVersion: StateFlow<String?> = _version.asStateFlow()

    fun isSupportedUrl(url: String): Boolean =
        Regex("""^https?://([\w-]+\.)*(youtube\.com|youtu\.be|youtube-nocookie\.com)/""", RegexOption.IGNORE_CASE)
            .containsMatchIn(url.trim())

    /** Ссылка содержит плейлист (страница плейлиста или видео внутри плейлиста). */
    fun isPlaylistUrl(url: String): Boolean = url.contains("list=") || url.contains("/playlist")

    /** Ссылка ведёт именно на страницу плейлиста, а не на видео. */
    fun isPlaylistPage(url: String): Boolean = url.contains("/playlist") && !url.contains("watch?v=")

    suspend fun ensureInit(context: Context) = initMutex.withLock {
        if (initialized) return@withLock
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            YoutubeDL.getInstance().init(app)
            FFmpeg.getInstance().init(app)
            initialized = true
            _version.value = runCatching { YoutubeDL.getInstance().version(app) }.getOrNull()
        }
    }

    fun enqueue(
        context: Context,
        url: String,
        kind: DownloadKind,
        audioFormat: String,
        videoHeight: Int,
        options: DownloadOptions = DownloadOptions(),
    ) {
        appContext = context.applicationContext
        val task = DownloadTask(UUID.randomUUID().toString(), url.trim(), kind, audioFormat, videoHeight, options)
        _tasks.update { it + task }
        startService(context)
    }

    fun retry(context: Context, id: String) {
        update(id) { it.copy(status = DownloadStatus.QUEUED, progress = 0f, message = null, etaSec = -1, failure = null) }
        startService(context)
    }

    /** «Обновить и повторить»: сначала принудительно обновляется плагин, затем загрузка повторяется. */
    fun updateAndRetry(context: Context, id: String) {
        update(id) {
            it.copy(status = DownloadStatus.QUEUED, progress = 0f, message = null, etaSec = -1, failure = null, forceUpdate = true)
        }
        startService(context)
    }

    /** Текст отчёта для разработчика. */
    fun report(context: Context, task: DownloadTask): String = buildString {
        appendLine("OFFPlayer — ошибка загрузки")
        appendLine("Ссылка: ${task.url}")
        appendLine("Тип: ${if (task.kind == DownloadKind.AUDIO) "музыка (${task.audioFormat})" else "видео (${task.videoHeight}p)"}")
        appendLine("Плейлист: ${task.options.playlist}")
        appendLine("Плагин yt-dlp: ${PluginUpdater.pluginVersion(context) ?: "?"}")
        appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}), ${android.os.Build.MODEL}")
        appendLine()
        append(task.errorDetails ?: task.message.orEmpty())
    }

    fun cancel(id: String) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        when (task.status) {
            DownloadStatus.QUEUED -> update(id) { it.copy(status = DownloadStatus.CANCELED, message = tr(R.string.canceled)) }
            DownloadStatus.RUNNING -> {
                update(id) { it.copy(status = DownloadStatus.CANCELED, message = tr(R.string.canceled)) }
                runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
            }
            else -> Unit
        }
    }

    fun remove(id: String) {
        cancel(id)
        _tasks.update { list -> list.filterNot { it.id == id } }
    }

    /**
     * Убирает карточки готовых загрузок, чей результат удалён (трек, видео, микс или список),
     * чтобы в «Загрузках» не оставались ссылки на несуществующее.
     */
    fun forgetResults(kinds: Set<DownloadResult.Kind>, ids: Set<String>) {
        val gone = _tasks.value.filter { t -> t.status == DownloadStatus.DONE && t.result?.let { it.kind in kinds && it.id in ids } == true }
        if (gone.isEmpty()) return
        _tasks.update { list -> list.filterNot { it in gone } }
        // …и уведомления о них в шторке
        appContext?.let { ctx ->
            val nm = androidx.core.app.NotificationManagerCompat.from(ctx)
            gone.forEach { runCatching { nm.cancel(it.id.hashCode()) } }
        }
    }

    @Volatile private var appContext: Context? = null

    /** Ответ на вопрос «куда сохранить длинное аудио». */
    suspend fun placeLongAudio(context: Context, taskId: String, podcast: Boolean) {
        val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
        update(taskId) { it.copy(longAudioIds = emptyList()) }
        if (podcast) {
            LibraryStore.setPodcast(task.longAudioIds.toSet(), true)
            MediaRepository.refresh(context.applicationContext)
        }
    }

    const val LONG_AUDIO_MS = 3 * 60_000L

    /** Сведения о ролике для окна «Поделиться»: название, канал, превью и примерные размеры вариантов. */
    data class ShareInfo(
        val title: String,
        val channel: String?,
        val thumbnail: String?,
        val durationSec: Long,
        /** Размер в байтах по ключу варианта: "mp3", "m4a", "opus", "v360", "v720", … "v0" — лучшее. */
        val sizes: Map<String, Long>,
        /** Высоты видео, которые есть у ролика (по возрастанию). */
        val heights: List<Int>,
    )

    /** Быстрые сведения (название, канал, превью) через oEmbed YouTube — за доли секунды, без yt-dlp. */
    data class QuickInfo(val title: String, val channel: String?, val thumbnail: String?)

    suspend fun fetchQuickInfo(url: String): QuickInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val id = Regex("""(?:v=|youtu\.be/|shorts/|live/)([\w-]{11})""").find(url)?.groupValues?.get(1)
            val target = if (id != null) "https://www.youtube.com/watch?v=$id" else url.replace("music.youtube.com", "www.youtube.com")
            val conn = (java.net.URL("https://www.youtube.com/oembed?format=json&url=" + java.net.URLEncoder.encode(target, "UTF-8"))
                .openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 5_000
            }
            try {
                val o = JSONObject(conn.inputStream.bufferedReader().readText())
                QuickInfo(
                    title = o.optString("title"),
                    channel = o.optString("author_name").ifBlank { null },
                    // Превью в высоком качестве
                    thumbnail = id?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" } ?: o.optString("thumbnail_url").ifBlank { null },
                )
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun infoCacheFile(context: Context, url: String) =
        File(context.applicationContext.cacheDir, "info/${url.trim().hashCode().toUInt()}.json")

    suspend fun fetchShareInfo(context: Context, url: String): ShareInfo? = withContext(Dispatchers.IO) {
        runCatching {
            ensureInit(context)
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-single-json")
                addOption("--no-playlist")
                addOption("--skip-download")
            }
            val raw = YoutubeDL.getInstance().execute(request).out
            // Сведения сохраняются: если сразу нажать «Скачать», yt-dlp не будет разбирать страницу повторно
            runCatching { infoCacheFile(context, url).apply { parentFile?.mkdirs() }.writeText(raw) }
            val o = JSONObject(raw)
            val duration = o.optLong("duration")
            val formats = o.optJSONArray("formats") ?: org.json.JSONArray()
            data class F(val ext: String, val audioOnly: Boolean, val videoOnly: Boolean, val height: Int, val bytes: Long)
            val list = (0 until formats.length()).mapNotNull { i ->
                val f = formats.optJSONObject(i) ?: return@mapNotNull null
                val vcodec = f.optString("vcodec")
                val acodec = f.optString("acodec")
                val tbr = f.optDouble("tbr", 0.0)
                val bytes = f.optLong("filesize").takeIf { it > 0 } ?: f.optLong("filesize_approx").takeIf { it > 0 }
                    ?: (tbr * 1000 / 8 * duration).toLong()
                F(f.optString("ext"), vcodec == "none" && acodec != "none", acodec == "none" && vcodec != "none", f.optInt("height"), bytes)
            }
            val m4a = list.filter { it.audioOnly && it.ext == "m4a" }.maxOfOrNull { it.bytes }
            val opus = list.filter { it.audioOnly && it.ext == "webm" }.maxOfOrNull { it.bytes }
            val sizes = HashMap<String, Long>()
            m4a?.let { sizes["m4a"] = it }
            opus?.let { sizes["opus"] = it }
            if (duration > 0) sizes["mp3"] = duration * 245_000 / 8 // MP3 с наилучшим качеством ≈ 245 кбит/с
            val heights = list.filter { it.videoOnly && it.height > 0 }.map { it.height }.distinct().sorted()
            val audioForVideo = m4a ?: opus ?: 0L
            // Размер видео: самая высокая подходящая высота, среди её вариантов — MP4 (его и качаем) с наименьшим объёмом
            fun videoSize(h: Int): Long? {
                val fit = list.filter { it.videoOnly && it.height in 1..(if (h == 0) Int.MAX_VALUE else h) }
                val top = fit.maxOfOrNull { it.height } ?: return null
                val same = fit.filter { it.height == top }
                val pick = same.filter { it.ext == "mp4" }.ifEmpty { same }.minByOrNull { it.bytes } ?: return null
                return pick.bytes + audioForVideo
            }
            (listOf(0) + heights).forEach { h -> videoSize(h)?.let { sizes["v$h"] = it } }
            ShareInfo(
                title = o.optString("title").ifBlank { url },
                channel = o.optString("channel").ifBlank { o.optString("uploader") }.ifBlank { null },
                thumbnail = o.optString("thumbnail").ifBlank { null },
                durationSec = duration,
                sizes = sizes,
                heights = heights,
            )
        }.getOrNull()
    }

    /** Длительность ролика до скачивания (для вопроса «Музыка или Подкасты»). null — не удалось узнать. */
    suspend fun probeDurationMs(context: Context, url: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            ensureInit(context)
            val request = YoutubeDLRequest(url).apply { addOption("--no-playlist") }
            YoutubeDL.getInstance().getInfo(request).duration.toLong() * 1000
        }.getOrNull()?.takeIf { it > 0 }
    }

    fun clearFinished() = _tasks.update { list ->
        list.filter { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RUNNING }
    }

    private fun startService(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
    }

    internal fun failPending(message: String) = _tasks.update { list ->
        list.map {
            if (it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RUNNING) {
                it.copy(status = DownloadStatus.FAILED, message = message)
            } else it
        }
    }

    internal fun nextQueued(): DownloadTask? = _tasks.value.firstOrNull { it.status == DownloadStatus.QUEUED }

    internal fun runningTask(): DownloadTask? = _tasks.value.firstOrNull { it.status == DownloadStatus.RUNNING }

    private fun update(id: String, block: (DownloadTask) -> DownloadTask) =
        _tasks.update { list -> list.map { if (it.id == id) block(it) else it } }

    private fun isCanceled(id: String) = _tasks.value.firstOrNull { it.id == id }?.status == DownloadStatus.CANCELED

    internal suspend fun run(context: Context, task: DownloadTask) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val dir = File(app.cacheDir, "downloads/${task.id}")
        dir.deleteRecursively()
        dir.mkdirs()
        if (task.forceUpdate) {
            update(task.id) { it.copy(status = DownloadStatus.RUNNING, progress = 0f, message = tr(R.string.dl_updating_plugin)) }
            PluginUpdater.update(app)
        }
        update(task.id) { it.copy(status = DownloadStatus.RUNNING, progress = 0f, message = tr(R.string.dl_fetching_info)) }

        val isAudio = task.kind == DownloadKind.AUDIO
        val opts = task.options
        // Свежие сведения о ролике уже получены (окно «Поделиться») — используем их, это экономит
        // несколько секунд на повторном разборе страницы YouTube
        val cachedInfo = infoCacheFile(app, task.url).takeIf {
            !opts.playlist && it.exists() && System.currentTimeMillis() - it.lastModified() < 60 * 60_000L
        }
        val request = (if (cachedInfo != null) YoutubeDLRequest(emptyList<String>()) else YoutubeDLRequest(task.url)).apply {
            if (cachedInfo != null) addOption("--load-info-json", cachedInfo.absolutePath)
            if (opts.playlist) {
                addOption("--yes-playlist")
                addOption("-i") // недоступные ролики пропускаем, а не прерываем весь плейлист
            } else {
                addOption("--no-playlist")
            }
            addOption("--no-mtime")
            addOption("--newline")
            // info.json: ID ролика, название плейлиста, эпизоды
            addOption("--write-info-json")
            val name = if (opts.playlist) "%(playlist_index)05d %(title).140B [%(id)s].%(ext)s" else "%(title).150B [%(id)s].%(ext)s"
            addOption("-o", File(dir, name).absolutePath)
            if (isAudio) {
                addOption("-f", "bestaudio/best")
                addOption("-x")
                addOption("--audio-format", task.audioFormat)
                addOption("--audio-quality", "0")
                addOption("--embed-metadata")
                addOption("--embed-thumbnail")
                addOption("--convert-thumbnails", "jpg")
                // Квадратная обложка — красиво смотрится на пластинке
                addOption("--ppa", "ThumbnailsConvertor:-q:v 1 -vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\"")
            } else {
                addOption("-f", videoFormat(task.videoHeight, opts.extraAudioLangs))
                if (opts.extraAudioLangs.isNotEmpty()) addOption("--audio-multistreams")
                addOption("--merge-output-format", "mp4")
                if (opts.subLangs.isNotEmpty()) {
                    addOption("--write-subs")
                    if (opts.autoSubs) addOption("--write-auto-subs")
                    // «ru» совпадает и с «ru-RU», и с автопереводом «ru-orig»
                    addOption("--sub-langs", opts.subLangs.joinToString(",") { if (it == "iw") "iw.*,he.*" else "$it.*" } + ",-live_chat")
                    addOption("--embed-subs")
                    // Если субтитров на нужном языке нет, это не должно сорвать загрузку видео
                    if (!opts.playlist) addOption("-i")
                }
                addOption("--embed-metadata")
                // Эпизоды встраиваются и в сам файл — их увидят и другие плееры
                if (opts.chapters) addOption("--embed-chapters")
            }
        }

        var error: Throwable? = null
        val tracker = ProgressTracker(isAudio, task.audioFormat)
        var playlistTitle: String? = null
        // Обновление карточки: прогресс из трекера и текущий этап
        fun publish(eta: Long, title: String?, phase: String?) = update(task.id) {
            val (index, count) = tracker.item()
            it.copy(
                progress = tracker.overall(),
                etaSec = if (count > 1 || tracker.postProcessing()) -1 else eta,
                title = if (opts.playlist) playlistTitle ?: it.title else title ?: it.title,
                message = (phase ?: it.message?.substringBefore(" • ")).let { m ->
                    if (count > 1) tr(R.string.dl_item_of, m ?: "", index, count) else m
                },
            )
        }
        try {
            coroutineScope {
                // Конвертация, склейка и запись тегов не сообщают процент — пока они идут,
                // прогресс плавно растёт по оценке времени, а не прыгает сразу к 99 %
                val ticker = launch {
                    while (isActive) {
                        delay(300)
                        if (tracker.tick()) publish(-1, null, null)
                    }
                }
                try {
                    YoutubeDL.getInstance().execute(request, task.id, false) { progress, eta, line ->
                        ITEM_REGEX.find(line)?.let { tracker.onItem(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
                        PLAYLIST_REGEX.find(line)?.let { playlistTitle = it.groupValues[1].trim() }
                        tracker.onLine(line, if (progress >= 0f) (progress / 100f).coerceIn(0f, 1f) else null)
                        publish(eta, destinationTitle(line), phase(line))
                    }
                } finally {
                    ticker.cancel()
                }
            }
        } catch (e: YoutubeDL.CanceledException) {
            dir.deleteRecursively()
            update(task.id) { it.copy(status = DownloadStatus.CANCELED, message = tr(R.string.canceled)) }
            return@withContext
        } catch (t: Throwable) {
            // При -i yt-dlp завершается с ошибкой, если хоть один ролик недоступен — файлы всё равно сохраняем
            error = t
        }

        if (isCanceled(task.id)) {
            dir.deleteRecursively()
            return@withContext
        }

        val exts = if (isAudio) AUDIO_EXT else VIDEO_EXT
        val files = dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in exts && !it.name.endsWith(".part") }
            .orEmpty()
        // Для одиночного ролика берём самый большой файл, для плейлиста — все по порядку
        val media = if (opts.playlist) files.sortedBy { it.name } else listOfNotNull(files.maxByOrNull { it.length() })

        if (media.isEmpty()) {
            dir.deleteRecursively()
            val (message, kind) = classifyError(error)
            // Если не помогло даже принудительное обновление плагина — просим сообщить разработчику
            val finalKind = if (kind == FailureKind.PLUGIN && task.forceUpdate) FailureKind.AFTER_UPDATE else kind
            update(task.id) {
                it.copy(
                    status = DownloadStatus.FAILED,
                    message = message,
                    failure = finalKind,
                    forceUpdate = false,
                    errorDetails = error?.message?.takeLast(4000),
                )
            }
            return@withContext
        }

        try {
            val savedIds = ArrayList<Long>()
            val saved = ArrayList<LibraryStore.Entry>()
            var listName: String? = null
            media.forEachIndexed { i, file ->
                update(task.id) {
                    it.copy(message = if (media.size > 1) tr(R.string.dl_saving_n, i + 1, media.size) else tr(R.string.dl_saving))
                }
                val info = readInfo(dir, file)
                if (listName == null) listName = info?.optString("playlist_title")?.takeIf { it.isNotBlank() && it != "NA" }
                // Файл переносится во внутреннюю память приложения — другим приложениям он не виден
                val entry = LibraryStore.import(file, isAudio, tagHints(info, if (opts.playlist) null else task.url))
                saved += entry
                val mediaId = entry.id
                savedIds += mediaId
                if (!isAudio) saveVideoMeta(mediaId, file, info, opts)
            }

            val single = media.first().nameWithoutExtension.replace(ID_SUFFIX, "").replace(INDEX_PREFIX, "")
            var doneMessage = describe(saved.first())
            var doneTitle = single
            if (isAudio && opts.podcast) LibraryStore.setPodcast(savedIds.toSet(), true)
            val singleKind = when {
                !isAudio -> DownloadResult.Kind.VIDEO
                opts.podcast -> DownloadResult.Kind.PODCAST
                else -> DownloadResult.Kind.TRACK
            }
            var result = DownloadResult(singleKind, savedIds.first().toString())
            if (opts.playlist && savedIds.isNotEmpty()) {
                val name = listName ?: playlistTitle ?: tr(R.string.playlist)
                doneTitle = name
                if (isAudio) {
                    val p = PlaylistStore.create(name, savedIds, isMix = true, sourceUrl = task.url)
                    result = DownloadResult(DownloadResult.Kind.MIX, p.id)
                    doneMessage = tr(R.string.dl_done_mix, trn(R.plurals.tracks, saved.size), totals(saved))
                } else {
                    val l = VideoLibraryStore.createList(name, savedIds, sourceUrl = task.url)
                    result = DownloadResult(DownloadResult.Kind.LIST, l.id)
                    doneMessage = tr(R.string.dl_done_list, trn(R.plurals.videos, saved.size), totals(saved))
                }
                if (error != null) doneMessage += " " + tr(R.string.dl_some_unavailable)
            }
            MediaRepository.refresh(app)
            update(task.id) {
                it.copy(status = DownloadStatus.DONE, progress = 1f, title = doneTitle, message = doneMessage, result = result)
            }
        } catch (t: Throwable) {
            update(task.id) { it.copy(status = DownloadStatus.FAILED, message = tr(R.string.dl_save_failed, t.message ?: "")) }
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * Название, исполнитель и альбом из info.json — те же поля, что yt-dlp записывает в теги.
     * Берём их отсюда, потому что чтение тегов из файла на части прошивок портит Unicode.
     */
    private fun tagHints(info: JSONObject?, sourceUrl: String? = null): LibraryStore.TagHints? {
        info ?: return null
        fun str(vararg keys: String) = keys.firstNotNullOfOrNull { k ->
            info.optString(k).trim().takeIf { it.isNotEmpty() && it != "NA" && it != "null" }
        }
        val artists = info.optJSONArray("artists")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).ifBlank { null } } }
            ?.distinct()?.joinToString(", ")?.ifBlank { null }
        val genres = info.optJSONArray("genres")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).ifBlank { null } } }
            ?.firstOrNull()
        // Канал-источник сохраняется, только если это включено в настройках
        val keepSource = AppSettings.saveSource.value
        return LibraryStore.TagHints(
            title = str("track", "title"),
            artist = artists ?: str("artist", "creator", "uploader", "channel"),
            album = str("album"),
            trackNo = info.optInt("track_number", 0).takeIf { it > 0 },
            genre = genres ?: str("genre"),
            channel = if (keepSource) str("channel", "uploader") else null,
            // Ссылка на канал автора; если её нет — на сам ролик (или ссылку, по которой скачивали)
            channelUrl = if (keepSource) str("channel_url", "uploader_url", "webpage_url") ?: sourceUrl else null,
        )
    }

    /** Сведения о готовом файле: «Видео • 1080p • 3:54 • 25 МБ» или «MP3 • 256 кбит/с • 3:29 • 8 МБ». */
    private fun describe(e: LibraryStore.Entry): String {
        val ext = e.file.substringAfterLast('.', "").uppercase()
        val parts = ArrayList<String>()
        if (e.isAudio) {
            parts += ext.ifEmpty { tr(R.string.audio) }
            bitrateKbps(LibraryStore.fileOf(e))?.let { parts += tr(R.string.kbps, it) }
        } else {
            parts += tr(R.string.tab_video)
            if (e.height > 0) parts += "${minOf(e.width, e.height)}p"
        }
        if (e.durationMs > 0) parts += formatDuration(e.durationMs)
        parts += formatSize(e.size)
        return parts.joinToString(" • ")
    }

    private fun totals(list: List<LibraryStore.Entry>): String {
        val ms = list.sumOf { it.durationMs }
        val min = (ms / 60_000).toInt()
        val time = if (min >= 60) tr(R.string.hours_minutes, min / 60, min % 60) else formatDuration(ms)
        return "$time • ${formatSize(list.sumOf { it.size })}"
    }

    /** Битрейт звуковой дорожки (без учёта встроенной обложки). */
    private fun bitrateKbps(file: File): Int? = runCatching {
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.div(1000)
        } finally {
            r.release()
        }
    }.getOrNull()?.takeIf { it > 0 }

    /**
     * Формат видео: лучшая картинка нужной высоты + оригинальная звуковая дорожка
     * (+ дубляж на выбранных языках, если он есть у ролика). Если какой-то дорожки нет,
     * yt-dlp переходит к следующему варианту — с меньшим набором языков.
     */
    private fun videoFormat(height: Int, langs: List<String>): String {
        val h = if (height > 0) "[height<=$height]" else ""
        val extras = langs.take(3)
        // Все подмножества языков — от полного к пустому
        val subsets = (0 until (1 shl extras.size)).map { mask -> extras.filterIndexed { i, _ -> mask and (1 shl i) != 0 } }
            .sortedByDescending { it.size }
        val alts = ArrayList<String>()
        for (s in subsets) {
            // language_preference < 10 — не оригинальная дорожка (у оригинала 10), чтобы не взять её дважды
            val dub = s.joinToString("") { "+ba[language^=${it.substringBefore('-').replace("iw", "he")}][language_preference<?10]" }
            alts += "bv*$h[ext=mp4]+ba[ext=m4a]$dub"
            alts += "bv*$h+ba$dub"
        }
        alts += "b$h"
        alts += "b"
        return alts.joinToString("/")
    }

    /** info.json, который yt-dlp пишет рядом с файлом (имя совпадает до расширения). */
    private fun readInfo(dir: File, media: File): JSONObject? {
        val f = File(dir, media.nameWithoutExtension + ".info.json")
        return if (f.exists()) runCatching { JSONObject(f.readText()) }.getOrNull() else null
    }

    /** Эпизоды из info.json и отрезки SponsorBlock — в офлайн-хранилище по id видео. */
    private suspend fun saveVideoMeta(mediaId: Long, file: File, info: JSONObject?, opts: DownloadOptions) {
        val youtubeId = info?.optString("id")?.ifBlank { null }
            ?: Regex("""\[([\w-]{11})]$""").find(file.nameWithoutExtension)?.groupValues?.get(1)
        val chapters = if (opts.chapters) parseChapters(info) else emptyList()
        val segments = if (opts.sponsorBlock && youtubeId != null) SponsorBlock.fetch(youtubeId).orEmpty() else emptyList()
        if (youtubeId == null && chapters.isEmpty()) return
        VideoMetaStore.put(
            mediaId,
            VideoMeta(
                youtubeId = youtubeId,
                chapters = chapters,
                segments = segments,
                segmentsFetchedAt = if (opts.sponsorBlock) System.currentTimeMillis() else 0,
            ),
        )
    }

    private fun parseChapters(info: JSONObject?): List<Chapter> {
        val arr = info?.optJSONArray("chapters") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val c = arr.optJSONObject(i) ?: return@mapNotNull null
            Chapter(
                startMs = (c.optDouble("start_time", 0.0) * 1000).toLong(),
                endMs = (c.optDouble("end_time", 0.0) * 1000).toLong(),
                title = c.optString("title").ifBlank { tr(R.string.chapter_n, i + 1) },
            )
        }.filter { it.endMs > it.startMs }
    }

    private fun destinationTitle(line: String): String? {
        val idx = line.indexOf("Destination: ")
        if (idx < 0) return null
        return File(line.substring(idx + 13).trim()).nameWithoutExtension.replace(ID_SUFFIX, "").replace(INDEX_PREFIX, "")
    }

    private fun phase(line: String): String? = when {
        line.startsWith("[ExtractAudio]") -> tr(R.string.ph_extract)
        line.startsWith("[Merger]") -> tr(R.string.ph_merge)
        line.startsWith("[EmbedThumbnail]") || line.startsWith("[ThumbnailsConvertor]") -> tr(R.string.ph_thumbnail)
        line.startsWith("[Metadata]") -> tr(R.string.ph_metadata)
        line.startsWith("[EmbedSubtitle]") -> tr(R.string.ph_subtitles)
        line.startsWith("[Fixup") -> tr(R.string.ph_fixup)
        line.startsWith("[VideoConvertor]") || line.startsWith("[VideoRemuxer]") -> tr(R.string.ph_remux)
        line.startsWith("[download]") && line.contains('%') -> tr(R.string.downloading)
        else -> null
    }

    /** Понятное сообщение и тип ошибки. */
    private fun classifyError(t: Throwable?): Pair<String, FailureKind> {
        val msg = t?.message.orEmpty()
        val errLine = msg.lines().lastOrNull { it.contains("ERROR") }?.substringAfter("ERROR:")?.trim()
        return when {
            msg.contains("Unable to resolve host", true) || msg.contains("Network is unreachable", true) ||
                msg.contains("timed out", true) || msg.contains("Connection reset", true) ->
                tr(R.string.err_no_internet) to FailureKind.NETWORK
            msg.contains("Private video", true) -> tr(R.string.err_private) to FailureKind.UNAVAILABLE
            msg.contains("Video unavailable", true) || msg.contains("has been removed", true) ->
                tr(R.string.err_unavailable) to FailureKind.UNAVAILABLE
            msg.contains("members-only", true) || msg.contains("Join this channel", true) ->
                tr(R.string.err_members) to FailureKind.UNAVAILABLE
            else -> (errLine?.take(200) ?: tr(R.string.download_failed)) to FailureKind.PLUGIN
        }
    }
}
