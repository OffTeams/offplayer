package com.offplayer.app.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.common.util.concurrent.ListenableFuture
import com.offplayer.app.model.MediaThumb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future

/**
 * Обложки для уведомления и экрана блокировки. Файлы медиатеки лежат во внутренней памяти,
 * поэтому система не может прочитать их сама: в artworkUri кладётся ссылка вида
 * offthumb://art?u=<файл>&v=<1 для видео>, а картинку извлекает приложение (обложка трека или кадр видео).
 */
@OptIn(UnstableApi::class)
class ThumbBitmapLoader(private val context: Context) : BitmapLoader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fallback = DataSourceBitmapLoader(context)

    override fun supportsMimeType(mimeType: String): Boolean = fallback.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = scope.future {
        BitmapFactory.decodeByteArray(data, 0, data.size) ?: error("Bad bitmap")
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val thumb = parse(uri) ?: return fallback.loadBitmap(uri)
        return scope.future { load(context, thumb) ?: error("No artwork for $uri") }
    }

    companion object {
        private const val SCHEME = "offthumb"

        fun artworkUri(media: Uri, isVideo: Boolean): Uri = Uri.Builder()
            .scheme(SCHEME).authority("art")
            .appendQueryParameter("u", media.toString())
            .appendQueryParameter("v", if (isVideo) "1" else "0")
            .build()

        fun parse(uri: Uri): MediaThumb? {
            if (uri.scheme != SCHEME) return null
            val media = uri.getQueryParameter("u")?.let(Uri::parse) ?: return null
            return MediaThumb(media, uri.getQueryParameter("v") == "1")
        }

        /** Обычный (не аппаратный) Bitmap — его можно передать в уведомление. */
        suspend fun load(context: Context, thumb: MediaThumb): Bitmap? {
            val request = ImageRequest.Builder(context)
                .data(thumb)
                .size(512)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
            return (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        }
    }
}
