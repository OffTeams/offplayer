package com.offplayer.app.data

import com.offplayer.app.R
import com.offplayer.app.model.SponsorSegment
import com.offplayer.app.tr
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
    /** Категории отрезков и их названия на языке интерфейса. */
    val CATEGORIES: Map<String, String> get() = linkedMapOf(
        "sponsor" to tr(R.string.sbc_sponsor),
        "selfpromo" to tr(R.string.sbc_selfpromo),
        "interaction" to tr(R.string.sbc_interaction),
        "intro" to tr(R.string.sbc_intro),
        "outro" to tr(R.string.sbc_outro),
        "preview" to tr(R.string.sbc_preview),
        "filler" to tr(R.string.sbc_filler),
        "music_offtopic" to tr(R.string.sbc_music_offtopic),
    )

    /** Пояснения к категориям — для настроек. */
    val DESCRIPTIONS: Map<String, String> get() = mapOf(
        "sponsor" to tr(R.string.sbd_sponsor),
        "selfpromo" to tr(R.string.sbd_selfpromo),
        "interaction" to tr(R.string.sbd_interaction),
        "intro" to tr(R.string.sbd_intro),
        "outro" to tr(R.string.sbd_outro),
        "preview" to tr(R.string.sbd_preview),
        "filler" to tr(R.string.sbd_filler),
        "music_offtopic" to tr(R.string.sbd_music_offtopic),
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
