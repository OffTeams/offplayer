package com.offplayer.app.ui.music

import com.offplayer.app.ui.theme.AppShapes
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.offplayer.app.data.AppSettings
import com.offplayer.app.data.CoverStyle
import com.offplayer.app.ui.MediaArt
import kotlinx.coroutines.isActive
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.TransformOrigin
import kotlin.math.absoluteValue

/** Данные текущего трека для анимации смены обложки. */
private data class CoverData(val key: Any?, val uri: Uri?, val title: String, val artist: String)

/**
 * Стили обложки: на экране всегда один стиль. Свайп влево/вправо заменяет его следующим —
 * текущий растворяется и уменьшается, новый проявляется на том же месте. Выбор запоминается.
 * Во время перелистывания и на паузе обложки статичны; анимации идут только при воспроизведении.
 */
@Composable
fun CoverCarousel(
    trackKey: Any?,
    artUri: Uri?,
    title: String,
    artist: String,
    isPlaying: Boolean,
    progress: Float,
    accent: Color,
    forward: Boolean,
    modifier: Modifier = Modifier,
) {
    val locked by AppSettings.coverLocked.collectAsStateWithLifecycle()
    val data0 = CoverData(trackKey, artUri, title, artist)
    if (locked) {
        // В настройках выбран конкретный стиль — показываем только его, без перелистывания
        val style by AppSettings.coverStyle.collectAsStateWithLifecycle()
        Box(modifier, contentAlignment = Alignment.Center) {
            Box(Modifier.padding(6.dp).aspectRatio(1f), contentAlignment = Alignment.Center) {
                TrackSwap(data0, forward) { StyledCover(style, it, isPlaying, isPlaying, progress, accent) }
            }
        }
        return
    }
    val styles = CoverStyle.entries
    val initial = remember { AppSettings.coverStyle.value.ordinal }
    val pagerState = rememberPagerState(initialPage = initial) { styles.size }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { AppSettings.setCoverStyle(styles[it]) }
    }
    val data = CoverData(trackKey, artUri, title, artist)
    // Анимации только при воспроизведении и не во время перелистывания
    val animate = isPlaying && !pagerState.isScrollInProgress

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val f = offset.absoluteValue.coerceIn(0f, 1f)
            Box(
                Modifier
                    .zIndex(1f - f)
                    .fillMaxSize()
                    .graphicsLayer {
                        // Страница не уезжает вбок, а остаётся на месте: старая обложка
                        // растворяется и уменьшается, новая проявляется вместо неё
                        translationX = offset * size.width
                        val s = 1f - 0.12f * f
                        scaleX = s
                        scaleY = s
                        alpha = (1f - f * 1.6f).coerceIn(0f, 1f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .padding(6.dp)
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    TrackSwap(data, forward) { StyledCover(styles[page], it, isPlaying, animate, progress, accent) }
                }
            }
        }
        // Индикатор стилей
        Row(
            Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            styles.indices.forEach { i ->
                val selected = pagerState.currentPage == i
                val w by animateDpAsState(if (selected) 18.dp else 6.dp, label = "dot")
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = w, height = 6.dp)
                        .clip(CircleShape)
                        .background(if (selected) accent else LocalContentColor.current.copy(alpha = 0.3f))
                )
            }
        }
        AnimatedContent(
            targetState = styles[pagerState.currentPage].label,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
            label = "styleName",
        ) {
            Text(it, style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.55f), modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun StyledCover(style: CoverStyle, data: CoverData, isPlaying: Boolean, animate: Boolean, progress: Float, accent: Color) {
    when (style) {
        CoverStyle.VINYL -> VinylSleeve(data.uri, isPlaying, animate, accent)
        CoverStyle.FRAME -> FramedCover(data.uri, isPlaying, animate, accent)
        CoverStyle.CASSETTE -> CassetteWithCard(data, isPlaying, animate, progress, accent)
        CoverStyle.POLAROID -> Polaroid(data, animate)
    }
}

/**
 * Мягкое свечение/тень, нарисованное вручную. В отличие от системной `shadow()`,
 * корректно ведёт себя при анимациях карусели (без тёмных прямоугольников).
 * cornerRadius = null — круглая форма.
 */
internal fun Modifier.softGlow(color: Color, spread: Dp, cornerRadius: Dp?, strength: Float = 1f): Modifier = drawBehind {
    val s = spread.toPx()
    val steps = 10
    for (i in steps downTo 1) {
        val f = i / steps.toFloat()
        val grow = s * f
        val alpha = (0.075f * strength * (1.15f - f)).coerceIn(0f, 1f)
        if (cornerRadius == null) {
            drawCircle(color.copy(alpha = alpha), radius = size.minDimension / 2f + grow)
        } else {
            drawRoundRect(
                color.copy(alpha = alpha),
                topLeft = Offset(-grow, -grow * 0.7f),
                size = Size(size.width + 2 * grow, size.height + 2 * grow * 0.85f),
                cornerRadius = CornerRadius(cornerRadius.toPx() + grow),
            )
        }
    }
}

/** Смена трека: старая обложка уезжает, новая приезжает. */
@Composable
private fun TrackSwap(data: CoverData, forward: Boolean, content: @Composable (CoverData) -> Unit) {
    AnimatedContent(
        targetState = data,
        transitionSpec = {
            val dir = if (forward) 1 else -1
            (slideInHorizontally(tween(550, easing = FastOutSlowInEasing)) { it * dir } +
                scaleIn(tween(550), initialScale = 0.8f) + fadeIn(tween(350))) togetherWith
                (slideOutHorizontally(tween(450, easing = FastOutSlowInEasing)) { -it * dir } +
                    scaleOut(tween(450), targetScale = 0.8f) + fadeOut(tween(300))) using
                SizeTransform(clip = false) // не обрезать свечение и «дышащую» обложку
        },
        contentKey = { it.key },
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
        label = "cover",
    ) { content(it) }
}

/** Угол вращения с плавным разгоном/торможением. */
@Composable
internal fun rememberSpinAngle(isPlaying: Boolean, degreesPerSecond: Float): FloatState {
    val angle = remember { mutableFloatStateOf(0f) }
    val speed = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        var last = withFrameNanos { it }
        val target = if (isPlaying) 1f else 0f
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            speed.floatValue += (target - speed.floatValue) * (dt * 2.5f).coerceAtMost(1f)
            if (!isPlaying && speed.floatValue < 0.005f) {
                speed.floatValue = 0f
                break
            }
            angle.floatValue = (angle.floatValue + degreesPerSecond * speed.floatValue * dt) % 360f
        }
    }
    return angle
}

