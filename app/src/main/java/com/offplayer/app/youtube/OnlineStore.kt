package com.offplayer.app.youtube

import android.content.Context
import android.net.Uri
import com.offplayer.app.model.Track
import com.offplayer.app.model.Video
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

/** Ролик YouTube в онлайн-плейлисте: файла нет, поток берётся при воспроизведении. */
data class OnlineItem(val ytId: String, val title: String, val channel: String, val durationSec: Long, val thumbnail: String) {
    val playerId: Long get() = YouTubeStreams.playerIdOf(ytId)
}

/** Вкладка, в которой показан онлайн-плейлист: от неё зависит, как он играет (звук, подкаст или видео). */
enum class OnlineTab { MUSIC, PODCASTS, VIDEO }

data class OnlinePlaylist(val id: String, val name: String, val items: List<OnlineItem>, val tab: OnlineTab = OnlineTab.MUSIC)

/**
 * Онлайн-плейлисты («Сохранить» на странице ролика YouTube). Одни и те же плейлисты видны
 * в «Музыке» (раздел «Онлайн», играют звуком) и в «Видео» (списки «Онлайн», играют с картинкой).
 */
object OnlineStore {
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var file: File
    private val _playlists = MutableStateFlow<List<OnlinePlaylist>>(emptyList())
    val playlists: StateFlow<List<OnlinePlaylist>> = _playlists.asStateFlow()

    fun init(context: Context) {
        file = File(context.filesDir, "online_playlists.json")
        _playlists.value = runCatching { parse(JSONArray(file.readText())) }.getOrDefault(emptyList())
    }

    fun create(name: String, first: OnlineItem? = null, tab: OnlineTab = OnlineTab.MUSIC): OnlinePlaylist {
        // Плейлист с таким названием в этой вкладке уже есть — ролик добавляется туда, второй не создаётся
        _playlists.value.firstOrNull { it.tab == tab && it.name.equals(name.trim(), ignoreCase = true) }?.let { existing ->
            if (first != null && existing.items.none { it.ytId == first.ytId }) toggle(existing.id, first)
            return _playlists.value.first { it.id == existing.id }
        }
        val p = OnlinePlaylist(UUID.randomUUID().toString(), name.trim(), listOfNotNull(first), tab)
        _playlists.update { it + p }
        save()
        return p
    }

    fun toggle(playlistId: String, item: OnlineItem) {
        _playlists.update { list ->
            list.map { p ->
                if (p.id != playlistId) p
                else if (p.items.any { it.ytId == item.ytId }) p.copy(items = p.items.filterNot { it.ytId == item.ytId })
                else p.copy(items = p.items + item)
            }
        }
        save()
    }

    fun remove(playlistId: String, ytId: String) {
        _playlists.update { list -> list.map { if (it.id == playlistId) it.copy(items = it.items.filterNot { i -> i.ytId == ytId }) else it } }
        save()
    }

    fun rename(playlistId: String, name: String) {
        _playlists.update { list -> list.map { if (it.id == playlistId) it.copy(name = name.trim()) else it } }
        save()
    }

    fun delete(playlistId: String) {
        _playlists.update { list -> list.filterNot { it.id == playlistId } }
        save()
    }

    fun item(playerId: Long): OnlineItem? =
        _playlists.value.asSequence().flatMap { it.items }.firstOrNull { it.playerId == playerId }

    /** Трек по id плеера; ролики из плейлистов вкладки «Подкасты» — подкасты (свой мини-плеер и экран). */
    fun trackOf(playerId: Long): Track? {
        val p = _playlists.value.firstOrNull { pl -> pl.items.any { it.playerId == playerId } } ?: return null
        return track(p.items.first { it.playerId == playerId }, podcast = p.tab == OnlineTab.PODCASTS)
    }

    /** Трек для музыкального плеера: только звук; поток получается при начале воспроизведения. */
    fun track(i: OnlineItem, podcast: Boolean = false): Track = Track(
        id = i.playerId,
        uri = StreamMediaSourceFactory.lazyUri(i.ytId, video = false, thumb = i.thumbnail),
        title = i.title,
        artist = i.channel,
        album = "YouTube",
        albumId = 0,
        trackNo = 0,
        durationMs = i.durationSec * 1000,
        dateAdded = 0,
        channel = i.channel,
        channelUrl = "https://www.youtube.com/watch?v=${i.ytId}",
        isPodcast = podcast,
    )

    /** Видео для видеоплеера: картинка и звук; имя файла с ID — для SponsorBlock. */
    fun video(i: OnlineItem): Video = Video(
        id = i.playerId,
        uri = StreamMediaSourceFactory.lazyUri(i.ytId, video = true, thumb = i.thumbnail),
        title = i.title,
        durationMs = i.durationSec * 1000,
        width = 1920,
        height = 1080,
        size = 0,
        dateAdded = 0,
        folder = "YouTube",
        fileName = "${i.title} [${i.ytId}].mp4",
    )

    fun itemOf(v: YtVideo): OnlineItem = OnlineItem(
        v.id, v.title, v.channel,
        v.duration?.split(':')?.fold(0L) { acc, p -> acc * 60 + (p.toLongOrNull() ?: 0) } ?: 0,
        v.thumbnail,
    )

    private fun save() {
        val snapshot = _playlists.value
        io.launch {
            runCatching {
                val arr = JSONArray()
                snapshot.forEach { p ->
                    arr.put(JSONObject().put("id", p.id).put("name", p.name).put("tab", p.tab.name).put("items", JSONArray().apply {
                        p.items.forEach { i ->
                            put(JSONObject().put("v", i.ytId).put("t", i.title).put("c", i.channel).put("d", i.durationSec).put("th", i.thumbnail))
                        }
                    }))
                }
                val tmp = File(file.parentFile, "online_playlists.json.tmp")
                tmp.writeText(arr.toString())
                tmp.renameTo(file)
            }
        }
    }

    private fun parse(arr: JSONArray): List<OnlinePlaylist> = (0 until arr.length()).map { n ->
        val o = arr.getJSONObject(n)
        val items = o.optJSONArray("items") ?: JSONArray()
        OnlinePlaylist(o.getString("id"), o.getString("name"), (0 until items.length()).map { k ->
            val i = items.getJSONObject(k)
            OnlineItem(i.getString("v"), i.optString("t"), i.optString("c"), i.optLong("d"), i.optString("th"))
        }.distinctBy { it.ytId }, runCatching { OnlineTab.valueOf(o.optString("tab")) }.getOrDefault(OnlineTab.MUSIC))
    }
}
