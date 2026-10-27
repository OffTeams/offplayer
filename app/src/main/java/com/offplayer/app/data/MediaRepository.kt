package com.offplayer.app.data

import com.offplayer.app.tr
import com.offplayer.app.R

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.offplayer.app.model.DeviceFolder
import com.offplayer.app.model.Track
import com.offplayer.app.model.Video
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Медиатека: собственные файлы приложения (LibraryStore) и — по желанию пользователя —
 * музыка и видео из памяти устройства (MediaStore). Музыка и видео хранятся раздельно.
 */
object MediaRepository {
    /** Метки «нет данных», сохранённые в library.json; на экране — на языке интерфейса. */
    const val UNKNOWN_ALBUM = "Без альбома"
    const val UNKNOWN_ARTIST = "Неизвестный исполнитель"
    fun displayArtist(a: String) = if (a == UNKNOWN_ARTIST || a.isBlank()) tr(R.string.unknown_artist) else a
    fun displayAlbum(a: String) = if (a == UNKNOWN_ALBUM || a.isBlank()) tr(R.string.unknown_album) else a

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    /** Треки для общих списков: скачанные + (в смешанном режиме) треки устройства. */
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _deviceTracks = MutableStateFlow<List<Track>>(emptyList())
    /** Все треки устройства — для раздела «На устройстве». */
    val deviceTracks: StateFlow<List<Track>> = _deviceTracks.asStateFlow()

    private val _podcasts = MutableStateFlow<List<Track>>(emptyList())
    /** Длинные аудио, отнесённые к подкастам (только скачанные в приложение). */
    val podcasts: StateFlow<List<Track>> = _podcasts.asStateFlow()

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()

    private val _deviceVideos = MutableStateFlow<List<Video>>(emptyList())
    val deviceVideos: StateFlow<List<Video>> = _deviceVideos.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _migration = MutableStateFlow<String?>(null)
    /** Текст прогресса переноса старых загрузок в защищённое хранилище (null — не идёт). */
    val migration: StateFlow<String?> = _migration.asStateFlow()

    @Volatile private var trackMap: Map<Long, Track> = emptyMap()
    @Volatile private var videoMap: Map<Long, Video> = emptyMap()

    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var started = false

    fun track(id: Long): Track? = trackMap[id]
    fun video(id: Long): Video? = videoMap[id]