// ---------- Обложка в рамке ----------

@Composable
private fun FramedCover(uri: Uri?, isPlaying: Boolean, animate: Boolean, accent: Color) {
    val breathe by rememberInfiniteTransition(label = "breathe").animateFloat(
        initialValue = 0.985f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val playScale by animateFloatAsState(if (isPlaying) 1f else 0.94f, spring(dampingRatio = 0.6f), label = "scale")
    val breatheMotion by animateFloatAsState(if (animate) 1f else 0f, tween(900), label = "breatheMotion")
    // Переливающаяся обводка: по рамке непрерывно бежит световой градиент
    val spin by rememberSpinAngle(true, 70f)
    // Размер считаем явно от меньшей стороны области — обложка гарантированно помещается
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    val side = minOf(maxWidth, maxHeight) * 0.76f
    Box(
        Modifier
            .size(side)
            .graphicsLayer {
                // «Дыхание» только во время воспроизведения; масштаб ≤ 1 — ничего не обрезается
                val s = playScale * (1f + (breathe - 1f) * breatheMotion)
                scaleX = s
                scaleY = s
            }
            .softGlow(accent, 12.dp, 24.dp, strength = 1.4f)
            .clip(AppShapes.Large)
            .drawBehind {
                rotate(spin) {
                    drawCircle(
                        Brush.sweepGradient(
                            listOf(accent, Color.White.copy(alpha = 0.95f), accent.copy(alpha = 0.55f), Color(0xFFB388FF), accent),
                            center = center,
                        ),
                        radius = size.maxDimension,
                    )
                }
            }
            .padding(5.dp)
    ) {
        MediaArt(uri, Modifier.fillMaxSize(), shape = RoundedCornerShape(19.dp)) // концентрично рамке 24dp
    }
    }
}

