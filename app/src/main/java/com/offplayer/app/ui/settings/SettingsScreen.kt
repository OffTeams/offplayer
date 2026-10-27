package com.offplayer.app.ui.settings

import com.offplayer.app.tr
import com.offplayer.app.R

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
import android.app.Activity
import com.offplayer.app.L
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import com.offplayer.app.data.DOWNLOAD_LANGUAGE_CODES
import com.offplayer.app.data.languageName
import com.offplayer.app.data.DeviceMediaMode
import com.offplayer.app.data.LibraryStore
import com.offplayer.app.data.MediaRepository
import com.offplayer.app.data.PlayOrder
import com.offplayer.app.update.AppUpdater
import com.offplayer.app.update.UpdateState
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Update
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
enum class SettingsPage(@androidx.annotation.StringRes private val res: Int) {
    ROOT(R.string.settings),
    GENERAL(R.string.set_general),
    MUSIC(R.string.set_music_player),
    VIDEO(R.string.set_video_player),
    SPONSORBLOCK(R.string.sponsorblock),
    DOWNLOADS(R.string.tab_downloads),
    ABOUT(R.string.set_about);

    val title: String get() = tr(res)
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
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr(R.string.action_back)) }
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
    Header(tr(R.string.set_app))
    NavRow(Icons.Rounded.Settings, tr(R.string.set_general), tr(R.string.set_general_desc)) { open(SettingsPage.GENERAL) }
    NavRow(Icons.Rounded.MusicNote, tr(R.string.set_music_player), tr(R.string.set_music_player_desc)) { open(SettingsPage.MUSIC) }
    NavRow(Icons.Rounded.Movie, tr(R.string.set_video_player), tr(R.string.set_video_player_desc)) { open(SettingsPage.VIDEO) }
    NavRow(Icons.Rounded.FastForward, tr(R.string.sponsorblock), tr(R.string.set_sb_desc)) { open(SettingsPage.SPONSORBLOCK) }
    NavRow(Icons.Rounded.Download, tr(R.string.tab_downloads), tr(R.string.set_downloads_desc)) { open(SettingsPage.DOWNLOADS) }
    Divider()
    Header(tr(R.string.set_info))
    NavRow(Icons.Rounded.Info, tr(R.string.set_about), tr(R.string.set_about_desc)) { open(SettingsPage.ABOUT) }
}

