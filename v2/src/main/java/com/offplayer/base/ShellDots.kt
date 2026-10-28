package com.offplayer.base

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private data class RailItem(val id: String, val title: String, val icon: ImageVector, val service: Boolean)

private val DOT = 7.dp
private val STEP = 18.dp
private val SERVICE_GAP = 12.dp
private val ICON = 38.dp
private val LABEL_H = 18.dp
private val PAD = 10.dp
/** Толщина полосы касания ленты. */
private val TOUCH = 40.dp
/** Отступ центра точек от края экрана. */
private val EDGE = 16.dp

/**
 * Настраиваемая лента: точки у края экрана. Быстрый свайп вдоль ленты — соседняя вкладка
 * (точки сходятся, касаются с вибрацией и возвращаются), медленное ведение — точка под пальцем
 * раскрывается в иконку с подписью. Долгое нажатие — ленту можно передвинуть вдоль края.
 */
@Composable
fun DotRailShell() {
    val tabs = Catalog.tabs()
    val current = Nav.current(tabs)
    val side = Prefs.railSide.value
    // Меню считается снизу вверх: модули, затем служебные точки — последними (в самом верху)
    val items = tabs.map { RailItem(it.id, it.title, it.icon, false) } +
        Catalog.headerModules().map { RailItem(it.id, it.title, it.icon, true) } +
        listOf(Catalog.downloads, Catalog.settings).map { RailItem(it.id, it.title, it.icon, true) }

    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
        // В портретной сбоку точки маленькие и лежат поверх края контента — модуль не теряет ширину.
        // В альбомной ширины с запасом — лента получает свою полосу и не закрывает содержимое.
        // Внизу лента оставляет узкую полосу, иначе закроет мини-плеер и нижние кнопки.
        val landscape = maxWidth > maxHeight
        val pad = when (side) {
            RailSide.LEFT -> PaddingValues(start = if (landscape) 32.dp else 0.dp)
            RailSide.RIGHT -> PaddingValues(end = if (landscape) 32.dp else 0.dp)
            RailSide.BOTTOM -> PaddingValues(bottom = 26.dp)
        }
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (current == null) EmptyTabs() else AnimatedContent(
                targetState = current,
                transitionSpec = { fadeIn(tunedSpring()) togetherWith fadeOut(tunedSpring()) },
                contentKey = { it.id },
                label = "dotTab",
            ) { spec -> ModuleHost(spec, ModuleEnv(), Modifier.fillMaxSize()) }
        }
        DotRail(items, current?.id, side) { Nav.select(it) }
    }
}

