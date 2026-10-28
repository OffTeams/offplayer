package com.offplayer.base

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Где модуль показывается в основе. */
enum class Placement {
    /** Отдельная вкладка (точка, карточка). */
    TAB,
    /** Кнопка в шапке и служебных местах (как «Поиск»). */
    HEADER,
}

/**
 * Описание модуля — то, что модуль сообщает основе. В макете модули встроены,
 * в 2.0 это будут подгружаемые подписанные файлы `.offmod`.
 */
class ModuleSpec(
    val id: String,
    val title: String,
    val icon: ImageVector,
    /** Цвет модуля (как сегменты кольца на иконке). */
    val color: Color,
    val description: String,
    val placement: Placement = Placement.TAB,
    /** Предустановлен и не удаляется из макета (Загрузка, Музыка, Видео). */
    val builtIn: Boolean = false,
    val content: @Composable () -> Unit,
)

/**
 * Что основа сообщает модулю: виден ли он впереди, живой ли он сейчас
 * и насколько его карточка уже уехала из стопки (0 — впереди, 1 — улетела).
 */
data class ModuleEnv(
    val live: Boolean = true,
    val front: Boolean = true,
    val inCard: Boolean = false,
    val stackProgress: Float = 0f,
    /** Показывать ли модулю свой заголовок (в классической панели заголовок — в шапке основы). */
    val showTitle: Boolean = true,
)

val LocalModuleEnv = compositionLocalOf { ModuleEnv() }

object Catalog {
    val music = ModuleSpec("music", "Музыка", Icons.Rounded.LibraryMusic, Color(0xFF8E7CFF),
        "Треки, плеер с очередью", builtIn = true) { MusicModule() }
    val video = ModuleSpec("video", "Видео", Icons.Rounded.Movie, Color(0xFFFF7A59),
        "Видео, «картинка в картинке», только звук", builtIn = true) { VideoModule() }
    val podcasts = ModuleSpec("podcasts", "Подкасты", Icons.Rounded.Podcasts, Color(0xFF2EC4B6),
        "Пустышка для проверки вкладок") { PlaceholderModule("podcasts") }
    val audiobooks = ModuleSpec("audiobooks", "Аудиокниги", Icons.Rounded.AutoStories, Color(0xFFFFC24B),
        "Пустышка для проверки вкладок") { PlaceholderModule("audiobooks") }
    val radio = ModuleSpec("radio", "Радио", Icons.Rounded.Radio, Color(0xFF5B4BDB),
        "Пустышка для проверки вкладок") { PlaceholderModule("radio") }
    val clips = ModuleSpec("clips", "Клипы", Icons.Rounded.SmartDisplay, Color(0xFFD94BC0),
        "Пустышка для проверки вкладок") { PlaceholderModule("clips") }
    val collections = ModuleSpec("collections", "Сборники", Icons.Rounded.Style, Color(0xFF4F8A3C),
        "Пустышка — шестая вкладка для выдвижной панели") { PlaceholderModule("collections") }
    val search = ModuleSpec("search", "Поиск", Icons.Rounded.Search, Color(0xFF3D5A80),
        "Поиск по платформам, живёт в шапке", placement = Placement.HEADER) { SearchModule() }

    /** Служебные пункты основы — не вкладки. */
    val downloads = ModuleSpec("downloads", "Загрузка", Icons.Rounded.Download, Color(0xFFFF7A59),
        "Модуль загрузки (служебный)", builtIn = true) { DownloadsScreen() }
    val settings = ModuleSpec("settings", "Настройки", Icons.Rounded.Settings, Color(0xFF8E7CFF),
        "Настройки основы и модулей") { SettingsScreen() }

    val installable = listOf(music, video, podcasts, audiobooks, radio, clips, collections, search)

    fun byId(id: String?): ModuleSpec? = (installable + downloads + settings).firstOrNull { it.id == id }

    fun installed(): List<ModuleSpec> = Prefs.installed.value.mapNotNull { id -> installable.firstOrNull { it.id == id } }

    /** Модули-вкладки: установленные, включённые, не ушедшие в «картинку в картинке». */
    fun tabs(): List<ModuleSpec> = installed().filter {
        it.placement == Placement.TAB && it.id !in Prefs.disabled.value && it.id != Playback.pipModule
    }

    fun headerModules(): List<ModuleSpec> = installed().filter {
        it.placement == Placement.HEADER && it.id !in Prefs.disabled.value
    }

    fun install(id: String) {
        if (id !in Prefs.installed.value) Prefs.installed.value = Prefs.installed.value + id
    }

    fun uninstall(id: String) {
        Prefs.installed.value = Prefs.installed.value - id
        Prefs.disabled.value = Prefs.disabled.value - id
    }

    fun setEnabled(id: String, enabled: Boolean) {
        Prefs.disabled.value = if (enabled) Prefs.disabled.value - id else (Prefs.disabled.value + id).distinct()
    }

    fun move(id: String, delta: Int) {
        val list = Prefs.installed.value.toMutableList()
        val i = list.indexOf(id)
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        Prefs.installed.value = list
    }
}

/** Навигация основы: текущая вкладка и открытый служебный экран. */
object Nav {
    var currentId by mutableStateOf<String?>(null)
    /** Служебный экран поверх оболочки: downloads, settings, search. */
    var service by mutableStateOf<String?>(null)

    fun current(tabs: List<ModuleSpec>): ModuleSpec? =
        tabs.firstOrNull { it.id == currentId } ?: tabs.firstOrNull()

    fun select(id: String) {
        if (Catalog.byId(id)?.let { it.placement == Placement.HEADER || it.id == "downloads" || it.id == "settings" } == true) {
            service = id
        } else {
            currentId = id
        }
    }
}
