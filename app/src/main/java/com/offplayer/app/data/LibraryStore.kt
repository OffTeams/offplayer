package com.offplayer.app.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.offplayer.app.R
import com.offplayer.app.tr
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
 * Собственная медиатека приложения. Файл записи лежит в одном из мест [StorageLocation]:
 * во внутренней памяти (files/library — по умолчанию), в папке приложения на SD-карте
 * или в общих папках Music / Podcasts / Movies → OFFPlayer (на телефоне или на карте).
 * Индекс с тегами хранится в library.json; id файлов отрицательные,
 * чтобы не пересекаться с id MediaStore (контент устройства).
 */
object LibraryStore {
    /**
     * Где лежит файл записи (поле loc):
     * ""                — внутренняя память, files/library/{audio|video}/file;
     * "sd:UUID"         — папка приложения на карте UUID, …/files/library/{audio|video}/file;
     * "pub:URI"         — общая папка на телефоне, запись MediaStore (Android 10+);
     * "pubsd:URI"       — общая папка на SD-карте, запись MediaStore;
     * "pubf:PATH"       — общая папка на телефоне до Android 10 (обычный файл).
     */
    private const val SD = "sd:"
    private const val PUB = "pub:"
    private const val PUB_SD = "pubsd:"
    private const val PUB_FILE = "pubf:"

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
        /** Место хранения файла (см. описание формата выше). */
        val loc: String = "",
    )

    private val ID_SUFFIX = Regex("""\s\[[\w-]{6,}]$""")
    private val INDEX_PREFIX = Regex("""^\d{5}\s""")

    private val mutex = Mutex()
    private lateinit var app: Context
    private lateinit var root: File
    private lateinit var indexFile: File
    @Volatile private var entries: List<Entry> = emptyList()
    private var nextId = -1L

    fun init(context: Context) {
        app = context.applicationContext
        root = File(context.filesDir, "library")
        File(root, "audio").mkdirs()
        File(root, "video").mkdirs()
        indexFile = File(context.filesDir, "library.json")
        runCatching {
            val o = JSONObject(indexFile.readText())
            nextId = o.optLong("next", -1L)
            val arr = o.getJSONArray("items")
            // Записи на вынутой карте не удаляются (null — «неизвестно»): они вернутся, когда карту вставят
            val mounted = Mounted.now(app)
            entries = (0 until arr.length()).map { parse(arr.getJSONObject(it)) }
                .filter { isPresent(it, mounted) != false }
        }
        repairBrokenTags()
    }

    /** Снимок подключённых носителей: считается один раз на весь список, а не для каждой записи. */
    private class Mounted(val sdUuid: String?, val sdDir: File?, val volumes: Set<String>) {
        companion object {
            fun now(context: Context) = Mounted(
                Storage.sdUuid(context), Storage.sdFilesDir(context),
                if (Build.VERSION.SDK_INT >= 29) MediaStore.getExternalVolumeNames(context).map { it.lowercase() }.toSet() else emptySet(),
            )
        }
    }

    /** Доступен ли файл записи сейчас: false — файла нет, null — носитель не подключён. */
    private fun isPresent(e: Entry, m: Mounted): Boolean? = when {
        e.loc.startsWith(SD) -> if (e.loc.removePrefix(SD) != m.sdUuid) null else fileOf(e, m)?.exists()
        e.loc.startsWith(PUB_SD) -> {
            val uri = Uri.parse(e.loc.removePrefix(PUB_SD))
            if (uri.pathSegments.firstOrNull()?.lowercase() !in m.volumes) null else uriExists(uri)
        }
        e.loc.startsWith(PUB) -> uriExists(Uri.parse(e.loc.removePrefix(PUB)))
        else -> fileOf(e, m)?.exists()
    }

    private fun uriExists(uri: Uri): Boolean = runCatching {
        app.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    fun locationOf(e: Entry): StorageLocation = when {
        e.loc.startsWith(SD) -> StorageLocation.SD_PRIVATE
        e.loc.startsWith(PUB_SD) -> StorageLocation.SD_SHARED
        e.loc.startsWith(PUB) || e.loc.startsWith(PUB_FILE) -> StorageLocation.PHONE_SHARED
        else -> StorageLocation.PHONE_PRIVATE
    }

    /** Записи файлов в общих папках (по id MediaStore и по пути) — их не нужно второй раз показывать среди файлов устройства. */
    fun sharedMediaIds(): Set<Long> = entries.mapNotNullTo(HashSet()) { e ->
        when {
            e.loc.startsWith(PUB_SD) -> runCatching { ContentUris.parseId(Uri.parse(e.loc.removePrefix(PUB_SD))) }.getOrNull()
            e.loc.startsWith(PUB) -> runCatching { ContentUris.parseId(Uri.parse(e.loc.removePrefix(PUB))) }.getOrNull()
            else -> null
        }
    }

    fun sharedPaths(): Set<String> = entries.mapNotNullTo(HashSet()) { e -> e.loc.takeIf { it.startsWith(PUB_FILE) }?.removePrefix(PUB_FILE) }

    /**
     * Названия, испорченные системным чтением тегов («????» на некоторых Samsung),
     * перечитываются своим разбором ID3 или берутся из имени файла.
     */
    private fun repairBrokenTags() {
        var changed = false
        entries = entries.map { e ->
            if (!Id3Reader.looksBroken(e.title) && !Id3Reader.looksBroken(e.artist) && !Id3Reader.looksBroken(e.album)) return@map e
            val file = fileOf(e)?.takeIf { it.exists() }
            val id3 = if (e.isAudio && file != null) Id3Reader.read(file) else null
            val fixed = e.copy(
                title = if (Id3Reader.looksBroken(e.title)) id3?.title ?: fileTitle(File(e.file)) else e.title,
                artist = if (Id3Reader.looksBroken(e.artist)) id3?.artist ?: MediaRepository.UNKNOWN_ARTIST else e.artist,
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

    /** Папка library для защищённого места: внутренняя память или папка приложения на карте UUID (если она вставлена). */
    private fun privateRoot(loc: String, m: Mounted): File? = when {
        loc.isEmpty() -> root
        loc.startsWith(SD) -> m.sdDir?.takeIf { m.sdUuid == loc.removePrefix(SD) }?.let { File(it, "library") }
        else -> null
    }

    /** Файл записи; null — файл в общей папке (доступ через MediaStore) или карта вынута. */
    fun fileOf(e: Entry): File? = fileOf(e, Mounted.now(app))

    private fun fileOf(e: Entry, m: Mounted): File? = when {
        e.loc.startsWith(PUB_FILE) -> File(e.loc.removePrefix(PUB_FILE))
        e.loc.startsWith(PUB) || e.loc.startsWith(PUB_SD) -> null
        else -> privateRoot(e.loc, m)?.let { File(File(it, if (e.isAudio) "audio" else "video"), e.file) }
    }

    /** Адрес для воспроизведения: file:// для своих папок, content:// для общих папок Android 10+. */
    fun uriOf(e: Entry): Uri? = uriOf(e, Mounted.now(app))

    private fun uriOf(e: Entry, m: Mounted): Uri? = when {
        e.loc.startsWith(PUB_SD) -> Uri.parse(e.loc.removePrefix(PUB_SD))
        e.loc.startsWith(PUB) -> Uri.parse(e.loc.removePrefix(PUB))
        else -> fileOf(e, m)?.let { Uri.fromFile(it) }
    }

    /** Записи, файлы которых доступны сейчас (без тех, что лежат на вынутой карте). */
    private fun visible(): List<Pair<Entry, Uri>> {
        val m = Mounted.now(app)
        return entries.mapNotNull { e ->
            val ok = when {
                e.loc.startsWith(SD) -> e.loc.removePrefix(SD) == m.sdUuid
                e.loc.startsWith(PUB_SD) -> Uri.parse(e.loc.removePrefix(PUB_SD)).pathSegments.firstOrNull()?.lowercase() in m.volumes
                else -> true
            }
            if (!ok) return@mapNotNull null
            uriOf(e, m)?.let { e to it }
        }
    }

    fun tracks(): List<Track> = visible().filter { it.first.isAudio }.map { (e, uri) ->
        Track(
            id = e.id,
            uri = uri,
            title = e.title,
            artist = MediaRepository.displayArtist(e.artist),
            album = MediaRepository.displayAlbum(e.album),
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

    fun videos(): List<Video> = visible().filter { !it.first.isAudio }.map { (e, uri) ->
        Video(
            id = e.id,
            uri = uri,
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
            gone.forEach { deleteFile(it) }
            entries = entries.filterNot { it.id in ids }
            save()
            gone.map { it.id }.toSet()
        }
    }

    /** Удаляет файл записи, где бы он ни лежал (свои записи MediaStore удаляются без системного запроса). */
    private fun deleteFile(e: Entry) {
        when {
            e.loc.startsWith(PUB_SD) -> runCatching { app.contentResolver.delete(Uri.parse(e.loc.removePrefix(PUB_SD)), null, null) }
            e.loc.startsWith(PUB) -> runCatching { app.contentResolver.delete(Uri.parse(e.loc.removePrefix(PUB)), null, null) }
            else -> fileOf(e)?.let { f ->
                f.delete()
                // Файл в общей папке до Android 10: убрать его и из MediaStore
                if (e.loc.startsWith(PUB_FILE)) MediaScannerConnection.scanFile(app, arrayOf(f.absolutePath), null, null)
            }
        }
    }

    // ---- Перенос между местами хранения ----

    data class MoveResult(val moved: Int, val failed: Int, val error: String?)

    /**
     * Переносит файлы записей в [target]. Файл сначала копируется, затем запись указывает на копию
     * и только после этого удаляется старый файл — сбой посередине ничего не теряет.
     * Теги, плейлисты, эпизоды и прогресс подкастов остаются: id записи не меняется.
     */
    suspend fun relocate(
        ids: Collection<Long>,
        target: StorageLocation,
        onProgress: (done: Int, total: Int, title: String) -> Unit = { _, _, _ -> },
        /** Запись уже указывает на копию, старый файл ещё не удалён — время перевести на копию очередь плеера. */
        onMoved: suspend (id: Long, uri: Uri) -> Unit = { _, _ -> },
    ): MoveResult = withContext(Dispatchers.IO) {
        val todo = entries.filter { it.id in ids && locationOf(it) != target }
        var moved = 0
        var failed = 0
        var error: String? = null
        todo.forEachIndexed { i, e ->
            onProgress(i, todo.size, e.title)
            try {
                val free = Storage.freeSpace(app, target)
                if (free != null && free < e.size + 16L * 1024 * 1024) error(tr(R.string.move_no_space))
                val (newLoc, newName) = copyTo(e, target)
                val updated = mutex.withLock {
                    val current = entries.firstOrNull { it.id == e.id }
                    if (current == null) null else {
                        val u = current.copy(loc = newLoc, file = newName)
                        entries = entries.map { if (it.id == e.id) u else it }
                        save()
                        u
                    }
                }
                if (updated == null) {
                    // Запись удалили, пока шло копирование: копия не нужна
                    deleteFile(e.copy(loc = newLoc, file = newName))
                } else {
                    uriOf(updated)?.let { runCatching { onMoved(e.id, it) } }
                    deleteFile(e)
                    moved++
                }
            } catch (t: Throwable) {
                failed++
                error = t.message ?: t.javaClass.simpleName
            }
        }
        onProgress(todo.size, todo.size, "")
        MoveResult(moved, failed, error)
    }

    private fun openSource(e: Entry): InputStream {
        val uri = uriOf(e) ?: error(tr(R.string.move_source_missing))
        return app.contentResolver.openInputStream(uri) ?: error(tr(R.string.move_source_missing))
    }

    /** Копирует файл записи в новое место; возвращает новое значение loc и имя файла. */
    private suspend fun copyTo(e: Entry, target: StorageLocation): Pair<String, String> = when (target) {
        StorageLocation.PHONE_PRIVATE, StorageLocation.SD_PRIVATE -> {
            val loc = if (target == StorageLocation.SD_PRIVATE) SD + (Storage.sdUuid(app) ?: error(tr(R.string.move_no_sd))) else ""
            val dir = File(privateRoot(loc, Mounted.now(app)) ?: error(tr(R.string.move_no_sd)), if (e.isAudio) "audio" else "video")
            val dest = uniqueIn(dir, e.file)
            try {
                openSource(e).use { input -> dest.outputStream().use { input.copyTo(it) } }
            } catch (t: Throwable) {
                dest.delete()
                throw t
            }
            loc to dest.name
        }
        StorageLocation.PHONE_SHARED, StorageLocation.SD_SHARED ->
            if (Build.VERSION.SDK_INT >= 29) copyToMediaStore(e, target) else copyToPublicFile(e)
    }

    private fun sharedDirType(e: Entry): String = when {
        !e.isAudio -> Environment.DIRECTORY_MOVIES
        e.podcast -> Environment.DIRECTORY_PODCASTS
        else -> Environment.DIRECTORY_MUSIC
    }

    /** Android 10+: новая запись MediaStore в Music|Podcasts|Movies/OFFPlayer на телефоне или на карте. */
    @androidx.annotation.RequiresApi(29)
    private fun copyToMediaStore(e: Entry, target: StorageLocation): Pair<String, String> {
        val sd = target == StorageLocation.SD_SHARED
        val volume = if (sd) Storage.sdVolumeName(app) ?: error(tr(R.string.move_no_sd)) else MediaStore.VOLUME_EXTERNAL_PRIMARY
        val collection = if (e.isAudio) MediaStore.Audio.Media.getContentUri(volume) else MediaStore.Video.Media.getContentUri(volume)
        val resolver = app.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, e.file)
            put(MediaStore.MediaColumns.MIME_TYPE, Storage.mimeOf(e.file, e.isAudio))
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${sharedDirType(e)}/${Storage.FOLDER}")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error(tr(R.string.move_failed_create))
        try {
            openSource(e).use { input ->
                (resolver.openOutputStream(uri) ?: error(tr(R.string.move_failed_create))).use { input.copyTo(it) }
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
        return (if (sd) PUB_SD else PUB) + uri.toString() to e.file
    }

    /** До Android 10: обычный файл в общей папке (нужно разрешение на запись) и сканирование для других приложений. */
    @Suppress("DEPRECATION")
    private fun copyToPublicFile(e: Entry): Pair<String, String> {
        val dir = File(Environment.getExternalStoragePublicDirectory(sharedDirType(e)), Storage.FOLDER)
        val dest = uniqueIn(dir, e.file)
        try {
            openSource(e).use { input -> dest.outputStream().use { input.copyTo(it) } }
        } catch (t: Throwable) {
            dest.delete()
            throw t
        }
        MediaScannerConnection.scanFile(app, arrayOf(dest.absolutePath), arrayOf(Storage.mimeOf(dest.name, e.isAudio)), null)
        return PUB_FILE + dest.absolutePath to dest.name
    }

    private fun uniqueIn(dir: File, name: String): File {
        dir.mkdirs()
        var target = File(dir, name)
        var n = 1
        while (target.exists()) {
            target = File(dir, "${name.substringBeforeLast('.')} ($n).${name.substringAfterLast('.', "")}")
            n++
        }
        return target
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
                    ?: tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) ?: MediaRepository.UNKNOWN_ARTIST,
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
            return Entry(id, isAudio, file.name, fallbackTitle, MediaRepository.UNKNOWN_ARTIST, MediaRepository.UNKNOWN_ALBUM,
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
        if (e.loc.isNotEmpty()) put("loc", e.loc)
    }

    private fun parse(o: JSONObject) = Entry(
        id = o.getLong("id"), isAudio = o.getBoolean("a"), file = o.getString("f"), title = o.getString("t"),
        artist = o.optString("ar"), album = o.optString("al"), trackNo = o.optInt("n"), durationMs = o.optLong("d"),
        width = o.optInt("w"), height = o.optInt("h"), size = o.optLong("s"), dateAdded = o.optLong("da"),
        genre = o.optString("g").ifEmpty { null }, channel = o.optString("ch").ifEmpty { null },
        channelUrl = o.optString("cu").ifEmpty { null }, podcast = o.optBoolean("pod", false),
        loc = o.optString("loc"),
    )
}
