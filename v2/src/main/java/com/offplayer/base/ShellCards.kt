package com.offplayer.base

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/** Сколько карточек рисуется в стопке. */
private const val VISIBLE = 4

/**
 * Активная карточная лента: модули стопкой наклонённых карт.
 * Быстрый свайп от края до края карточки — она улетает в сторону свайпа и уходит в конец стопки.
 * Жест-дуга (снизу вверх и обратно вниз) — последняя убранная карточка возвращается.
 * Живут только передняя карточка и карточка с играющим контентом, остальные показывают снимок.
 */
@Composable
fun CardStackShell() {
    val tabs = Catalog.tabs()
    val ids = tabs.map { it.id }
    val order = remember { mutableStateListOf<String>() }
    // Порядок стопки: известные карточки в своём порядке, новые — в конец
    val stack = order.filter { it in ids } + ids.filter { it !in order }
    val stackNow by rememberUpdatedState(stack)
    SideEffect { Nav.currentId = stack.firstOrNull() }

    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val dragX = remember { Animatable(0f) }
    val pipAnim = remember { Animatable(0f) }
    val entry = remember { mutableStateMapOf<String, Animatable<Float, AnimationVector1D>>() }
    val holder = rememberSaveableStateHolder()
    var menuAt by remember { mutableStateOf<DpOffset?>(null) }

    fun setOrder(list: List<String>) {
        order.clear()
        order.addAll(list)
    }

    val corner = Prefs.cardCorner.value
    val light = Prefs.lightMode.value

    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
        val boxW = maxWidth
        val boxH = maxHeight
        val wPx = with(density) { boxW.toPx() }
        val top = if (corner == Corner.TOP_START || corner == Corner.TOP_END) 64.dp else 24.dp
        val bottom = if (corner == Corner.BOTTOM_START || corner == Corner.BOTTOM_END) 76.dp else 40.dp
        val side = 20.dp
        val cardW = maxWidth - side * 2
        val cardH = maxHeight - top - bottom
        // Скругление как у игральной карты — доля ширины, чтобы пропорции не зависели от размера
        val radius = cardW * 0.055f
        val shape = RoundedCornerShape(radius)

        fun flyAway(dir: Float) {
            val id = stackNow.firstOrNull() ?: return
            scope.launch {
                dragX.animateTo(dir * wPx * 1.25f, tween(200, easing = FastOutLinearInEasing))
                setOrder(stackNow.drop(1) + id)
                val a = Animatable(dir * wPx * 1.25f)
                entry[id] = a
                dragX.snapTo(0f)
                a.animateTo(0f, tunedSpring())
                entry.remove(id)
            }
        }

        fun restore(dir: Float) {
            val s = stackNow
            if (s.size < 2) return
            val last = s.last()
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            setOrder(listOf(last) + s.dropLast(1))
            scope.launch {
                val a = Animatable(-dir * wPx * 1.2f)
                entry[last] = a
                a.animateTo(0f, tunedSpring())
                entry.remove(last)
            }
        }

        fun bringToFront(id: String) {
            setOrder(listOf(id) + stackNow.filter { it != id })
        }

        // Модуль возвращается из «картинки в картинке» — выезжает из-за края в начало или конец стопки
        LaunchedEffect(Playback.returning) {
            val r = Playback.returning ?: return@LaunchedEffect
            val rest = stackNow.filter { it != r }
            setOrder(if (Prefs.pipReturnToEnd.value) rest + r else listOf(r) + rest)
            Playback.returning = null
            val a = Animatable(wPx * 1.2f)
            entry[r] = a
            a.animateTo(0f, tunedSpring())
            entry.remove(r)
        }

        // Внутренняя «картинка в картинке»: карточка уменьшается и улетает вверх к месту окошка
        LaunchedEffect(Playback.pipRequest) {
            val id = Playback.pipRequest ?: return@LaunchedEffect
            if (stackNow.firstOrNull() == id && !Prefs.lightMode.value) {
                pipAnim.snapTo(0f)
                pipAnim.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            }
            Playback.pipRequest = null
            Playback.enterPip(id)
            pipAnim.snapTo(0f)
        }

        // Подсветка полей: дышит цветом играющего трека
        val glow = if (!light && Prefs.colorMode.value == ColorMode.WALLPAPER && Playback.playing && Playback.track != null) {
            rememberInfiniteTransition(label = "glow").animateFloat(
                0.18f, 0.42f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "g",
            ).value
        } else 0f
        val glowColor = Playback.track?.color ?: Coral

        Box(
            Modifier.fillMaxSize()
                .drawBehind {
                    if (glow > 0f) {
                        drawRect(
                            Brush.radialGradient(
                                listOf(glowColor.copy(alpha = glow), glowColor.copy(alpha = 0f)),
                                center = Offset(size.width / 2, size.height * 0.55f),
                                radius = size.maxDimension * 0.7f,
                            )
                        )
                    }
                }
                // Долгое нажатие по полю вокруг карточек — меню основы
                .pointerInput(cardW, cardH) {
                    detectTapGestures(onLongPress = { p ->
                        // Сама карточка не реагирует: долгое нажатие внутри модулей — их выбор нескольких
                        val onCard = p.x in side.toPx()..(side + cardW).toPx() && p.y in top.toPx()..(top + cardH).toPx()
                        if (!onCard) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            menuAt = DpOffset(p.x.toDp(), p.y.toDp())
                        }
                    })
                }
                // Жест-дуга: вверх и обратно вниз со сдвигом вбок — вернуть последнюю убранную карточку
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val sx = down.position.x
                        val sy = down.position.y
                        var minY = sy
                        var triggered = false
                        while (true) {
                            val ch = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            if (triggered) { ch.consume(); continue }
                            val p = ch.position
                            if (p.y < minY) minY = p.y
                            val rise = sy - minY
                            val descent = p.y - minY
                            val dx = p.x - sx
                            if (rise > 70.dp.toPx() && descent > 50.dp.toPx() && abs(dx) > 40.dp.toPx()) {
                                triggered = true
                                ch.consume()
                                restore(sign(dx))
                            }
                        }
                    }
                },
        ) {
            if (stack.isEmpty()) EmptyTabs()
            val visible = stack.take(VISIBLE)
            val progress = (abs(dragX.value) / wPx).coerceIn(0f, 1f)
            val activeId = when {
                Playback.video != null && Playback.videoPlaying && Playback.pipModule != "video" -> "video"
                Playback.playing -> "music"
                else -> null
            }
            for (i in visible.indices.reversed()) {
                val id = visible[i]
                val spec = Catalog.byId(id) ?: continue
                key(id) {
                    val front = i == 0
                    // Пока передняя уезжает, задние подтягиваются на её место
                    val depth = if (front) 0f else (i - progress).coerceAtLeast(0f)
                    val tiltSign = if (id.hashCode() % 2 == 0) 1f else -1f
                    val rotation = if (front) dragX.value / wPx * 10f else tiltSign * Prefs.cardTilt.value * min(depth, 1.6f)
                    val pipA = if (front) pipAnim.value else 0f
                    val pipScale = PIP_WIDTH / cardW
                    // Центр окошка «картинки в картинке» относительно центра карточки
                    val pipTx = with(density) { ((boxW - 12.dp - PIP_WIDTH / 2) - (side + cardW / 2)).toPx() }
                    val pipTy = with(density) { ((12.dp + PIP_WIDTH * 9f / 32f) - (top + cardH / 2)).toPx() }
                    val entryX = entry[id]?.value ?: 0f
                    val live = front || (id == activeId && !light)

                    Box(
                        Modifier.offset(side, top).size(cardW, cardH)
                            .then(
                                if (front) Modifier.pointerInput(id, boxW, boxH) {
                                    // Свайп засчитывается, только если начался в узкой полосе у края карточки
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                        val edge = 28.dp.toPx()
                                        val fromLeft = down.position.x < edge
                                        val fromRight = down.position.x > size.width - edge
                                        if (!fromLeft && !fromRight) return@awaitEachGesture
                                        val tracker = VelocityTracker()
                                        tracker.addPosition(down.uptimeMillis, down.position)
                                        var dx = 0f
                                        var dragging = false
                                        while (true) {
                                            val ch = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                                            if (!ch.pressed) break
                                            tracker.addPosition(ch.uptimeMillis, ch.position)
                                            dx += ch.position.x - ch.previousPosition.x
                                            if (!dragging && abs(dx) > viewConfiguration.touchSlop) dragging = true
                                            if (dragging) {
                                                ch.consume()
                                                val v = dx
                                                scope.launch { dragX.snapTo(v) }
                                            }
                                        }
                                        if (!dragging) return@awaitEachGesture
                                        val velocity = tracker.calculateVelocity().x
                                        val towardOtherEdge = (fromLeft && dx > 0) || (fromRight && dx < 0)
                                        val far = abs(dx) > size.width * Prefs.swipeFraction.value ||
                                            abs(velocity) > Prefs.flingVelocity.value.dp.toPx()
                                        if (towardOtherEdge && far && stackNow.size > 1) flyAway(sign(dx))
                                        else scope.launch { dragX.animateTo(0f, tunedSpring()) }
                                    }
                                } else Modifier.pointerInput(id) {
                                    // Нажатие на выглядывающую заднюю карточку выводит её вперёд
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                        down.consume()
                                        var up = false
                                        while (true) {
                                            val ch = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                                            ch.consume()
                                            if (!ch.pressed) { up = true; break }
                                        }
                                        if (up) bringToFront(id)
                                    }
                                }
                            ),
                    ) {
                        Box(
                            Modifier.fillMaxSize()
                                .graphicsLayer {
                                    transformOrigin = TransformOrigin.Center
                                    translationX = (if (front) dragX.value else 0f) + entryX + pipTx * pipA
                                    translationY = depth * 12.dp.toPx() + pipTy * pipA
                                    rotationZ = rotation * (1f - pipA)
                                    val s = (1f - depth * 0.045f) * lerp(1f, pipScale, pipA)
                                    scaleX = s
                                    scaleY = s
                                    this.shape = shape
                                    clip = true
                                    shadowElevation = if (light) 0f else (if (front) 14.dp else 6.dp).toPx()
                                }
                                .background(MaterialTheme.colorScheme.surfaceContainerLow),
                        ) {
                            if (live) {
                                holder.SaveableStateProvider(id) {
                                    ModuleHost(
                                        spec,
                                        ModuleEnv(live = true, front = front, inCard = true,
                                            stackProgress = if (front) progress else 1f),
                                        Modifier.fillMaxSize(),
                                    )
                                }
                            } else {
                                CardSnapshot(spec)
                            }
                        }
                    }
                }
            }

            CornerButtons(corner)

            menuAt?.let { at ->
                Box(Modifier.offset(at.x, at.y)) {
                    DropdownMenu(expanded = true, onDismissRequest = { menuAt = null }) {
                        DropdownMenuItem(text = { Text("Загрузка") }, leadingIcon = { Icon(Catalog.downloads.icon, null) },
                            onClick = { menuAt = null; Nav.select("downloads") })
                        DropdownMenuItem(text = { Text("Настройки") }, leadingIcon = { Icon(Catalog.settings.icon, null) },
                            onClick = { menuAt = null; Nav.select("settings") })
                        DropdownMenuItem(text = { Text("Стиль основы") }, leadingIcon = { Icon(Icons.Rounded.Style, null) },
                            onClick = { menuAt = null; Prefs.shell.value = ShellStyle.CLASSIC })
                    }
                }
            }
        }
    }
}

/** Загрузка, Настройки и модули шапки — в выбранном углу экрана. */
@Composable
private fun CornerButtons(corner: Corner) {
    val align = when (corner) {
        Corner.TOP_START -> Alignment.TopStart
        Corner.TOP_END -> Alignment.TopEnd
        Corner.BOTTOM_START -> Alignment.BottomStart
        Corner.BOTTOM_END -> Alignment.BottomEnd
    }
    Box(Modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.align(align), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (Catalog.headerModules() + Catalog.downloads + Catalog.settings).forEach { m ->
                FilledTonalIconButton(onClick = { Nav.select(m.id) }) { Icon(m.icon, m.title) }
            }
        }
    }
}

/** Снимок спящего модуля: шапка и заглушки строк вместо живого содержимого. */
@Composable
private fun CardSnapshot(spec: ModuleSpec) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(spec.color.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                Icon(spec.icon, null, tint = spec.color)
            }
            Text(spec.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp))
        }
        Spacer(Modifier.height(20.dp))
        repeat(7) { i ->
            Box(Modifier.fillMaxWidth(if (i % 3 == 2) 0.6f else 0.9f).height(14.dp).clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh))
            Spacer(Modifier.height(14.dp))
        }
    }
}
