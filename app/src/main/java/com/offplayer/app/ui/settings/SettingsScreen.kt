package com.offplayer.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.HeadsetOff
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offplayer.app.data.AppSettings
import com.offplayer.app.data.CoverStyle
import com.offplayer.app.data.DOWNLOAD_LANGUAGES
import com.offplayer.app.data.DeviceMediaMode
import com.offplayer.app.data.LibraryStore
import com.offplayer.app.data.MediaRepository
import com.offplayer.app.data.PlayOrder
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import com.offplayer.app.data.SbAction
import com.offplayer.app.data.SponsorBlock
import com.offplayer.app.data.ThemeMode
import com.offplayer.app.ui.UiEvents
import com.offplayer.app.ui.download.AUDIO_FORMATS
import com.offplayer.app.ui.download.VIDEO_QUALITIES
import com.offplayer.app.ui.formatSize
import com.offplayer.app.ui.theme.AppShapes

/** Страницы настроек: корневой список разделов и сами разделы. */
enum class SettingsPage(val title: String) {
    ROOT("Настройки"),
    GENERAL("Общие"),
    MUSIC("Музыкальный плеер"),
    VIDEO("Видеоплеер"),
    SPONSORBLOCK("SponsorBlock"),
    DOWNLOADS("Загрузки"),
    ABOUT("О приложении"),
}

/**
 * Настройки в духе YouTube: экран выезжает справа поверх приложения, разделы
 * открываются следующим уровнем с тем же сдвигом. «Назад» возвращает на уровень выше.
 */
@Composable
fun SettingsHost() {
    val request by UiEvents.openSettings.collectAsStateWithLifecycle()
    var stack by remember { mutableStateOf(listOf<SettingsPage>()) }
    var lastShown by remember { mutableStateOf(SettingsPage.ROOT) }
    LaunchedEffect(request) {
        val p = request ?: return@LaunchedEffect
        stack = if (p == SettingsPage.ROOT) listOf(p) else listOf(SettingsPage.ROOT, p)
        UiEvents.openSettings.value = null
    }
    stack.lastOrNull()?.let { lastShown = it }
    val back = { stack = stack.dropLast(1) }

    AnimatedVisibility(
        visible = stack.isNotEmpty(),
        enter = slideInHorizontally(tween(300)) { it },
        exit = slideOutHorizontally(tween(300)) { it },
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AnimatedContent(
                targetState = lastShown,
                transitionSpec = {
                    // Глубже — сдвиг влево, назад — вправо
                    val forward = targetState.ordinal > initialState.ordinal
                    (slideInHorizontally(tween(280)) { if (forward) it else -it / 3 } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(280)) { if (forward) -it / 3 else it } + fadeOut(tween(200)))
                },
                label = "settingsPage",
            ) { page ->
                SettingsPageScaffold(page.title, onBack = back) {
                    when (page) {
                        SettingsPage.ROOT -> RootPage { stack = stack + it }
                        SettingsPage.GENERAL -> GeneralPage()
                        SettingsPage.MUSIC -> MusicPage()
                        SettingsPage.VIDEO -> VideoPage()
                        SettingsPage.SPONSORBLOCK -> SponsorBlockPage()
                        SettingsPage.DOWNLOADS -> DownloadsPage()
                        SettingsPage.ABOUT -> AboutPage()
                    }
                }
            }
        }
    }
    BackHandler(stack.isNotEmpty()) { back() }
}

@Composable
private fun SettingsPageScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            content = content,
        )
    }
}

// ---------- Страницы ----------

@Composable
private fun RootPage(open: (SettingsPage) -> Unit) {
    Header("Приложение")
    NavRow(Icons.Rounded.Settings, "Общие", "Тема оформления, медиатека") { open(SettingsPage.GENERAL) }
    NavRow(Icons.Rounded.MusicNote, "Музыкальный плеер", "Обложка, порядок, повтор, кнопки плеера") { open(SettingsPage.MUSIC) }
    NavRow(Icons.Rounded.Movie, "Видеоплеер", "Фон, скорость, жесты, эпизоды") { open(SettingsPage.VIDEO) }
    NavRow(Icons.Rounded.FastForward, "SponsorBlock", "Что пропускать, о чём спрашивать") { open(SettingsPage.SPONSORBLOCK) }
    NavRow(Icons.Rounded.Download, "Загрузки", "Качество, аудиодорожки, субтитры") { open(SettingsPage.DOWNLOADS) }
    Divider()
    Header("Информация")
    NavRow(Icons.Rounded.Info, "О приложении", "Версия, место в памяти") { open(SettingsPage.ABOUT) }
}

