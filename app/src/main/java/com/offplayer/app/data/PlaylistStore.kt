package com.offplayer.app.data

import com.offplayer.app.tr
import com.offplayer.app.R

import android.content.Context
import com.offplayer.app.download.DownloadResult
import com.offplayer.app.download.Downloader
import com.offplayer.app.model.Playlist
import com.offplayer.app.model.Track
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

enum class PlaylistSort(@androidx.annotation.StringRes private val res: Int) {
    TITLE(com.offplayer.app.R.string.psort_title),
    ARTIST(com.offplayer.app.R.string.psort_artist),
    ALBUM(com.offplayer.app.R.string.psort_album),
    DURATION(com.offplayer.app.R.string.psort_duration),
    DATE_ADDED(com.offplayer.app.R.string.psort_date),
    REVERSE(com.offplayer.app.R.string.psort_reverse),
    RANDOM(com.offplayer.app.R.string.psort_random);

    val label: String get() = com.offplayer.app.tr(res)
}

/** Плейлисты хранятся в JSON-файле во внутренней памяти приложения. */
object PlaylistStore {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private lateinit var file: File

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    fun init(context: Context) {
        file = File(context.filesDir, "playlists.json")
        _playlists.value = runCatching { parse(file.readText()) }.getOrDefault(emptyList())
    }

    fun get(id: String): Playlist? = _playlists.value.firstOrNull { it.id == id }

    fun create(name: String, trackIds: List<Long> = emptyList(), isMix: Boolean = false, sourceUrl: String? = null): Playlist {
        val p = Playlist(
            UUID.randomUUID().toString(), name.trim().ifEmpty { tr(R.string.new_playlist) }, trackIds.distinct(),
            System.currentTimeMillis(), isMix, sourceUrl,
        )
        mutate { it + p }
        return p
    }

    fun rename(id: String, name: String) = edit(id) { it.copy(name = name.trim().ifEmpty { it.name }) }

    fun delete(id: String) {
        mutate { list -> list.filterNot { it.id == id } }
        Downloader.forgetResults(setOf(DownloadResult.Kind.MIX), setOf(id))
    }

    fun addTracks(id: String, ids: List<Long>) = edit(id) { p ->
        p.copy(trackIds = p.trackIds + ids.filterNot { it in p.trackIds }.distinct())
    }

    fun removeTrack(id: String, trackId: Long) = edit(id) { p -> p.copy(trackIds = p.trackIds - trackId) }

    /** Удаляет треки из всех плейлистов (после удаления файлов с устройства). */
    fun purge(trackIds: Set<Long>) = mutate { list ->
        list.map { p -> if (p.trackIds.any { it in trackIds }) p.copy(trackIds = p.trackIds.filterNot { it in trackIds }) else p }
    }

    /** Замена id треков (перенос файлов в новое хранилище). */
    fun remap(map: Map<Long, Long>) = mutate { list ->
        list.map { p -> if (p.trackIds.any { it in map }) p.copy(trackIds = p.trackIds.map { map[it] ?: it }) else p }
    }

    fun setOrder(id: String, ids: List<Long>) = edit(id) { it.copy(trackIds = ids) }

    fun sort(id: String, sort: PlaylistSort) = edit(id) { p ->
        val tracks = p.trackIds.map { tid -> tid to MediaRepository.track(tid) }
        val ordered: List<Long> = when (sort) {
            PlaylistSort.REVERSE -> p.trackIds.reversed()
            PlaylistSort.RANDOM -> p.trackIds.shuffled()
            else -> {
                val cmp: Comparator<Pair<Long, Track?>> = when (sort) {
                    PlaylistSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.second?.title ?: "" }
                    PlaylistSort.ARTIST -> compareBy<Pair<Long, Track?>, String>(String.CASE_INSENSITIVE_ORDER) { it.second?.artist ?: "" }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.second?.title ?: "" }
                    PlaylistSort.ALBUM -> compareBy<Pair<Long, Track?>, String>(String.CASE_INSENSITIVE_ORDER) { it.second?.album ?: "" }
                        .thenBy { it.second?.trackNo ?: 0 }
                    PlaylistSort.DURATION -> compareBy { it.second?.durationMs ?: 0L }
                    else -> compareBy { it.second?.dateAdded ?: 0L }
                }
                tracks.sortedWith(cmp).map { it.first }
            }
        }
        p.copy(trackIds = ordered)
    }

    private fun edit(id: String, block: (Playlist) -> Playlist) =
        mutate { list -> list.map { if (it.id == id) block(it) else it } }

    private fun mutate(block: (List<Playlist>) -> List<Playlist>) {
        _playlists.update(block)
        val snapshot = _playlists.value
        io.launch {
            runCatching {
                val tmp = File(file.parentFile, "playlists.json.tmp")
                tmp.writeText(serialize(snapshot))
                tmp.renameTo(file)
            }
        }
    }

    private fun serialize(list: List<Playlist>): String {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("created", p.created)
                put("mix", p.isMix)
                p.sourceUrl?.let { put("source", it) }
                put("tracks", JSONArray().apply { p.trackIds.forEach { put(it) } })
            })
        }
        return arr.toString()
    }

    private fun parse(text: String): List<Playlist> {
        val arr = JSONArray(text)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val t = o.getJSONArray("tracks")
            Playlist(
                id = o.getString("id"),
                name = o.getString("name"),
                trackIds = (0 until t.length()).map { t.getLong(it) },
                created = o.optLong("created"),
                isMix = o.optBoolean("mix", false),
                sourceUrl = o.optString("source").ifEmpty { null },
            )
        }
    }
}
