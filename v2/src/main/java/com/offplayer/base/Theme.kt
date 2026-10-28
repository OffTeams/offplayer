package com.offplayer.base

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

/** Фирменный коралл — ярлык пластинки на иконке. */
val Coral = Color(0xFFFF7A59)

/** Тёмная ли сейчас тема основы. */
val LocalDark = compositionLocalOf { true }

private fun coralScheme(dark: Boolean): ColorScheme =
    if (dark) darkColorScheme(
        primary = Coral,
        onPrimary = Color(0xFF3A0E00),
        primaryContainer = Color(0xFF5C2414),
        onPrimaryContainer = Color(0xFFFFDBD0),
        secondaryContainer = Color(0xFF3B2B26),
        onSecondaryContainer = Color(0xFFFFDBD0),
        background = Color(0xFF17120F),
        surface = Color(0xFF17120F),
        surfaceContainer = Color(0xFF241D1A),
        surfaceContainerHigh = Color(0xFF2F2724),
        surfaceContainerHighest = Color(0xFF3A322F),
    ) else lightColorScheme(
        primary = Color(0xFFC8462A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDBD0),
        onPrimaryContainer = Color(0xFF3A0E00),
        secondaryContainer = Color(0xFFF7DED6),
        onSecondaryContainer = Color(0xFF2C150E),
        background = Color(0xFFFFF8F6),
        surface = Color(0xFFFFF8F6),
        surfaceContainer = Color(0xFFFBEAE5),
        surfaceContainerHigh = Color(0xFFF5E4DF),
        surfaceContainerHighest = Color(0xFFEFDED9),
    )

/** Классический режим: статичный фон пресета, коралловый акцент. */
private fun classicScheme(p: ClassicPreset): ColorScheme {
    val bg = Color(p.background)
    val base = coralScheme(p.dark)
    val fg = if (p.dark) Color.White else Color.Black
    return base.copy(
        background = bg,
        surface = bg,
        surfaceContainerLowest = bg,
        surfaceContainerLow = lerp(bg, fg, 0.03f),
        surfaceContainer = lerp(bg, fg, 0.06f),
        surfaceContainerHigh = lerp(bg, fg, 0.10f),
        surfaceContainerHighest = lerp(bg, fg, 0.14f),
        onBackground = fg,
        onSurface = fg,
        onSurfaceVariant = lerp(fg, bg, 0.35f),
        outlineVariant = lerp(bg, fg, 0.18f),
        secondaryContainer = lerp(bg, Coral, if (p.dark) 0.22f else 0.16f),
        onSecondaryContainer = fg,
    )
}