@Composable
private fun GeneralPage() {
    val theme by AppSettings.themeMode.collectAsStateWithLifecycle()
    val language by L.language.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    Header(tr(R.string.set_appearance))
    ChoiceRow(
        Icons.Rounded.Language, tr(R.string.set_language),
        L.LANGUAGES.map { it to if (it.isEmpty()) tr(R.string.language_system) else L.nativeName(it) }, language,
        { tag -> if (tag != language && activity != null) L.set(activity, tag) },
    )
    ChoiceRow(
        Icons.Rounded.DarkMode, tr(R.string.set_theme), ThemeMode.entries.map { it to it.label }, theme,
        AppSettings::setThemeMode,
    )
    Divider()
    Header(tr(R.string.set_library))
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
    Header(tr(R.string.set_cover))
    // «Сменяемая» — стили листаются свайпом; конкретный стиль — только он, остальные скрыты
    ChoiceRow(
        Icons.Rounded.Album, tr(R.string.set_cover_style),
        listOf<CoverStyle?>(null).map { it to tr(R.string.cover_switchable) } + CoverStyle.entries.map { it to it.label },
        if (locked) cover else null,
        { s -> if (s == null) AppSettings.setCoverLocked(false) else { AppSettings.setCoverStyle(s); AppSettings.setCoverLocked(true) } },
        optionDescription = { if (it == null) tr(R.string.cover_switchable_desc) else tr(R.string.cover_fixed_desc) },
    )
    Divider()
    Header(tr(R.string.set_playback))
    ChoiceRow(
        Icons.Rounded.Shuffle, tr(R.string.set_default_order), PlayOrder.entries.map { it to it.label }, order,
        AppSettings::setPlayOrder, optionDescription = { it.description },
    )
    ChoiceRow(
        Icons.Rounded.Repeat, tr(R.string.set_default_repeat),
        listOf(0 to tr(R.string.repeat_mode_off), 2 to tr(R.string.repeat_mode_all), 1 to tr(R.string.repeat_mode_one)), repeat, AppSettings::setDefaultRepeat,
    )
    Divider()
    Header(tr(R.string.set_player))
    SwitchRow(Icons.Rounded.Bedtime, tr(R.string.sleep_timer), tr(R.string.set_sleep_btn_desc), sleep, AppSettings::setShowSleepTimer)
    SwitchRow(Icons.AutoMirrored.Rounded.PlaylistAdd, tr(R.string.add_to_playlist), tr(R.string.set_addpl_btn_desc), addPl, AppSettings::setShowAddToPlaylist)
    SwitchRow(
        Icons.Rounded.Link, tr(R.string.set_source),
        tr(R.string.set_source_desc),
        source, AppSettings::setSaveSource,
    )
    SwitchRow(
        if (noisy) Icons.Rounded.HeadsetOff else Icons.Rounded.Headphones,
        tr(R.string.set_pause_noisy),
        tr(R.string.set_pause_noisy_desc),
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

    Header(tr(R.string.set_playback))
    SwitchRow(Icons.Rounded.Headphones, tr(R.string.bg_playback), tr(R.string.bg_playback_desc), bg, AppSettings::setVideoBackground)
    SwitchRow(Icons.Rounded.PictureInPictureAlt, tr(R.string.set_auto_pip), tr(R.string.set_auto_pip_desc), pip, AppSettings::setVideoAutoPip)
    ChoiceRow(
        Icons.Rounded.Speed, tr(R.string.set_default_speed),
        SPEEDS.map { it to "${it.toString().removeSuffix(".0")}×" }, speed, AppSettings::setVideoSpeed,
    )
    Divider()
    Header(tr(R.string.set_screen))
    SwitchRow(Icons.Rounded.ScreenRotation, tr(R.string.set_rotate), tr(R.string.set_rotate_desc), rotate, AppSettings::setVideoAutoRotate)
    SwitchRow(Icons.Rounded.Fullscreen, tr(R.string.set_fill), tr(R.string.set_fill_desc), fill, AppSettings::setVideoFill)
    Divider()
    Header(tr(R.string.set_gestures))
    ChoiceRow(
        Icons.Rounded.TouchApp, tr(R.string.set_double_tap),
        listOf(5, 10, 15, 30).map { it to tr(R.string.seconds_n, it) }, step, AppSettings::setSeekStep,
        subtitle = tr(R.string.set_double_tap_desc),
    )
    Divider()
    Header(tr(R.string.chapters))
    SwitchRow(Icons.AutoMirrored.Rounded.FormatListBulleted, tr(R.string.set_chapters_player), tr(R.string.set_chapters_player_desc), chapters, AppSettings::setShowChapters)
}

@Composable
private fun SponsorBlockPage() {
    val actions by AppSettings.sbActions.collectAsStateWithLifecycle()
    val notice by AppSettings.sbNotice.collectAsStateWithLifecycle()
    val onDownload by AppSettings.sbOnDownload.collectAsStateWithLifecycle()
    Note(tr(R.string.set_sb_intro))
    Header(tr(R.string.categories))
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
    Header(tr(R.string.set_behavior))
    SwitchRow(Icons.Rounded.Tune, tr(R.string.set_sb_notice), tr(R.string.set_sb_notice_desc), notice, AppSettings::setSbNotice)
    SwitchRow(Icons.Rounded.Download, tr(R.string.set_sb_on_download), tr(R.string.set_sb_on_download_desc), onDownload, AppSettings::setSbOnDownload)
    Note(tr(R.string.set_sb_privacy))
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

    Header(tr(R.string.set_defaults))
    ChoiceRow(Icons.Rounded.AudioFile, tr(R.string.audio_format), AUDIO_FORMATS, audioFormat, AppSettings::setAudioFormat)
    ChoiceRow(Icons.Rounded.HighQuality, tr(R.string.video_quality), VIDEO_QUALITIES, height, AppSettings::setVideoHeight)
    SwitchRow(
        Icons.AutoMirrored.Rounded.PlaylistPlay, tr(R.string.set_whole_playlist),
        tr(R.string.set_whole_playlist_desc),
        whole, AppSettings::setWholePlaylist,
    )
    Divider()
    Header(tr(R.string.tab_video))
    SwitchRow(Icons.AutoMirrored.Rounded.FormatListBulleted, tr(R.string.chapters), tr(R.string.set_save_chapters_desc), chapters, AppSettings::setSaveChapters)
    SwitchRow(Icons.Rounded.FastForward, tr(R.string.sponsorblock), tr(R.string.set_save_sb_desc), sb, AppSettings::setSbOnDownload)
    LanguageRow(
        Icons.Rounded.RecordVoiceOver, tr(R.string.audio_tracks),
        if (audioLangs.isEmpty()) tr(R.string.audio_tracks_original)
        else tr(R.string.audio_tracks_plus, audioLangs.joinToString(", ") { languageName(it) }),
        tr(R.string.audio_tracks_hint), audioLangs, max = 3, onChange = AppSettings::setExtraAudioLangs,
    )
    LanguageRow(
        Icons.Rounded.ClosedCaption, tr(R.string.subtitles),
        if (subLangs.isEmpty()) tr(R.string.subtitles_off) else subLangs.joinToString(", ") { languageName(it) },
        tr(R.string.subtitles_hint), subLangs, max = Int.MAX_VALUE, onChange = AppSettings::setSubLangs,
    )
    SwitchRow(
        Icons.Rounded.AutoAwesome, tr(R.string.auto_subs), tr(R.string.auto_subs_desc),
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
    InfoRow(Icons.Rounded.Info, tr(R.string.version), "OFFPlayer $version")
    UpdateRows()
    InfoRow(Icons.Rounded.Storage, tr(R.string.set_library), tr(R.string.files_count, count, formatSize(used)))
    Note(tr(R.string.set_about_note))
}

// ---------- Обновления приложения ----------

@Composable
private fun UpdateRows() {
    val context = LocalContext.current
    val state by AppUpdater.state.collectAsStateWithLifecycle()
    val auto by AppUpdater.autoCheck.collectAsStateWithLifecycle()
    val status = when (val s = state) {
        UpdateState.Checking -> tr(R.string.upd_checking)
        UpdateState.UpToDate -> tr(R.string.upd_up_to_date)
        is UpdateState.Available -> tr(R.string.upd_tap_to_update, s.info.version)
        is UpdateState.Downloading -> tr(R.string.upd_downloading_v, s.info.version, (s.progress * 100).toInt())
        is UpdateState.Installing -> tr(R.string.upd_confirm_short)
        is UpdateState.NeedsPermission -> tr(R.string.upd_need_permission_short)
        is UpdateState.Error -> s.message
        UpdateState.Idle -> tr(R.string.upd_tap_to_check)
    }
    SettingRow(Icons.Rounded.SystemUpdate, tr(R.string.upd_check), status, onClick = {
        if (state is UpdateState.Available || state is UpdateState.NeedsPermission || state is UpdateState.Error && (state as UpdateState.Error).info != null) {
            AppUpdater.showPrompt()
        } else {
            AppUpdater.check(context, manual = true)
        }
    })
    SwitchRow(
        Icons.Rounded.Update, tr(R.string.upd_auto),
        tr(R.string.upd_auto_desc),
        auto, { AppUpdater.setAutoCheck(context, it) },
    )
}

// ---------- Медиатека: контент устройства ----------

@Composable
private fun DeviceMediaRows() {
    val context = LocalContext.current
    val mode by AppSettings.deviceMedia.collectAsStateWithLifecycle()
    var choosing by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (MediaRepository.hasPermission(context)) choosing = true
        else Toast.makeText(context, tr(R.string.device_no_permission), Toast.LENGTH_LONG).show()
    }
    val apply = { m: DeviceMediaMode ->
        AppSettings.setDeviceMedia(m)
        MediaRepository.refreshAsync(context)
    }
    SwitchRow(
        Icons.Rounded.PhoneAndroid, tr(R.string.device_content),
        tr(R.string.device_content_desc),
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
            Icons.Rounded.MergeType, tr(R.string.device_how_show),
            listOf(DeviceMediaMode.MIXED to tr(R.string.device_mixed), DeviceMediaMode.SEPARATE to tr(R.string.device_separate)),
            mode, apply,
            optionDescription = {
                if (it == DeviceMediaMode.MIXED) tr(R.string.device_mixed_desc) else tr(R.string.device_separate_desc)
            },
        )
    }
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(tr(R.string.device_how_show_q)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeOption(Icons.Rounded.MergeType, tr(R.string.device_mixed), tr(R.string.device_mixed_long)) {
                        apply(DeviceMediaMode.MIXED); choosing = false
                    }
                    ModeOption(Icons.Rounded.FolderOpen, tr(R.string.device_separate), tr(R.string.device_separate_long)) {
                        apply(DeviceMediaMode.SEPARATE); choosing = false
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text(tr(R.string.action_cancel)) } },
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
            confirmButton = { TextButton(onClick = { open = false }) { Text(tr(R.string.action_close)) } },
        )
    }
}

