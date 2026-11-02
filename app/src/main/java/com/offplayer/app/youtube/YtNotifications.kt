package com.offplayer.app.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Оповещение YouTube (колокольчик): новый ролик канала, ответ на комментарий и т. п. */
data class YtNotification(
    val text: String,
    val time: String,
    /** Аватар канала. */
    val avatar: String?,
    /** Превью ролика. */
    val thumbnail: String?,
    /** Ролик, который открывается по нажатию; null — оповещение без ролика. */
    val videoId: String?,
    /** Канал автора — открывается, если ролика нет. */
    val channelId: String?,
    /** Оповещение о Shorts — открывается в ленте Shorts. */
    val isShort: Boolean,
    val read: Boolean,
    /** Комментарий, о котором оповещение (ответ на ваш комментарий и т. п.): его id и параметры открытия ролика. */
    val commentId: String? = null,
    val watchParams: String? = null,
)

/** Оповещения аккаунта YouTube — тот же список, что под колокольчиком на сайте. */
object YtNotifications {
    private val _unseen = MutableStateFlow(0)
    /** Число непросмотренных оповещений — значок на колокольчике. */
    val unseen: StateFlow<Int> = _unseen.asStateFlow()

    suspend fun refreshUnseen() {
        if (!YouTubeAccount.signedIn.value) { _unseen.value = 0; return }
        val r = runCatching { withContext(Dispatchers.IO) { YouTubeApi.authPost("notification/get_unseen_count", JSONObject()) } }.getOrNull() ?: return
        _unseen.value = r.optInt("unseenCount", Regex(""""unseenCount":(\d+)""").find(r.toString())?.groupValues?.get(1)?.toIntOrNull() ?: 0)
    }

    private const val PREFS = "yt_notifications"
    private const val KEY_PUSH = "push"
    private const val KEY_POSTED = "posted2" // ключи сменились — новый набор, первая проверка только запоминает

