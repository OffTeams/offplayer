package com.offplayer.base

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Элементы основы в стиле B2: белые (тёмные) блоки на нейтральном фоне, стекло на градиенте

/** Иконка-кнопка, которая пружинисто «проседает» при нажатии. */
@Composable
fun BouncyIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 48.dp, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "press")
    IconButton(onClick = onClick, interactionSource = interaction, modifier = modifier.size(size).scale(scale)) { content() }
}

/** Лёгкое пружинистое нажатие для строк и карточек. */
fun Modifier.bouncyClick(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "pressRow")
    scale(scale).clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

@Composable
fun PlayPauseIcon(isPlaying: Boolean, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = isPlaying,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = 0.5f), initialScale = 0.5f) + fadeIn(tween(150))) togetherWith
                (scaleOut(tween(120), targetScale = 0.5f) + fadeOut(tween(120)))
        },
        label = "playPause",
    ) { playing ->
        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Пауза" else "Играть", modifier)
    }
}

/** Блок (карточка, список, плитка) — белый в светлой теме, тёмно-серый в тёмной. */
fun Modifier.block(shape: Shape = RoundedCornerShape(24.dp)): Modifier = composed {
    val pal = LocalPalette.current
    clip(shape).background(pal.surface, shape)
}

/** Стекло поверх градиента: полупрозрачная белая подложка и тонкая светлая кромка. Без градиента — обычный блок. */
fun Modifier.glass(shape: Shape): Modifier = composed {
    val pal = LocalPalette.current
    if (!pal.gradient) clip(shape).background(pal.surface, shape)
    else clip(shape)
        .background(Color.White.copy(alpha = if (pal.dark) 0.12f else 0.16f), shape)
        .border(1.dp, Color.White.copy(alpha = if (pal.dark) 0.16f else 0.24f), shape)
}

/** Круглая стеклянная кнопка (шапка, плеер). */
@Composable
fun GlassIconButton(icon: ImageVector, description: String, size: Dp = 44.dp, iconSize: Dp = 21.dp, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Box(Modifier.size(size).glass(CircleShape), contentAlignment = Alignment.Center) {
        BouncyIconButton(onClick = onClick, size = size) { Icon(icon, description, Modifier.size(iconSize), tint = pal.onGradient) }
    }
}

/** Крупный заголовок экрана поверх градиента. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    Text(
        text, modifier = modifier, color = pal.onGradient, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
    )
}

/** Заголовок раздела на фоне: «Продолжить», «Недавно скачано». */
@Composable
fun SectionHeader(text: String, action: String? = null, onAction: () -> Unit = {}) {
    val pal = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, modifier = Modifier.weight(1f), color = pal.content,
            style = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp))
        if (action != null) {
            Text(action, color = pal.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(4.dp))
        }
    }
}

/** Маленькая подпись над списком: «ВСЕ ТРЕКИ · 12». */
@Composable
fun CaptionHeader(text: String, action: String? = null, onAction: () -> Unit = {}) {
    val pal = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), modifier = Modifier.weight(1f), color = pal.contentSecondary, fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
        if (action != null) {
            Text(action, color = pal.accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(4.dp))
        }
    }
}

/** Поле поиска на градиенте (стекло). */
@Composable
fun GlassSearchField(placeholder: String, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    Row(
        modifier.fillMaxWidth().height(44.dp).glass(RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = pal.onGradient)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(placeholder, color = pal.onGradient.copy(alpha = 0.8f), fontSize = 15.sp)
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(color = pal.onGradient, fontSize = 15.sp),
                cursorBrush = SolidColor(pal.onGradient), modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Кнопка Play: скруглённый квадрат (Material 3 Expressive), белый на градиенте, иконка цвета акцента. */
@Composable
fun PlaySquareButton(isPlaying: Boolean, size: Dp = 56.dp, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "press")
    // Квадрат слегка «перетекает» в более круглый, пока идёт воспроизведение
    val corner by animateDpAsState(if (isPlaying) size * 0.33f else size * 0.5f, spring(dampingRatio = 0.6f), label = "corner")
    val bg = if (pal.gradient) Color.White else pal.accent
    val fg = if (pal.gradient) pal.accent else Color.White
    Box(
        Modifier.size(size).scale(scale)
            .shadow(if (Prefs.lightMode.value) 0.dp else size * 0.14f, RoundedCornerShape(corner))
            .clip(RoundedCornerShape(corner)).background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) { PlayPauseIcon(isPlaying, Modifier.size(size * 0.42f)) }
    }
}

/**
 * Толстая полоса прогресса Material 3 Expressive: пройденная часть, вертикальная ручка, зазор, остаток.
 * [onGradient] — белая поверх градиента, иначе цвета акцента.
 */
