package com.offplayer.app.ui

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Общая панель мини-плеера (музыка и видео): во всю ширину, тонкая линия прогресса сверху,
 * обложка, название и кнопки. Тап или свайп вверх — развернуть плеер.
 */
@Composable
fun MiniBar(
    title: String,
    subtitle: String,
    progress: Float,
    accent: Color,
    onOpen: () -> Unit,
    /** Горизонтальный свайп: +1 — следующий, -1 — предыдущий. null — свайп выключен. */
    onSwipe: ((Int) -> Unit)? = null,
    art: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    var swipe by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 40.dp.toPx() }
    val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = Modifier.draggable(
            orientation = Orientation.Vertical,
            state = rememberDraggableState { swipe += it },
            onDragStarted = { swipe = 0f },
            onDragStopped = { v ->
                if (swipe < -threshold || v < -800f) onOpen()
                swipe = 0f
            },
        ),
    ) {
        Column {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = accent,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                drawStopIndicator = {},
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .then(
                        // Свайп ничего не сдвигает — работает как кнопки: слева направо — предыдущий,
                        // справа налево — следующий
                        if (onSwipe == null) Modifier else Modifier.pointerInput(Unit) {
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = {
                                    if (total > swipeThreshold) onSwipe(-1)
                                    else if (total < -swipeThreshold) onSwipe(1)
                                },
                            ) { change, dx ->
                                change.consume()
                                total += dx
                            }
                        }
                    )
                    .clickable(onClick = onOpen)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                art()
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        title, maxLines = 1,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                actions()
            }
        }
    }
}
