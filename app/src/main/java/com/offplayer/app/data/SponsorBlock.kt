package com.offplayer.app.data

import com.offplayer.app.model.SponsorSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Клиент SponsorBlock (sponsor.ajay.app). Используется приватный режим API:
 * на сервер уходят только первые 4 символа SHA-256 от ID ролика, нужный ролик
 * выбирается из ответа локально.
 */
object SponsorBlock {
    /** Категории отрезков и их русские названия. */
    val CATEGORIES: Map<String, String> = linkedMapOf(
        "sponsor" to "Спонсор",
        "selfpromo" to "Самореклама",
        "interaction" to "Призыв подписаться",
        "intro" to "Заставка",
        "outro" to "Концовка",
        "preview" to "Превью",
        "filler" to "Отступление",
        "music_offtopic" to "Не музыка",
    )

    /** Пояснения к категориям — для настроек. */
    val DESCRIPTIONS: Map<String, String> = mapOf(
        "sponsor" to "Оплаченная реклама и интеграции",
        "selfpromo" to "Свой мерч, донаты, другие каналы автора",
        "interaction" to "«Подпишитесь», «поставьте лайк» посреди видео",
        "intro" to "Вступительная заставка без содержания",
        "outro" to "Титры, конечные заставки",
        "preview" to "Пересказ того, что будет в видео",
        "filler" to "Шутки и отступления не по теме",
        "music_offtopic" to "Части музыкальных клипов без музыки",
    )

    val DEFAULT_ENABLED = setOf("sponsor", "selfpromo", "interaction")

    fun label(category: String) = CATEGORIES[category] ?: category

    /**
     * Отрезки для ролика. Пустой список — отрезков нет; null — ошибка сети/сервера.
     */
    suspend fun fetch(youtubeId: String): List<SponsorSegment>? = withContext(Dispatchers.IO) {
        runCatching {
            val prefix = MessageDigest.getInstance("SHA-256")
                .digest(youtubeId.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(4)
            val categories = URLEncoder.encode(JSONArray(CATEGORIES.keys.toList()).toString(), "UTF-8")
            val url = URL("https://sponsor.ajay.app/api/skipSegments/$prefix?categories=$categories")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "OFFPlayer")
            }
            try {
                when (conn.responseCode) {
                    404 -> emptyList()
                    200 -> {
                        val arr = JSONArray(conn.inputStream.bufferedReader().readText())
                        val out = ArrayList<SponsorSegment>()
                        for (i in 0 until arr.length()) {
                            val video = arr.getJSONObject(i)
                            if (video.optString("videoID") != youtubeId) continue
                            val segs = video.optJSONArray("segments") ?: continue
                            for (j in 0 until segs.length()) {
                                val s = segs.getJSONObject(j)
                                val action = s.optString("actionType", "skip")
                                if (action != "skip" && action != "mute") continue
                                val range = s.getJSONArray("segment")
                                val start = (range.getDouble(0) * 1000).toLong()
                                val end = (range.getDouble(1) * 1000).toLong()
                                if (end > start) out += SponsorSegment(start, end, s.optString("category"))
                            }
                        }
                        out.sortedBy { it.startMs }
                    }
                    else -> null
                }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}
