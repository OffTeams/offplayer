package com.offplayer.base

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableStateOf

enum class ShellStyle(val title: String, val description: String) {
    CLASSIC("Классическая панель", "До пяти вкладок внизу, шапка с загрузкой и настройками"),
    DOTS("Настраиваемая лента", "Маленькие точки у края экрана"),
    CARDS("Карточная лента", "Модули живут стопкой карт"),
}

enum class RailSide(val title: String) { LEFT("Слева"), BOTTOM("Внизу"), RIGHT("Справа") }

enum class ColorMode(val title: String, val description: String) {
    WALLPAPER("По обоям", "Градиенты из цветов обоев, плеер подстраивается под трек"),
    CLASSIC("Классический", "Статичные фоны без размытия и градиентов"),
}

enum class ClassicPreset(val title: String, val background: Long, val dark: Boolean) {
    WHITE("Белый", 0xFFFFFFFF, false),
    LIGHT_GRAY("Светло-серый", 0xFFECECEF, false),
    GRAY("Серый", 0xFFC6C6CB, false),
    DARK_GRAY("Тёмно-серый", 0xFF3A3A3E, true),
    DARK("Тёмный", 0xFF1B1B1E, true),
    AMOLED("AMOLED", 0xFF000000, true),
}

enum class Corner(val title: String) {
    TOP_START("Слева сверху"), TOP_END("Справа сверху"), BOTTOM_START("Слева снизу"), BOTTOM_END("Справа снизу"),
}

/** Значение настройки: состояние Compose + запись в SharedPreferences. */
class Pref<T>(
    private val key: String,
    private val default: T,
    private val read: SharedPreferences.(String, T) -> T,
    private val write: SharedPreferences.Editor.(String, T) -> Unit,
) {
    private val state = mutableStateOf(default)

    var value: T
        get() = state.value
        set(v) {
            state.value = v
            Prefs.sp.edit().apply { write(key, v) }.apply()
        }

    fun load(sp: SharedPreferences) { state.value = sp.read(key, default) }
    fun reset() { value = default }
}

/** Настройки основы макета. */
object Prefs {
    internal lateinit var sp: SharedPreferences
    private val all = mutableListOf<Pref<*>>()

    private fun <T> pref(key: String, default: T, read: SharedPreferences.(String, T) -> T, write: SharedPreferences.Editor.(String, T) -> Unit) =
        Pref(key, default, read, write).also { all += it }

    private fun bool(key: String, default: Boolean) = pref(key, default, { k, d -> getBoolean(k, d) }, { k, v -> putBoolean(k, v) })
    private fun float(key: String, default: Float) = pref(key, default, { k, d -> getFloat(k, d) }, { k, v -> putFloat(k, v) })
    private inline fun <reified E : Enum<E>> enum(key: String, default: E) = pref(
        key, default,
        { k, d -> getString(k, null)?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: d },
        { k, v -> putString(k, v.name) },
    )
    private fun list(key: String, default: List<String>) = pref(
        key, default,
        { k, d -> getString(k, null)?.split(',')?.filter { it.isNotBlank() } ?: d },
        { k, v -> putString(k, v.joinToString(",")) },
    )

    val firstRunDone = bool("first_run_done", false)
    val shell = enum("shell", ShellStyle.CLASSIC)
    val railSide = enum("rail_side", RailSide.RIGHT)
    /** Положение ленты вдоль края, 0..1. */
    val railOffset = float("rail_offset", 0.5f)
    val railLabels = bool("rail_labels", true)
    val colorMode = enum("color_mode", ColorMode.WALLPAPER)
    val preset = enum("preset", ClassicPreset.DARK)
    val lightMode = bool("light_mode", false)
    val cardCorner = enum("card_corner", Corner.TOP_END)
    /** Куда возвращается модуль после «картинки в картинке»: в конец (true) или в начало стопки. */
    val pipReturnToEnd = bool("pip_return_end", true)

    /** Установленные модули по порядку; на старте — Музыка и Видео (Загрузка — служебный). */
    val installed = list("installed", listOf("music", "video"))
    val disabled = list("disabled", emptyList())

    // ---- Подбор анимаций ----
    val stiffness = float("t_stiffness", 380f)
    val damping = float("t_damping", 0.72f)
    val cardTilt = float("t_card_tilt", 5f)
    val swipeFraction = float("t_swipe_fraction", 0.45f)
    val flingVelocity = float("t_fling_velocity", 1600f)
    val dotExpandDelay = float("t_dot_expand_delay", 140f)
    val collisionMs = float("t_collision_ms", 150f)

    val tuning = listOf(stiffness, damping, cardTilt, swipeFraction, flingVelocity, dotExpandDelay, collisionMs)

    fun init(context: Context) {
        sp = context.getSharedPreferences("base", Context.MODE_PRIVATE)
        all.forEach { it.load(sp) }
    }
}

/** Пружина из настроек подбора; в лёгком режиме — короткий простой переход. */
fun <T> tunedSpring(): FiniteAnimationSpec<T> =
    if (Prefs.lightMode.value) tween(160)
    else spring(dampingRatio = Prefs.damping.value, stiffness = Prefs.stiffness.value)
