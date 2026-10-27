package com.offplayer.app.ui.download

import com.offplayer.app.ui.theme.AppShapes
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.animation.AnimatedVisibility
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offplayer.app.data.AppSettings
import com.offplayer.app.download.DownloadKind
import com.offplayer.app.download.DownloadOptions
import com.offplayer.app.download.FailureKind
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.clip
import com.offplayer.app.download.DownloadStatus
import com.offplayer.app.download.DownloadTask
import com.offplayer.app.download.Downloader
import com.offplayer.app.ui.formatDuration
import com.offplayer.app.ui.LocalScreenBackEnabled
import com.offplayer.app.ui.RevealRequest
import com.offplayer.app.ui.UiEvents
import com.offplayer.app.ui.settings.SettingsPage
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalFocusManager
import com.offplayer.app.ui.theme.Accent
import kotlinx.coroutines.launch

val VIDEO_QUALITIES = listOf(0 to "Лучшее", 2160 to "4K", 1440 to "1440p", 1080 to "1080p", 720 to "720p", 480 to "480p", 360 to "360p")
val AUDIO_FORMATS = listOf("mp3" to "MP3", "m4a" to "M4A (AAC)", "opus" to "Opus")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(pendingUrl: String?, onUrlConsumed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tasks by Downloader.tasks.collectAsStateWithLifecycle()
    val audioFormat by AppSettings.audioFormat.collectAsStateWithLifecycle()
    val videoHeight by AppSettings.videoHeight.collectAsStateWithLifecycle()

    var url by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(DownloadKind.AUDIO) }
    var batteryAsk by remember { mutableStateOf(false) }
    // «Сохранить как: Музыку / Подкаст» — выбирает сам пользователь, панель видна сразу
    var asPodcast by remember { mutableStateOf(false) }

    LaunchedEffect(pendingUrl) {
        if (pendingUrl != null) {
            url = pendingUrl
            // Ссылка с music.youtube.com — скорее всего нужен звук
            if (pendingUrl.contains("music.youtube.com")) kind = DownloadKind.AUDIO
            onUrlConsumed()
        }
    }
    LaunchedEffect(Unit) { runCatching { Downloader.ensureInit(context) } }

    val urlValid = Downloader.isSupportedUrl(url)
    val isPlaylist = urlValid && Downloader.isPlaylistUrl(url)
    // Для страницы плейлиста по умолчанию качаем весь; для видео внутри плейлиста — только его
    var wholePlaylist by remember(url) { mutableStateOf(Downloader.isPlaylistPage(url) && AppSettings.wholePlaylist.value) }


    // Вкладка создаётся заранее (для свайпов), поэтому поле ссылки может получить фокус
    // и вызвать клавиатуру, только когда вкладка реально на экране и поверх нет плеера
    val pageActive = LocalScreenBackEnabled.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(pageActive) { if (!pageActive) focusManager.clearFocus(force = true) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            TopAppBar(
                expandedHeight = 52.dp, // компактнее стандартных 64dp
                title = { Text("Загрузки", fontWeight = FontWeight.Bold) },
                actions = {
                    if (tasks.any { it.status != DownloadStatus.QUEUED && it.status != DownloadStatus.RUNNING }) {
                        TextButton(onClick = Downloader::clearFinished) { Text("Очистить") }
                    }
                    IconButton(onClick = { UiEvents.openSettings.value = SettingsPage.ROOT }) {
                        Icon(Icons.Rounded.Settings, "Настройки")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Ссылка на YouTube или YouTube Music") },
                    placeholder = { Text("https://youtu.be/…") },
                    leadingIcon = { Icon(Icons.Rounded.Link, null) },
                    trailingIcon = {
                        if (url.isEmpty()) {
                            IconButton(onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                                if (text.isNullOrBlank()) {
                                    Toast.makeText(context, "Буфер обмена пуст", Toast.LENGTH_SHORT).show()
                                } else {
                                    url = Regex("""https?://\S+""").find(text)?.value ?: text.trim()
                                }
                            }) { Icon(Icons.Rounded.ContentPaste, "Вставить") }
                        } else {
                            IconButton(onClick = { url = "" }) { Icon(Icons.Rounded.Close, "Очистить") }
                        }
                    },
                    isError = url.isNotBlank() && !urlValid,
                    supportingText = if (url.isNotBlank() && !urlValid) {
                        { Text("Поддерживаются ссылки youtube.com, youtu.be и music.youtube.com") }
                    } else null,
                    singleLine = true,
                    shape = AppShapes.Card,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties { canFocus = pageActive },
                )
                Spacer(Modifier.height(12.dp))

                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = kind == DownloadKind.AUDIO,
                        onClick = { kind = DownloadKind.AUDIO },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        icon = { Icon(Icons.Rounded.MusicNote, null, Modifier.size(18.dp)) },
                    ) { Text("Аудио") }
                    SegmentedButton(
                        selected = kind == DownloadKind.VIDEO,
                        onClick = { kind = DownloadKind.VIDEO },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        icon = { Icon(Icons.Rounded.Movie, null, Modifier.size(18.dp)) },
                    ) { Text("Видео") }
                }
                Spacer(Modifier.height(12.dp))

                Text(
                    if (kind == DownloadKind.AUDIO) "Формат аудио" else "Качество видео",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (kind == DownloadKind.AUDIO) {
                    items(AUDIO_FORMATS) { (value, label) ->
                        OptionChip(label, audioFormat == value) { AppSettings.setAudioFormat(value) }
                    }
                } else {
                    items(VIDEO_QUALITIES) { (value, label) ->
                        OptionChip(label, videoHeight == value) { AppSettings.setVideoHeight(value) }
                    }
                }
            }
            // «Сохранить как» — для аудио всегда (кроме плейлиста целиком: он сохраняется миксом)
            AnimatedVisibility(visible = kind == DownloadKind.AUDIO && !(isPlaylist && wholePlaylist)) {
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Сохранить как",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OptionChip("Музыку", !asPodcast) { asPodcast = false }
                        OptionChip("Подкаст", asPodcast) { asPodcast = true }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(8.dp))
                // Плейлист целиком: видео → список, музыка → микс
                if (isPlaylist) {
                    OptionSwitch(
                        title = "Скачать весь плейлист",
                        subtitle = if (kind == DownloadKind.AUDIO) "Сохранится как микс в разделе «Музыка → Миксы»"
                        else "Сохранится как отдельный список в разделе «Видео → Списки»",
                        checked = wholePlaylist,
                        onChange = { wholePlaylist = it },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val link = url
                        val wholeList = isPlaylist && wholePlaylist
                        val start = { podcast: Boolean ->
                        Downloader.enqueue(
                            context, link, kind, audioFormat, videoHeight,
                            DownloadOptions(
                                podcast = podcast,
                                playlist = isPlaylist && wholePlaylist,
                                chapters = kind == DownloadKind.VIDEO && AppSettings.saveChapters.value,
                                sponsorBlock = kind == DownloadKind.VIDEO && AppSettings.sbOnDownload.value,
                                extraAudioLangs = if (kind == DownloadKind.VIDEO) AppSettings.extraAudioLangs.value.toList() else emptyList(),
                                subLangs = if (kind == DownloadKind.VIDEO) AppSettings.subLangs.value.toList() else emptyList(),
                                autoSubs = AppSettings.autoSubs.value,
                            ),
                        )
                        url = ""
                        if (BackgroundWork.shouldAsk(context)) batteryAsk = true
                        }
                        start(kind == DownloadKind.AUDIO && !wholeList && asPodcast)
                        asPodcast = false // следующая загрузка — снова в музыку, пока не выбрано иное
                    },
                    enabled = urlValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface),
                ) {
                    Icon(Icons.Rounded.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (kind == DownloadKind.AUDIO) "Скачать аудио" else "Скачать видео", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Скачивайте только то, на что у вас есть права.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
        items(tasks.asReversed(), key = { it.id }) { task ->
            DownloadCard(task, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
    }
    if (batteryAsk) BackgroundWorkDialog(onDismiss = { batteryAsk = false })

}

/** Работа загрузок в фоне: исключение из экономии батареи (иначе система «замораживает» загрузку). */
private object BackgroundWork {
    fun isExempt(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** Спрашиваем один раз; если пользователь отказался — больше не надоедаем. */
    fun shouldAsk(context: Context): Boolean =
        !isExempt(context) && !context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("battery_asked", false)

    fun markAsked(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("battery_asked", true).apply()
}

@Composable
private fun BackgroundWorkDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val close = { BackgroundWork.markAsked(context); onDismiss() }
    val isXiaomi = Build.MANUFACTURER.equals("Xiaomi", true) || Build.MANUFACTURER.equals("Redmi", true) || Build.MANUFACTURER.equals("POCO", true)
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Загрузка в фоне") },
        text = {
            Text(
                "Чтобы загрузка не останавливалась, когда вы уходите из приложения или гаснет экран, " +
                    "разрешите OFFPlayer работать без ограничений батареи." +
                    if (isXiaomi) "\n\nНа Xiaomi также откройте «Настройки приложения» → «Контроль активности» и выберите «Нет ограничений»." else ""
            )
        },
        confirmButton = {
            TextButton(onClick = {
                close()
                runCatching {
                    @SuppressLint("BatteryLife")
                    val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                    context.startActivity(i)
                }.onFailure {
                    runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                }
            }) { Text("Разрешить") }
        },
        dismissButton = {
            if (isXiaomi) {
                TextButton(onClick = {
                    close()
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }
                }) { Text("Настройки приложения") }
            } else {
                TextButton(onClick = close) { Text("Не сейчас") }
            }
        },
    )
}

/** Длинное аудио (> 5 мин): куда сохранить — в «Музыку» или в «Подкасты». */
@Composable
private fun PlacementChoice(task: DownloadTask) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(top = 10.dp)) {
        Text(
            if (task.longAudioIds.size > 1) "Это длинные аудио. Куда их сохранить?" else "Это длинное аудио. Куда его сохранить?",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedButton(
                onClick = { scope.launch { Downloader.placeLongAudio(context, task.id, podcast = false) } },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.MusicNote, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("В музыку")
            }
            Button(
                onClick = { scope.launch { Downloader.placeLongAudio(context, task.id, podcast = true) } },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface),
            ) {
                Icon(Icons.Rounded.Podcasts, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("В подкасты")
            }
        }
    }
}