// ---------- Кассета с коробкой ----------

/**
 * Коробка от кассеты с обложкой-вкладышем (видна целиком) и кассета, наклонённая влево
 * и прислонённая к коробке справа — она не закрывает обложку. На этикетке кассеты —
 * название и исполнитель, катушки вращаются, плёнка перематывается по ходу трека.
 */
@Composable
private fun CassetteWithCard(data: CoverData, isPlaying: Boolean, animate: Boolean, progress: Float, accent: Color) {
    val angle by rememberSpinAngle(animate, 160f)
    val lean by animateFloatAsState(
        if (isPlaying) 1f else 0f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessVeryLow),
        label = "lean",
    )
    val motion by animateFloatAsState(if (animate) 1f else 0f, tween(900), label = "motion")
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val s = minOf(maxWidth, maxHeight)
        val ox = (maxWidth - s) / 2
        val oy = (maxHeight - s) / 2

        // Коробка: прозрачный пластик, слева корешок с рёбрами, внутри вкладыш с обложкой и подписью
        // Коробка чуть повёрнута против часовой стрелки и смещена к центру, кассета лежит
        // у её нижнего правого угла поверх подписи вкладыша — обложку не закрывает.
        // Оба предмета слегка покачиваются (в противофазе) во время воспроизведения.
        // Покачивание плавно нарастает при старте и затихает при паузе
        val sway = bob * motion
        val caseW = s * 0.58f
        val caseH = s * 0.76f
        Box(
            Modifier
                .offset(ox + s * 0.13f, oy + s * 0.07f)
                .size(caseW, caseH)
                .graphicsLayer {
                    rotationZ = -5f + 1.2f * sway
                    translationY = sway * 2.dp.toPx()
                }
                .softGlow(Color.Black, 12.dp, 10.dp, strength = 1.6f)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2A2A30), Color(0xFF151518))))
        ) {
            Row(Modifier.fillMaxSize()) {
                // Корешок
                Canvas(
                    Modifier
                        .fillMaxHeight()
                        .width(caseW * 0.09f)
                ) {
                    drawRect(Brush.horizontalGradient(listOf(Color(0xFF3A3A42), Color(0xFF1E1E22))))
                    val step = size.height / 14
                    for (i in 1 until 14) {
                        drawLine(Color.White.copy(alpha = 0.10f), Offset(size.width * 0.25f, step * i), Offset(size.width * 0.75f, step * i), 1.5f)
                    }
                }
                // Вкладыш
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFF3EEE4)),
                ) {
                    MediaArt(data.uri, Modifier.fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    Box(Modifier.fillMaxWidth().height(3.dp).background(accent))
                    // Название и исполнитель уже есть в плеере — на вкладыше только линовка
                    Canvas(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp, vertical = 6.dp)) {
                        val rows = 3
                        for (i in 1..rows) {
                            val y = size.height * i / (rows + 1)
                            drawLine(Color(0xFFD8D0C2), Offset(0f, y), Offset(size.width * (1f - 0.25f * (i - 1)), y), 1.5f)
                        }
                    }
                }
            }
            // Блики на пластике крышки
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.Transparent, Color.White.copy(alpha = 0.06f)),
                        start = Offset.Zero, end = Offset(size.width, size.height),
                    )
                )
                drawRoundRect(Color.White.copy(alpha = 0.18f), cornerRadius = CornerRadius(8.dp.toPx()), style = Stroke(width = 2f))
            }
        }

        // Кассета наклонена влево и опирается на правый край коробки, ниже обложки
        val cw = s * 0.46f
        val ch = cw / 1.58f
        Box(
            Modifier
                .offset(x = ox + s * 0.44f, y = oy + s * 0.655f)
                .size(cw, ch)
                .graphicsLayer {
                    rotationZ = -13f - 2f * lean - 2f * sway
                    translationY = -sway * 2.dp.toPx()
                }
                .softGlow(Color.Black, 10.dp, 12.dp, strength = 1.7f)
        ) {
            Canvas(Modifier.fillMaxSize()) { drawCassetteBody(accent) }
            Row(
                Modifier
                    .offset(x = cw * 0.08f, y = ch * 0.1f)
                    .width(cw * 0.84f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(ch * 0.14f)
                        .background(accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("A", color = Color.White, fontWeight = FontWeight.Black, fontSize = 9.sp)
                }
            }
            Canvas(Modifier.fillMaxSize()) { drawCassetteMechanics(angle, progress, accent) }
        }
    }
}
private fun DrawScope.drawCassetteBody(accent: Color) {
    val w = size.width
    val h = size.height
    val corner = CornerRadius(w * 0.045f)
    drawRoundRect(brush = Brush.verticalGradient(listOf(Color(0xFF3A3A40), Color(0xFF17171B))), cornerRadius = corner)
    // Фаска и блик по верхнему краю
    drawRoundRect(Color.White.copy(alpha = 0.12f), cornerRadius = corner, style = Stroke(width = 2f))
    drawLine(Color.White.copy(alpha = 0.18f), Offset(w * 0.06f, 2.5f), Offset(w * 0.94f, 2.5f), strokeWidth = 1.5f)
    // Этикетка
    val label = Rect(w * 0.05f, h * 0.07f, w * 0.95f, h * 0.67f)
    drawRoundRect(Color(0xFFF5F0E6), label.topLeft, label.size, CornerRadius(w * 0.025f))
    // Цветная полоса этикетки под окном
    drawRect(accent.copy(alpha = 0.9f), topLeft = Offset(label.left, h * 0.355f), size = Size(label.width, h * 0.27f))
    drawLine(Color.White.copy(alpha = 0.5f), Offset(label.left, h * 0.355f), Offset(label.right, h * 0.355f), strokeWidth = 2f)
    // Линейки для подписи
    listOf(0.30f).forEach { y ->
        drawLine(Color(0xFFD8D0C2), Offset(w * 0.08f, h * y), Offset(w * 0.92f, h * y), strokeWidth = 1.2f)
    }
}

