package com.offplayer.app.youtube

import com.offplayer.app.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Ролик в списке YouTube. */
data class YtVideo(
    val id: String,
    val title: String,
    val channel: String,
    val channelId: String?,
    /** «10:07»; null — прямой эфир или длительность неизвестна. */
    val duration: String?,
    /** «615 тыс. просмотров • 3 дня назад». */
    val meta: String,
    val thumbnail: String,
    val isShort: Boolean = false,
    val isLive: Boolean = false,
    /** Аватар канала. */
    val avatar: String? = null,
) {
    val url: String get() = if (isShort) "https://www.youtube.com/shorts/$id" else "https://www.youtube.com/watch?v=$id"
}

/** Страница списка; continuation — продолжение для бесконечной прокрутки (токен youtubei или [NpContinuation]). */
/** chips — темы под шапкой главной («Музыка», «Видеоигры»…): название и токен ленты темы. */
data class YtPage(val items: List<YtVideo>, val continuation: Any?, val title: String? = null, val chips: List<Pair<String, String>> = emptyList())

/** Канал из подписок аккаунта; hasNew — есть новые ролики (синяя точка, как в YouTube). */
data class YtChannel(val id: String, val name: String, val avatar: String?, val hasNew: Boolean)

/** Что показываем: главная, подписки, поиск, канал. */
sealed interface YtFeed {
    data object Home : YtFeed
    data object Subscriptions : YtFeed
    /** История просмотра аккаунта. */
    data object History : YtFeed
    /** Отдельная вертикальная лента Shorts (в остальных списках Shorts не показываются). */
    data object Shorts : YtFeed
    /** Тема главной (чип под шапкой): лента по токену из ответа YouTube. */
    data class Chip(val title: String, val token: String) : YtFeed
    data class Search(val query: String) : YtFeed
    data class Channel(val id: String, val name: String) : YtFeed
}

/**
 * Клиент внутреннего API сайта YouTube (youtubei/v1) — те же запросы, что делает сайт.
 * Отсюда берутся только списки роликов; потоки для воспроизведения получает yt-dlp ([YouTubeStreams]).
 */
object YouTubeApi {
    private const val CLIENT_VERSION = "2.20251030.01.00"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
    /** Вкладка «Видео» на странице канала. */
    private const val CHANNEL_VIDEOS = "EgZ2aWRlb3PyBgQKAjoA"

    suspend fun load(feed: YtFeed, continuation: Any? = null): YtPage = withContext(Dispatchers.IO) {
        // Поиск и каналы — через NewPipeExtractor; главной и подпискам нужен аккаунт, их даёт только youtubei
        when (feed) {
            is YtFeed.Search -> return@withContext NpSource.search(feed.query, continuation as? NpContinuation)
            is YtFeed.Channel -> return@withContext NpSource.channel(feed.id, shorts = false, cont = continuation as? NpContinuation)
            else -> Unit
        }
        val body = JSONObject().put("context", context())
        val endpoint = if (feed is YtFeed.Search) "search" else "browse"
        if (continuation is String) {
            body.put("continuation", continuation)
        } else when (feed) {
            YtFeed.Home -> body.put("browseId", "FEwhat_to_watch")
            YtFeed.Subscriptions -> body.put("browseId", "FEsubscriptions")
            YtFeed.History -> body.put("browseId", "FEhistory")
            YtFeed.Shorts -> error("Shorts идут через shorts()")
            is YtFeed.Chip -> body.put("continuation", feed.token)
            is YtFeed.Search -> body.put("query", feed.query)
            is YtFeed.Channel -> body.put("browseId", feed.id).put("params", CHANNEL_VIDEOS)
        }
        parse(post(endpoint, body))
    }

