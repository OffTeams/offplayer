package com.offplayer.app.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import com.offplayer.app.model.MediaThumb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/**
 * Обложка трека (встроенная в файл) или кадр из видео.
 * Для видео берём кадр не с начала (ролики часто начинаются с чёрного экрана),
 * пропускаем слишком тёмные кадры и кэшируем результат на диске.
 */
class MediaThumbFetcher(
    private val data: MediaThumb,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val context = options.context
        // Ролик YouTube, играющий онлайн: вместо кадра из файла — его превью с YouTube
        val remote = com.offplayer.app.youtube.StreamMediaSourceFactory.thumbnailOf(data.uri)
        val bitmap = when {
            remote != null -> java.net.URL(remote).openStream().use { BitmapFactory.decodeStream(it) }
                ?: throw FileNotFoundException("Bad thumbnail $remote")
            data.isVideo -> videoFrame(context)
            else -> audioArt(context)
        }
        DrawableResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    private fun audioArt(context: Context): Bitmap {
        // Миниатюры MediaStore — только для файлов устройства; свои файлы читаем напрямую
        if (Build.VERSION.SDK_INT >= 29 && data.uri.scheme == "content") {
            return context.contentResolver.loadThumbnail(data.uri, Size(THUMB_SIZE, THUMB_SIZE), null)
        }
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, data.uri)
            val bytes = r.embeddedPicture ?: throw FileNotFoundException("No artwork for ${data.uri}")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= THUMB_SIZE) sample *= 2
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: throw FileNotFoundException("Bad artwork for ${data.uri}")
        } finally {
            r.release()
        }
    }

    private fun videoFrame(context: Context): Bitmap {
        val cache = File(context.cacheDir, "video_thumbs").apply { mkdirs() }
        // Кадр для карточки — в высоком разрешении (карточка во всю ширину экрана), для эпизодов — поменьше
        val suffix = data.timeMs?.let { "_t$it" } ?: "_cover"
        val size = if (data.timeMs == null) VIDEO_SIZE else THUMB_SIZE
        val file = File(cache, "${data.uri.toString().hashCode().toUInt()}$suffix.jpg")
        if (file.exists()) BitmapFactory.decodeFile(file.path)?.let { return it }

        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, data.uri)
            // Кадр в заданный момент — превью эпизода (берём чуть позже начала, чтобы не попасть на склейку)
            // Обложка карточки: превью ролика, а не кадр — встроенное в файл или с YouTube по ID в имени файла
            if (data.timeMs == null) {
                r.embeddedPicture?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }?.let { art ->
                    runCatching { file.outputStream().use { art.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
                    return art
                }
                val id = Regex("""\[([\w-]{11})]\.\w+$""").find(android.net.Uri.decode(data.uri.lastPathSegment.orEmpty()))?.groupValues?.get(1)
                if (id != null) {
                    val remote = listOf("maxresdefault", "hqdefault").firstNotNullOfOrNull { q ->
                        runCatching { java.net.URL("https://i.ytimg.com/vi/$id/$q.jpg").openStream().use { BitmapFactory.decodeStream(it) } }.getOrNull()
                            ?.takeIf { it.width > 120 } // заглушка «нет картинки» у YouTube — 120×90
                    }
                    if (remote != null) {
                        runCatching { file.outputStream().use { remote.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
                        return remote
                    }
                }
            }
            data.timeMs?.let { t ->
                val frame = frameAt(r, (t + 1500) * 1000, size) ?: frameAt(r, t * 1000, size)
                    ?: throw FileNotFoundException("No frame at $t for ${data.uri}")
                runCatching { file.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 80, it) } }
                return frame
            }
            val durationUs = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
            var best: Bitmap? = null
            var bestLuma = -1f
            for (fraction in listOf(0.15, 0.35, 0.6)) {
                val frame = frameAt(r, (durationUs * fraction).toLong(), size) ?: continue
                val luma = averageLuma(frame)
                if (luma > bestLuma) {
                    best = frame
                    bestLuma = luma
                }
                if (luma > DARK_THRESHOLD) break // кадр достаточно светлый
            }
            val result = best ?: frameAt(r, 0, size) ?: throw FileNotFoundException("No frame for ${data.uri}")
            runCatching { file.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
            return result
        } finally {
            r.release()
        }
    }

    private fun frameAt(r: MediaMetadataRetriever, timeUs: Long, size: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 27) {
            // Рамка size×size: вертикальные и квадратные кадры тоже получаются чёткими
            r.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, size, size)
        } else {
            r.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }
    }.getOrNull()

    /** Средняя яркость по сетке 16×9 точек (0..1). */
    private fun averageLuma(b: Bitmap): Float {
        var sum = 0f
        var n = 0
        for (yi in 0 until 9) for (xi in 0 until 16) {
            val c = b.getPixel((b.width - 1) * xi / 15, (b.height - 1) * yi / 8)
            sum += (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f
            n++
        }
        return sum / n
    }

    class Factory : Fetcher.Factory<MediaThumb> {
        override fun create(data: MediaThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            MediaThumbFetcher(data, options)
    }

    class Key : Keyer<MediaThumb> {
        override fun key(data: MediaThumb, options: Options): String = "thumb3:${data.uri}:${data.timeMs ?: ""}"
    }

    private companion object {
        const val THUMB_SIZE = 512
        const val VIDEO_SIZE = 1280
        const val DARK_THRESHOLD = 0.12f
    }
}