@Composable
fun ThickSeekBar(progress: Float, onGradient: Boolean, modifier: Modifier = Modifier, onSeek: (Float) -> Unit) {
    val pal = LocalPalette.current
    val active = if (onGradient && pal.gradient) Color.White else pal.accent
    val inactive = if (onGradient && pal.gradient) Color.White.copy(alpha = 0.28f) else pal.surfaceVariant
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val shown = (if (dragging) dragValue else progress).coerceIn(0f, 1f)
    val thick by animateDpAsState(if (dragging) 14.dp else 10.dp, label = "thick")
    BoxWithConstraints(
        modifier.fillMaxWidth().height(28.dp)
            .pointerInput(Unit) { detectTapGestures { o -> currentOnSeek((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; dragValue = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { dragging = false; currentOnSeek(dragValue) },
                    onDragCancel = { dragging = false },
                ) { change, dx -> change.consume(); dragValue = (dragValue + dx / size.width).coerceIn(0f, 1f) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val handle = 4.dp
        val gap = 4.dp
        val usable = maxWidth - handle - gap * 2
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(usable * shown).height(thick).clip(RoundedCornerShape(thick / 2)).background(active))
            Spacer(Modifier.width(gap))
            Box(Modifier.width(handle).height(thick * 2).clip(RoundedCornerShape(2.dp)).background(active))
            Spacer(Modifier.width(gap))
            Box(Modifier.width(usable * (1f - shown)).height(thick).clip(RoundedCornerShape(thick / 2)).background(inactive))
        }
    }
}

/** Маленький анимированный эквалайзер у играющего элемента. */
@Composable
fun EqualizerBars(isPlaying: Boolean, color: Color, height: Dp = 14.dp, bars: Int = 3) {
    val transition = rememberInfiniteTransition(label = "eq")
    val speeds = listOf(420, 560, 360, 610, 470)
    val values = (0 until bars).map { i ->
        if (isPlaying && !Prefs.lightMode.value) transition.animateFloat(
            0.25f, 1f, infiniteRepeatable(tween(speeds[i % speeds.size]), RepeatMode.Reverse), label = "bar$i",
        ).value else 0.35f
    }
    Row(Modifier.height(height), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        values.forEach { v -> Box(Modifier.width(3.dp).height(height * v).background(color, CircleShape)) }
    }
}

/** Плитка (как в Xiaomi Earbuds): значок или крупное значение сверху, подпись снизу. */
@Composable
fun Tile(label: String, modifier: Modifier = Modifier, top: @Composable () -> Unit, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Column(
        modifier.height(84.dp).block(RoundedCornerShape(22.dp)).bouncyClick(onClick).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        top()
        Text(label, color = pal.content, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Тонкий разделитель внутри блока, с отступом под обложку слева. */
@Composable
fun InsetDivider(start: Dp = 70.dp) {
    val pal = LocalPalette.current
    Box(Modifier.fillMaxWidth().padding(start = start).height(1.dp).background(pal.divider))
}

/** Стеклянная плашка (например, «Микс … » с эквалайзером в плеере). */
@Composable
fun GlassChip(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier.height(32.dp).glass(RoundedCornerShape(16.dp)).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        content()
    }
}

/** Насколько прокручен список переднего модуля (px) — градиент шапки уезжает вместе со страницей. */
object ScreenScroll {
    var offsetPx by mutableFloatStateOf(0f)
}

/**
 * Состояние списка модуля, которое сообщает основе, насколько страница прокручена.
 * Размеры уже пролистанных строк запоминаются, поэтому смещение считается плавно, без скачков.
 */
@Composable
fun trackedListState(): androidx.compose.foundation.lazy.LazyListState {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    val env = LocalModuleEnv.current
    if (env.front) {
        androidx.compose.runtime.LaunchedEffect(state) {
            val sizes = HashMap<Int, Int>()
            androidx.compose.runtime.snapshotFlow {
                state.layoutInfo.visibleItemsInfo.forEach { sizes[it.index] = it.size }
                val first = state.firstVisibleItemIndex
                (0 until first).sumOf { sizes[it] ?: 0 }.toFloat() + state.firstVisibleItemScrollOffset
            }.collect { ScreenScroll.offsetPx = it }
        }
        androidx.compose.runtime.DisposableEffect(Unit) { onDispose { ScreenScroll.offsetPx = 0f } }
    }
    return state
}

/** Сдвиг градиента шапки вверх вместе с прокруткой страницы. */
fun Modifier.scrollWithPage(): Modifier = this.then(
    Modifier.graphicsLayer { translationY = -ScreenScroll.offsetPx }
)

/** Стиль заголовка блока — для единообразия текстов внутри модулей. */
val TitleStyle @Composable get() = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
