package com.offplayer.app.ui.music

import com.offplayer.app.tr
import com.offplayer.app.R

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offplayer.app.playback.MusicController
import com.offplayer.app.playback.PlayerUiState
import com.offplayer.app.ui.formatDuration
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Текущее время, обновляемое раз в секунду, пока таймер активен. */
@Composable
private fun rememberNow(active: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        while (active) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return now
}

/** Луна в шапке плеера: подсвечивается и пульсирует, когда таймер заведён. */
@Composable
fun SleepTimerButton(state: PlayerUiState, pal: PlayerPalette) {
    var open by remember { mutableStateOf(false) }
    val active = state.sleepAtMs != null
    val now = rememberNow(active)
    val pulse by rememberInfiniteTransition(label = "moon").animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "moon",
    )
    val tint by animateColorAsState(if (active) pal.accent else LocalContentColor.current, label = "moonTint")

    Box(contentAlignment = Alignment.Center) {
        BouncyIconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.Bedtime, tr(R.string.sleep_timer), tint = tint, modifier = Modifier.scale(if (active) pulse else 1f))
        }
        AnimatedVisibility(
            visible = active,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 6.dp),
        ) {
            val minutes = ((state.sleepAtMs ?: now) - now).coerceAtLeast(0L).let { (it + 59_999) / 60_000 }
            Text(
                tr(R.string.min_short, minutes),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = pal.base,
                modifier = Modifier
                    .background(pal.accent, CircleShape)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
    if (open) SleepTimerSheet(state, pal, now) { open = false }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SleepTimerSheet(state: PlayerUiState, pal: PlayerPalette, now: Long, onDismiss: () -> Unit) {
    val active = state.sleepAtMs != null
    val sheetColor = if (pal.dark) lerp(Color(0xFF141414), pal.accent, 0.10f) else lerp(Color.White, pal.accent, 0.06f)
    var custom by remember { mutableFloatStateOf(20f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.statusBarsPadding(),
        containerColor = sheetColor,
        contentColor = pal.content,
        dragHandle = { BottomSheetDefaults.DragHandle(color = pal.content.copy(alpha = 0.25f)) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Заголовок
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(pal.accent.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Bedtime, null, tint = pal.accent) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(tr(R.string.sleep_timer), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        tr(R.string.sleep_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = pal.contentSecondary,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            // Активный таймер — кольцо обратного отсчёта
            AnimatedContent(
                targetState = active,
                transitionSpec = { (fadeIn(tween(300)) + scaleIn(initialScale = 0.9f)) togetherWith fadeOut(tween(200)) },
                label = "timerState",
            ) { isActive ->
                if (isActive) {
                    val remaining = ((state.sleepAtMs ?: now) - now).coerceAtLeast(0L)
                    val total = (state.sleepTotalMs ?: remaining).coerceAtLeast(1L)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CountdownRing(
                            fraction = remaining.toFloat() / total,
                            accent = pal.accent,
                            track = pal.content.copy(alpha = 0.10f),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(formatDuration(remaining), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    if (state.sleepEndOfTrack) tr(R.string.until_track_end_lc) else tr(R.string.remaining),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = pal.contentSecondary,
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Pill(
                                text = tr(R.string.plus_5_min), icon = Icons.Rounded.Add, selected = false, pal = pal,
                                onClick = { MusicController.extendSleepTimer(5) },
                            )
                            Pill(
                                text = tr(R.string.turn_off), icon = Icons.Rounded.Close, selected = true, pal = pal,
                                onClick = { MusicController.setSleepTimer(null) },
                            )
                        }
                        Spacer(Modifier.height(22.dp))
                    }
                } else {
                    Spacer(Modifier.height(0.dp))
                }
            }

            // Быстрый выбор
            Text(
                if (active) tr(R.string.change_time) else tr(R.string.stop_after),
                style = MaterialTheme.typography.labelLarge,
                color = pal.contentSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            )
            val totalMin = state.sleepTotalMs?.let { (it / 60_000).toInt() }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf(10, 15, 30, 45, 60, 90).forEach { m ->
                    Pill(
                        text = tr(R.string.min_short, m),
                        selected = active && !state.sleepEndOfTrack && totalMin == m,
                        pal = pal,
                        onClick = { MusicController.setSleepTimer(m) },
                    )
                }
                Pill(
                    text = tr(R.string.until_track_end),
                    selected = active && state.sleepEndOfTrack,
                    pal = pal,
                    onClick = { MusicController.setSleepAtEndOfTrack() },
                )
            }

            // Своё время
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr(R.string.custom_time), style = MaterialTheme.typography.labelLarge, color = pal.contentSecondary, modifier = Modifier.weight(1f))
                Text(tr(R.string.min_short, custom.roundToInt()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Slider(
                value = custom,
                onValueChange = { custom = it },
                valueRange = 5f..180f,
                steps = 34,
                colors = SliderDefaults.colors(
                    thumbColor = pal.accent,
                    activeTrackColor = pal.accent,
                    inactiveTrackColor = pal.content.copy(alpha = 0.12f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(CircleShape)
                    .background(pal.content)
                    .clickable { MusicController.setSleepTimer(custom.roundToInt()) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tr(R.string.start_for_min, custom.roundToInt()),
                    color = pal.base,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
private fun CountdownRing(fraction: Float, accent: Color, track: Color, content: @Composable () -> Unit) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "ring")
    Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(190.dp)) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(accent, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Скруглённая кнопка-«пилюля»; выбранная заливается акцентом. */
@Composable
private fun Pill(
    text: String,
    selected: Boolean,
    pal: PlayerPalette,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val bg by animateColorAsState(if (selected) pal.accent else pal.content.copy(alpha = 0.07f), label = "pillBg")
    val fg by animateColorAsState(if (selected) pal.base else pal.content, label = "pillFg")
    val scale by animateFloatAsState(if (selected) 1.04f else 1f, spring(dampingRatio = 0.5f), label = "pillScale")
    Row(
        Modifier
            .scale(scale)
            .clip(CircleShape)
            .background(bg)
            .border(BorderStroke(1.dp, pal.content.copy(alpha = if (selected) 0f else 0.10f)), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}
