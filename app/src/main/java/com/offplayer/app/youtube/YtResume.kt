package com.offplayer.app.youtube

import android.content.Context

/**
 * Место, на котором остановились в ролике YouTube: при повторном открытии он продолжается оттуда же.
 * NewPipeExtractor только получает данные с YouTube и не хранит историю, поэтому позиции храним сами.
 */
object YtResume {
    private const val PREFS = "yt_resume"
    /** Сколько роликов помнить: старые позиции вытесняются. */
    private const val LIMIT = 500
    /** Начало и конец ролика не запоминаем — такой ролик открывается с начала. */
    private const val EDGE_MS = 10_000L

    private val prefs by lazy { com.offplayer.app.L.ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    /** Просмотренная доля ролика в процентах (по месту остановки в OFFPlayer); 0 — не смотрели. */
    fun percent(youtubeId: String): Int {
        val parts = prefs.getString(youtubeId, null)?.split(':') ?: return 0
        val pos = parts.getOrNull(0)?.toLongOrNull() ?: return 0
        val dur = parts.getOrNull(2)?.toLongOrNull()?.takeIf { it > 0 } ?: return 0
        return (pos * 100 / dur).toInt().coerceIn(0, 100)
    }

    /** Позиция, с которой продолжить ролик; 0 — с начала. */
    fun get(youtubeId: String): Long = prefs.getString(youtubeId, null)?.substringBefore(':')?.toLongOrNull() ?: 0L

    fun save(youtubeId: String, positionMs: Long, durationMs: Long) {
        val finished = durationMs > 0 && positionMs > durationMs - EDGE_MS
        if (positionMs < EDGE_MS || finished) {
            if (prefs.contains(youtubeId)) prefs.edit().remove(youtubeId).apply()
            return
        }
        // Значение — «позиция:время сохранения:длительность»: по времени вытесняются самые старые записи
        val edit = prefs.edit().putString(youtubeId, "$positionMs:${System.currentTimeMillis()}:${durationMs.coerceAtLeast(0)}")
        val all = prefs.all
        if (all.size > LIMIT) {
            all.entries.sortedBy { (it.value as? String)?.substringAfter(':')?.toLongOrNull() ?: 0L }
                .take(all.size - LIMIT).forEach { edit.remove(it.key) }
        }
        edit.apply()
    }
}
