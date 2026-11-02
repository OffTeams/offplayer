package com.offplayer.app.ui

import com.offplayer.app.tr
import com.offplayer.app.R

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offplayer.app.data.LibraryStore
import com.offplayer.app.data.MediaRepository
import com.offplayer.app.data.Storage
import com.offplayer.app.data.StorageLocation
import com.offplayer.app.ui.theme.AppShapes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ход переноса файлов между местами хранения. */
data class MoveProgress(val done: Int, val total: Int, val title: String, val target: StorageLocation)

/**
 * Перенос идёт в своей области, не привязанной к экрану: его не прерывает
 * закрытие окна, поворот экрана или переход на другую вкладку.
 */
object StorageMover {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _progress = MutableStateFlow<MoveProgress?>(null)
    val progress = _progress.asStateFlow()

    fun start(context: Context, ids: List<Long>, target: StorageLocation) {
        if (_progress.value != null) return
        val app = context.applicationContext
        _progress.value = MoveProgress(0, ids.size, "", target)
        scope.launch {
            val result = LibraryStore.relocate(
                ids, target,
                onProgress = { done, total, title -> _progress.value = MoveProgress(done, total, title, target) },
                // Играющий или стоящий в очереди файл переводится на копию до удаления старого
                onMoved = { id, uri ->
                    withContext(Dispatchers.Main) {
                        com.offplayer.app.playback.MusicController.relinkMedia(id.toString(), uri)
                        com.offplayer.app.video.VideoController.relinkMedia(id, uri)
                    }
                },
            )
            MediaRepository.refresh(app)
            _progress.value = null
            withContext(Dispatchers.Main) {
                val text = when {
                    result.failed > 0 -> tr(R.string.move_failed_n, result.failed, result.error.orEmpty())
                    result.moved == 0 -> tr(R.string.move_nothing)
                    else -> tr(R.string.move_done_n, result.moved, target.label)
                }
                Toast.makeText(app, text, Toast.LENGTH_LONG).show()
            }
        }
    }
}

/** Окно «Где хранить»: выбор места для файлов медиатеки и ход переноса. */
@Composable
fun StorageMoveHost() {
    val context = LocalContext.current
    val request by UiEvents.move.collectAsStateWithLifecycle()
    val progress by StorageMover.progress.collectAsStateWithLifecycle()
    var hidden by remember { mutableStateOf(false) }
    if (progress == null) hidden = false

    progress?.let { p ->
        if (!hidden) AlertDialog(
            onDismissRequest = {},
            title = { Text(tr(R.string.move_progress_title, p.target.label)) },
            text = {
                Column {
                    Text(
                        tr(R.string.move_progress, (p.done + 1).coerceAtMost(p.total), p.total),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (p.title.isNotEmpty()) {
                        Text(
                            p.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { if (p.total == 0) 0f else p.done.toFloat() / p.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(tr(R.string.move_progress_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            // Перенос продолжается в фоне — окно можно просто скрыть
            confirmButton = { TextButton(onClick = { hidden = true }) { Text(tr(R.string.action_hide)) } },
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        )
        // Пока идёт перенос, новый не начинается
        if (request != null) UiEvents.move.value = null
        return
    }

    val ids = request ?: return
    val entries = remember(ids) { LibraryStore.all().filter { it.id in ids } }
    if (entries.isEmpty()) {
        UiEvents.move.value = null
        return
    }
    val options = remember { Storage.available(context) }
    val current = remember(entries) { entries.map { LibraryStore.locationOf(it) }.toSet() }
    var chosen by remember { mutableStateOf(current.singleOrNull()?.let { c -> options.firstOrNull { it != c } } ?: options.first()) }
    val size = remember(entries) { entries.sumOf { it.size } }
    val close = { UiEvents.move.value = null }
    val go = {
        close()
        StorageMover.start(context, entries.map { it.id }, chosen)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) go() else Toast.makeText(context, tr(R.string.move_need_permission), Toast.LENGTH_LONG).show()
    }

    AlertDialog(
        onDismissRequest = close,
        title = { Text(tr(R.string.move_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    tr(R.string.files_count, entries.size, formatSize(size)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                options.forEach { loc ->
                    LocationOption(
                        loc = loc,
                        selected = loc == chosen,
                        here = loc in current && current.size == 1,
                        free = remember(loc) { Storage.freeSpace(context, loc) },
                        onClick = { chosen = loc },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !(current.size == 1 && chosen in current),
                onClick = {
                    if (Storage.needsWritePermission(context, chosen)) permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else go()
                },
            ) { Text(tr(R.string.move_action)) }
        },
        dismissButton = { TextButton(onClick = close) { Text(tr(R.string.action_cancel)) } },
    )
}

/** Вариант места хранения: название, описание и свободное место. */
@Composable
fun LocationOption(loc: StorageLocation, selected: Boolean, here: Boolean, free: Long?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(AppShapes.Thumb)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(loc.label, style = MaterialTheme.typography.bodyLarge)
            Text(loc.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val extra = listOfNotNull(
                free?.let { tr(R.string.loc_free, formatSize(it)) },
                if (here) tr(R.string.loc_here) else null,
            ).joinToString(" • ")
            if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
