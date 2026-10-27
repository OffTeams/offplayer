package com.offplayer.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.offplayer.app.model.Track
import com.offplayer.app.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import kotlin.math.abs

/**
 * Собственная медиатека приложения: скачанные файлы лежат во внутренней памяти
 * (files/library), недоступной другим приложениям и файловым менеджерам.
 * Индекс с тегами хранится в library.json; id файлов отрицательные,
 * чтобы не пересекаться с id MediaStore (контент устройства).
 */
object LibraryStore {
    data class Entry(
        val id: Long,
        val isAudio: Boolean,
        val file: String,
        val title: String,
        val artist: String,
        val album: String,
        val trackNo: Int,
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val size: Long,
        val dateAdded: Long,
        val genre: String? = null,
        val channel: String? = null,
        val channelUrl: String? = null,
        /** Длинное аудио, отнесённое к подкастам. */
        val podcast: Boolean = false,
    )

    private val ID_SUFFIX = Regex("""\s\[[\w-]{6,}]$""")
    private val INDEX_PREFIX = Regex("""^\d{5}\s""")

    private val mutex = Mutex()
    private lateinit var root: File
    private lateinit var indexFile: File
    @Volatile private var entries: List<Entry> = emptyList()
    private var nextId = -1L

    fun init(context: Context) {
        root = File(context.filesDir, "library")
        File(root, "audio").mkdirs()
        File(root, "video").mkdirs()
        indexFile = File(context.filesDir, "library.json")
        runCatching {
            val o = JSONObject(indexFile.readText())
            nextId = o.optLong("next", -1L)
            val arr = o.getJSONArray("items")
            entries = (0 until arr.length()).map { parse(arr.getJSONObject(it)) }
                .filter { fileOf(it).exists() }
        }
        repairBrokenTags()
    }

    /**
     * Названия, испорченные системным чтением тегов («????» на некоторых Samsung),
     * перечитываются своим разбором ID3 или берутся из имени файла.
     */
    private fun repairBrokenTags() {
        var changed = false
        entries = entries.map { e ->
            if (!Id3Reader.looksBroken(e.title) && !Id3Reader.looksBroken(e.artist) && !Id3Reader.looksBroken(e.album)) return@map e
            val id3 = if (e.isAudio) Id3Reader.read(fileOf(e)) else null
            val fixed = e.copy(
                title = if (Id3Reader.looksBroken(e.title)) id3?.title ?: fileTitle(fileOf(e)) else e.title,
                artist = if (Id3Reader.looksBroken(e.artist)) id3?.artist ?: "Неизвестный исполнитель" else e.artist,
                album = if (Id3Reader.looksBroken(e.album)) id3?.album ?: MediaRepository.UNKNOWN_ALBUM else e.album,
            )
            if (fixed != e) changed = true
            fixed
        }
        if (changed) save()
    }

    private fun fileTitle(file: File) = file.nameWithoutExtension.replace(ID_SUFFIX, "").replace(INDEX_PREFIX, "")

    /** Теги из сведений yt-dlp (info.json) — надёжнее, чем чтение из файла. */
    data class TagHints(
        val title: String?,
        val artist: String?,
        val album: String?,
        val trackNo: Int?,
        val genre: String? = null,
        val channel: String? = null,
        val channelUrl: String? = null,
    )