    private fun prefs(context: android.content.Context) = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)

    /** Показывать оповещения YouTube уведомлениями приложения (по умолчанию — да). */
    fun pushEnabled(context: android.content.Context): Boolean = prefs(context).getBoolean(KEY_PUSH, true)

    fun setPushEnabled(context: android.content.Context, v: Boolean) {
        prefs(context).edit().putBoolean(KEY_PUSH, v).apply()
        YtNotifyWorker.schedule(context)
    }

    /**
     * Оповещения, о которых ещё не было уведомления. Признак «прочитано» не учитывается: YouTube сбрасывает его,
     * как только список открывают где угодно (в приложении YouTube, на сайте, у нас) — новые определяются
     * по запомненным ключам. Первый запуск только запоминает текущие, чтобы не завалить уведомлениями.
     */
    suspend fun listForPush(context: android.content.Context): List<YtNotification> {
        val all = list(markSeen = false)
        val p = prefs(context)
        val first = !p.contains(KEY_POSTED)
        val posted = p.getStringSet(KEY_POSTED, emptySet()).orEmpty()
        // Ключ — ролик (или канал, или комментарий): тексты одного оповещения у YouTube бывают разными
        val key = { n: YtNotification -> n.commentId ?: n.videoId ?: n.channelId ?: n.text.hashCode().toString() }
        val fresh = all.filter { key(it) !in posted }
        // Храним только ключи последних оповещений — список не растёт бесконечно
        p.edit().putStringSet(KEY_POSTED, (all.map(key) + posted).take(200).toSet()).apply()
        return if (first) emptyList() else fresh
    }

    suspend fun list(markSeen: Boolean = true): List<YtNotification> = withContext(Dispatchers.IO) {
        // Вкладка «Уведомления» приложения YouTube (Важное / Сегодня / …) — полнее меню колокольчика сайта
        val inbox = runCatching { YouTubeApi.authPost("browse", JSONObject().put("browseId", "FEnotifications_inbox")) }
        val fromInbox = ArrayList<YtNotification>()
        inbox.getOrNull()?.let { collect(it, fromInbox) }
        if (fromInbox.isNotEmpty()) {
            val all = ArrayList(fromInbox)
            var token = continuationOf(inbox.getOrNull())
            var pages = 0
            while (token != null && pages < 3) {
                val more = runCatching { YouTubeApi.authPost("browse", JSONObject().put("continuation", token)) }.getOrNull() ?: break
                val before = all.size
                collect(more, all)
                if (all.size == before) break
                token = continuationOf(more)
                pages++
            }
            if (markSeen) _unseen.value = 0
            return@withContext all.distinctBy { (it.videoId ?: it.channelId ?: "") + it.text }
        }
        val r = YouTubeApi.authPost(
            "notification/get_notification_menu",
            JSONObject().put("notificationsMenuRequestType", "NOTIFICATIONS_MENU_REQUEST_TYPE_INBOX"),
        )
        val out = ArrayList<YtNotification>()
        collect(r, out)
        // Длинный список приходит частями — догружаем продолжения, как при прокрутке меню на сайте
        var token = continuationOf(r)
        var pages = 0
        while (token != null && pages < 3) {
            val more = runCatching {
                YouTubeApi.authPost("notification/get_notification_menu", JSONObject().put("ctoken", token))
            }.getOrNull() ?: break
            val before = out.size
            collect(more, out)
            if (out.size == before) break
            token = continuationOf(more)
            pages++
        }
        // Список открыт — YouTube считает оповещения просмотренными
        if (markSeen) _unseen.value = 0
        out.distinctBy { (it.videoId ?: it.channelId ?: "") + it.text }
    }

    /** Токен следующей части списка: continuationCommand.token или getNotificationMenuRequest.ctoken. */
    private fun continuationOf(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("continuationCommand")?.optString("token")?.takeIf { it.isNotEmpty() }?.let { return it }
                node.optJSONObject("getNotificationMenuRequest")?.optString("ctoken")?.takeIf { it.isNotEmpty() }?.let { return it }
                // Продолжение списка на сайте — getNotificationMenuEndpoint.ctoken в последнем элементе раздела
                node.optJSONObject("getNotificationMenuEndpoint")?.optString("ctoken")?.takeIf { it.isNotEmpty() }?.let { return it }
                for (k in node.keys()) continuationOf(node.opt(k))?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) continuationOf(node.opt(i))?.let { return it }
        }
        return null
    }

    private fun collect(node: Any?, out: MutableList<YtNotification>) {
        when (node) {
            is JSONObject -> {
                val n = node.optJSONObject("notificationRenderer")
                if (n != null) parse(n)?.let(out::add)
                else node.keys().forEach { collect(node.opt(it), out) }
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), out)
        }
    }

    private fun parse(n: JSONObject): YtNotification? {
        val text = text(n.optJSONObject("shortMessage")).ifBlank { return null }
        // Ролик бывает не в watchEndpoint, а глубже (команды, ссылки на комментарий) — ищем по всему оповещению;
        // запасной путь — ID из адреса превью
        val all = n.toString()
        val thumb = lastThumb(n.optJSONObject("videoThumbnail"))
        val videoId = Regex(""""videoId":"([\w-]{11})"""").find(all)?.groupValues?.get(1)
            ?: Regex("""[?&]v=([\w-]{11})""").find(all)?.groupValues?.get(1)
            ?: Regex("""/vi(?:_webp)?/([\w-]{11})/""").find(thumb.orEmpty())?.groupValues?.get(1)
        val channelId = Regex(""""browseId":"(UC[\w-]{22})"""").find(all)?.groupValues?.get(1)
        return YtNotification(
            text = text,
            time = text(n.optJSONObject("sentTimeText")),
            avatar = lastThumb(n.optJSONObject("thumbnail")),
            thumbnail = thumb,
            videoId = videoId,
            channelId = channelId,
            isShort = "reelWatchEndpoint" in all || "/shorts/" in all,
            read = n.optBoolean("read", true),
            // Ссылка на комментарий — параметр lc (как в адресе youtube.com/watch?v=…&lc=…)
            commentId = Regex("""[?&]lc=([\w.-]+)""").find(all)?.groupValues?.get(1)
                ?: Regex(""""linkedCommentId":"([\w.-]+)""").find(all)?.groupValues?.get(1),
            watchParams = watchEndpointOf(n)?.optString("params")?.takeIf { it.isNotEmpty() },
        )
    }

    private fun watchEndpointOf(node: Any?): JSONObject? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("watchEndpoint")?.let { return it }
                for (k in node.keys()) watchEndpointOf(node.opt(k))?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) watchEndpointOf(node.opt(i))?.let { return it }
        }
        return null
    }

    private fun text(o: JSONObject?): String {
        if (o == null) return ""
        o.optString("simpleText").takeIf { it.isNotEmpty() }?.let { return it }
        val runs = o.optJSONArray("runs") ?: return ""
        return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
    }

    private fun lastThumb(o: JSONObject?): String? {
        val arr = o?.optJSONArray("thumbnails") ?: return null
        if (arr.length() == 0) return null
        val url = arr.optJSONObject(arr.length() - 1)?.optString("url").orEmpty()
        return when {
            url.isBlank() -> null
            url.startsWith("//") -> "https:$url"
            else -> url
        }
    }
}
