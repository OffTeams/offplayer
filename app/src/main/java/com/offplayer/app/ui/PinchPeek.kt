package com.offplayer.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch

/** Состояние «подсмотреть»: увеличение или отдаление двумя пальцами, пока они на экране. */
@Stable
class PeekState {
    val scale = Animatable(1f)
    val offset = Animatable(Offset.Zero, Offset.VectorConverter)
    /** Жест идёт или картинка ещё возвращается. */
    var active by mutableStateOf(false)
}

@Composable
fun rememberPeekState() = remember { PeekState() }

/**
 * Жест щипка, как в Telegram: увеличение (до ×4) или отдаление (до ×0,6) вокруг точки между пальцами,
 * картинка двигается за ними, а когда пальцы отпускают — плавно возвращается на место.
 * Касание одним пальцем не перехватывается: прокрутка и нажатия работают.
 */
fun Modifier.peekGestures(state: PeekState, minScale: Float = 0.6f, maxScale: Float = 4f): Modifier = composed {
    val scope = rememberCoroutineScope()
    pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var started = false
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed == 0) break
                if (pressed >= 2) {
                    if (!started) {
                        started = true
                        state.active = true
                    }
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    val centroid = event.calculateCentroid(useCurrent = true) - Offset(size.width / 2f, size.height / 2f)
                    val old = state.scale.value
                    val target = (old * zoom).coerceIn(minScale, maxScale)
                    val k = target / old
                    // Точка между пальцами остаётся под ними; движение пальцев двигает картинку
                    val newOffset = (state.offset.value - centroid) * k + centroid + pan
                    scope.launch {
                        state.scale.snapTo(target)
                        state.offset.snapTo(newOffset)
                    }
                    event.changes.forEach { it.consume() }
                } else if (started) {
                    event.changes.forEach { it.consume() }
                }
            }
            if (started) scope.launch {
                launch { state.offset.animateTo(Offset.Zero, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)) }
                state.scale.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow))
                state.active = false
            }
        }
    }
}

/** Щипок прямо по элементу (видео на весь экран, где перекрывать нечего): жест и увеличение на нём самом. */
fun Modifier.pinchPeek(state: PeekState, minScale: Float = 0.6f, maxScale: Float = 4f): Modifier =
    this.peekGestures(state, minScale, maxScale).graphicsLayer {
        scaleX = state.scale.value
        scaleY = state.scale.value
        translationX = state.offset.value.x
        translationY = state.offset.value.y
    }

/**
 * Щипок по картинке в списке: на время жеста она рисуется во всплывающем слое поверх всего экрана —
 * подписи и соседние карточки её не перекрывают; после отпускания возвращается на место.
 */
@Composable
fun PeekBox(modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val state = rememberPeekState()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    Box(
        modifier
            .onGloballyPositioned { size = it.size; origin = it.positionInWindow() }
            .then(if (enabled) Modifier.peekGestures(state) else Modifier),
    ) {
        // Пока картинка во всплывающем слое, на её месте пусто
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (state.active) 0f else 1f }) { content() }
        if (state.active && size.width > 0) {
            // Слой на весь экран (а не размером с картинку): увеличенная картинка не обрезается его краями
            Popup(popupPositionProvider = WindowOrigin, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
                Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .offset { androidx.compose.ui.unit.IntOffset(origin.x.toInt(), origin.y.toInt()) }
                        .size(with(density) { size.width.toDp() }, with(density) { size.height.toDp() })
                        .graphicsLayer {
                            scaleX = state.scale.value
                            scaleY = state.scale.value
                            translationX = state.offset.value.x
                            translationY = state.offset.value.y
                        },
                ) { content() }
                }
            }
        }
    }
}

/** Всплывающий слой — от левого верхнего угла окна: координаты картинки внутри него те же, что в окне. */
private object WindowOrigin : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: IntSize,
    ): androidx.compose.ui.unit.IntOffset = androidx.compose.ui.unit.IntOffset.Zero
}
