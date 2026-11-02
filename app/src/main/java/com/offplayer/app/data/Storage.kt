package com.offplayer.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.offplayer.app.R
import com.offplayer.app.tr
import java.io.File

/**
 * Где лежат файлы медиатеки. Защищённые места видны только OFFPlayer и удаляются вместе с ним;
 * общие папки (Music, Podcasts, Movies → OFFPlayer) видят другие приложения и файловые менеджеры.
 */
enum class StorageLocation(@StringRes private val res: Int, @StringRes private val descRes: Int) {
    PHONE_PRIVATE(R.string.loc_phone_private, R.string.loc_phone_private_desc),
    SD_PRIVATE(R.string.loc_sd_private, R.string.loc_sd_private_desc),
    PHONE_SHARED(R.string.loc_phone_shared, R.string.loc_phone_shared_desc),
    SD_SHARED(R.string.loc_sd_shared, R.string.loc_sd_shared_desc);

    val label: String get() = tr(res)
    val description: String get() = tr(descRes)
    val isShared: Boolean get() = this == PHONE_SHARED || this == SD_SHARED
    val isSd: Boolean get() = this == SD_PRIVATE || this == SD_SHARED
}

object Storage {
    /** Подпапка в общих каталогах: Music/OFFPlayer, Podcasts/OFFPlayer, Movies/OFFPlayer. */
    const val FOLDER = "OFFPlayer"

    /** Папка приложения на вставленной SD-карте (Android/data/…/files) или null, если карты нет. */
    fun sdFilesDir(context: Context): File? =
        context.getExternalFilesDirs(null).drop(1).firstOrNull { dir ->
            dir != null && runCatching {
                Environment.isExternalStorageRemovable(dir) && Environment.getExternalStorageState(dir) == Environment.MEDIA_MOUNTED
            }.getOrDefault(false)
        }

    /** UUID карты памяти («1234-ABCD») — по нему запись помнит, на какой карте лежит файл. */
    fun sdUuid(context: Context): String? {
        val dir = sdFilesDir(context) ?: return null
        val sm = context.getSystemService(StorageManager::class.java) ?: return null
        return runCatching { sm.getStorageVolume(dir)?.uuid }.getOrNull()
    }

    /** Имя тома карты в MediaStore (Android 10+); null — общие папки на карте недоступны. */
    fun sdVolumeName(context: Context): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        val uuid = sdUuid(context)?.lowercase() ?: return null
        return MediaStore.getExternalVolumeNames(context).firstOrNull { it.equals(uuid, ignoreCase = true) }
    }

    /** Места, доступные на этом устройстве сейчас. */
    fun available(context: Context): List<StorageLocation> = buildList {
        add(StorageLocation.PHONE_PRIVATE)
        if (sdFilesDir(context) != null) add(StorageLocation.SD_PRIVATE)
        add(StorageLocation.PHONE_SHARED)
        if (sdVolumeName(context) != null) add(StorageLocation.SD_SHARED)
    }

    /** До Android 10 запись в общие папки требует разрешения на запись в память. */
    fun needsWritePermission(context: Context, loc: StorageLocation): Boolean =
        loc.isShared && Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

    /** Свободное место в выбранном месте, байт. */
    fun freeSpace(context: Context, loc: StorageLocation): Long? = runCatching {
        val dir = when (loc) {
            StorageLocation.PHONE_PRIVATE -> context.filesDir
            StorageLocation.PHONE_SHARED -> @Suppress("DEPRECATION") Environment.getExternalStorageDirectory()
            StorageLocation.SD_PRIVATE, StorageLocation.SD_SHARED -> sdFilesDir(context) ?: return null
        }
        StatFs(dir.path).availableBytes
    }.getOrNull()

    /** MIME-тип по расширению: MediaStore не принимает файл, тип которого не подходит к коллекции. */
    fun mimeOf(name: String, isAudio: Boolean): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> if (ext == "aac") "audio/aac" else "audio/mp4"
            "opus", "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "wav" -> "audio/x-wav"
            "webm" -> if (isAudio) "audio/webm" else "video/webm"
            "mkv" -> "video/x-matroska"
            "mp4" -> if (isAudio) "audio/mp4" else "video/mp4"
            else -> android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?.takeIf { it.startsWith(if (isAudio) "audio/" else "video/") }
                ?: if (isAudio) "audio/mpeg" else "video/mp4"
        }
    }
}
