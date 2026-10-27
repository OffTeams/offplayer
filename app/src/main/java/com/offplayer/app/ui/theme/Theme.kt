package com.offplayer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offplayer.app.data.AppSettings
import com.offplayer.app.data.ThemeMode

/** Спокойный серо-голубой акцент: выделяет, но не спорит с обложками и превью. */
val Accent = Color(0xFF9CB8CC)

/** true, если сейчас активна тёмная тема. */
val LocalDarkTheme = staticCompositionLocalOf { true }

// Тёмная палитра в духе YouTube Music
private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF0E1D27),
    primaryContainer = Color(0xFF2B3A45),
    onPrimaryContainer = Color.White,
    secondary = Color.White,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF3A3A3A),
    onSecondaryContainer = Color.White,
    background = Color(0xFF030303),
    onBackground = Color.White,
    surface = Color(0xFF030303),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF272727),
    onSurfaceVariant = Color(0xFFAAAAAA),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0F0F0F),
    surfaceContainer = Color(0xFF181818),
    surfaceContainerHigh = Color(0xFF212121),
    surfaceContainerHighest = Color(0xFF2C2C2C),
    outline = Color(0xFF4A4A4A),
    outlineVariant = Color(0xFF2E2E2E),
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
)

// Светлая палитра: белый фон, почти чёрный текст, приглушённый серо-голубой акцент
private val LightColors = lightColorScheme(
    primary = Color(0xFF46677D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E6EF),
    onPrimaryContainer = Color(0xFF102634),
    secondary = Color(0xFF0F0F0F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E5E5),
    onSecondaryContainer = Color(0xFF0F0F0F),
    // Фон чуть серее, карточки и панели — белые: так у интерфейса появляется объём
    background = Color(0xFFF4F4F6),
    onBackground = Color(0xFF0F0F0F),
    surface = Color(0xFFF4F4F6),
    onSurface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFFE4E4E8),
    onSurfaceVariant = Color(0xFF5E5E66),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFEBEBEF),
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFE0E0E0),
    inverseSurface = Color(0xFF0F0F0F),
    inverseOnSurface = Color.White,
)

@Composable
fun OffPlayerTheme(content: @Composable () -> Unit) {
    val mode by AppSettings.themeMode.collectAsStateWithLifecycle()
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            shapes = AppMaterialShapes,
            content = content,
        )
    }
}
