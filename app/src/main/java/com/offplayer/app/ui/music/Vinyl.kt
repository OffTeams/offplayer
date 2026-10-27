package com.offplayer.app.ui.music

import com.offplayer.app.ui.theme.AppShapes
import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.offplayer.app.ui.MediaArt

private const val LABEL_FRACTION = 0.36f
/** 33⅓ оборота в минуту, в градусах в секунду. */
private const val DEGREES_PER_SECOND = 360f * (100f / 3f) / 60f

/**
 * Пластинка в конверте: обложка трека целиком на квадратном конверте,
 * при воспроизведении пластинка выезжает из конверта и вращается,
 * на паузе плавно задвигается обратно. В центре пластинки — та же обложка.
 */
@Composable
fun VinylSleeve(artUri: Uri?, isPlaying: Boolean, animate: Boolean, accent: Color) {
    // Вращение только во время воспроизведения (не на паузе и не при перелистывании)
    val angle by rememberSpinAngle(animate, DEGREES_PER_SECOND)
    val out by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessVeryLow),
        label = "discOut",
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val sleeve = w * 0.70f
        // На паузе конверт по центру, при игре сдвигается влево, освобождая место пластинке
        val sleeveX = w * 0.03f + w * 0.12f * (1f - out)
        val sleeveY = (maxHeight - sleeve) / 2
        val disc = sleeve * 0.94f
        val discX = sleeveX + sleeve / 2 + sleeve * (0.06f + 0.30f * out) - disc / 2
        val discY = sleeveY + (sleeve - disc) / 2

        // Пластинка (за конвертом)
        Box(
            Modifier
                .offset(discX, discY)
                .size(disc)
                .softGlow(accent, 10.dp, null, strength = 0.3f + 1.0f * out)
                .softGlow(Color.Black, 6.dp, null, strength = 1.6f)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = angle }
            ) {
                Canvas(Modifier.fillMaxSize()) { drawDisc(accent) }
                MediaArt(
                    artUri,
                    Modifier
                        .fillMaxSize(LABEL_FRACTION)
                        .align(Alignment.Center),
                    shape = CircleShape,
                )
                Canvas(Modifier.fillMaxSize()) { drawSpindle() }
            }
            // Блик неподвижен — так видно вращение
            Canvas(Modifier.fillMaxSize()) { drawSheen() }
        }

        // Конверт с обложкой
        Box(
            Modifier
                .offset(sleeveX, sleeveY)
                .size(sleeve)
                .softGlow(Color.Black, 10.dp, 10.dp, strength = 1.6f)
                .clip(AppShapes.Thumb)
        ) {
            MediaArt(artUri, Modifier.fillMaxSize(), shape = AppShapes.Thumb)
            Canvas(Modifier.fillMaxSize()) { drawSleeveDetails() }
        }
    }
}

/** Глянец, потёртость от пластинки и тень у прорези конверта. */
private fun DrawScope.drawSleeveDetails() {
    val w = size.width
    drawCircle(
        Color.White.copy(alpha = 0.07f),
        radius = w * 0.44f,
        style = Stroke(width = w * 0.014f),
    )
    drawRect(
        Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.32f)), startX = w * 0.86f, endX = w),
    )
    drawRect(
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
            start = Offset.Zero, end = Offset(w * 0.55f, w * 0.55f),
        ),
    )
    drawRect(Color.White.copy(alpha = 0.16f), style = Stroke(width = 1.5f))
}

private fun DrawScope.drawDisc(accent: Color) {
    val r = size.minDimension / 2f
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color(0xFF2C2C2C),
            0.45f to Color(0xFF151515),
            1f to Color(0xFF060606),
            center = center, radius = r,
        ),
        radius = r,
    )
    // Канавки
    var gr = r * 0.97f
    var i = 0
    while (gr > r * (LABEL_FRACTION / 2f + 0.04f)) {
        drawCircle(
            color = Color.White.copy(alpha = if (i % 3 == 0) 0.075f else 0.03f),
            radius = gr,
            style = Stroke(width = 1f),
        )
        gr -= r * 0.017f
        i++
    }
    // Паузы между треками
    listOf(0.82f, 0.64f).forEach { k ->
        drawCircle(Color.Black.copy(alpha = 0.6f), radius = r * k, style = Stroke(width = r * 0.012f))
    }
    drawCircle(Color.White.copy(alpha = 0.10f), radius = r - 1f, style = Stroke(width = 2f))
    // Цветное кольцо вокруг этикетки
    drawCircle(accent.copy(alpha = 0.9f), radius = r * (LABEL_FRACTION + 0.025f), style = Stroke(width = r * 0.03f))
}

private fun DrawScope.drawSpindle() {
    val r = size.minDimension / 2f
    drawCircle(Color(0xFFE6E6E6), radius = r * 0.04f)
    drawCircle(Color(0xFF101010), radius = r * 0.02f)
}

private fun DrawScope.drawSheen() {
    val r = size.minDimension / 2f
    drawCircle(
        brush = Brush.sweepGradient(
            0f to Color.Transparent,
            0.10f to Color.White.copy(alpha = 0.13f),
            0.20f to Color.Transparent,
            0.55f to Color.Transparent,
            0.62f to Color.White.copy(alpha = 0.08f),
            0.72f to Color.Transparent,
            1f to Color.Transparent,
            center = center,
        ),
        radius = r,
    )
}