@Composable
private fun GeneralPage() {
    val theme by AppSettings.themeMode.collectAsStateWithLifecycle()
    Header("Оформление")
    ChoiceRow(
        Icons.Rounded.DarkMode, "Тема", ThemeMode.entries.map { it to it.label }, theme,
        AppSettings::setThemeMode,
    )
    Divider()
    Header("Медиатека")
    DeviceMediaRows()
}

@Composable
private fun MusicPage() {
    val cover by AppSettings.coverStyle.collectAsStateWithLifecycle()
    val locked by AppSettings.coverLocked.collectAsStateWithLifecycle()
    val noisy by AppSettings.pauseOnNoisy.collectAsStateWithLifecycle()
    val order by AppSettings.playOrder.collectAsStateWithLifecycle()
    val repeat by AppSettings.defaultRepeat.collectAsStateWithLifecycle()
    val sleep by AppSettings.showSleepTimer.collectAsStateWithLifecycle()
    val addPl by AppSettings.showAddToPlaylist.collectAsStateWithLifecycle()
    val source by AppSettings.saveSource.collectAsStateWithLifecycle()
    Header("Обложка")
    // «Сменяемая» — стили листаются свайпом; конкретный стиль — только он, остальные скрыты
    ChoiceRow(
        Icons.Rounded.Album, "Вид обложки",
        listOf<CoverStyle?>(null).map { it to "Сменяемая" } + CoverStyle.entries.map { it to it.label },
        if (locked) cover else null,
        { s -> if (s == null) AppSettings.setCoverLocked(false) else { AppSettings.setCoverStyle(s); AppSettings.setCoverLocked(true) } },
        optionDescription = { if (it == null) "Свайп по обложке в плеере меняет стиль" else "Всегда этот стиль" },
    )
    Divider()
    Header("Воспроизведение")
    ChoiceRow(
        Icons.Rounded.Shuffle, "Порядок по умолчанию", PlayOrder.entries.map { it to it.label }, order,
        AppSettings::setPlayOrder, optionDescription = { it.description },
    )
    ChoiceRow(
        Icons.Rounded.Repeat, "Повтор по умолчанию",
        listOf(0 to "Выключен", 2 to "Очередь по кругу", 1 to "Один трек"), repeat, AppSettings::setDefaultRepeat,
    )
    Divider()
    Header("Плеер")
    SwitchRow(Icons.Rounded.Bedtime, "Таймер сна", "Кнопка таймера сна в плеере", sleep, AppSettings::setShowSleepTimer)
    SwitchRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Добавить в плейлист", "Кнопка добавления текущего трека в плейлист", addPl, AppSettings::setShowAddToPlaylist)
    SwitchRow(
        Icons.Rounded.Link, "Источник трека",
        "Сохранять канал, с которого скачан трек, и показывать «Сейчас играет трек от:» со ссылкой на него",
        source, AppSettings::setSaveSource,
    )
    SwitchRow(
        if (noisy) Icons.Rounded.HeadsetOff else Icons.Rounded.Headphones,
        "Пауза при отключении наушников",
        "Музыка и видео встают на паузу, когда наушники отключаются",
        noisy, AppSettings::setPauseOnNoisy,
    )
}

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@Composable
private fun VideoPage() {
    val bg by AppSettings.videoBackground.collectAsStateWithLifecycle()
    val pip by AppSettings.videoAutoPip.collectAsStateWithLifecycle()
    val rotate by AppSettings.videoAutoRotate.collectAsStateWithLifecycle()
    val speed by AppSettings.videoSpeed.collectAsStateWithLifecycle()
    val fill by AppSettings.videoFill.collectAsStateWithLifecycle()
    val step by AppSettings.seekStep.collectAsStateWithLifecycle()
    val chapters by AppSettings.showChapters.collectAsStateWithLifecycle()

    Header("Воспроизведение")
    SwitchRow(Icons.Rounded.Headphones, "Воспроизведение в фоне", "Звук продолжает играть, когда плеер свёрнут", bg, AppSettings::setVideoBackground)
    SwitchRow(Icons.Rounded.PictureInPictureAlt, "Авто «картинка в картинке»", "Видео уходит в окно поверх других приложений при нажатии «Домой»", pip, AppSettings::setVideoAutoPip)
    ChoiceRow(
        Icons.Rounded.Speed, "Скорость по умолчанию",
        SPEEDS.map { it to "${it.toString().removeSuffix(".0")}×" }, speed, AppSettings::setVideoSpeed,
    )
    Divider()
    Header("Экран")
    SwitchRow(Icons.Rounded.ScreenRotation, "Поворот под видео", "Широкие ролики открываются в альбомной ориентации, вертикальные и квадратные — в портретной", rotate, AppSettings::setVideoAutoRotate)
    SwitchRow(Icons.Rounded.Fullscreen, "Заполнять экран", "Видео без полос по краям, края кадра обрезаются. Переключается двойным нажатием в центре", fill, AppSettings::setVideoFill)
    Divider()
    Header("Жесты")
    ChoiceRow(
        Icons.Rounded.TouchApp, "Перемотка двойным нажатием",
        listOf(5, 10, 15, 30).map { it to "$it секунд" }, step, AppSettings::setSeekStep,
        subtitle = "Двойное нажатие у левого края — назад, у правого — вперёд",
    )
    Divider()
    Header("Эпизоды")
    SwitchRow(Icons.AutoMirrored.Rounded.FormatListBulleted, "Эпизоды в плеере", "Кнопка и панель «Эпизоды» для видео, скачанных с главами", chapters, AppSettings::setShowChapters)
}