private fun DrawScope.drawCassetteMechanics(angle: Float, progress: Float, accent: Color) {
    val w = size.width
    val h = size.height
    // Окно с плёнкой
    val window = Rect(w * 0.25f, h * 0.385f, w * 0.75f, h * 0.60f)
    val wr = CornerRadius(window.height / 2)
    drawRoundRect(Color(0xF2121214), window.topLeft, window.size, wr)
    val leftC = Offset(w * 0.355f, window.center.y)
    val rightC = Offset(w * 0.645f, window.center.y)
    val maxTape = h * 0.165f
    val minTape = h * 0.075f
    val p = progress.coerceIn(0f, 1f)
    clipRect(window.left, window.top, window.right, window.bottom) {
        drawCircle(Color(0xFF4A3426), radius = minTape + (maxTape - minTape) * (1f - p), center = leftC)
        drawCircle(Color(0xFF4A3426), radius = minTape + (maxTape - minTape) * p, center = rightC)
        // Блик на стекле окна
        drawRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), startY = window.top, endY = window.center.y),
            topLeft = window.topLeft, size = Size(window.width, window.height / 2),
        )
    }
    drawRoundRect(Color.White.copy(alpha = 0.28f), window.topLeft, window.size, wr, style = Stroke(width = 1.5f))
    // Катушки
    listOf(leftC, rightC).forEach { c ->
        val hub = h * 0.06f
        drawCircle(Color(0xFFF0F0F0), radius = hub, center = c)
        rotate(angle, c) {
            for (i in 0 until 6) {
                rotate(i * 60f, c) {
                    drawRoundRect(
                        Color(0xFF2B2B2B),
                        topLeft = Offset(c.x - hub * 0.13f, c.y - hub * 0.95f),
                        size = Size(hub * 0.26f, hub * 0.40f),
                        cornerRadius = CornerRadius(hub * 0.06f),
                    )
                }
            }
        }
        drawCircle(Color(0xFF1A1A1A), radius = hub * 0.36f, center = c)
        drawCircle(accent.copy(alpha = 0.8f), radius = hub * 0.12f, center = c)
    }
    // Нижняя трапеция с отверстиями
    val trap = Path().apply {
        moveTo(w * 0.21f, h)
        lineTo(w * 0.27f, h * 0.76f)
        lineTo(w * 0.73f, h * 0.76f)
        lineTo(w * 0.79f, h)
        close()
    }
    drawPath(trap, Color(0xFF26262B))
    drawPath(trap, Color.White.copy(alpha = 0.10f), style = Stroke(width = 1.5f))
    listOf(0.36f, 0.64f).forEach { x -> drawCircle(Color(0xFF0A0A0A), radius = h * 0.032f, center = Offset(w * x, h * 0.885f)) }
    listOf(0.46f, 0.54f).forEach { x ->
        drawRoundRect(
            Color(0xFF0A0A0A),
            topLeft = Offset(w * x - h * 0.018f, h * 0.85f), size = Size(h * 0.036f, h * 0.06f),
            cornerRadius = CornerRadius(h * 0.01f),
        )
    }
    // Винты
    listOf(Offset(0.035f, 0.045f), Offset(0.965f, 0.045f), Offset(0.035f, 0.955f), Offset(0.965f, 0.955f)).forEach { o ->
        val c = Offset(w * o.x, h * o.y)
        drawCircle(Color(0xFF9A9A9A), radius = h * 0.02f, center = c)
        drawLine(Color(0xFF444444), Offset(c.x - h * 0.012f, c.y), Offset(c.x + h * 0.012f, c.y), strokeWidth = 2f)
    }
}

