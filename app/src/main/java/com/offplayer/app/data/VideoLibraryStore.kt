package com.offplayer.app.data

import com.offplayer.app.tr
import com.offplayer.app.R

import android.content.Context
import com.offplayer.app.download.DownloadResult
import com.offplayer.app.download.Downloader
import com.offplayer.app.model.VideoCategory
import com.offplayer.app.model.VideoList
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
import java.util.UUID

/** Списки и категории видео. Хранятся в JSON во внутренней памяти приложения. */
object VideoLibraryStore {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private lateinit var file: File

    private val _lists = MutableStateFlow<List<VideoList>>(emptyList())
    val lists: StateFlow<List<VideoList>> = _lists.asStateFlow()

    private val _categories = MutableStateFlow<List<VideoCategory>>(emptyList())
    val categories: StateFlow<List<VideoCategory>> = _categories.asStateFlow()

    fun init(context: Context) {
        file = File(context.filesDir, "video_library.json")
        runCatching {
            val root = JSONObject(file.readText())
            _lists.value = root.optJSONArray("lists")?.let(::parseLists).orEmpty()
            _categories.value = root.optJSONArray("categories")?.let(::parseCategories).orEmpty()
        }
    }

    // ---- Списки ----

    fun createList(name: String, videoIds: List<Long> = emptyList(), sourceUrl: String? = null): VideoList {
        val l = VideoList(UUID.randomUUID().toString(), name.trim().ifEmpty { tr(R.string.new_list) }, videoIds.distinct(), System.currentTimeMillis(), sourceUrl)
        _lists.update { it + l }
        save()
        return l
    }

    fun renameList(id: String, name: String) = editList(id) { it.copy(name = name.trim().ifEmpty { it.name }) }
    fun deleteList(id: String) {
        _lists.update { l -> l.filterNot { it.id == id } }
        save()
        Downloader.forgetResults(setOf(DownloadResult.Kind.LIST), setOf(id))
    }
    fun addToList(id: String, ids: List<Long>) = editList(id) { l -> l.copy(videoIds = l.videoIds + ids.filterNot { it in l.videoIds }.distinct()) }
    fun removeFromList(id: String, videoId: Long) = editList(id) { it.copy(videoIds = it.videoIds - videoId) }
    fun setListOrder(id: String, ids: List<Long>) = editList(id) { it.copy(videoIds = ids) }

    private fun editList(id: String, block: (VideoList) -> VideoList) {
        _lists.update { list -> list.map { if (it.id == id) block(it) else it } }
        save()
    }

    // ---- Категории ----

    fun createCategory(name: String, videoIds: Set<Long> = emptySet()): VideoCategory {
        val c = VideoCategory(UUID.randomUUID().toString(), name.trim().ifEmpty { tr(R.string.new_category) }, videoIds, System.currentTimeMillis())
        _categories.update { it + c }
        save()
        return c
    }

    fun renameCategory(id: String, name: String) = editCategory(id) { it.copy(name = name.trim().ifEmpty { it.name }) }
    fun deleteCategory(id: String) { _categories.update { l -> l.filterNot { it.id == id } }; save() }

    /** Включает/выключает видео в категории. */
    fun setInCategory(categoryId: String, videoId: Long, inCategory: Boolean) = editCategory(categoryId) {
        it.copy(videoIds = if (inCategory) it.videoIds + videoId else it.videoIds - videoId)
    }

    private fun editCategory(id: String, block: (VideoCategory) -> VideoCategory) {
        _categories.update { list -> list.map { if (it.id == id) block(it) else it } }
        save()
    }

    /** Убирает удалённые с устройства видео отовсюду. */
    fun purge(videoIds: Set<Long>) {
        _lists.update { l -> l.map { it.copy(videoIds = it.videoIds.filterNot { id -> id in videoIds }) } }
        _categories.update { l -> l.map { it.copy(videoIds = it.videoIds - videoIds) } }
        save()
    }

    /** Замена id видео (перенос файлов в новое хранилище). */
    fun remap(map: Map<Long, Long>) {
        _lists.update { l -> l.map { it.copy(videoIds = it.videoIds.map { id -> map[id] ?: id }) } }
        _categories.update { l -> l.map { it.copy(videoIds = it.videoIds.map { id -> map[id] ?: id }.toSet()) } }
        save()
    }

    // ---- Хранение ----

    private fun save() {
        val lists = _lists.value
        val categories = _categories.value
        io.launch {
            runCatching {
                val root = JSONObject()
                root.put("lists", JSONArray().apply {
                    lists.forEach { l ->
                        put(JSONObject().apply {
                            put("id", l.id); put("name", l.name); put("created", l.created)
                            l.sourceUrl?.let { put("source", it) }
                            put("videos", JSONArray().apply { l.videoIds.forEach { put(it) } })
                        })
                    }
                })
                root.put("categories", JSONArray().apply {
                    categories.forEach { c ->
                        put(JSONObject().apply {
                            put("id", c.id); put("name", c.name); put("created", c.created)
                            put("videos", JSONArray().apply { c.videoIds.forEach { put(it) } })
                        })
                    }
                })
                val tmp = File(file.parentFile, "video_library.json.tmp")
                tmp.writeText(root.toString())
                tmp.renameTo(file)
            }
        }
    }

    private fun parseLists(arr: JSONArray) = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val v = o.getJSONArray("videos")
        VideoList(
            id = o.getString("id"), name = o.getString("name"),
            videoIds = (0 until v.length()).map { v.getLong(it) },
            created = o.optLong("created"), sourceUrl = o.optString("source").ifEmpty { null },
        )
    }

    private fun parseCategories(arr: JSONArray) = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val v = o.getJSONArray("videos")
        VideoCategory(
            id = o.getString("id"), name = o.getString("name"),
            videoIds = (0 until v.length()).map { v.getLong(it) }.toSet(),
            created = o.optLong("created"),
        )
    }
}
