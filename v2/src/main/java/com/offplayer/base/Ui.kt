package com.offplayer.base

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Общие элементы основы в стиле музыкального плеера 1.x

/** Иконка-кнопка, которая пружинисто «проседает» при нажатии. */
@Composable
fun BouncyIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 48.dp, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.82f else 1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium), label = "press")
    IconButton(onClick = onClick, interactionSource = interaction, modifier = modifier.size(size).scale(scale)) { content() }
}

/** Пружинистое нажатие для любых элементов (строк, карточек). */
fun Modifier.bouncyClick(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "pressRow")
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

/** Большая кнопка Play/Pause со свечением в цвет акцента. */
@Composable
fun PlayButton(isPlaying: Boolean, size: Dp = 78.dp, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "press")
    val glow by animateDpAsState(if (isPlaying && !Prefs.lightMode.value) 22.dp else 8.dp, tween(600), label = "glow")
    Box(
        Modifier.size(size).scale(scale)
            .shadow(glow, CircleShape, ambientColor = pal.accent, spotColor = pal.accent)
            .clip(CircleShape)
            .background(pal.content)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides pal.base) {
            PlayPauseIcon(isPlaying, Modifier.size(size * 0.56f))
        }
    }
}

/** Тонкая полоса прогресса: утолщается при перемотке, ползунок с ореолом акцента. */
@Composable
fun SeekBar(progress: Float, modifier: Modifier = Modifier, onSeek: (Float) -> Unit) {
    val pal = LocalPalette.current
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val shown = if (dragging) dragValue else progress.coerceIn(0f, 1f)
    val thumb by animateDpAsState(if (dragging) 18.dp else 12.dp, label = "thumb")
    val track by animateDpAsState(if (dragging) 7.dp else 4.dp, label = "track")
    Canvas(
        modifier.fillMaxWidth().height(30.dp)
            .pointerInput(Unit) { detectTapGestures { o -> currentOnSeek((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; dragValue = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { dragging = false; currentOnSeek(dragValue) },
                    onDragCancel = { dragging = false },
                ) { change, dx ->
                    change.consume()
                    dragValue = (dragValue + dx / size.width).coerceIn(0f, 1f)
                }
            },
    ) {
        val y = size.height / 2
        val th = track.toPx()
        val x = size.width * shown
        drawLine(pal.content.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y), th, StrokeCap.Round)
        if (x > 0f) {
            drawLine(
                Brush.horizontalGradient(listOf(pal.accent.copy(alpha = 0.55f), pal.accent), endX = x.coerceAtLeast(1f)),
                Offset(0f, y), Offset(x, y), th, StrokeCap.Round,
            )
        }
        drawCircle(pal.accent.copy(alpha = 0.35f), radius = thumb.toPx(), center = Offset(x, y))
        drawCircle(pal.content, radius = thumb.toPx() / 2f, center = Offset(x, y))
    }
}

/** Маленький анимированный эквалайзер у играющего трека. */
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
        values.forEach { v ->
            Box(Modifier.width(3.dp).height(height * v).background(color, CircleShape))
        }
    }
}

/**
 * «Стекло» поверх живого фона: полупрозрачная заливка в тон палитры и тонкая светлая кромка.
 * В лёгком и классическом режимах — непрозрачная заливка.
 */
fun Modifier.glass(shape: Shape, strength: Float = 1f): Modifier = composed {
    val pal = LocalPalette.current
    val opaque = Prefs.lightMode.value || !pal.living
    val fill = if (opaque) lerp(pal.base, pal.content, if (pal.dark) 0.10f else 0.06f)
    else (if (pal.dark) Color.White else Color.Black).copy(alpha = 0.07f * strength)
    clip(shape).background(fill, shape).border(0.5.dp, pal.content.copy(alpha = 0.10f), shape)
}

/** Точка под активным элементом (вкладка, режим), как у переключателей плеера 1.x. */
@Composable
fun ActiveDot(active: Boolean, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    val dot by animateFloatAsState(if (active) 1f else 0f, label = "dot")
    Box(modifier.size(4.dp).scale(dot).background(pal.accent, CircleShape))
}

/** Акцентная подложка мини-плеера: цвет акцента, смешанный с базой. */
@Composable
fun accentFill(): Color {
    val pal = LocalPalette.current
    return lerp(pal.base, pal.accent, if (pal.dark) 0.28f else 0.22f).copy(alpha = if (Prefs.lightMode.value || !pal.living) 1f else 0.82f)
}