@Composable
private fun DotRail(items: List<RailItem>, selectedId: String?, side: RailSide, onSelect: (String) -> Unit) {
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val vertical = side != RailSide.BOTTOM
    val selected by rememberUpdatedState(selectedId)

    val firstService = items.indexOfFirst { it.service }
    // Базовые позиции центров вдоль ленты (от её начала: снизу для боковых, слева для нижней)
    val base: List<Dp> = items.indices.map { i ->
        PAD + DOT / 2 + STEP * i + if (firstService in 0..i) SERVICE_GAP else 0.dp
    }
    val baseLength = (base.lastOrNull() ?: 0.dp) + DOT / 2 + PAD
    val mid = if (base.isEmpty()) 0.dp else (base.first() + base.last()) / 2

    var hovered by remember { mutableStateOf<Int?>(null) }
    val expand = remember { Animatable(0f) }
    val squeeze = remember { Animatable(0f) }
    var squeezeDir by remember { mutableFloatStateOf(0f) }
    var moving by remember { mutableStateOf(false) }
    var offsetLive by remember { mutableFloatStateOf(Prefs.railOffset.value) }
    var containerRoot by remember { mutableStateOf(Offset.Zero) }
    var stripRoot by remember { mutableStateOf(Offset.Zero) }

    val labels = Prefs.railLabels.value
    val e = expand.value
    // Раскрытая точка раздвигает соседей; лента растёт симметрично от своего центра
    val grow = (ICON + (if (labels) LABEL_H else 0.dp) - DOT) * e
    val k = 1f - (1f - (DOT.value + 0.5f) / STEP.value) * squeeze.value
    val positions = base.mapIndexed { i, p ->
        var pos = mid + (p - mid) * k + 6.dp * squeezeDir * squeeze.value
        val h = hovered
        if (h != null && i != h) pos += if (i < h) -grow / 2 else grow / 2
        pos + grow / 2
    }
    val length = baseLength + grow

    fun haptic(c: Int) { view.performHapticFeedback(c) }

    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { containerRoot = it.positionInRoot() }) {
        val along = if (vertical) maxHeight else maxWidth
        val free = (along - baseLength).coerceAtLeast(0.dp)
        val center = free * offsetLive.coerceIn(0f, 1f) + baseLength / 2
        val start = center - length / 2
        val stripOffset = when (side) {
            RailSide.LEFT -> DpOffset(0.dp, start)
            RailSide.RIGHT -> DpOffset(maxWidth - TOUCH, start)
            RailSide.BOTTOM -> DpOffset(start, maxHeight - TOUCH)
        }
        val crossCenter = when (side) {
            RailSide.LEFT -> EDGE
            RailSide.RIGHT, RailSide.BOTTOM -> TOUCH - EDGE
        }

        fun toStrip(axis: Dp): DpOffset =
            if (vertical) DpOffset(crossCenter, length - axis) else DpOffset(axis, crossCenter)

        /** Позиция пальца → координата вдоль базовой (нераскрытой) ленты; не зависит от её роста. */
        fun baseAxisOf(local: Offset): Dp = with(density) {
            val abs = local + stripRoot
            val c = free * offsetLive.coerceIn(0f, 1f) + baseLength / 2
            if (vertical) (containerRoot.y.toDp() + c + baseLength / 2) - abs.y.toDp()
            else abs.x.toDp() - (containerRoot.x.toDp() + c - baseLength / 2)
        }

        fun indexAt(axis: Dp): Int = base.indices.minByOrNull { abs((base[it] - axis).value) } ?: 0

        fun collide(dir: Float) {
            scope.launch {
                squeezeDir = dir
                squeeze.animateTo(1f, tween(Prefs.collisionMs.value.toInt(), easing = FastOutLinearInEasing))
                haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                squeeze.animateTo(0f, spring(Prefs.damping.value, Prefs.stiffness.value))
            }
        }

        Box(
            Modifier
                .offset(stripOffset.x, stripOffset.y)
                .size(if (vertical) TOUCH else length, if (vertical) length else TOUCH)
                .onGloballyPositioned { stripRoot = it.positionInRoot() }
                .pointerInput(items.map { it.id }, side, maxWidth, maxHeight) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val t0 = down.uptimeMillis
                        val startAxis = baseAxisOf(down.position)
                        var moved = false
                        var released = false
                        // Фаза 1: касание, сдвиг или долгое нажатие (ничего не делать — тоже решение)
                        val decided = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            var result = true
                            while (true) {
                                val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) { released = true; break }
                                val d = with(density) { (baseAxisOf(ch.position) - startAxis).toPx() }
                                if (abs(d) > viewConfiguration.touchSlop) {
                                    moved = true
                                    ch.consume()
                                    break
                                }
                            }
                            result
                        }
                        if (released) {
                            onSelect(items[indexAt(startAxis)].id)
                            return@awaitEachGesture
                        }
                        val moveMode = decided == null
                        if (moveMode) {
                            moving = true
                            haptic(HapticFeedbackConstants.LONG_PRESS)
                        }
                        var scrub = false
                        var lastAxis = startAxis
                        var lastAbs = down.position + stripRoot
                        while (true) {
                            val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            if (moveMode) {
                                // Перетаскивание ленты вдоль края экрана
                                val abs = ch.position + stripRoot
                                val d = with(density) { (if (vertical) abs.y - lastAbs.y else abs.x - lastAbs.x).toDp() }
                                lastAbs = abs
                                if (free > 0.dp) offsetLive = (offsetLive + d / free).coerceIn(0f, 1f)
                                continue
                            }
                            moved = true
                            lastAxis = baseAxisOf(ch.position)
                            if (!scrub && ch.uptimeMillis - t0 > Prefs.dotExpandDelay.value) scrub = true
                            if (scrub) {
                                val i = indexAt(lastAxis)
                                if (i != hovered) {
                                    hovered = i
                                    haptic(HapticFeedbackConstants.CLOCK_TICK)
                                    scope.launch {
                                        expand.snapTo(0.3f)
                                        expand.animateTo(1f, tween(110))
                                    }
                                }
                            }
                        }
                        when {
                            moveMode -> {
                                moving = false
                                Prefs.railOffset.value = offsetLive
                            }
                            scrub -> {
                                hovered?.let { onSelect(items[it].id) }
                                scope.launch {
                                    expand.animateTo(0f, tween(130))
                                    hovered = null
                                }
                            }
                            moved -> {
                                // Быстрый свайп: соседний модуль по направлению, точки «сталкиваются»
                                val dir = sign((lastAxis - startAxis).value).takeIf { it != 0f } ?: 1f
                                val modules = items.filter { !it.service }
                                val cur = modules.indexOfFirst { it.id == selected }
                                val next = (cur + dir.toInt()).coerceIn(0, (modules.size - 1).coerceAtLeast(0))
                                if (modules.isNotEmpty() && next != cur) onSelect(modules[next].id)
                                collide(dir)
                            }
                        }
                    }
                },
        ) {
            if (items.isNotEmpty()) {
                // Подложка ленты
                val bgStart = positions.first() - DOT / 2 - PAD / 2
                val bgEnd = positions.last() + DOT / 2 + PAD / 2
                val bgLen = bgEnd - bgStart
                val topLeft = if (vertical) DpOffset(crossCenter - 11.dp, length - bgEnd) else DpOffset(bgStart, crossCenter - 11.dp)
                Box(
                    Modifier.offset(topLeft.x, topLeft.y)
                        .size(if (vertical) 22.dp else bgLen, if (vertical) bgLen else 22.dp)
                        .glass(CircleShape, strength = 1.6f)
                        .then(if (moving) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                )
            }
            items.forEachIndexed { i, item ->
                val p = toStrip(positions[i])
                val isSelected = item.id == selectedId
                val f = if (hovered == i) e else 0f
                // Для TalkBack у каждой точки есть имя и действие, даже если подписи скрыты
                Box(
                    Modifier.offset(p.x - 14.dp, p.y - STEP / 2).size(28.dp, STEP)
                        .semantics {
                            contentDescription = item.title
                            role = Role.Tab
                            this.selected = isSelected
                            onClick(label = item.title) { onSelect(item.id); true }
                        },
                )
                val size = lerp(if (isSelected) 9.dp else DOT, ICON, f)
                val inward = (ICON / 2 + 8.dp) * f
                val shifted = when (side) {
                    RailSide.LEFT -> DpOffset(p.x + inward, p.y)
                    RailSide.RIGHT -> DpOffset(p.x - inward, p.y)
                    RailSide.BOTTOM -> DpOffset(p.x, p.y - inward)
                }
                val color = when {
                    f > 0f -> MaterialTheme.colorScheme.secondaryContainer
                    isSelected -> MaterialTheme.colorScheme.primary
                    item.service -> Color.Transparent
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                }
                Box(
                    Modifier.offset(shifted.x - size / 2, shifted.y - size / 2)
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .size(size)
                        .background(color, CircleShape)
                        .then(
                            if (item.service && f == 0f && !isSelected)
                                Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (f > 0.05f) {
                        Icon(item.icon, null, Modifier.size(22.dp).graphicsLayer { alpha = f },
                            tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                if (f > 0.05f && labels) {
                    // Подпись шире полосы ленты — снимаем ограничение размера родителя
                    Box(
                        Modifier.offset(shifted.x - 60.dp, shifted.y + ICON / 2 + 2.dp)
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .size(120.dp, LABEL_H + 4.dp),
                    ) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.Center).wrapContentSize()
                                .graphicsLayer { alpha = f }
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
