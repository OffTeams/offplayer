package com.offplayer.base

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

/** Фирменный коралл — ярлык пластинки на иконке. */
val Coral = Color(0xFFFF7A59)

/**
 * Палитра основы (направление B2): нейтральная база, белые (тёмные) блоки
 * и градиент из цвета текущего трека (или обоев) в верхней части экрана.
 */
data class OffPalette(
    val dark: Boolean,
    /** Фон экрана. */
    val base: Color,
    /** Блоки: карточки, списки, плитки, нижняя панель. */
    val surface: Color,
    /** Поля ввода и подложки переключателей вне градиента. */
    val surfaceVariant: Color,
    val divider: Color,
    val content: Color,
    val contentSecondary: Color,
    val accent: Color,
    /** Подложка активной вкладки и выбранных элементов. */
    val accentContainer: Color,
    /** Градиент: верх, середина, два цветовых пятна. */
    val gradTop: Color,
    val gradMid: Color,
    val spotA: Color,
    val spotB: Color,
    /** Есть ли градиент (нет в классическом режиме). */
    val gradient: Boolean,
) {
    /** Цвет текста и значков поверх градиента. */
    val onGradient: Color get() = if (gradient) Color.White else content
}

private val LightBase = Color(0xFFF2F3F7)
private val DarkBase = Color(0xFF0E0F12)

val LocalPalette = staticCompositionLocalOf {
    OffPalette(
        dark = true, base = DarkBase, surface = Color(0xFF1B1D22), surfaceVariant = Color(0xFF262930),
        divider = Color(0xFF272A31), content = Color(0xFFF3F4F6), contentSecondary = Color(0xFFA3A7B3),
        accent = Coral, accentContainer = Color(0xFF4A2219),
        gradTop = Color(0xFF5C2116), gradMid = Color(0xFF3A160F), spotA = Color(0xFFB23A26), spotB = Color(0xFFC97A2C),
        gradient = true,
    )
}

private fun hsl(h: Float, s: Float, l: Float) =
    Color(ColorUtils.HSLToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))

private fun Color.hslParts(): FloatArray = FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }

/** Палитра из оттенка трека (или обоев): градиент — сам оттенок и соседний (+35°), блоки нейтральные. */
private fun gradientPalette(h: Float, s: Float, dark: Boolean): OffPalette {
    val sat = s.coerceIn(0.45f, 0.9f)
    return if (dark) OffPalette(
        dark = true,
        base = DarkBase,
        surface = Color(0xFF1B1D22),
        surfaceVariant = Color(0xFF262930),
        divider = Color(0xFF272A31),
        content = Color(0xFFF3F4F6),
        contentSecondary = Color(0xFFA3A7B3),
        accent = hsl(h, sat * 0.85f, 0.72f),
        accentContainer = hsl(h, sat * 0.45f, 0.2f),
        gradTop = hsl(h, sat * 0.6f, 0.22f),
        gradMid = hsl(h, sat * 0.6f, 0.15f),
        spotA = hsl(h, sat * 0.65f, 0.42f),
        spotB = hsl(h + 35f, sat * 0.6f, 0.46f),
        gradient = true,
    ) else OffPalette(
        dark = false,
        base = LightBase,
        surface = Color.White,
        surfaceVariant = Color(0xFFE4E6EC),
        divider = Color(0xFFECEDF1),
        content = Color(0xFF15171C),
        contentSecondary = Color(0xFF5D6170),
        accent = hsl(h, sat * 0.62f, 0.46f),
        accentContainer = hsl(h, sat * 0.8f, 0.92f),
        gradTop = hsl(h, sat * 0.6f, 0.32f),
        gradMid = hsl(h, sat * 0.5f, 0.58f),
        spotA = hsl(h, sat * 0.62f, 0.44f),
        spotB = hsl(h + 35f, sat * 0.45f, 0.45f),
        gradient = true,
    )
}

