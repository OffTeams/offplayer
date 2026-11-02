package com.offplayer.app.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem

/** Оценка ролика. */
enum class YtRating { LIKE, DISLIKE, NONE }

/** Комментарий: автор с аватаром, текст; лайки и ответы — как их пишет YouTube («220 тыс.»). */
data class YtComment(
    val author: String,
    val avatar: String?,
    val text: String,
    val likes: String,
    val date: String,
    val replies: String,
    /** id комментария и токен ветки ответов (null — ответов нет или это сам ответ). */
    val id: String = "",
    val repliesToken: String? = null,
    /** Параметры ответа на этот комментарий (createReplyParams); null — ответить нельзя. */
    val replyParams: String? = null,
    /** Действия оценки комментария (performCommentActionEndpoint) и текущая оценка аккаунта: 1, −1 или 0. */
    val likeAction: String? = null,
    val unlikeAction: String? = null,
    val dislikeAction: String? = null,
    val undislikeAction: String? = null,
    val myRating: Int = 0,
)

/** Страница комментариев; total — «38» или «1,2 тыс.»; next — токен продолжения. */
class YtComments(val items: List<YtComment>, val total: String?, val next: String?, val byId: Map<String, YtComment> = emptyMap())


/**
 * Действия от имени аккаунта (те же запросы youtubei, что делает сайт) и чтение комментариев (NewPipe).
 * Без входа доступно только чтение.
 */
object YouTubeActions {
    private val yt get() = ServiceList.YouTube

    suspend fun rate(videoId: String, rating: YtRating) = withContext(Dispatchers.IO) {
        val ep = when (rating) {
            YtRating.LIKE -> "like/like"
            YtRating.DISLIKE -> "like/dislike"
            YtRating.NONE -> "like/removelike"
        }
        YouTubeApi.authPost(ep, JSONObject().put("target", JSONObject().put("videoId", videoId)))
    }

    /** Текущая оценка ролика и подписка на канал — из страницы ролика от имени аккаунта. */
    suspend fun state(videoId: String): Pair<YtRating, Boolean?> = withContext(Dispatchers.IO) {
        val page = YouTubeApi.authPost("next", JSONObject().put("videoId", videoId))
        val rating = when (findString(page, "likeStatus")) {
            "LIKE" -> YtRating.LIKE
            "DISLIKE" -> YtRating.DISLIKE
            else -> YtRating.NONE
        }
        rating to findBool(page, "subscribed")
    }