/**
 * Подсказка при ошибке: если, вероятно, устарел плагин — «Обновить и повторить»;
 * если не помогло и это — просьба сообщить разработчику и копирование отчёта.
 */
@Composable
private fun FailureHelp(task: DownloadTask) {
    val context = LocalContext.current
    val (text, action) = when (task.failure) {
        FailureKind.PLUGIN ->
            "Возможно, устарел плагин загрузки (YouTube часто меняет сайт). Попробуйте принудительно обновить его." to "Обновить и повторить"
        FailureKind.AFTER_UPDATE ->
            "Не удалось скачать даже после обновления плагина. Пожалуйста, сообщите об этом разработчику — приложите отчёт." to "Скопировать отчёт"
        else -> return
    }
    Spacer(Modifier.height(10.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), AppShapes.Thumb)
            .padding(12.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (task.failure == FailureKind.PLUGIN) {
                    Downloader.updateAndRetry(context, task.id)
                } else {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("OFFPlayer", Downloader.report(context, task)))
                    Toast.makeText(context, "Отчёт скопирован — отправьте его разработчику", Toast.LENGTH_LONG).show()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(if (task.failure == FailureKind.PLUGIN) Icons.Rounded.Update else Icons.Rounded.ContentPaste, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(action)
        }
    }
}

