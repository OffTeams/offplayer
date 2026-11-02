package com.offplayer.app.youtube

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/** Дорожка субтитров ролика YouTube (WebVTT). */
data class YtSubtitle(val language: String, val label: String, val url: String)

/**
 * Источник для роликов YouTube, играющих онлайн. Адрес «offstream://merge?v=…&a=…&ua=…»
 * — раздельные потоки видео и звука, которые склеиваются в один; «offstream://single?u=…» — готовый поток.
 * Параметры «sub» — дорожки субтитров (включаются кнопкой «CC» во время просмотра).
 * Все остальные адреса (файлы медиатеки и устройства) уходят обычной фабрике.
 */
@OptIn(UnstableApi::class)
class StreamMediaSourceFactory(context: Context) : MediaSource.Factory {
    private val fallback = DefaultMediaSourceFactory(context)

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory {
        fallback.setDrmSessionManagerProvider(provider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory {
        fallback.setLoadErrorHandlingPolicy(policy)
        return this
    }

    override fun getSupportedTypes(): IntArray = fallback.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri = mediaItem.localConfiguration?.uri ?: return fallback.createMediaSource(mediaItem)
        if (uri.scheme == LAZY) return lazySource(mediaItem, uri)
        if (uri.scheme != SCHEME) return fallback.createMediaSource(mediaItem)
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(uri.getQueryParameter("ua") ?: DEFAULT_UA)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        // Потоки качаются кусками — иначе YouTube урезает скорость и ролик подвисает
        val chunked = ChunkedDataSource.Factory(http)
        val subtitles = uri.getQueryParameters("sub").mapNotNull { s ->
            val parts = s.split('|', limit = 3).takeIf { it.size == 3 } ?: return@mapNotNull null
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(parts[2]))
                .setMimeType(MimeTypes.TEXT_VTT).setLanguage(parts[0]).setLabel(parts[1]).build()
        }
        // Элемент очереди сохраняет id и название — меняется только адрес загрузки
        fun item(u: String, mime: String? = null, subs: List<MediaItem.SubtitleConfiguration> = emptyList()) =
            mediaItem.buildUpon().setUri(u).setSubtitleConfigurations(subs).apply { if (mime != null) setMimeType(mime) }.build()
        // Субтитры подключает обычная фабрика — она сама разбирает WebVTT. Не загрузившаяся дорожка
        // (YouTube не отдал перевод) — пустая, а не ошибка: иначе субтитры зависали и не выключались
        val withSubs = DefaultMediaSourceFactory(SafeSubtitlesDataSource.Factory(chunked))
        return when (uri.host) {
            "merge" -> MergingMediaSource(
                withSubs.createMediaSource(item(uri.getQueryParameter("v")!!, MimeTypes.VIDEO_MP4, subtitles)),
                ProgressiveMediaSource.Factory(chunked).createMediaSource(item(uri.getQueryParameter("a")!!)),
            )
            else -> {
                val u = uri.getQueryParameter("u")!!
                // Прямой эфир YouTube отдаёт как HLS
                val hls = u.contains(".m3u8") || u.contains("/manifest/hls")
                if (hls) DefaultMediaSourceFactory(http).createMediaSource(item(u, MimeTypes.APPLICATION_M3U8))
                else withSubs.createMediaSource(item(u, null, subtitles))
            }
        }
    }

    /**
     * Элемент онлайн-плейлиста: адреса потоков получаются только когда плеер начинает его загружать,
     * поэтому очередь из сотни роликов стартует сразу. «ytres://audio?id=…» — только звук, «ytres://av?id=…» — видео и звук.
     */
    private fun lazySource(mediaItem: MediaItem, uri: Uri): MediaSource {
        val id = uri.getQueryParameter("id")!!
        val http = DefaultHttpDataSource.Factory().setUserAgent(DEFAULT_UA).setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000)
        val resolving = androidx.media3.datasource.ResolvingDataSource.Factory(ChunkedDataSource.Factory(http)) { spec ->
            val p = YouTubeStreams.partsBlocking(id)
            val real = if (spec.uri.getQueryParameter("part") == "v") p.video else p.audio
            spec.withUri(Uri.parse(real)).let { s -> p.ua?.let { s.withAdditionalHeaders(mapOf("User-Agent" to it)) } ?: s }
        }
        fun part(p: String, mime: String) = mediaItem.buildUpon().setUri(uri.buildUpon().appendQueryParameter("part", p).build()).setMimeType(mime).build()
        val audio = ProgressiveMediaSource.Factory(resolving).createMediaSource(part("a", MimeTypes.AUDIO_MP4))
        return if (uri.host == "audio") audio
        else MergingMediaSource(ProgressiveMediaSource.Factory(resolving).createMediaSource(part("v", MimeTypes.VIDEO_MP4)), audio)
    }

    companion object {
        const val SCHEME = "offstream"
        const val LAZY = "ytres"

        fun lazyUri(youtubeId: String, video: Boolean, thumb: String?): Uri = Uri.Builder().scheme(LAZY)
            .authority(if (video) "av" else "audio").appendQueryParameter("id", youtubeId)
            .apply { if (!thumb.isNullOrEmpty()) appendQueryParameter("thumb", thumb) }.build()
        private const val DEFAULT_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"

        fun mergedUri(video: String, audio: String, ua: String?, thumb: String?, subs: List<YtSubtitle> = emptyList()): Uri =
            Uri.Builder().scheme(SCHEME).authority("merge")
                .appendQueryParameter("v", video).appendQueryParameter("a", audio)
                .extras(ua, thumb, subs).build()

        fun singleUri(url: String, ua: String?, thumb: String?, subs: List<YtSubtitle> = emptyList()): Uri =
            Uri.Builder().scheme(SCHEME).authority("single")
                .appendQueryParameter("u", url)
                .extras(ua, thumb, subs).build()

        private fun Uri.Builder.extras(ua: String?, thumb: String?, subs: List<YtSubtitle>) = apply {
            if (!ua.isNullOrEmpty()) appendQueryParameter("ua", ua)
            if (!thumb.isNullOrEmpty()) appendQueryParameter("thumb", thumb)
            subs.forEach { appendQueryParameter("sub", "${it.language}|${it.label.replace('|', ' ')}|${it.url}") }
        }

        /** Превью ролика для мини-плеера и эпизодов (кадр из потока не достать без загрузки видео). */
        fun thumbnailOf(uri: Uri): String? = if (uri.scheme == SCHEME || uri.scheme == LAZY) uri.getQueryParameter("thumb") else null
    }
}
