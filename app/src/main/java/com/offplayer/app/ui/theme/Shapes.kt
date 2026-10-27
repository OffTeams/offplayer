package com.offplayer.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Единая система скруглений интерфейса — всего четыре уровня (+ бейдж):
 *  • Thumb  10dp — мелкие обложки в списках, очереди и мини-плеерах, объекты на обложке;
 *  • Card   16dp — обложки в сетках, превью видео, карточки, поля ввода, меню;
 *  • Large  24dp — крупные обложки и панели, нижние листы, диалоги;
 *  • Pill        — все кнопки, чипы и переключатели.
 */
object AppShapes {
    val Badge = RoundedCornerShape(6.dp)
    val Thumb = RoundedCornerShape(10.dp)
    val Card = RoundedCornerShape(16.dp)
    val Large = RoundedCornerShape(24.dp)
    val Pill = CircleShape
}

/** Те же уровни для стандартных компонентов Material (меню, поля, чипы, карточки, листы, диалоги). */
val AppMaterialShapes = Shapes(
    extraSmall = AppShapes.Card,                 // выпадающие меню, текстовые поля
    small = RoundedCornerShape(percent = 50),    // чипы — капсулы, как кнопки
    medium = AppShapes.Card,                     // карточки
    large = AppShapes.Large,
    extraLarge = AppShapes.Large,                // диалоги и нижние листы
)
