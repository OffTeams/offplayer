package com.offplayer.base

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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

@Composable
fun OffTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val mode = Prefs.colorMode.value
    val preset = Prefs.preset.value
    val dark = if (mode == ColorMode.CLASSIC) preset.dark else systemDark

    val base = when (mode) {
        ColorMode.WALLPAPER ->
            if (Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else coralScheme(dark)
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
        MaterialTheme(colorScheme = scheme, content = content)
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
