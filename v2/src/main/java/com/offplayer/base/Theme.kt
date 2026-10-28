package com.offplayer.base

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Фирменный коралл — ярлык пластинки на иконке. */
val Coral = Color(0xFFFF7A59)

/**
 * Палитра основы в духе плеера 1.x: чёрная (или белая) база, белый (чёрный) текст,
 * акцент и пятна живого фона — все из одного семейства оттенков, без серых и бурых тонов.
 */
data class OffPalette(
    val dark: Boolean,
    val base: Color,
    val content: Color,
    val contentSecondary: Color,
    val accent: Color,
    val blobTop: Color,
    val blobMid: Color,
    val blobBottom: Color,
    val blobAccent: Color,
    /** Живой фон с плывущими пятнами (нет в классическом режиме). */
    val living: Boolean,
)

val LocalPalette = staticCompositionLocalOf {
    OffPalette(true, Color.Black, Color.White, Color.White.copy(alpha = 0.7f), Coral, Color.Black, Color.Black, Color.Black, Coral, true)
}

private fun hsl(h: Float, s: Float, l: Float) =
    Color(ColorUtils.HSLToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))

private fun Color.hslParts(): FloatArray = FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }

private fun Color.readableOnDark(): Color = when {
    luminance() < 0.18f -> lerp(this, Color.White, 0.45f)
    luminance() < 0.30f -> lerp(this, Color.White, 0.25f)
    else -> this
}

private fun Color.readableOnLight(): Color = when {
    luminance() > 0.60f -> lerp(this, Color.Black, 0.50f)
    luminance() > 0.40f -> lerp(this, Color.Black, 0.30f)
    else -> this
}

/**
 * Пятна — один оттенок (h) и два соседних (±20°), разной светлоты: смесь остаётся чистой.
 * Насыщенность ограничена снизу, чтобы фон не уходил в серый.
 */
private fun livingPalette(h: Float, s: Float, dark: Boolean): OffPalette {
    val sat = s.coerceIn(0.45f, 0.9f)
    return if (dark) OffPalette(
        dark = true,
        base = Color.Black,
        content = Color.White,
        contentSecondary = Color.White.copy(alpha = 0.7f),
        accent = hsl(h, sat, 0.62f).readableOnDark(),
        blobTop = hsl(h, sat * 0.85f, 0.26f),
        blobMid = hsl(h + 20f, sat * 0.8f, 0.22f),
        blobBottom = hsl(h - 20f, sat * 0.85f, 0.2f),
        blobAccent = hsl(h, sat, 0.5f).copy(alpha = 0.32f),
        living = true,
    ) else OffPalette(
        dark = false,
        base = Color.White,
        content = Color(0xFF111111),
        contentSecondary = Color(0xFF111111).copy(alpha = 0.62f),
        accent = hsl(h, sat, 0.55f).readableOnLight(),
        blobTop = hsl(h, sat * 0.9f, 0.82f),
        blobMid = hsl(h + 20f, sat * 0.8f, 0.86f),
        blobBottom = hsl(h - 20f, sat * 0.85f, 0.84f),
        blobAccent = hsl(h, sat, 0.72f).copy(alpha = 0.45f),
        living = true,
    )
}

/** Классический режим: статичный фон пресета и коралловый акцент, без пятен. */
private fun classicPalette(p: ClassicPreset): OffPalette {
    val bg = Color(p.background)
    val fg = if (p.dark) Color.White else Color(0xFF111111)
    return OffPalette(
        dark = p.dark, base = bg, content = fg, contentSecondary = fg.copy(alpha = if (p.dark) 0.7f else 0.62f),
        accent = if (p.dark) Coral else Color(0xFFC8462A),
        blobTop = bg, blobMid = bg, blobBottom = bg, blobAccent = bg, living = false,
    )
}

/** Схема Material из палитры: поверхности — база с лёгким оттенком акцента. */
private fun schemeOf(p: OffPalette): ColorScheme {
    val tint = lerp(p.blobTop, p.accent, 0.35f)
    fun layer(k: Float) = lerp(p.base, if (p.dark) lerp(tint, Color.White, 0.08f) else lerp(tint, Color.Black, 0.04f), k)
    val onAccent = if (p.accent.luminance() > 0.45f) Color(0xFF111111) else Color.White
    val common = if (p.dark) darkColorScheme() else lightColorScheme()
    return common.copy(
        primary = p.accent,
        onPrimary = onAccent,
        primaryContainer = lerp(p.base, p.accent, if (p.dark) 0.32f else 0.22f),
        onPrimaryContainer = p.content,
        secondaryContainer = lerp(p.base, p.accent, if (p.dark) 0.2f else 0.14f),
        onSecondaryContainer = p.content,
        background = p.base,
        onBackground = p.content,
        surface = p.base,
        onSurface = p.content,
        onSurfaceVariant = p.contentSecondary,
        surfaceContainerLowest = p.base,
        surfaceContainerLow = layer(0.10f),
        surfaceContainer = layer(0.16f),
        surfaceContainerHigh = layer(0.22f),
        surfaceContainerHighest = layer(0.30f),
        outlineVariant = p.content.copy(alpha = 0.14f),
        outline = p.content.copy(alpha = 0.4f),
    )
}

/** Самый «цветной» из цветов обоев (основной может быть серым, если обои с большим тёмным участком). */
private fun readWallpaperSeed(context: Context): Color? {
    if (Build.VERSION.SDK_INT < 27) return null
    val colors = runCatching {
        WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
    }.getOrNull() ?: return null
    val hslArr = FloatArray(3)
    return listOfNotNull(colors.primaryColor, colors.secondaryColor, colors.tertiaryColor)
        .map { it.toArgb() }
        .filter { ColorUtils.colorToHSL(it, hslArr); hslArr[1] > 0.12f && hslArr[2] in 0.12f..0.9f }
        .maxByOrNull { ColorUtils.colorToHSL(it, hslArr); hslArr[1] }
        ?.let { Color(it) }
}

