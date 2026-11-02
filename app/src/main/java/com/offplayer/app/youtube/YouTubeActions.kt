package com.offplayer.app.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
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
)

/** Страница комментариев; total — «38» или «1,2 тыс.»; next — токен продолжения. */
class YtComments(val items: List<YtComment>, val total: String?, val next: String?)


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
    fun prefetch(videoId: String) {
        firstPages.computeIfAbsent(videoId) { prefetchScope.async { load(videoId, null) } }
    }

    suspend fun comments(videoId: String, next: String?): YtComments =
        if (next == null) firstPages.remove(videoId)?.let { runCatching { it.await() }.getOrNull() } ?: load(videoId, null)
        else load(videoId, next)

    private suspend fun load(videoId: String, next: String?): YtComments = withContext(Dispatchers.IO) {
        val token = next ?: findCommentsToken(YouTubeApi.call("next", JSONObject().put("videoId", videoId)))
            ?: return@withContext YtComments(emptyList(), null, null)
        val r = YouTubeApi.call("next", JSONObject().put("continuation", token))
        val items = ArrayList<YtComment>()
        val mutations = r.optJSONObject("frameworkUpdates")?.optJSONObject("entityBatchUpdate")?.optJSONArray("mutations") ?: JSONArray()
        for (i in 0 until mutations.length()) {
            val p = mutations.optJSONObject(i)?.optJSONObject("payload")?.optJSONObject("commentEntityPayload") ?: continue
            val props = p.optJSONObject("properties")
            val author = p.optJSONObject("author")
            val toolbar = p.optJSONObject("toolbar")
            items += YtComment(
                author = author?.optString("displayName").orEmpty(),
                avatar = author?.optString("avatarThumbnailUrl")?.takeIf { it.startsWith("http") },
                text = props?.optJSONObject("content")?.optString("content").orEmpty(),
                likes = toolbar?.optString("likeCountNotliked").orEmpty().takeIf { it != "0" }.orEmpty(),
                date = props?.optString("publishedTime").orEmpty(),
                replies = toolbar?.optString("replyCount").orEmpty().takeIf { it != "0" }.orEmpty(),
            )
        }
        // Число комментариев — в шапке блока (только на первой странице)
        val total = if (next == null) findString(r, "countText")
            ?: Regex(""""countText":\{"runs":\[\{"text":"([^"]+)"""").find(r.toString())?.groupValues?.get(1) else null
        YtComments(items, total, lastContinuation(r))
    }

    /** Продолжение списка — токен последнего continuationItemRenderer в ответе. */
    private fun lastContinuation(node: JSONObject): String? =
        Regex(""""continuationItemRenderer":\{.*?"token":"([^"]+)"""").findAll(node.toString()).lastOrNull()?.groupValues?.get(1)
    /**
     * Новый комментарий. Параметры отправки сайт получает из блока комментариев страницы ролика:
     * запрашиваем его так же и отправляем текст.
     */
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