    /** Каналы из подписок — раздел «Подписки» бокового меню сайта (запрос guide). */
    suspend fun subscribedChannels(): List<YtChannel> = withContext(Dispatchers.IO) {
        val r = authPost("guide", JSONObject())
        val out = LinkedHashMap<String, YtChannel>()
        fun collect(node: Any?) {
            when (node) {
                is JSONObject -> {
                    node.optJSONObject("guideEntryRenderer")?.let { e ->
                        val id = e.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
                        if (id.startsWith("UC") && id !in out) {
                            val thumbs = e.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                            val photo = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")?.takeIf { it.isNotEmpty() }
                                ?.let { if (it.startsWith("//")) "https:$it" else it }
                            out[id] = YtChannel(id, text(e.optJSONObject("formattedTitle")).ifEmpty { e.optString("title") }, photo,
                                e.optString("presentationStyle").contains("NEW_CONTENT"))
                        }
                    }
                    node.keys().forEach { collect(node.opt(it)) }
                }
                is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i))
            }
        }
        // Только раздел подписок: в остальных разделах меню тоже бывают ссылки на каналы (например, свой)
        fun find(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val sub = node.optJSONObject("guideSubscriptionsSectionRenderer")
                    if (sub != null) collect(sub) else node.keys().forEach { find(node.opt(it)) }
                }
                is JSONArray -> for (i in 0 until node.length()) find(node.opt(i))
            }
        }
        find(r)
        // Сначала каналы с новыми роликами, как в приложении YouTube
        out.values.sortedByDescending { it.hasNew }
    }

    /**
     * Лента Shorts — та же бесконечная последовательность, что на youtube.com/shorts
     * (после входа — подобранная под аккаунт). Возвращает id роликов и токен продолжения.
     */
    suspend fun shorts(continuation: String?): Pair<List<String>, String?> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("context", context()).put("sequenceParams", continuation ?: "CA8%3D")
        val r = post("reel/reel_watch_sequence", body)
        val entries = r.optJSONArray("entries") ?: JSONArray()
        val ids = (0 until entries.length()).mapNotNull { i ->
            entries.optJSONObject(i)?.optJSONObject("command")?.optJSONObject("reelWatchEndpoint")?.optString("videoId")?.takeIf { it.length == 11 }
        }
        ids to r.optJSONObject("continuationEndpoint")?.optJSONObject("continuationCommand")?.optString("token")?.takeIf { it.isNotEmpty() }
    }

    /** Запрос к youtubei; после входа — от имени аккаунта, без него — как гость. */
    fun call(endpoint: String, body: JSONObject): JSONObject = post(endpoint, body.put("context", context()))

    /** Запрос от имени аккаунта (лайк, подписка, комментарий); без входа — ошибка. */
    fun authPost(endpoint: String, body: JSONObject): JSONObject {
        check(YouTubeAccount.signedIn.value) { "Not signed in" }
        return post(endpoint, body.put("context", context()))
    }

    private fun context(): JSONObject {
        val locale = L.ctx.resources.configuration.locales[0]
        return JSONObject().put(
            "client",
            JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", CLIENT_VERSION)
                .put("hl", locale.language.ifEmpty { "en" })
                .put("gl", locale.country.ifEmpty { "US" }),
        )
    }

    /** Запрос от имени аккаунта сессии с номером [index] (для списка аккаунтов). */
    fun callAs(index: Int, endpoint: String, body: JSONObject): JSONObject = post(endpoint, body.put("context", context()), index)

    private fun post(endpoint: String, body: JSONObject, authUser: Int? = null): JSONObject {
        val conn = URL("https://www.youtube.com/youtubei/v1/$endpoint?prettyPrint=false").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Origin", YouTubeAccount.ORIGIN)
            conn.setRequestProperty("X-YouTube-Client-Name", "1")
            conn.setRequestProperty("X-YouTube-Client-Version", CLIENT_VERSION)
            (if (authUser != null) YouTubeAccount.authHeaders(authUser) else YouTubeAccount.authHeaders()).forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return JSONObject(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }

    // ---------- Разбор ответа ----------

    private val VIDEO_RENDERERS = setOf("videoRenderer", "gridVideoRenderer", "compactVideoRenderer", "videoWithContextRenderer")

    /** Обходит ответ целиком и собирает ролики любого известного вида — разметка YouTube часто меняется. */
    fun parse(root: JSONObject): YtPage {
        val items = LinkedHashMap<String, YtVideo>()
        var continuation: String? = null
        val chips = ArrayList<Pair<String, String>>()
        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val keys = node.keys().asSequence().toList()
                    for (k in keys) {
                        val v = node.opt(k)
                        when {
                            k in VIDEO_RENDERERS && v is JSONObject -> classic(v)?.let { items.putIfAbsent(it.id, it) }
                            k == "lockupViewModel" && v is JSONObject -> lockup(v)?.let { items.putIfAbsent(it.id, it) }
                            k == "shortsLockupViewModel" && v is JSONObject -> short(v)?.let { items.putIfAbsent(it.id, it) }
                            k == "reelItemRenderer" && v is JSONObject -> reel(v)?.let { items.putIfAbsent(it.id, it) }
                            // Продолжение ленты — первое в блоке continuationItemRenderer; другие токены принадлежат фильтрам и меню
                            k == "continuationItemRenderer" && v is JSONObject -> if (continuation == null) continuation = token(v)
                            // Меню, всплывающие окна и прочие служебные ветки роликов не содержат
                            // Темы главной: у выбранной («Все») токена нет — её покажет кнопка «Главная»
                            k == "chipCloudChipRenderer" && v is JSONObject -> {
                                val title = text(v.optJSONObject("text"))
                                val tok = token(v.optJSONObject("navigationEndpoint"))
                                if (title.isNotEmpty() && tok != null && !v.optBoolean("isSelected")) chips += title to tok
                            }
                            k == "menu" || k == "topbar" || k == "frameworkUpdates" -> Unit
                            else -> walk(v)
                        }
                    }
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
            }
        }
        walk(root)
        return YtPage(items.values.toList(), continuation, chips = chips.distinctBy { it.first })
    }

    private fun token(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("continuationCommand")?.optString("token")?.takeIf { it.isNotEmpty() }?.let { return it }
                for (k in node.keys()) token(node.opt(k))?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) token(node.opt(i))?.let { return it }
        }
        return null
    }

    private fun text(o: JSONObject?): String {
        o ?: return ""
        o.optString("simpleText").takeIf { it.isNotEmpty() }?.let { return it }
        o.optString("content").takeIf { it.isNotEmpty() }?.let { return it }
        val runs = o.optJSONArray("runs") ?: return ""
        return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
    }

    private fun bestThumb(arr: JSONArray?, id: String): String {
        if (arr != null && arr.length() > 0) {
            val last = arr.optJSONObject(arr.length() - 1)
            last?.optString("url")?.takeIf { it.isNotEmpty() }?.let { return if (it.startsWith("//")) "https:$it" else it }
        }
        return "https://i.ytimg.com/vi/$id/hqdefault.jpg"
    }

    /** Первый browseId канала (UC…) внутри узла. */
    private fun channelId(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("browseEndpoint")?.optString("browseId")?.takeIf { it.startsWith("UC") }?.let { return it }
                for (k in node.keys()) channelId(node.opt(k))?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) channelId(node.opt(i))?.let { return it }
        }
        return null
    }

    /** Аватар канала — первая картинка с серверов аватаров YouTube (yt3.*) внутри узла. */
    private val AVATAR = Regex(""""url":"(https:\\?/\\?/yt3\.[^"]+)"""")
    private fun avatar(node: JSONObject?): String? =
        node?.let { AVATAR.find(it.toString())?.groupValues?.get(1)?.replace("\\/", "/") }

    private fun classic(v: JSONObject): YtVideo? {
        val id = v.optString("videoId").takeIf { it.isNotEmpty() } ?: return null
        val owner = v.optJSONObject("ownerText") ?: v.optJSONObject("shortBylineText") ?: v.optJSONObject("longBylineText")
        val duration = text(v.optJSONObject("lengthText")).ifEmpty { null }
        val views = text(v.optJSONObject("shortViewCountText")).ifEmpty { text(v.optJSONObject("viewCountText")) }
        val published = text(v.optJSONObject("publishedTimeText"))
        val live = duration == null && v.optJSONArray("badges")?.toString()?.contains("LIVE") == true
        return YtVideo(
            id = id,
            title = text(v.optJSONObject("title")).ifEmpty { text(v.optJSONObject("headline")) },
            channel = text(owner),
            channelId = channelId(owner),
            duration = duration,
            meta = listOf(views, published).filter { it.isNotEmpty() }.joinToString(" • "),
            thumbnail = bestThumb(v.optJSONObject("thumbnail")?.optJSONArray("thumbnails"), id),
            isLive = live,
            avatar = avatar(v.optJSONObject("channelThumbnailSupportedRenderers")) ?: avatar(v.optJSONObject("avatar")),
        )
    }

    private fun lockup(v: JSONObject): YtVideo? {
        if (v.optString("contentType") != "LOCKUP_CONTENT_TYPE_VIDEO") return null
        val id = v.optString("contentId").takeIf { it.isNotEmpty() } ?: return null
        val md = v.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
        val rows = md?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
        // Первая строка — канал, следующие — просмотры и дата
        val lines = (0 until (rows?.length() ?: 0)).map { i ->
            val parts = rows!!.optJSONObject(i)?.optJSONArray("metadataParts")
            (0 until (parts?.length() ?: 0)).mapNotNull { p -> text(parts!!.optJSONObject(p)?.optJSONObject("text")).ifEmpty { null } }
        }
        val thumbVm = v.optJSONObject("contentImage")?.optJSONObject("thumbnailViewModel")
        val badge = Regex(""""thumbnailBadgeViewModel":\{"text":"([^"]+)"""").find(thumbVm?.optJSONArray("overlays")?.toString().orEmpty())?.groupValues?.get(1)
        val isDuration = badge?.matches(Regex("""\d+(:\d{2})+""")) == true
        return YtVideo(
            id = id,
            title = text(md?.optJSONObject("title")),
            channel = lines.firstOrNull()?.firstOrNull().orEmpty(),
            channelId = channelId(md),
            duration = badge.takeIf { isDuration },
            meta = lines.drop(1).flatten().joinToString(" • "),
            thumbnail = bestThumb(thumbVm?.optJSONObject("image")?.optJSONArray("sources"), id),
            isLive = badge != null && !isDuration && thumbVm?.toString()?.contains("LIVE") == true,
            avatar = avatar(md?.optJSONObject("image")),
        )
    }

    private fun short(v: JSONObject): YtVideo? {
        val id = v.optJSONObject("onTap")?.optJSONObject("innertubeCommand")?.optJSONObject("reelWatchEndpoint")?.optString("videoId")
            ?.takeIf { it.isNotEmpty() } ?: return null
        val om = v.optJSONObject("overlayMetadata")
        return YtVideo(
            id = id,
            title = text(om?.optJSONObject("primaryText")),
            channel = "",
            channelId = null,
            duration = null,
            meta = text(om?.optJSONObject("secondaryText")),
            thumbnail = bestThumb(v.optJSONObject("thumbnailViewModel")?.optJSONObject("thumbnailViewModel")?.optJSONObject("image")?.optJSONArray("sources")
                ?: v.optJSONObject("thumbnail")?.optJSONArray("sources"), id),
            isShort = true,
        )
    }

    private fun reel(v: JSONObject): YtVideo? {
        val id = v.optString("videoId").takeIf { it.isNotEmpty() } ?: return null
        return YtVideo(
            id = id, title = text(v.optJSONObject("headline")), channel = "", channelId = null, duration = null,
            meta = text(v.optJSONObject("viewCountText")),
            thumbnail = bestThumb(v.optJSONObject("thumbnail")?.optJSONArray("thumbnails"), id), isShort = true,
        )
    }
}
