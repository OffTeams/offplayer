package com.offplayer.app.ui.music

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import android.content.Context
import com.offplayer.app.data.MediaRepository
import com.offplayer.app.playback.PlayerUiState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.offplayer.app.model.MediaThumb
import com.offplayer.app.ui.theme.Accent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Цвета, извлечённые из обложки (как есть): яркий, тёмный и приглушённый. */
data class ArtColors(val accent: Color, val dark: Color, val muted: Color)

val DefaultArtColors = ArtColors(Accent, Color(0xFF2B3A45), Color(0xFF3B3B5C))

/**
 * Палитра развёрнутого плеера под тему: градиент из цветов обложки,
 * уходящий в чёрный (тёмная тема) или в белый (светлая).
 */
data class PlayerPalette(
    val dark: Boolean,
    val base: Color,
    val content: Color,
    val contentSecondary: Color,
    val accent: Color,
    val blobTop: Color,
    val blobAccent: Color,
    val blobBottom: Color,
)

fun ArtColors.palette(dark: Boolean): PlayerPalette = if (dark) {
    PlayerPalette(
        dark = true,
        base = Color.Black,
        content = Color.White,
        contentSecondary = Color.White.copy(alpha = 0.7f),
        accent = accent.readableOnDark(),
        blobTop = lerp(this.dark, Color.Black, 0.35f),
        blobAccent = accent.copy(alpha = 0.38f),
        blobBottom = lerp(muted, Color.Black, 0.45f),
    )
} else {
    PlayerPalette(
        dark = false,
        base = Color.White,
        content = Color(0xFF111111),
        contentSecondary = Color(0xFF111111).copy(alpha = 0.62f),
        accent = accent.readableOnLight(),
        blobTop = lerp(accent, Color.White, 0.45f),
        blobAccent = lerp(this.dark, Color.White, 0.55f).copy(alpha = 0.55f),
        blobBottom = lerp(muted, Color.White, 0.70f),
    )
}

/**
 * Кэш палитр обложек: цвета каждой обложки вычисляются один раз. Благодаря этому
 * при открытии плеера сразу виден правильный цвет, а цвета соседних треков очереди
 * считаются заранее — при переключении трека палитра уже готова.
 */
object ArtColorCache {
    private val cache = ConcurrentHashMap<Uri, ArtColors>()
    private val mutex = Mutex()

    fun peek(uri: Uri?): ArtColors? = uri?.let { cache[it] }

    suspend fun get(context: Context, uri: Uri): ArtColors {
        cache[uri]?.let { return it }
        return mutex.withLock {
            cache[uri] ?: compute(context, uri).also { cache[uri] = it }
        }
    }

    private suspend fun compute(context: Context, uri: Uri): ArtColors = withContext(Dispatchers.Default) {
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(MediaThumb(uri, false))
                .size(160)
                .allowHardware(false)
                .build()
            val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable
            var bitmap = drawable?.bitmap ?: return@runCatching null
            if (Build.VERSION.SDK_INT >= 26 && bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            }
            val p = Palette.from(bitmap).maximumColorCount(16).generate()
            val accent = (p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.dominantSwatch)?.rgb ?: return@runCatching null
            val dark = (p.darkVibrantSwatch ?: p.darkMutedSwatch ?: p.dominantSwatch)?.rgb ?: accent
            val muted = (p.mutedSwatch ?: p.lightMutedSwatch ?: p.darkMutedSwatch)?.rgb ?: dark
            ArtColors(accent = Color(accent), dark = Color(dark), muted = Color(muted))
        }.getOrNull() ?: DefaultArtColors
    }
}

/** Заранее считает палитры текущего и соседних (±2) треков очереди. */
@Composable
fun PrefetchArtColors(state: PlayerUiState) {
    val context = LocalContext.current
    val pos = state.queue.indexOfFirst { it.itemIndex == state.currentIndex }
    val ids = remember(state.queue, pos) {
        if (pos < 0) emptyList()
        else (listOf(pos) + (1..2).flatMap { listOf(pos + it, pos - it) })
            .filter { it in state.queue.indices }
            .map { state.queue[it].mediaId }
    }
    LaunchedEffect(ids) {
        ids.forEach { id ->
            val uri = id.toLongOrNull()?.let { MediaRepository.track(it) }?.uri ?: return@forEach
            ArtColorCache.get(context, uri)
        }
    }
}

/**
 * Палитра обложки с плавной сменой. Если палитра уже в кэше — она применяется сразу,
 * без перехода от «чужого» цвета; смена идёт синхронно с анимацией смены обложки.
 */
@Composable
fun rememberArtColors(uri: Uri?): ArtColors {
    val context = LocalContext.current
    var computed by remember { mutableStateOf(ArtColorCache.peek(uri) ?: DefaultArtColors) }
    LaunchedEffect(uri) {
        computed = if (uri == null) DefaultArtColors else ArtColorCache.get(context, uri)
    }
    // Готовая палитра из кэша применяется в той же композиции, что и новая обложка
    val target = ArtColorCache.peek(uri) ?: computed

    val spec = tween<Color>(durationMillis = 550)
    val accent by animateColorAsState(target.accent, spec, label = "accent")
    val dark by animateColorAsState(target.dark, spec, label = "dark")
    val muted by animateColorAsState(target.muted, spec, label = "muted")
    return ArtColors(accent, dark, muted)
}

/** Акцент должен хорошо читаться на тёмном фоне плеера. */
private fun Color.readableOnDark(): Color = when {
    luminance() < 0.18f -> lerp(this, Color.White, 0.45f)
    luminance() < 0.30f -> lerp(this, Color.White, 0.25f)
    else -> this
}

/** …и на светлом. */
private fun Color.readableOnLight(): Color = when {
    luminance() > 0.60f -> lerp(this, Color.Black, 0.50f)
    luminance() > 0.40f -> lerp(this, Color.Black, 0.30f)
    else -> this
}