private fun hsl(h: Float, s: Float, l: Float) =
    Color(ColorUtils.HSLToColor(floatArrayOf(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))

/** Полная схема из одного цвета обоев: оттенок сохраняется, светлота и насыщенность — по ролям. */
private fun seedScheme(seed: Color, dark: Boolean): ColorScheme {
    val hslArr = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hslArr)
    val h = hslArr[0]
    val s = hslArr[1].coerceIn(0.35f, 0.85f)
    return if (dark) darkColorScheme(
        primary = hsl(h, s, 0.72f),
        onPrimary = hsl(h, s * 0.6f, 0.14f),
        primaryContainer = hsl(h, s * 0.55f, 0.27f),
        onPrimaryContainer = hsl(h, s * 0.6f, 0.88f),
        secondaryContainer = hsl(h, s * 0.3f, 0.21f),
        onSecondaryContainer = hsl(h, 0.2f, 0.88f),
        background = hsl(h, s * 0.25f, 0.07f),
        surface = hsl(h, s * 0.25f, 0.07f),
        surfaceContainerLowest = hsl(h, s * 0.2f, 0.05f),
        surfaceContainerLow = hsl(h, s * 0.2f, 0.09f),
        surfaceContainer = hsl(h, s * 0.2f, 0.11f),
        surfaceContainerHigh = hsl(h, s * 0.2f, 0.14f),
        surfaceContainerHighest = hsl(h, s * 0.2f, 0.18f),
        onBackground = hsl(h, 0.12f, 0.92f),
        onSurface = hsl(h, 0.12f, 0.92f),
        onSurfaceVariant = hsl(h, 0.12f, 0.74f),
        outlineVariant = hsl(h, 0.1f, 0.3f),
    ) else lightColorScheme(
        primary = hsl(h, s, 0.36f),
        onPrimary = Color.White,
        primaryContainer = hsl(h, s * 0.6f, 0.86f),
        onPrimaryContainer = hsl(h, s * 0.6f, 0.14f),
        secondaryContainer = hsl(h, s * 0.35f, 0.88f),
        onSecondaryContainer = hsl(h, 0.2f, 0.14f),
        background = hsl(h, s * 0.3f, 0.97f),
        surface = hsl(h, s * 0.3f, 0.97f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = hsl(h, s * 0.3f, 0.95f),
        surfaceContainer = hsl(h, s * 0.3f, 0.93f),
        surfaceContainerHigh = hsl(h, s * 0.3f, 0.91f),
        surfaceContainerHighest = hsl(h, s * 0.3f, 0.88f),
        onBackground = hsl(h, 0.12f, 0.1f),
        onSurface = hsl(h, 0.12f, 0.1f),
        onSurfaceVariant = hsl(h, 0.12f, 0.35f),
        outlineVariant = hsl(h, 0.1f, 0.8f),
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

@Composable
fun OffTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val mode = Prefs.colorMode.value
    val preset = Prefs.preset.value
    val dark = if (mode == ColorMode.CLASSIC) preset.dark else systemDark

    val wallpaperSeed = rememberWallpaperSeed()
    val base = when (mode) {
        // Палитра строится из цвета самих обоев: на части прошивок (HyperOS и др.) системная
        // палитра Material You остаётся серой, даже если обои цветные
        ColorMode.WALLPAPER -> when {
            wallpaperSeed != null -> seedScheme(wallpaperSeed, dark)
            Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            else -> coralScheme(dark)
        }
        ColorMode.CLASSIC -> classicScheme(preset)
    }

    // Пока играет трек, цвета основы подстраиваются под его обложку (только режим «По обоям»)
    val tint = Playback.track?.color?.takeIf { mode == ColorMode.WALLPAPER && Playback.playing }
    val spec = tween<Color>(if (Prefs.lightMode.value) 200 else 700)
    val primary by animateColorAsState(
        tint?.let { lerp(it, if (dark) Color.White else Color.Black, 0.12f) } ?: base.primary, spec, label = "primary",
    )
    val primaryContainer by animateColorAsState(
        tint?.let { lerp(base.surface, it, if (dark) 0.38f else 0.28f) } ?: base.primaryContainer, spec, label = "primaryContainer",
    )
    val secondaryContainer by animateColorAsState(
        tint?.let { lerp(base.surface, it, if (dark) 0.24f else 0.18f) } ?: base.secondaryContainer, spec, label = "secondaryContainer",
    )

    val scheme = base.copy(primary = primary, primaryContainer = primaryContainer, secondaryContainer = secondaryContainer)
    CompositionLocalProvider(LocalDark provides dark) {
        MaterialTheme(colorScheme = scheme) {
            // Текст и значки вне Surface по умолчанию берут цвет фона темы, а не чёрный
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}

/**
 * Фон основы: в режиме «По обоям» — плавный градиент из цветов темы,
 * в классическом и лёгком режимах — ровная заливка.
 */
fun Modifier.baseBackground(): Modifier = composed {
    val c = MaterialTheme.colorScheme
    if (Prefs.colorMode.value == ColorMode.WALLPAPER && !Prefs.lightMode.value) {
        background(
            Brush.verticalGradient(
                0f to lerp(c.background, c.primaryContainer, 0.55f),
                0.55f to lerp(c.background, c.secondaryContainer, 0.35f),
                1f to c.background,
            )
        )
    } else background(c.background)
}