@Composable
private fun SponsorBlockPage() {
    val actions by AppSettings.sbActions.collectAsStateWithLifecycle()
    val notice by AppSettings.sbNotice.collectAsStateWithLifecycle()
    val onDownload by AppSettings.sbOnDownload.collectAsStateWithLifecycle()
    Note(
        "SponsorBlock — открытая база отметок рекламы и других вставок в роликах YouTube. " +
            "Для каждой категории выберите: пропускать автоматически, спрашивать кнопкой «Пропустить» или не трогать. " +
            "Отмеченные отрезки закрашиваются зелёным на полосе перемотки.",
    )
    Header("Категории")
    SponsorBlock.CATEGORIES.forEach { (key, label) ->
        ChoiceRow(
            icon = null,
            title = label,
            options = SbAction.entries.map { it to it.label },
            selected = actions[key] ?: SbAction.OFF,
            onSelect = { AppSettings.setSbAction(key, it) },
            subtitle = SponsorBlock.DESCRIPTIONS[key],
            optionDescription = { it.description },
        )
    }
    Divider()
    Header("Поведение")
    SwitchRow(Icons.Rounded.Tune, "Плашка после пропуска", "«Пропущено: Спонсор · Вернуть» — можно вернуться к отрезку", notice, AppSettings::setSbNotice)
    SwitchRow(Icons.Rounded.Download, "Загружать при скачивании", "Отметки сохраняются вместе с видео и работают офлайн", onDownload, AppSettings::setSbOnDownload)
    Note("Запрос к SponsorBlock приватный: на сервер уходят только первые символы хеша ID ролика.")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadsPage() {
    val audioFormat by AppSettings.audioFormat.collectAsStateWithLifecycle()
    val height by AppSettings.videoHeight.collectAsStateWithLifecycle()
    val chapters by AppSettings.saveChapters.collectAsStateWithLifecycle()
    val sb by AppSettings.sbOnDownload.collectAsStateWithLifecycle()
    val audioLangs by AppSettings.extraAudioLangs.collectAsStateWithLifecycle()
    val subLangs by AppSettings.subLangs.collectAsStateWithLifecycle()
    val autoSubs by AppSettings.autoSubs.collectAsStateWithLifecycle()
    val whole by AppSettings.wholePlaylist.collectAsStateWithLifecycle()

    Header("По умолчанию")
    ChoiceRow(Icons.Rounded.AudioFile, "Формат музыки", AUDIO_FORMATS, audioFormat, AppSettings::setAudioFormat)
    ChoiceRow(Icons.Rounded.HighQuality, "Качество видео", VIDEO_QUALITIES, height, AppSettings::setVideoHeight)
    SwitchRow(
        Icons.AutoMirrored.Rounded.PlaylistPlay, "Плейлист целиком",
        "По ссылке на страницу плейлиста сразу скачивать его весь: видео — списком, музыка — миксом",
        whole, AppSettings::setWholePlaylist,
    )
    Divider()
    Header("Видео")
    SwitchRow(Icons.AutoMirrored.Rounded.FormatListBulleted, "Эпизоды", "Сохранять главы видео для навигации в плеере офлайн", chapters, AppSettings::setSaveChapters)
    SwitchRow(Icons.Rounded.FastForward, "SponsorBlock", "Сохранять отметки рекламы и вставок для пропуска офлайн", sb, AppSettings::setSbOnDownload)
    Divider()
    Header("Аудиодорожки")
    Note(
        if (audioLangs.isEmpty()) "Сейчас: только оригинальная дорожка. Выберите языки, чтобы добавить дубляж YouTube, если он есть у ролика. Переключать дорожки можно в настройках плеера."
        else "Оригинальная дорожка + дубляж на выбранных языках, если он есть у ролика. Переключение — в настройках плеера.",
    )
    LanguageChips(audioLangs, AppSettings::toggleExtraAudioLang, Icons.Rounded.RecordVoiceOver)
    Divider()
    Header("Субтитры")
    Note(
        if (subLangs.isEmpty()) "Сейчас: без субтитров. Выберите языки — субтитры встроятся в видео и включаются кнопкой CC в плеере."
        else "Субтитры на выбранных языках встраиваются в видео и включаются кнопкой CC в плеере.",
    )
    LanguageChips(subLangs, AppSettings::toggleSubLang, Icons.Rounded.ClosedCaption)
    SwitchRow(
        Icons.Rounded.ClosedCaption, "Автоматические субтитры",
        "Если автор не добавил субтитры — брать созданные YouTube автоматически",
        autoSubs, AppSettings::setAutoSubs, enabled = subLangs.isNotEmpty(),
    )
}

@Composable
private fun AboutPage() {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    val used = remember { LibraryStore.all().sumOf { it.size } }
    val count = remember { LibraryStore.all().size }
    InfoRow(Icons.Rounded.Info, "Версия", "OFFPlayer $version")
    InfoRow(Icons.Rounded.Storage, "Медиатека", "Файлов: $count • ${formatSize(used)}")
    Note("Плагин загрузки обновляется автоматически при подключении к сети.")
}

// ---------- Медиатека: контент устройства ----------

@Composable
private fun DeviceMediaRows() {
    val context = LocalContext.current
    val mode by AppSettings.deviceMedia.collectAsStateWithLifecycle()
    var choosing by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (MediaRepository.hasPermission(context)) choosing = true
        else Toast.makeText(context, "Без доступа к медиафайлам контент устройства недоступен", Toast.LENGTH_LONG).show()
    }
    val apply = { m: DeviceMediaMode ->
        AppSettings.setDeviceMedia(m)
        MediaRepository.refreshAsync(context)
    }
    SwitchRow(
        Icons.Rounded.PhoneAndroid, "Контент из памяти устройства",
        "Слушать и смотреть ваши собственные аудио и видео, а не только скачанное через OFFPlayer",
        mode != DeviceMediaMode.OFF,
        onChange = { on ->

        when {
            !on -> apply(DeviceMediaMode.OFF)
            MediaRepository.hasPermission(context) -> choosing = true
            else -> launcher.launch(MediaRepository.mediaPermissions())
        }
        },
    )
    if (mode != DeviceMediaMode.OFF) {
        ChoiceRow(
            Icons.Rounded.MergeType, "Как показывать",
            listOf(DeviceMediaMode.MIXED to "Вместе со скачанным", DeviceMediaMode.SEPARATE to "Отдельный раздел"),
            mode, apply,
            optionDescription = {
                if (it == DeviceMediaMode.MIXED) "В общих списках «Треки» и «Все»" else "Раздел «На устройстве», файлы по папкам"
            },
        )
    }
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Как показывать ваши файлы?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeOption(Icons.Rounded.MergeType, "Вместе со скачанным", "Ваши треки и видео появятся в общих списках «Треки» и «Все» рядом со скачанными") {
                        apply(DeviceMediaMode.MIXED); choosing = false
                    }
                    ModeOption(Icons.Rounded.FolderOpen, "Отдельно — «На устройстве»", "Отдельные разделы в «Музыке» и «Видео»: файлы сгруппированы по папкам") {
                        apply(DeviceMediaMode.SEPARATE); choosing = false
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ModeOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(AppShapes.Card)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------- Элементы ----------

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

/** Строка с значком слева (как в настройках YouTube); без значка — отступ под него сохраняется. */
@Composable
private fun SettingRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(44.dp)) {
            if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha), modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            }
        }
        trailing()
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) =
    SettingRow(icon, title, subtitle, onClick = onClick)

