package com.offplayer.app.ui

import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.composed
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import com.offplayer.app.tr
import com.offplayer.app.trn
import com.offplayer.app.R

import com.offplayer.app.ui.theme.AppShapes
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.offplayer.app.data.PlaylistStore
import com.offplayer.app.download.DownloadResult
import com.offplayer.app.model.MediaThumb
import com.offplayer.app.model.Track
import com.offplayer.app.playback.MusicController
import kotlinx.coroutines.flow.MutableStateFlow
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> tr(R.string.size_gb, bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> tr(R.string.size_mb, bytes / (1L shl 20).toDouble())
    else -> tr(R.string.size_kb, bytes / 1024.0)
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    val word = when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
    return "$n $word"
}

fun tracksCount(n: Int) = trn(R.plurals.tracks, n)

/**
 * Запрос на удаление файлов. Свои файлы приложения удаляются напрямую,
 * файлы устройства — через системный запрос MediaStore.
 * after — что сделать после успешного удаления (например, удалить сам микс/список).
 */
data class DeleteRequest(
    val ids: List<Long>,
    val deviceUris: List<Uri>,
    val title: String,
    val isVideo: Boolean,
    val after: (() -> Unit)? = null,
) {
    val count: Int get() = ids.size

    companion object {
        fun tracks(tracks: List<Track>, title: String, after: (() -> Unit)? = null) =
            DeleteRequest(tracks.map { it.id }, tracks.filter { it.isDevice }.map { it.uri }, title, false, after)

        fun videos(videos: List<com.offplayer.app.model.Video>, title: String, after: (() -> Unit)? = null) =
            DeleteRequest(videos.map { it.id }, videos.filter { it.isDevice }.map { it.uri }, title, true, after)
    }
}

/** Итог загрузки для плашки внутри приложения. */
data class DownloadDoneEvent(val title: String, val message: String, val success: Boolean, val result: DownloadResult?)

/** Переход к результату загрузки (по нажатию на уведомление). */
data class RevealRequest(val kind: DownloadResult.Kind, val id: String, val at: Long = System.nanoTime())

/**
 * false, когда экран вкладки не виден (другая страница пейджера) или поверх открыт плеер:
 * тогда его обработчики «Назад» не должны срабатывать.
 */
val LocalScreenBackEnabled = compositionLocalOf { true }

/** Глобальные запросы к диалогам, которые показываются в MainScreen. */
object UiEvents {
    val addToPlaylist = MutableStateFlow<List<Long>?>(null)
    val delete = MutableStateFlow<DeleteRequest?>(null)
    /** Окно «Где хранить» для файлов медиатеки (id своих записей). */
    val move = MutableStateFlow<List<Long>?>(null)
    val reveal = MutableStateFlow<RevealRequest?>(null)
    val openDownloads = MutableStateFlow(0)
    /** Загрузка завершилась, пока приложение открыто, — показать плашку внутри приложения. */
    val downloadDone = kotlinx.coroutines.flow.MutableSharedFlow<DownloadDoneEvent>(extraBufferCapacity = 4)

    /** Открыто ли приложение на экране (тогда системные уведомления о загрузках не нужны). */
    @Volatile var appVisible = false

    /** Открыть настройки (на нужной странице). */
    val openSettings = MutableStateFlow<com.offplayer.app.ui.settings.SettingsPage?>(null)
}

@Composable
fun MediaArt(
    uri: Uri?,
    modifier: Modifier = Modifier,
    isVideo: Boolean = false,
    shape: Shape = AppShapes.Thumb,
    icon: ImageVector = if (isVideo) Icons.Rounded.Movie else Icons.Rounded.MusicNote,
    /** Тень под обложкой — придаёт объём спискам и сеткам. */
    elevation: Dp = 0.dp,
    /** Щипок — рассмотреть обложку (увеличить или отдалить, после отпускания — на место). */
    peek: Boolean = true,
) {
    PeekBox(modifier.then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, clip = false) else Modifier), enabled = peek) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxSize(0.45f),
        )
        if (uri != null && isVideo) {
            // Кадр целиком (вертикальные и квадратные видео не обрезаются),
            // а поля по бокам — то же изображение, размытое и растянутое на всю карточку
            AsyncImage(
                model = MediaThumb(uri, true),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .blur(24.dp)
                    .drawWithContent {
                        drawContent()
                        drawRect(Color.Black.copy(alpha = 0.25f))
                    },
            )
            AsyncImage(
                model = MediaThumb(uri, true),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.matchParentSize(),
            )
        } else if (uri != null) {
            AsyncImage(
                model = MediaThumb(uri, isVideo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
    }
}

@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable RowScope.() -> Unit)? = null,
    /** Кратковременная подсветка — переход к только что скачанному треку. */
    highlighted: Boolean = false,
    /** Режим выбора: null — обычный список, true/false — отмечен ли трек. */
    selected: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
    extraMenu: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit = {},
) {
    val flash by animateColorAsState(
        when {
            selected == true -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            highlighted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            else -> Color.Transparent
        },
        tween(if (selected != null) 150 else 600), label = "flash",
    )
    Row(
        modifier
            .fillMaxWidth()
            .background(flash)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = if (leading != null) 4.dp else 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(this)
        Box(contentAlignment = Alignment.Center) {
            MediaArt(track.uri, Modifier.size(48.dp))
            if (selected == true) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(AppShapes.Thumb)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, tr(R.string.selected), tint = MaterialTheme.colorScheme.onPrimary)
                }
            } else if (isCurrent) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(AppShapes.Thumb)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                track.title,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "${track.artist} • ${formatDuration(track.durationMs)}",
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (selected == null) TrackMenu(track, extraMenu) else Spacer(Modifier.size(48.dp))
    }
}