/** Цвет обоев; перечитывается при смене обоев и при каждом возвращении в приложение. */
@Composable
private fun rememberWallpaperSeed(): Color? {
    val context = LocalContext.current
    var seed by remember { mutableStateOf(readWallpaperSeed(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) seed = readWallpaperSeed(context) }
        lifecycle.addObserver(observer)
        val wm = if (Build.VERSION.SDK_INT >= 27) WallpaperManager.getInstance(context) else null
        val listener = if (Build.VERSION.SDK_INT >= 27) {
            WallpaperManager.OnColorsChangedListener { _, _ -> seed = readWallpaperSeed(context) }
        } else null
        if (Build.VERSION.SDK_INT >= 27 && listener != null) wm?.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        onDispose {
            lifecycle.removeObserver(observer)
            if (Build.VERSION.SDK_INT >= 27 && listener != null) wm?.removeOnColorsChangedListener(listener)
        }
    }
    return seed
}

/**
 * Смена цвета идёт по кругу оттенков кратчайшим путём (зелёный → жёлтый → коралл),
 * а не прямым смешиванием RGB, которое проходит через бурый и серый.
 */
@Composable
private fun animateSeed(target: Color, durationMs: Int): Pair<Float, Float> {
    val parts = target.hslParts()
    val hue = remember { Animatable(parts[0]) }
    val sat = remember { Animatable(parts[1]) }
    LaunchedEffect(parts[0], parts[1]) {
        val cur = ((hue.value % 360f) + 360f) % 360f
        var d = parts[0] - cur
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        hue.snapTo(cur)
        launch { hue.animateTo(cur + d, tween(durationMs)) }
        sat.animateTo(parts[1], tween(durationMs))
    }
    return hue.value to sat.value
}

@Composable
fun OffTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val mode = Prefs.colorMode.value
    val wallpaperSeed = rememberWallpaperSeed()

    // Пока играет трек, цвета основы берутся из его обложки; иначе — из обоев
    val playingTint = Playback.track?.color?.takeIf { mode == ColorMode.WALLPAPER && Playback.playing }
    val (hue, sat) = animateSeed(playingTint ?: wallpaperSeed ?: Coral, if (Prefs.lightMode.value) 200 else 700)

    val palette = when (mode) {
        ColorMode.WALLPAPER -> livingPalette(hue, sat, systemDark)
        ColorMode.CLASSIC -> classicPalette(Prefs.preset.value)
    }
    val scheme = schemeOf(palette)
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme) {
            // Текст и значки вне Surface по умолчанию берут цвет содержимого, а не чёрный
            CompositionLocalProvider(LocalContentColor provides palette.content, content = content)
        }
    }
}

/**
 * Живой фон, как у плеера 1.x: медленно плывущие цветные пятна, ярче во время музыки,
 * и затемнение (осветление) книзу под элементы управления. В лёгком режиме пятна стоят.
 */
@Composable
fun LivingBackground(modifier: Modifier = Modifier, still: Boolean = false) {
    val pal = LocalPalette.current
    if (!pal.living) {
        Box(modifier.fillMaxSize().background(pal.base))
        return
    }
    val still = still || Prefs.lightMode.value
    val phase = if (still) 0.8f else rememberInfiniteTransition(label = "bg").animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "phase",
    ).value
    val energy by animateFloatAsState(if (Playback.playing || Playback.videoPlaying) 1f else 0.6f, tween(1200), label = "energy")
    Box(modifier.fillMaxSize().background(pal.base)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Размер пятен — от длинной стороны, чтобы цвет покрывал весь экран, а не только верх
            val r = maxOf(w, h)
            fun blob(color: Color, cx: Float, cy: Float, radius: Float) = drawCircle(
                brush = Brush.radialGradient(listOf(color, color.copy(alpha = color.alpha * 0.5f), Color.Transparent),
                    center = Offset(cx, cy), radius = radius),
                radius = radius, center = Offset(cx, cy),
            )
            // Пятна разнесены сверху донизу и медленно плывут каждое по своей траектории
            blob(pal.blobTop, w * (0.2f + 0.15f * cos(phase)), h * (0.1f + 0.05f * sin(phase)), r * 0.55f)
            blob(pal.blobMid, w * (0.88f + 0.1f * sin(phase * 2)), h * (0.38f + 0.07f * cos(phase)), r * 0.45f)
            blob(pal.blobBottom, w * (0.12f + 0.14f * cos(phase + 2f)), h * (0.68f + 0.06f * sin(phase + 1f)), r * 0.45f)
            blob(pal.blobTop, w * (0.8f + 0.12f * cos(phase + 1f)), h * (0.96f + 0.04f * sin(phase * 2)), r * 0.5f)
            blob(pal.blobAccent.copy(alpha = pal.blobAccent.alpha * energy),
                w * (0.5f + 0.25f * sin(phase + 0.5f)), h * (0.52f + 0.12f * cos(phase * 2)), r * 0.35f)
        }
        // Лёгкое ровное приглушение для читаемости текста — без выбеливания (затемнения) низа
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to pal.base.copy(alpha = 0.10f),
                    0.5f to pal.base.copy(alpha = 0.06f),
                    1f to pal.base.copy(alpha = 0.18f),
                )
            )
        )
    }
}