    fun mediaPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun hasPermission(context: Context): Boolean = mediaPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Запуск при старте приложения: перенос старых загрузок, первое сканирование, наблюдение за MediaStore. */
    @OptIn(FlowPreview::class)
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (AppSettings.deviceMedia.value != DeviceMediaMode.OFF) changes.tryEmit(Unit)
            }
        }
        app.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        app.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        scope.launch {
            runCatching { migrateLegacy(app) }
            _migration.value = null
            refresh(app)
            changes.debounce(1500).collect { refresh(app) }
        }
    }

    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val ownAll = LibraryStore.tracks()
            val ownTracks = ownAll.filterNot { it.isPodcast }
            val ownVideos = LibraryStore.videos()
            val mode = AppSettings.deviceMedia.value
            val device = mode != DeviceMediaMode.OFF && hasPermission(context)
            val dTracks = if (device) runCatching { queryTracks(context) }.getOrDefault(emptyList()) else emptyList()
            val dVideos = if (device) runCatching { queryVideos(context) }.getOrDefault(emptyList()) else emptyList()
            trackMap = (ownAll + dTracks).associateBy { it.id }
            _podcasts.value = ownAll.filter { it.isPodcast }.sortedByDescending { it.dateAdded }
            videoMap = (ownVideos + dVideos).associateBy { it.id }
            val mixed = mode == DeviceMediaMode.MIXED
            _tracks.value = if (mixed) ownTracks + dTracks else ownTracks
            _videos.value = if (mixed) ownVideos + dVideos else ownVideos
            _deviceTracks.value = dTracks
            _deviceVideos.value = dVideos
            _loaded.value = true
        }
    }

    fun refreshAsync(context: Context) {
        val app = context.applicationContext
        scope.launch { refresh(app) }
    }

    // ---- Перенос загрузок прошлых версий (Music / Movies) во внутреннюю память ----

    private suspend fun migrateLegacy(context: Context) {
        if (Build.VERSION.SDK_INT < 29) return
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("legacy_migrated", false)) return
        data class Owned(val uri: Uri, val id: Long, val name: String, val isAudio: Boolean)

        val resolver = context.contentResolver
        fun query(collection: Uri, isAudio: Boolean): List<Owned> {
            val out = ArrayList<Owned>()
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?", arrayOf(context.packageName), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1) ?: "media_$id"
                    out += Owned(ContentUris.withAppendedId(collection, id), id, name, isAudio)
                }
            }
            return out
        }

        val owned = runCatching { query(audioCollection(), true) + query(videoCollection(), false) }.getOrNull() ?: return
        var ok = true
        owned.forEachIndexed { i, o ->
            _migration.value = tr(R.string.migration_progress, i + 1, owned.size)
            try {
                val input = resolver.openInputStream(o.uri) ?: error("no stream")
                val e = LibraryStore.importStream(input, o.name, o.isAudio)
                // Связи в плейлистах, списках и эпизодах переносятся на новый id сразу после копирования
                val map = mapOf(o.id to e.id)
                PlaylistStore.remap(map)
                VideoLibraryStore.remap(map)
                VideoMetaStore.remap(map)
                runCatching { resolver.delete(o.uri, null, null) }
            } catch (t: Throwable) {
                ok = false
            }
        }
        if (ok) prefs.edit().putBoolean("legacy_migrated", true).apply()
    }

    // ---- Контент устройства ----

    private fun audioCollection(): Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    private fun videoCollection(): Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    /** Папка по полному пути файла: «/storage/emulated/0/Music/x.mp3» → primary:Music. */
    private fun folderOf(path: String?): DeviceFolder? {
        val parent = path?.substringBeforeLast('/', "") ?: return null
        if (parent.isEmpty()) return null
        @Suppress("DEPRECATION")
        val primary = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
        return when {
            parent == primary -> DeviceFolder("", "primary:")
            parent.startsWith("$primary/") -> parent.removePrefix("$primary/").let { DeviceFolder(it, "primary:$it") }
            else -> {
                // Карта памяти: /storage/1234-ABCD/...
                val m = Regex("""^/storage/([^/]+)/?(.*)$""").find(parent) ?: return DeviceFolder(parent, "")
                val rel = m.groupValues[2]
                DeviceFolder(rel, "${m.groupValues[1].uppercase()}:$rel")
            }
        }
    }

    private fun queryTracks(context: Context): List<Track> {
        val collection = audioCollection()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
        ) + if (Build.VERSION.SDK_INT >= 30) arrayOf(MediaStore.Audio.Media.GENRE) else emptyArray()
        val result = ArrayList<Track>()
        context.contentResolver.query(
            collection, projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val genreCol = if (Build.VERSION.SDK_INT >= 30) c.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val data = c.getString(dataCol)
                // Если в файле нет тега альбома, Android подставляет имя папки — такой «альбом» скрываем
                val folder = data?.let { java.io.File(it).parentFile?.name }
                val album = c.getString(albumCol)
                    ?.takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING || it.equals(folder, ignoreCase = true) }
                    ?: tr(R.string.unknown_album)
                val artist = c.getString(artistCol)
                    ?.takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING }
                    ?: tr(R.string.unknown_artist)
                val title = c.getString(titleCol)?.takeUnless { it.isBlank() }
                    ?: c.getString(nameCol)?.substringBeforeLast('.') ?: tr(R.string.untitled)
                result += Track(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = title,
                    artist = artist,
                    album = album,
                    albumId = c.getLong(albumIdCol),
                    trackNo = c.getInt(trackCol) % 1000,
                    durationMs = c.getLong(durCol),
                    dateAdded = c.getLong(dateCol),
                    isDevice = true,
                    folder = folderOf(data),
                    genre = if (genreCol >= 0) Id3Reader.cleanGenre(c.getString(genreCol)) else null,
                )
            }
        }
        return result
    }

    private fun queryVideos(context: Context): List<Video> {
        val collection = videoCollection()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
        )
        val result = ArrayList<Video>()
        context.contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val wCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val hCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val bucketCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val title = c.getString(titleCol)?.takeUnless { it.isBlank() }
                    ?: c.getString(nameCol)?.substringBeforeLast('.') ?: tr(R.string.untitled)
                result += Video(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = title,
                    durationMs = c.getLong(durCol),
                    width = c.getInt(wCol),
                    height = c.getInt(hCol),
                    size = c.getLong(sizeCol),
                    dateAdded = c.getLong(dateCol),
                    folder = c.getString(bucketCol) ?: tr(R.string.other_folder),
                    fileName = c.getString(nameCol).orEmpty(),
                    isDevice = true,
                    deviceFolder = folderOf(c.getString(dataCol)),
                )
            }
        }
        return result
    }
}