@Composable
fun TrackMenu(track: Track, extraMenu: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit = {}) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, tr(R.string.more), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(tr(R.string.play_next)) },
                leadingIcon = { Icon(Icons.Rounded.SkipNext, null) },
                onClick = {
                    open = false
                    MusicController.playNext(listOf(track))
                    Toast.makeText(context, tr(R.string.will_play_next), Toast.LENGTH_SHORT).show()
                },
            )
            DropdownMenuItem(
                text = { Text(tr(R.string.add_to_queue)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                onClick = {
                    open = false
                    MusicController.addToQueue(listOf(track))
                    Toast.makeText(context, tr(R.string.added_to_queue), Toast.LENGTH_SHORT).show()
                },
            )
            DropdownMenuItem(
                text = { Text(tr(R.string.add_to_playlist)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                onClick = {
                    open = false
                    UiEvents.addToPlaylist.value = listOf(track.id)
                },
            )
            extraMenu { open = false }
            if (!track.isDevice) StorageMenuItem(listOf(track.id)) { open = false }
            DropdownMenuItem(
                text = { Text(if (track.isDevice) tr(R.string.delete_from_device) else tr(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    open = false
                    UiEvents.delete.value = DeleteRequest.tracks(listOf(track), track.title)
                },
            )
        }
    }
}

/** Пункт меню «Где хранить…» — только для файлов медиатеки (не для файлов устройства). */
@Composable
fun StorageMenuItem(ids: List<Long>, dismiss: () -> Unit) {
    DropdownMenuItem(
        text = { Text(tr(R.string.move_menu)) },
        leadingIcon = { Icon(Icons.Rounded.Storage, null) },
        onClick = {
            dismiss()
            UiEvents.move.value = ids
        },
    )
}

@Composable
fun AddToPlaylistDialog(trackIds: List<Long>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val playlists by PlaylistStore.playlists.collectAsState()
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.add_to_playlist)) },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(playlists, key = { it.id }) { p ->
                        Text(
                            p.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    PlaylistStore.addTracks(p.id, trackIds)
                                    Toast.makeText(context, tr(R.string.added_to, p.name), Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                .padding(vertical = 14.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                if (playlists.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(tr(R.string.new_playlist)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = newName.isNotBlank(),
                onClick = {
                    val p = PlaylistStore.create(newName, trackIds)
                    Toast.makeText(context, tr(R.string.created, p.name), Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
            ) {
                Icon(Icons.Rounded.Add, null)
                Text(tr(R.string.action_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.action_cancel)) } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value); onDismiss() }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.action_cancel)) } },
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        // Заголовок и пояснение строго по центру под значком
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** Фирменный красный — значок YouTube, полоса просмотренного, включённые переключатели. */
val BrandRed = androidx.compose.ui.graphics.Color(0xFFFF0033)

/** Переключатели приложения: включённый — красный, как значок YouTube (текст остаётся серо-голубым). */
@androidx.compose.runtime.Composable
fun appSwitchColors() = androidx.compose.material3.SwitchDefaults.colors(
    checkedTrackColor = BrandRed,
    checkedBorderColor = BrandRed,
    checkedThumbColor = androidx.compose.ui.graphics.Color.White,
)

/**
 * Отступ под строкой состояния, который не пропадает, когда строка скрыта (её прячет открытый видеоплеер).
 * Обычный statusBarsPadding в это время становится нулевым — при сворачивании плеера шапки вкладок
 * на миг залезали под строку состояния.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun androidx.compose.ui.Modifier.stableStatusBarsPadding(): androidx.compose.ui.Modifier = composed {
    windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.Companion.statusBarsIgnoringVisibility)
}

/** То же для TopAppBar (его windowInsets). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@androidx.compose.runtime.Composable
fun stableTopBarInsets(): androidx.compose.foundation.layout.WindowInsets =
    androidx.compose.foundation.layout.WindowInsets.Companion.statusBarsIgnoringVisibility
