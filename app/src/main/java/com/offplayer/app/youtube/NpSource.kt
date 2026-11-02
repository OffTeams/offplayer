package com.offplayer.app.youtube

import android.icu.text.CompactDecimalFormat
import com.offplayer.app.L
import com.offplayer.app.R
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Продолжение списка NewPipe: страница + ссылка на сам список (поиск или вкладка канала). */
data class NpContinuation(val handler: ListLinkHandler, val page: Page)

/**
 * Источник на NewPipeExtractor: поиск, каналы и потоки роликов. Работает без аккаунта;
 * главная и подписки (нужен вход) берутся из [YouTubeApi].
 */
object NpSource {
    @Volatile private var ready = false

    private val yt get() = ServiceList.YouTube

    fun init() {
        if (ready) return
        synchronized(this) {
            if (ready) return
            val locale = L.ctx.resources.configuration.locales[0]
            val country = locale.country.ifEmpty { "US" }
            NewPipe.init(HttpDownloader, Localization(locale.language.ifEmpty { "en" }, country), ContentCountry(country))
            ready = true
        }
    }

    fun search(query: String, cont: NpContinuation?): YtPage {
        init()
        if (cont != null) {
            val more = SearchInfo.getMoreItems(yt, cont.handler as org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler, cont.page)
            return YtPage(more.items.toVideos(), next(cont.handler, more.nextPage))
        }
        val handler = yt.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        val info = SearchInfo.getInfo(yt, handler)
        return YtPage(info.relatedItems.toVideos(), next(handler, info.nextPage))
    }

    /** Вкладка канала: ролики или Shorts. */
    fun channel(id: String, shorts: Boolean, cont: NpContinuation?): YtPage {
        init()
        if (cont != null) {
            val more = ChannelTabInfo.getMoreItems(yt, cont.handler, cont.page)
            return YtPage(more.items.toVideos(), next(cont.handler, more.nextPage))
        }
        val info = ChannelInfo.getInfo(yt, "https://www.youtube.com/channel/$id")
        val wanted = if (shorts) ChannelTabs.SHORTS else ChannelTabs.VIDEOS
        val tab = info.tabs.firstOrNull { wanted in it.contentFilters } ?: return YtPage(emptyList(), null, info.name)
        val tabInfo = ChannelTabInfo.getInfo(yt, tab)
        return YtPage(tabInfo.relatedItems.toVideos(), next(tab, tabInfo.nextPage), info.name)
    }

    fun stream(id: String): StreamInfo {
        init()
        return StreamInfo.getInfo(yt, "https://www.youtube.com/watch?v=$id")
    }

    /** Похожие ролики для ленты под страницей ролика (без Shorts). */
    fun related(info: StreamInfo): List<YtVideo> = info.relatedItems.toVideos().filterNot { it.isShort }

    private fun next(handler: ListLinkHandler, page: Page?): NpContinuation? =
        if (Page.isValid(page)) NpContinuation(handler, page!!) else null

    private fun List<InfoItem>.toVideos(): List<YtVideo> = filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideo() }

    private val ID = Regex("""(?:v=|/shorts/|youtu\.be/|/live/)([\w-]{11})""")

    private fun StreamInfoItem.toVideo(): YtVideo? {
        val id = ID.find(url)?.groupValues?.get(1) ?: return null
        val live = streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM
        val short = isShortFormContent || url.contains("/shorts/")
        val views = viewCount.takeIf { it >= 0 }?.let { viewsText(it, live) }
        return YtVideo(
            id = id,
            title = name,
            channel = uploaderName.orEmpty(),
            channelId = uploaderUrl?.substringAfterLast("/channel/", "")?.takeIf { it.startsWith("UC") },
            duration = duration.takeIf { it > 0 && !live }?.let(::clock),
            meta = listOfNotNull(views, textualUploadDate?.takeIf { it.isNotBlank() }).joinToString(" • "),
            thumbnail = thumbnails.maxByOrNull { it.height }?.url ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            isShort = short,
            isLive = live,
            avatar = uploaderAvatars.maxByOrNull { it.height }?.url,
        )
    }

    fun clock(seconds: Long): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** «1,2 млн» на языке интерфейса. */
    fun viewsCompact(count: Long): String =
        CompactDecimalFormat.getInstance(L.ctx.resources.configuration.locales[0], CompactDecimalFormat.CompactStyle.SHORT).format(count)

    /** «1,2 млн просмотров» на языке интерфейса. */
    fun viewsText(count: Long, live: Boolean = false): String {
        val res = L.ctx.resources
        val locale: Locale = res.configuration.locales[0]
        val compact = CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT).format(count)
        val quantity = if (count > Int.MAX_VALUE) Int.MAX_VALUE else count.toInt()
        return res.getQuantityString(if (live) R.plurals.yt_watching else R.plurals.yt_views, quantity, compact)
    }

    /** HTTP для NewPipeExtractor: обычный HttpURLConnection, без сторонних библиотек. */
    private object HttpDownloader : Downloader() {
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

        override fun execute(request: Request): Response {
            val conn = URL(request.url()).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = request.httpMethod()
                conn.connectTimeout = 15_000
                conn.readTimeout = 20_000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", UA)
                request.headers().forEach { (k, values) ->
                    if (values.isNotEmpty()) conn.setRequestProperty(k, values.first())
                    values.drop(1).forEach { conn.addRequestProperty(k, it) }
                }
                request.dataToSend()?.let { body ->
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body) }
                }
                val code = conn.responseCode
                if (code == 429) throw ReCaptchaException("reCaptcha Challenge requested", request.url())
                val stream = if (code >= 400) conn.errorStream else conn.inputStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val headers = conn.headerFields.filterKeys { it != null }
                return Response(code, conn.responseMessage, headers, body, conn.url.toString())
            } finally {
                conn.disconnect()
            }
        }
    }
}
