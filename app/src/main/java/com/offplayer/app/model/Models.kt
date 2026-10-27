package com.offplayer.app.model

import com.offplayer.app.R

import android.net.Uri

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val trackNo: Int,
    val durationMs: Long,
    val dateAdded: Long,
    /** Файл из памяти устройства (MediaStore), а не скачанный в приложение. */
    val isDevice: Boolean = false,
    /** Папка файла на устройстве — только для isDevice. */
    val folder: DeviceFolder? = null,
    /** Жанр (из тегов или сведений YouTube), если известен. */
    val genre: String? = null,
    /** Канал-источник, с которого скачан трек (если включено сохранение источника). */
    val channel: String? = null,
    val channelUrl: String? = null,
    /** Длинное аудио, отнесённое к подкастам. */
    val isPodcast: Boolean = false,
)

/**
 * Папка на устройстве: путь для показа («Music/Telegram») и id документа
 * для открытия в файловом менеджере («primary:Music/Telegram»).
 */
data class DeviceFolder(val path: String, val documentId: String) {
    val name: String get() = path.trimEnd('/').substringAfterLast('/').ifEmpty { com.offplayer.app.tr(com.offplayer.app.R.string.device_storage) }
}

data class Video(
    val id: Long,
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val size: Long,
    val dateAdded: Long,
    val folder: String,
    /** Имя файла — из него можно извлечь ID ролика YouTube («… [dQw4w9WgXcQ].mp4»). */
    val fileName: String = "",
    val isDevice: Boolean = false,
    val deviceFolder: DeviceFolder? = null,
) {
    val youtubeIdFromName: String? get() = YOUTUBE_ID.find(fileName)?.groupValues?.get(1)

    private companion object {
        val YOUTUBE_ID = Regex("""\[([\w-]{11})]\.\w+$""")
    }
}

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<Long>,
    val created: Long,
    /** Микс — плейлист, скачанный целиком с YouTube / YouTube Music. */
    val isMix: Boolean = false,
    val sourceUrl: String? = null,
)

/** Список видео — упорядоченная подборка (аналог плейлиста). */
data class VideoList(
    val id: String,
    val name: String,
    val videoIds: List<Long>,
    val created: Long,
    val sourceUrl: String? = null,
)

/** Категория — тематическая метка; одно видео может быть в нескольких категориях. */
data class VideoCategory(
    val id: String,
    val name: String,
    val videoIds: Set<Long>,
    val created: Long,
)

/** Эпизод (глава) видео — как список «Эпизоды» на YouTube. */
data class Chapter(val startMs: Long, val endMs: Long, val title: String)

/** Отрезок SponsorBlock: интеграция, самореклама, заставка и т. п. */
data class SponsorSegment(val startMs: Long, val endMs: Long, val category: String)

/** Офлайн-данные скачанного видео: эпизоды и отрезки SponsorBlock. */
data class VideoMeta(
    val youtubeId: String?,
    val chapters: List<Chapter> = emptyList(),
    val segments: List<SponsorSegment> = emptyList(),
    val segmentsFetchedAt: Long = 0,
)

/**
 * Ключ для Coil: обложка аудиофайла или кадр видео из MediaStore.
 * timeMs — кадр в конкретный момент (превью эпизодов); null — автоматически подобранный кадр.
 */
data class MediaThumb(val uri: Uri, val isVideo: Boolean, val timeMs: Long? = null)