    /** Перенести аудио в «Подкасты» или обратно в «Музыку». */
    suspend fun setPodcast(ids: Set<Long>, podcast: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            entries = entries.map { if (it.id in ids && it.isAudio) it.copy(podcast = podcast) else it }
            save()
        }
    }

    fun all(): List<Entry> = entries

    fun fileOf(e: Entry): File = File(File(root, if (e.isAudio) "audio" else "video"), e.file)

    fun tracks(): List<Track> = entries.filter { it.isAudio }.map { e ->
        Track(
            id = e.id,
            uri = Uri.fromFile(fileOf(e)),
            title = e.title,
            artist = e.artist,
            album = e.album,
            // Альбомы группируются по названию; отрицательный id не пересекается с MediaStore
            albumId = -(abs(e.album.lowercase().hashCode().toLong()) + 1),
            trackNo = e.trackNo,
            durationMs = e.durationMs,
            dateAdded = e.dateAdded,
            genre = e.genre,
            channel = e.channel,
            channelUrl = e.channelUrl,
            isPodcast = e.podcast,
        )
    }

    fun videos(): List<Video> = entries.filter { !it.isAudio }.map { e ->
        Video(
            id = e.id,
            uri = Uri.fromFile(fileOf(e)),
            title = e.title,
            durationMs = e.durationMs,
            width = e.width,
            height = e.height,
            size = e.size,
            dateAdded = e.dateAdded,
            folder = "OFFPlayer",
            fileName = e.file,
        )
    }

    /** Переносит скачанный файл в медиатеку (переименованием, без копирования). */
    suspend fun import(src: File, isAudio: Boolean, hints: TagHints? = null): Entry = withContext(Dispatchers.IO) {
        mutex.withLock {
            val target = uniqueTarget(src.name, isAudio)
            if (!src.renameTo(target)) {
                src.copyTo(target, overwrite = true)
                src.delete()
            }
            addEntry(target, isAudio, hints)
        }
    }

    /** Копирует файл из потока (перенос старых загрузок из общей памяти). */
    suspend fun importStream(input: InputStream, name: String, isAudio: Boolean): Entry = withContext(Dispatchers.IO) {
        mutex.withLock {
            val target = uniqueTarget(name, isAudio)
            try {
                target.outputStream().use { out -> input.use { it.copyTo(out) } }
            } catch (t: Throwable) {
                target.delete()
                throw t
            }
            addEntry(target, isAudio)
        }
    }

    /** Удаляет файлы и записи. Возвращает id, которые действительно были удалены. */
    suspend fun remove(ids: Set<Long>): Set<Long> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val gone = entries.filter { it.id in ids }
            gone.forEach { fileOf(it).delete() }
            entries = entries.filterNot { it.id in ids }
            save()
            gone.map { it.id }.toSet()
        }
    }

    private fun uniqueTarget(name: String, isAudio: Boolean): File {
        val dir = File(root, if (isAudio) "audio" else "video").apply { mkdirs() }
        var target = File(dir, name)
        var n = 1
        while (target.exists()) {
            target = File(dir, "${name.substringBeforeLast('.')} ($n).${name.substringAfterLast('.', "")}")
            n++
        }
        return target
    }

    private fun addEntry(file: File, isAudio: Boolean, hints: TagHints? = null): Entry {
        val e = readTags(file, isAudio, nextId--, hints)
        entries = entries + e
        save()
        return e
    }

    private fun readTags(file: File, isAudio: Boolean, id: Long, hints: TagHints? = null): Entry {
        val fallbackTitle = fileTitle(file)
        // Порядок: сведения yt-dlp → свой разбор ID3 → системный (может испортить Unicode) → имя файла
        val id3 = if (isAudio) Id3Reader.read(file) else null
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            fun sys(key: Int) = r.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() && !Id3Reader.looksBroken(it) }
            fun tag(key: Int): String? {
                val known = when (key) {
                    MediaMetadataRetriever.METADATA_KEY_TITLE -> hints?.title ?: id3?.title
                    MediaMetadataRetriever.METADATA_KEY_ARTIST -> hints?.artist ?: id3?.artist
                    MediaMetadataRetriever.METADATA_KEY_ALBUM -> hints?.album ?: id3?.album
                    MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER -> (hints?.trackNo ?: id3?.trackNo)?.toString()
                    else -> null
                }
                return known?.trim()?.takeIf { it.isNotEmpty() } ?: sys(key)
            }
            var w = tag(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = tag(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = tag(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) w = h.also { h = w }
            return Entry(
                id = id,
                isAudio = isAudio,
                file = file.name,
                title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: fallbackTitle,
                artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) ?: "Неизвестный исполнитель",
                album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: MediaRepository.UNKNOWN_ALBUM,
                trackNo = tag(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.substringBefore('/')?.toIntOrNull() ?: 0,
                durationMs = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                width = w,
                height = h,
                size = file.length(),
                dateAdded = System.currentTimeMillis() / 1000,
                genre = hints?.genre ?: id3?.genre ?: Id3Reader.cleanGenre(sys(MediaMetadataRetriever.METADATA_KEY_GENRE)),
                channel = hints?.channel,
                channelUrl = hints?.channelUrl,
            )
        } catch (t: Throwable) {
            return Entry(id, isAudio, file.name, fallbackTitle, "Неизвестный исполнитель", MediaRepository.UNKNOWN_ALBUM,
                0, 0, 0, 0, file.length(), System.currentTimeMillis() / 1000)
        } finally {
            runCatching { r.release() }
        }
    }

    private fun save() {
        runCatching {
            val o = JSONObject()
            o.put("next", nextId)
            o.put("items", JSONArray().apply { entries.forEach { put(toJson(it)) } })
            val tmp = File(indexFile.parentFile, "library.json.tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(indexFile)
        }
    }

    private fun toJson(e: Entry) = JSONObject().apply {
        put("id", e.id); put("a", e.isAudio); put("f", e.file); put("t", e.title)
        put("ar", e.artist); put("al", e.album); put("n", e.trackNo); put("d", e.durationMs)
        put("w", e.width); put("h", e.height); put("s", e.size); put("da", e.dateAdded)
        e.genre?.let { put("g", it) }; e.channel?.let { put("ch", it) }; e.channelUrl?.let { put("cu", it) }
        if (e.podcast) put("pod", true)
    }

    private fun parse(o: JSONObject) = Entry(
        id = o.getLong("id"), isAudio = o.getBoolean("a"), file = o.getString("f"), title = o.getString("t"),
        artist = o.optString("ar"), album = o.optString("al"), trackNo = o.optInt("n"), durationMs = o.optLong("d"),
        width = o.optInt("w"), height = o.optInt("h"), size = o.optLong("s"), dateAdded = o.optLong("da"),
        genre = o.optString("g").ifEmpty { null }, channel = o.optString("ch").ifEmpty { null },
        channelUrl = o.optString("cu").ifEmpty { null }, podcast = o.optBoolean("pod", false),
    )
}