@Composable
private fun OptionSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(AppShapes.Card)
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.inverseSurface, selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface),
    )
}

@Composable
private fun DownloadCard(task: DownloadTask, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Готовая загрузка ведёт к результату: трек/видео с подсветкой, микс/список открывается
    val result = task.result.takeIf { task.status == DownloadStatus.DONE }
    Card(
        onClick = { result?.let { UiEvents.reveal.value = RevealRequest(it.kind, it.id) } },
        enabled = result != null,
        modifier = modifier.fillMaxWidth(),
        // Незавершённые карточки «неактивны» только для нажатия — выглядят как обычно
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp, disabledElevation = 3.dp),
        shape = AppShapes.Card,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) = when (task.status) {
                    DownloadStatus.DONE -> Icons.Rounded.CheckCircle to Color(0xFF4CAF50)
                    DownloadStatus.FAILED -> Icons.Rounded.ErrorOutline to MaterialTheme.colorScheme.error
                    DownloadStatus.QUEUED -> Icons.Rounded.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
                    else -> (if (task.kind == DownloadKind.AUDIO) Icons.Rounded.MusicNote else Icons.Rounded.Movie) to MaterialTheme.colorScheme.onSurface
                }
                Icon(icon, null, tint = tint)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(task.title ?: task.url, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    val status = when (task.status) {
                        DownloadStatus.QUEUED -> "В очереди"
                        DownloadStatus.RUNNING -> buildString {
                            append(task.message ?: "Загрузка…")
                            if (task.progress > 0f) append(" ${(task.progress * 100).toInt()}%")
                            if (task.etaSec > 0) append(" • осталось ${formatDuration(task.etaSec * 1000)}")
                        }
                        else -> task.message.orEmpty()
                    }
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                }
                when (task.status) {
                    DownloadStatus.QUEUED, DownloadStatus.RUNNING ->
                        IconButton(onClick = { Downloader.cancel(task.id) }) { Icon(Icons.Rounded.Close, "Отменить") }
                    DownloadStatus.FAILED, DownloadStatus.CANCELED ->
                        IconButton(onClick = { Downloader.retry(context, task.id) }) { Icon(Icons.Rounded.Refresh, "Повторить") }
                    DownloadStatus.DONE ->
                        IconButton(onClick = { Downloader.remove(task.id) }) { Icon(Icons.Rounded.Close, "Убрать") }
                }
            }
            if (task.status == DownloadStatus.FAILED) FailureHelp(task)
            if (task.status == DownloadStatus.RUNNING) {
                Spacer(Modifier.height(10.dp))
                // Плавное заполнение без точки в конце и без зазора между частями полосы
                val smooth by animateFloatAsState(task.progress, tween(600), label = "progress")
                val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                if (task.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { smooth },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = track,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                        strokeCap = StrokeCap.Round,
                    )
                } else {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().height(4.dp),
                        color = MaterialTheme.colorScheme.primary, trackColor = track, gapSize = 0.dp, strokeCap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}