@Composable
private fun InfoRow(icon: ImageVector, title: String, value: String) = SettingRow(icon, title, value)

@Composable
private fun SwitchRow(
    icon: ImageVector?,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    SettingRow(icon, title, subtitle, enabled = enabled, onClick = { onChange(!checked) }) {
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Значение выбирается в диалоге со списком вариантов; текущее показано под названием. */
@Composable
private fun <T> ChoiceRow(
    icon: ImageVector?,
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    subtitle: String? = null,
    optionDescription: ((T) -> String)? = null,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second.orEmpty()
    // Как в настройках YouTube: текущее значение — второй строкой под названием
    SettingRow(icon, title, subtitle = listOfNotNull(current, subtitle).joinToString("\n"), onClick = { open = true }) {
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { (value, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(AppShapes.Thumb)
                                .clickable { onSelect(value); open = false }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = value == selected, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                                optionDescription?.invoke(value)?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Закрыть") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageChips(selected: Set<String>, toggle: (String) -> Unit, icon: ImageVector) {
    FlowRow(
        Modifier.padding(start = 64.dp, end = 20.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DOWNLOAD_LANGUAGES.forEach { (code, name) ->
            FilterChip(
                selected = code in selected,
                onClick = { toggle(code) },
                label = { Text(name) },
                leadingIcon = if (code in selected) {
                    { Icon(icon, null, Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                    selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.inverseOnSurface,
                ),
            )
        }
    }
}