    private fun findBool(node: Any?, key: String): Boolean? {
        when (node) {
            is JSONObject -> {
                (node.opt(key) as? Boolean)?.let { return it }
                for (k in node.keys()) findBool(node.opt(k), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findBool(node.opt(i), key)?.let { return it }
        }
        return null
    }

    suspend fun subscribe(channelId: String, on: Boolean) = withContext(Dispatchers.IO) {
        YouTubeApi.authPost(
            if (on) "subscription/subscribe" else "subscription/unsubscribe",
            JSONObject().put("channelIds", JSONArray().put(channelId)),
        )
    }

    /**
     * Комментарии — тем же запросом, что сайт YouTube (NewPipe не находит аватары авторов в новом формате).
     * Первая страница: страница ролика → токен блока комментариев → сами комментарии.
     */
    private val prefetchScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val firstPages = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<YtComments>>()

    /** Первая страница комментариев грузится заранее — пока получается поток ролика, а не после открытия страницы. */
    private val watchScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val markedWatched = java.util.Collections.synchronizedSet(HashSet<String>())

    /**
     * Отметить ролик просмотренным в аккаунте, чтобы он попал в историю YouTube (так же делает yt-dlp
     * с --mark-watched): со страницы ролика берётся адрес статистики просмотра и вызывается от имени аккаунта.
     * Один раз за запуск на ролик; без входа ничего не делает.
     */
    fun markWatched(videoId: String, short: Boolean = false) {
        if (!YouTubeAccount.signedIn.value || !markedWatched.add(videoId)) return
        watchScope.launch {
            runCatching {
                val headers = YouTubeAccount.authHeaders()
                // Shorts — со страницы Shorts: тогда YouTube записывает просмотр в историю как Shorts, а не как ролик
                val page = httpGet(if (short) "https://www.youtube.com/shorts/$videoId?hl=en" else "https://www.youtube.com/watch?v=$videoId&hl=en", headers)
                val base = WATCH_URL.find(page)?.groupValues?.get(1) ?: error("no playback url")
                val url = base.replace("\\u0026", "&").replace("\\/", "/")
                val cpn = (1..16).map { CPN_CHARS.random() }.joinToString("")
                httpGet("$url&ver=2&cpn=$cpn", headers)
            }.onFailure { markedWatched.remove(videoId) }
        }
    }

    private val WATCH_URL = Regex("\"videostatsPlaybackUrl\"\\s*:\\s*\\{\\s*\"baseUrl\"\\s*:\\s*\"([^\"]+)\"")
    private const val CPN_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"

    private fun httpGet(url: String, headers: Map<String, String>): String {
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
            setRequestProperty("Accept-Language", "en")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    fun prefetch(videoId: String) {
        firstPages.computeIfAbsent(videoId) { prefetchScope.async { load(videoId, null) } }
        // Подписка и оценка — тоже заранее: на странице ролика кнопка «Подписаться» сразу в нужном состоянии
        if (YouTubeAccount.signedIn.value) states.computeIfAbsent(videoId) { prefetchScope.async { state(videoId) } }
    }

    private val states = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<Pair<YtRating, Boolean?>>>()

    /** Подписка и оценка: подготовленные заранее (если есть), иначе — запрос. */
    suspend fun stateFast(videoId: String): Pair<YtRating, Boolean?> =
        states.remove(videoId)?.let { runCatching { it.await() }.getOrNull() } ?: state(videoId)

    /** watchParams — параметры ссылки на комментарий: YouTube ставит этот комментарий первым в списке. */
    suspend fun comments(videoId: String, next: String?, watchParams: String? = null): YtComments =
        if (next == null && watchParams != null) load(videoId, null, watchParams)
        else if (next == null) firstPages.remove(videoId)?.let { runCatching { it.await() }.getOrNull() } ?: load(videoId, null)
        else load(videoId, next)

    private suspend fun load(videoId: String, next: String?, watchParams: String? = null): YtComments = withContext(Dispatchers.IO) {
        val token = next ?: findCommentsToken(YouTubeApi.call("next", JSONObject().put("videoId", videoId).apply { watchParams?.let { put("params", it) } }))
            ?: return@withContext YtComments(emptyList(), null, null)
        val r = YouTubeApi.call("next", JSONObject().put("continuation", token))
        val page = parseComments(r)
        // Число комментариев — в шапке блока (только на первой странице)
        val total = if (next == null) findString(r, "countText")
            ?: Regex(""""countText":\{"runs":\[\{"text":"([^"]+)"""").find(r.toString())?.groupValues?.get(1) else null
        YtComments(page.items, total, page.next, page.byId)
    }

    /** Ветка ответов на комментарий (и её продолжение — «Показать другие ответы»). */
    suspend fun replies(token: String): YtComments = withContext(Dispatchers.IO) {
        parseComments(YouTubeApi.call("next", JSONObject().put("continuation", token)))
    }

    /**
     * Комментарии из ответа youtubei: порядок и ветки ответов — из списка (commentThreadRenderer / commentViewModel),
     * тексты, авторы и лайки — из frameworkUpdates (commentEntityPayload) по id комментария.
     * Продолжение — continuationItemRenderer самого списка, а не токены веток ответов внутри него.
     */
    private fun parseComments(r: JSONObject): YtComments {
        val payloads = LinkedHashMap<String, JSONObject>()
        // Панель действий комментария (там параметры ответа) — по ключу toolbarSurfaceKey
        val toolbars = HashMap<String, String>()
        val toolbarCmds = HashMap<String, JSONObject>()
        val likeStates = HashMap<String, String>()
        val mutations = r.optJSONObject("frameworkUpdates")?.optJSONObject("entityBatchUpdate")?.optJSONArray("mutations") ?: JSONArray()
        for (i in 0 until mutations.length()) {
            val m = mutations.optJSONObject(i) ?: continue
            m.optJSONObject("payload")?.optJSONObject("engagementToolbarSurfaceEntityPayload")?.let { t ->
                findString(t, "createReplyParams")?.let { toolbars[m.optString("entityKey")] = it }
                toolbarCmds[m.optString("entityKey")] = t
            }
            m.optJSONObject("payload")?.optJSONObject("engagementToolbarStateEntityPayload")?.let { s ->
                likeStates[m.optString("entityKey")] = s.optString("likeState")
            }
            val p = m.optJSONObject("payload")?.optJSONObject("commentEntityPayload") ?: continue
            val id = p.optJSONObject("properties")?.optString("commentId").orEmpty()
            payloads[id.ifEmpty { "#$i" }] = p
        }
        val toolbarOf = HashMap<String, String>() // id комментария → ключ его панели действий
        val stateOf = HashMap<String, String>() // id комментария → ключ состояния оценки
        val order = ArrayList<Pair<String, String?>>() // id комментария → токен ответов
        var nextToken: String? = null
        val endpoints = r.optJSONArray("onResponseReceivedEndpoints") ?: JSONArray()
        for (e in 0 until endpoints.length()) {
            val ep = endpoints.optJSONObject(e) ?: continue
            val list = ep.optJSONObject("reloadContinuationItemsCommand")?.optJSONArray("continuationItems")
                ?: ep.optJSONObject("appendContinuationItemsAction")?.optJSONArray("continuationItems") ?: continue
            for (k in 0 until list.length()) {
                val item = list.optJSONObject(k) ?: continue
                val thread = item.optJSONObject("commentThreadRenderer")
                val vm = (thread ?: item).optJSONObject("commentViewModel")?.let { it.optJSONObject("commentViewModel") ?: it }
                val cont = item.optJSONObject("continuationItemRenderer")
                when {
                    vm != null -> {
                        val id = vm.optString("commentId")
                        vm.optString("toolbarSurfaceKey").takeIf { it.isNotEmpty() }?.let { toolbarOf[id] = it }
                        vm.optString("toolbarStateKey").takeIf { it.isNotEmpty() }?.let { stateOf[id] = it }
                        val rt = thread?.optJSONObject("replies")?.let { firstToken(it) }
                        if (id.isNotEmpty()) order += id to rt
                    }
                    cont != null -> nextToken = firstToken(cont)
                }
            }
        }
        val items = ArrayList<YtComment>()
        val ids = if (order.isNotEmpty()) order else payloads.keys.map { it to null }
        val tokens = ids.toMap()
        // Все комментарии ответа по id — среди них и ответ, открытый по ссылке (он приходит внутри ветки)
        val byId = LinkedHashMap<String, YtComment>()
        for (id in payloads.keys) {
            val p = payloads[id] ?: continue
            val repliesToken = tokens[id]
            val props = p.optJSONObject("properties")
            val author = p.optJSONObject("author")
            val toolbar = p.optJSONObject("toolbar")
            byId[id] = YtComment(
                author = author?.optString("displayName").orEmpty(),
                avatar = author?.optString("avatarThumbnailUrl")?.takeIf { it.startsWith("http") },
                text = props?.optJSONObject("content")?.optString("content").orEmpty(),
                likes = toolbar?.optString("likeCountNotliked").orEmpty().takeIf { it != "0" }.orEmpty(),
                date = props?.optString("publishedTime").orEmpty(),
                replies = toolbar?.optString("replyCount").orEmpty().takeIf { it != "0" }.orEmpty(),
                id = id,
                repliesToken = repliesToken,
                replyParams = toolbarOf[id]?.let { toolbars[it] },
                likeAction = toolbarOf[id]?.let { toolbarCmds[it] }?.let { cmd(it, "likeCommand") },
                unlikeAction = toolbarOf[id]?.let { toolbarCmds[it] }?.let { cmd(it, "unlikeCommand") },
                dislikeAction = toolbarOf[id]?.let { toolbarCmds[it] }?.let { cmd(it, "dislikeCommand") },
                undislikeAction = toolbarOf[id]?.let { toolbarCmds[it] }?.let { cmd(it, "undislikeCommand") },
                myRating = when (stateOf[id]?.let { likeStates[it] }) {
                    "TOOLBAR_LIKE_STATE_LIKED" -> 1
                    "TOOLBAR_LIKE_STATE_DISLIKED" -> -1
                    else -> 0
                },
            )
        }
        for ((id, _) in ids) byId[id]?.let { items += it }
        return YtComments(items, null, nextToken ?: if (order.isEmpty()) lastContinuation(r) else null, byId)
    }

    /** Действие оценки из панели комментария: …Command → performCommentActionEndpoint.action. */
    private fun cmd(toolbar: JSONObject, key: String): String? =
        toolbar.optJSONObject(key)?.let { findString(it, "action") }

    /** Оценить комментарий (лайк, дизлайк или снять оценку) от имени аккаунта. */
    suspend fun rateComment(action: String) = withContext(Dispatchers.IO) {
        YouTubeApi.authPost("comment/perform_comment_action", JSONObject().put("actions", JSONArray().put(action)))
    }

    /** Первый токен продолжения внутри узла. */
    private fun firstToken(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("continuationCommand")?.optString("token")?.takeIf { it.isNotEmpty() }?.let { return it }
                for (k in node.keys()) firstToken(node.opt(k))?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) firstToken(node.opt(i))?.let { return it }
        }
        return null
    }

    /** Продолжение списка — токен последнего continuationItemRenderer в ответе. */
    private fun lastContinuation(node: JSONObject): String? =
        Regex(""""continuationItemRenderer":\{.*?"token":"([^"]+)"""").findAll(node.toString()).lastOrNull()?.groupValues?.get(1)
    /**
     * Новый комментарий. Параметры отправки сайт получает из блока комментариев страницы ролика:
     * запрашиваем его так же и отправляем текст.
     */
    /** Ответ на комментарий от имени аккаунта. */
    suspend fun postReply(replyParams: String, text: String) = withContext(Dispatchers.IO) {
        YouTubeApi.authPost("comment/create_comment_reply", JSONObject().put("createReplyParams", replyParams).put("commentText", text))
    }

    suspend fun postComment(videoId: String, text: String) = withContext(Dispatchers.IO) {
        val page = YouTubeApi.authPost("next", JSONObject().put("videoId", videoId))
        // Блок комментариев приходит отдельным продолжением — в нём и есть createCommentParams
        val token = findCommentsToken(page) ?: error("No comments section")
        val section = YouTubeApi.authPost("next", JSONObject().put("continuation", token))
        val params = findString(section, "createCommentParams") ?: error("Comments are turned off")
        YouTubeApi.authPost("comment/create_comment", JSONObject().put("createCommentParams", params).put("commentText", text))
    }

    private fun findCommentsToken(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                if (node.optString("sectionIdentifier") == "comment-item-section" || node.optString("targetId") == "comments-section") {
                    findString(node, "token")?.let { return it }
                }
                for (k in node.keys()) findCommentsToken(node.opt(k))?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findCommentsToken(node.opt(i))?.let { return it }
        }
        return null
    }

    private fun findString(node: Any?, key: String): String? {
        when (node) {
            is JSONObject -> {
                node.optString(key).takeIf { it.isNotEmpty() && node.opt(key) is String }?.let { return it }
                for (k in node.keys()) findString(node.opt(k), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findString(node.opt(i), key)?.let { return it }
        }
        return null
    }
}