/** Классический режим: статичный фон пресета и коралловый акцент, без градиента. */
private fun classicPalette(p: ClassicPreset): OffPalette {
    val bg = Color(p.background)
    val fg = if (p.dark) Color(0xFFF3F4F6) else Color(0xFF15171C)
    val surface = if (p.dark) lerp(bg, Color.White, 0.08f) else if (bg == Color.White) Color(0xFFF2F3F7) else Color.White
    return OffPalette(
        dark = p.dark, base = bg, surface = surface,
        surfaceVariant = lerp(surface, fg, 0.08f), divider = lerp(surface, fg, 0.07f),
        content = fg, contentSecondary = lerp(fg, bg, 0.38f),
        accent = if (p.dark) Coral else Color(0xFFC8462A),
        accentContainer = lerp(surface, Coral, if (p.dark) 0.25f else 0.18f),
        gradTop = bg, gradMid = bg, spotA = bg, spotB = bg, gradient = false,
    )
}

/** Схема Material из палитры — для стандартных компонентов (диалоги, переключатели, ползунки). */
private fun schemeOf(p: OffPalette): ColorScheme {
    val common = if (p.dark) darkColorScheme() else lightColorScheme()
    val onAccent = if (p.dark) Color(0xFF15171C) else Color.White
    return common.copy(
        primary = p.accent,
        onPrimary = onAccent,
        primaryContainer = p.accentContainer,
        onPrimaryContainer = p.content,
        secondaryContainer = p.accentContainer,
        onSecondaryContainer = p.content,
        background = p.base,
        onBackground = p.content,
        surface = p.base,
        onSurface = p.content,
        onSurfaceVariant = p.contentSecondary,
        surfaceContainerLowest = p.surface,
        surfaceContainerLow = p.surface,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface,
        surfaceContainerHighest = p.surfaceVariant,
        outlineVariant = p.divider,
        outline = p.contentSecondary,
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
 * Смена цвета идёт по кругу оттенков кратчайшим путём (фиолетовый → розовый → коралл),
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

    // Пока играет трек — цвет из его обложки, иначе — из обоев
    val playingTint = Playback.track?.color?.takeIf { mode == ColorMode.WALLPAPER && Playback.playing }
    val (hue, sat) = animateSeed(playingTint ?: wallpaperSeed ?: Coral, if (Prefs.lightMode.value) 200 else 800)

    val palette = when (mode) {
        ColorMode.WALLPAPER -> gradientPalette(hue, sat, systemDark)
        ColorMode.CLASSIC -> classicPalette(Prefs.preset.value)
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = schemeOf(palette)) {
            CompositionLocalProvider(LocalContentColor provides palette.content, content = content)
        }
    }
}

/**
 * Градиент от трека в верхней части экрана: глубокий цвет сверху, светлее к середине,
 * два цветовых пятна по углам — и плавный уход в фон. [height] — докуда доходит цвет.
 */
@Composable
fun HeaderGradient(height: Dp, modifier: Modifier = Modifier, solid: Float = 0.4f) {
    val pal = LocalPalette.current
    if (!pal.gradient) return
    Box(
        modifier.fillMaxWidth().height(height).drawBehind {
            val w = size.width
            val h = size.height
            // [solid] — доля высоты, до которой цвет держится насыщенным (в плеере — до самих кнопок)
            drawRect(
                Brush.verticalGradient(
                    0f to pal.gradTop,
                    solid to pal.gradMid,
                    1f to pal.gradMid.copy(alpha = 0f),
                )
            )
            drawRect(
                Brush.radialGradient(
                    listOf(pal.spotA, pal.spotA.copy(alpha = 0f)),
                    center = Offset(w * 0.08f, 0f), radius = maxOf(w, h) * 0.8f,
                )
            )
            drawRect(
                Brush.radialGradient(
                    listOf(pal.spotB.copy(alpha = 0.85f), pal.spotB.copy(alpha = 0f)),
                    center = Offset(w, h * 0.1f), radius = w * 0.75f,
                )
            )
            // Низ градиента растворяется в фоне
            drawRect(Brush.verticalGradient(maxOf(0.55f, solid) to Color.Transparent, 1f to pal.base))
        }
    )
}

/** Сплошной фон экрана. */
fun Modifier.screenBackground(pal: OffPalette): Modifier = background(pal.base)
