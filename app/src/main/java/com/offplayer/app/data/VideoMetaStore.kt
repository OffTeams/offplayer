package com.offplayer.app.data

import android.content.Context
import com.offplayer.app.model.Chapter
import com.offplayer.app.model.SponsorSegment
import com.offplayer.app.model.VideoMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Офлайн-данные скачанных видео (ключ — id видео в MediaStore):
 * эпизоды (главы) и отрезки SponsorBlock. Всё хранится локально и доступно без интернета.
 */
object VideoMetaStore {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private lateinit var file: File

    private val _meta = MutableStateFlow<Map<Long, VideoMeta>>(emptyMap())
    val meta: StateFlow<Map<Long, VideoMeta>> = _meta.asStateFlow()

    fun init(context: Context) {
        file = File(context.filesDir, "video_meta.json")
        _meta.value = runCatching { parse(JSONObject(file.readText())) }.getOrDefault(emptyMap())
    }

    fun get(videoId: Long): VideoMeta? = _meta.value[videoId]

    /** id роликов YouTube, играющих онлайн: их эпизоды и отрезки живут только в памяти и не сохраняются. */
    private val transientIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    fun putTransient(videoId: Long, meta: VideoMeta) {
        transientIds += videoId
        _meta.update { it + (videoId to meta) }
    }

    fun put(videoId: Long, meta: VideoMeta) {
        _meta.update { it + (videoId to meta) }
        save()
    }

    fun updateSegments(videoId: Long, youtubeId: String, segments: List<SponsorSegment>) {
        _meta.update { m ->
            val old = m[videoId] ?: VideoMeta(youtubeId)
            m + (videoId to old.copy(youtubeId = youtubeId, segments = segments, segmentsFetchedAt = System.currentTimeMillis()))
        }
        save()
    }

    fun remove(videoIds: Set<Long>) {
        _meta.update { it - videoIds }
        save()
    }

    /** Замена id видео (перенос файлов в новое хранилище). */
    fun remap(map: Map<Long, Long>) {
        _meta.update { m -> m.mapKeys { (id, _) -> map[id] ?: id } }
        save()
    }

    private fun save() {
        val snapshot = _meta.value - transientIds
        io.launch {
            runCatching {
                val root = JSONObject()
                snapshot.forEach { (id, m) ->
                    root.put(id.toString(), JSONObject().apply {
                        m.youtubeId?.let { put("yt", it) }
                        put("fetched", m.segmentsFetchedAt)
                        put("chapters", JSONArray().apply {
                            m.chapters.forEach { c -> put(JSONObject().apply { put("s", c.startMs); put("e", c.endMs); put("t", c.title) }) }
                        })
                        put("segments", JSONArray().apply {
                            m.segments.forEach { s -> put(JSONObject().apply { put("s", s.startMs); put("e", s.endMs); put("c", s.category) }) }
                        })
                    })
                }
                val tmp = File(file.parentFile, "video_meta.json.tmp")
                tmp.writeText(root.toString())
                tmp.renameTo(file)
            }
        }
    }

    private fun parse(root: JSONObject): Map<Long, VideoMeta> {
        val out = HashMap<Long, VideoMeta>()
        root.keys().forEach { key ->
            val o = root.getJSONObject(key)
            val ch = o.optJSONArray("chapters") ?: JSONArray()
            val sg = o.optJSONArray("segments") ?: JSONArray()
            out[key.toLong()] = VideoMeta(
                youtubeId = o.optString("yt").ifEmpty { null },
                chapters = (0 until ch.length()).map { i ->
                    val c = ch.getJSONObject(i)
                    Chapter(c.getLong("s"), c.getLong("e"), c.getString("t"))
                },
                segments = (0 until sg.length()).map { i ->
                    val s = sg.getJSONObject(i)
                    SponsorSegment(s.getLong("s"), s.getLong("e"), s.getString("c"))
                },
                segmentsFetchedAt = o.optLong("fetched"),
            )
        }
        return out
    }
}