// ---------- Полароид ----------

/**
 * Полароид висит на кнопке и покачивается, как от сквозняка, слегка «дышит»;
 * по снимку время от времени пробегает блик. При смене трека фото «проявляется»
 * из белой засветки, как настоящий снимок.
 */
@Composable
private fun Polaroid(data: CoverData, animate: Boolean) {
    val t = rememberInfiniteTransition(label = "polaroid")
    val swing by t.animateFloat(
        initialValue = -5f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "swing",
    )
    val float by t.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float",
    )
    val sheen by t.animateFloat(
        initialValue = -0.6f, targetValue = 1.8f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "sheen",
    )
    // Плавный переход между покачиванием и покоем при паузе
    val motion by animateFloatAsState(if (animate) 1f else 0f, tween(900), label = "motion")
    // «Проявление» снимка при появлении нового трека
    val develop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { develop.animateTo(1f, tween(1800, easing = FastOutSlowInEasing)) }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val cardH = minOf(maxWidth, maxHeight) * 0.78f
        Box(contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .padding(top = 10.dp)
                    .size(width = cardH * 0.84f, height = cardH)
                    .graphicsLayer {
                        // Подвешен за верхний край: качается вокруг кнопки
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        rotationZ = -2f + swing * motion
                        translationY = float * 3.dp.toPx() * motion
                        val sc = 1f + 0.012f * float * motion
                        scaleX = sc
                        scaleY = sc
                    }
                    .softGlow(Color.Black, 10.dp, 10.dp, strength = 1.4f)
                    .background(Color(0xFFF7F5F0), AppShapes.Thumb)
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    MediaArt(data.uri, Modifier.fillMaxSize(), shape = RoundedCornerShape(4.dp))
                    Canvas(Modifier.fillMaxSize()) {
                        // Засветка, которая уходит при проявлении
                        drawRect(Color(0xFFFFFBF0).copy(alpha = (1f - develop.value) * 0.9f))
                        // Блик пробегает по глянцу
                        if (motion > 0f) {
                            val x = size.width * sheen
                            drawRect(
                                Brush.linearGradient(
                                    listOf(Color.Transparent, Color.White.copy(alpha = 0.22f * motion), Color.Transparent),
                                    start = Offset(x - size.width * 0.3f, 0f), end = Offset(x + size.width * 0.3f, size.height),
                                )
                            )
                        }
                    }
                }
                // Подпись под снимком не нужна — название и исполнитель уже показаны в плеере
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

