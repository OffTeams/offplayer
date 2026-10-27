package com.offplayer.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Где остановились в каждом подкасте — чтобы продолжить с того же места. */
object PodcastProgress {
    private lateinit var prefs: SharedPreferences
    private val _positions = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val positions: StateFlow<Map<Long, Long>> = _positions.asStateFlow()

    private lateinit var kindPrefs: SharedPreferences
    private val _kinds = MutableStateFlow<Map<Long, String>>(emptyMap())
    /** Категории, выбранные пользователем вручную (иначе — определяются по названию). */
    val kinds: StateFlow<Map<Long, String>> = _kinds.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("podcast_progress", Context.MODE_PRIVATE)
        _positions.value = prefs.all.mapNotNull { (k, v) -> k.toLongOrNull()?.let { id -> (v as? Long)?.let { id to it } } }.toMap()
        kindPrefs = context.getSharedPreferences("podcast_kinds", Context.MODE_PRIVATE)
        _kinds.value = kindPrefs.all.mapNotNull { (k, v) -> k.toLongOrNull()?.let { id -> (v as? String)?.let { id to it } } }.toMap()
    }

    fun setKind(id: Long, kind: String) {
        _kinds.value = _kinds.value + (id to kind)
        kindPrefs.edit().putString(id.toString(), kind).apply()
    }

    fun get(id: Long): Long = _positions.value[id] ?: 0L

    fun set(id: Long, positionMs: Long) {
        if (_positions.value[id] == positionMs) return
        _positions.value = _positions.value + (id to positionMs)
        prefs.edit().putLong(id.toString(), positionMs).apply()
    }
}