/**
 * Компактная строка выбора языков: текущий выбор — под названием,
 * по нажатию — список всех языков YouTube с поиском и галочками.
 */
@Composable
private fun LanguageRow(
    icon: ImageVector,
    title: String,
    summary: String,
    hint: String,
    selected: Set<String>,
    max: Int,
    onChange: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    SettingRow(icon, title, summary, onClick = { open = true }) {
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
    if (!open) return
    var query by remember { mutableStateOf("") }
    // Выбранные — сверху (порядок фиксируется при открытии, чтобы строки не прыгали), остальные — по алфавиту
    val ordered = remember {
        val names = DOWNLOAD_LANGUAGE_CODES.associateWith { languageName(it) }
        val (sel, rest) = DOWNLOAD_LANGUAGE_CODES.partition { it in selected }
        (sel.sortedBy { names[it] } + rest.sortedBy { names[it] }).map { it to names.getValue(it) }
    }
    val shown = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) ordered
        else ordered.filter { (code, name) -> name.contains(q, ignoreCase = true) || code.startsWith(q, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(title) },
        text = {
            Column {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(tr(R.string.search_language)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    shape = AppShapes.Card,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(shown, key = { it.first }) { (code, name) ->
                        val checked = code in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(AppShapes.Thumb)
                                .clickable {
                                    when {
                                        checked -> onChange(selected - code)
                                        selected.size >= max ->
                                            Toast.makeText(context, tr(R.string.max_three), Toast.LENGTH_SHORT).show()
                                        else -> onChange(selected + code)
                                    }
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text(tr(R.string.done)) } },
        dismissButton = if (selected.isNotEmpty()) {
            { TextButton(onClick = { onChange(emptySet()) }) { Text(tr(R.string.action_clear_all)) } }
        } else null,
    )
}
