package com.offplayer.base

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val ROOT = "root"
private const val APPEARANCE = "appearance"
private const val SHELL = "shell"
private const val MODULES = "modules"
private const val TUNING = "tuning"
private const val ABOUT = "about"
private const val MODULE_PREFIX = "module:"

/** Настройки основы: общий список + отдельный пункт на каждый модуль. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    var stack by rememberSaveable { mutableStateOf(listOf(ROOT)) }
    val page = stack.last()
    val back = { if (stack.size > 1) stack = stack.dropLast(1) else Nav.service = null }
    val open = { p: String -> stack = stack + p }
    BackHandler(stack.size > 1) { back() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(pageTitle(page)) },
            navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        )
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState.length >= initialState.length && targetState != ROOT
                (slideInHorizontally(tunedSpring()) { if (forward) it else -it / 3 }) togetherWith
                    (slideOutHorizontally(tunedSpring()) { if (forward) -it / 3 else it })
            },
            label = "settingsPage",
        ) { p ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                when {
                    p == ROOT -> RootPage(open)
                    p == APPEARANCE -> AppearancePage()
                    p == SHELL -> ShellPage()
                    p == MODULES -> ModulesPage()
                    p == TUNING -> TuningPage()
                    p == ABOUT -> AboutPage()
                    p.startsWith(MODULE_PREFIX) -> ModuleSettingsPage(p.removePrefix(MODULE_PREFIX))
                }
            }
        }
    }
}

private fun pageTitle(p: String) = when {
    p == ROOT -> "Настройки"
    p == APPEARANCE -> "Оформление"
    p == SHELL -> "Стиль основы"
    p == MODULES -> "Модули"
    p == TUNING -> "Подбор анимаций"
    p == ABOUT -> "О сборке"
    p.startsWith(MODULE_PREFIX) -> Catalog.byId(p.removePrefix(MODULE_PREFIX))?.title ?: ""
    else -> ""
}

@Composable
private fun RootPage(open: (String) -> Unit) {
    Header("Основа")
    NavRow(Icons.Rounded.Palette, "Оформление", "Цвета, классический режим, лёгкий режим") { open(APPEARANCE) }
    NavRow(Icons.Rounded.Dashboard, "Стиль основы", Prefs.shell.value.title) { open(SHELL) }
    NavRow(Icons.Rounded.Extension, "Модули", "Установка, включение, порядок вкладок") { open(MODULES) }
    val installed = Catalog.installed()
    if (installed.isNotEmpty()) {
        Divider()
        Header("Модули")
        // Каждый модуль добавляет свой пункт настроек
        installed.forEach { m -> NavRow(m.icon, m.title, m.description, tint = m.color) { open(MODULE_PREFIX + m.id) } }
        NavRow(Catalog.downloads.icon, Catalog.downloads.title, "Служебный модуль", tint = Catalog.downloads.color) {
            open(MODULE_PREFIX + "downloads")
        }
    }
    Divider()
    Header("Макет")
    NavRow(Icons.Rounded.Animation, "Подбор анимаций", "Пружины, наклон карт, пороги жестов") { open(TUNING) }
    NavRow(Icons.Rounded.Restore, "Пройти первую настройку заново", null) {
        Nav.service = null
        Prefs.firstRunDone.value = false
    }
    NavRow(Icons.Rounded.Info, "О сборке", null) { open(ABOUT) }
}

@Composable
private fun AppearancePage() {
    Header("Цвета")
    Chips(ColorMode.entries, Prefs.colorMode.value, { it.title }) { Prefs.colorMode.value = it }
    Note(Prefs.colorMode.value.description)
    if (Prefs.colorMode.value == ColorMode.CLASSIC) {
        Header("Фон")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(ClassicPreset.entries) { p ->
                val sel = Prefs.preset.value == p
                Column(
                    Modifier.clip(MaterialTheme.shapes.medium).clickable { Prefs.preset.value = p }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(Color(p.background))
                            .border(if (sel) 3.dp else 1.dp, if (sel) Coral else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                    Text(p.title, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Note("Акцентный цвет в классическом режиме — фирменный коралл OFFPlayer.")
    }
    Divider()
    Header("Производительность")
    SwitchRow("Лёгкий режим", "Без размытия, градиентов и живых задних карточек — для слабых устройств", Prefs.lightMode.value) {
        Prefs.lightMode.value = it
    }
    Divider()
    Header("Шрифт")
    Note("Сейчас — системный шрифт. Выбор из списка шрифтов появится в следующих сборках.")
}

@Composable
private fun ShellPage() {
    Header("Главный экран")
    ShellStyle.entries.forEach { s ->
        Row(
            Modifier.fillMaxWidth().clickable { Prefs.shell.value = s }.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.RadioButton(selected = Prefs.shell.value == s, onClick = null)
            Column(Modifier.padding(start = 14.dp)) {
                Text(s.title, style = MaterialTheme.typography.bodyLarge)
                Text(s.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Divider()
    Header("Классическая панель")
    Note("До пяти вкладок. Если модулей больше — свайп вверх по нижней панели открывает остальные.")
    Divider()
    Header("Настраиваемая лента")
    Chips(RailSide.entries, Prefs.railSide.value, { it.title }) { Prefs.railSide.value = it }
    SwitchRow("Подписи под иконками", "TalkBack читает названия всегда, даже если подписи скрыты", Prefs.railLabels.value) {
        Prefs.railLabels.value = it
    }
    NavRow(Icons.Rounded.Restore, "Вернуть ленту в центр", "Двигать её можно долгим нажатием и перетаскиванием вдоль края") {
        Prefs.railOffset.value = 0.5f
    }
    Divider()
    Header("Карточная лента")
    Text("Кнопки «Загрузка» и «Настройки»", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium)
    Chips(Corner.entries, Prefs.cardCorner.value, { it.title }) { Prefs.cardCorner.value = it }
    Text("Возврат после «картинки в картинке»", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium)
    Chips(listOf(false, true), Prefs.pipReturnToEnd.value, { if (it) "В конец стопки" else "В начало стопки" }) {
        Prefs.pipReturnToEnd.value = it
    }
    Note("Свайп от края до края карточки убирает её в конец стопки. Дуга снизу вверх и обратно вниз — возвращает. Долгое нажатие по полю вокруг карточек — меню основы.")
}

@Composable
private fun ModulesPage() {
    Header("Установленные")
    val installed = Catalog.installed()
    installed.forEachIndexed { i, m ->
        val enabled = m.id !in Prefs.disabled.value
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(m.icon, null, tint = m.color)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(m.title)
                Text(if (m.builtIn) "Предустановлен" else m.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { Catalog.move(m.id, -1) }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Выше") }
            IconButton(onClick = { Catalog.move(m.id, 1) }, enabled = i < installed.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Ниже") }
            Switch(enabled, { Catalog.setEnabled(m.id, it) })
            if (!m.builtIn) IconButton(onClick = { Catalog.uninstall(m.id) }) { Icon(Icons.Rounded.Delete, "Удалить") }
        }
    }
    val available = Catalog.installable.filter { it.id !in Prefs.installed.value }
    if (available.isNotEmpty()) {
        Divider()
        Header("Каталог")
        available.forEach { m ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(m.icon, null, tint = m.color)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(m.title)
                    Text(m.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { Catalog.install(m.id) }) { Text("Установить") }
            }
        }
    }
    Note("«Загрузка» — служебный модуль: он не занимает вкладку. В 2.0 модули будут скачиваться из репозитория и обновляться без переустановки приложения.")
}

@Composable
private fun TuningPage() {
    Note("Значения применяются сразу — переключайтесь на вкладки и проверяйте.")
    TuneSlider("Жёсткость пружины", Prefs.stiffness, 100f..1500f) { "%.0f".format(it) }
    TuneSlider("Затухание пружины", Prefs.damping, 0.3f..1f) { "%.2f".format(it) }
    TuneSlider("Наклон карточек", Prefs.cardTilt, 0f..12f) { "%.1f°".format(it) }
    TuneSlider("Порог свайпа карточки", Prefs.swipeFraction, 0.2f..0.8f) { "${(it * 100).toInt()}% ширины" }
    TuneSlider("Скорость броска карточки", Prefs.flingVelocity, 400f..4000f) { "%.0f dp/с".format(it) }
    TuneSlider("Задержка раскрытия точки", Prefs.dotExpandDelay, 60f..400f) { "%.0f мс".format(it) }
    TuneSlider("Время столкновения точек", Prefs.collisionMs, 60f..400f) { "%.0f мс".format(it) }
    Spacer(Modifier.height(8.dp))
    Button(onClick = { Prefs.tuning.forEach { it.reset() } }, modifier = Modifier.padding(horizontal = 20.dp)) {
        Text("Сбросить к исходным")
    }
}

@Composable
private fun TuneSlider(title: String, pref: Pref<Float>, range: ClosedFloatingPointRange<Float>, format: (Float) -> String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row {
            Text(title, modifier = Modifier.weight(1f))
            Text(format(pref.value), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
        Slider(value = pref.value, onValueChange = { pref.value = it }, valueRange = range)
    }
}

@Composable
private fun ModuleSettingsPage(id: String) {
    val spec = Catalog.byId(id) ?: return
    var example by rememberSaveable(id) { mutableStateOf(true) }
    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(spec.color.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
            Icon(spec.icon, null, tint = spec.color)
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(spec.title, style = MaterialTheme.typography.titleMedium)
            Text(spec.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    SwitchRow("Пример настройки модуля", "Этот пункт добавил сам модуль — основа только показывает его", example) { example = it }
    Note("В 2.0 каждый модуль описывает свои настройки сам, и они появляются здесь отдельным пунктом.")
}

@Composable
private fun AboutPage() {
    val context = LocalContext.current
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        VinylLogo(120.dp, rotating = true)
        Spacer(Modifier.height(16.dp))
        Text("OFFPlayer 2.0 — макет основы", style = MaterialTheme.typography.titleMedium)
        Text("Версия $version", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(context.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(
            "Сборка R: только русский язык. Модули — пустышки для проверки дизайна, жестов и анимаций.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------- Элементы ----------

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String?, tint: Color? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.onSurface)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onChange)
    }
}

@Composable
fun <T> Chips(options: List<T>, selected: T, label: (T) -> String, inset: androidx.compose.ui.unit.Dp = 16.dp, onSelect: (T) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = inset), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
    }
}
