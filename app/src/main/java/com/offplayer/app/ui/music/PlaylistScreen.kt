package com.offplayer.app.ui.music

import com.offplayer.app.tr
import com.offplayer.app.trn
import com.offplayer.app.R

import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.offplayer.app.data.MediaRepository
import com.offplayer.app.data.PlaylistStore
import com.offplayer.app.model.Track
import com.offplayer.app.playback.MusicController
import com.offplayer.app.ui.MediaArt
import com.offplayer.app.ui.TextInputDialog
import com.offplayer.app.ui.DeleteRequest
import com.offplayer.app.ui.UiEvents
import com.offplayer.app.ui.TrackRow
import com.offplayer.app.ui.formatDuration
import com.offplayer.app.ui.tracksCount
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlaylistScreen(id: String, currentId: Long?, onBack: () -> Unit) {
    val context = LocalContext.current
    val playlists by PlaylistStore.playlists.collectAsStateWithLifecycle()
    val allTracks by MediaRepository.tracks.collectAsStateWithLifecycle()
    val playlist = playlists.firstOrNull { it.id == id }
    if (playlist == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var items by remember(playlist.trackIds, allTracks) {
        mutableStateOf(playlist.trackIds.mapNotNull { MediaRepository.track(it) })
    }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    // Микс — одна обложка на всю очередь в уведомлении, плейлист — у каждого трека своя
    val cover = if (playlist.isMix) items.firstOrNull()?.uri else null

    val persistOrder = {
        // Треки, которых сейчас нет на устройстве, сохраняем в конце, чтобы не потерять
        val present = items.map { it.id }
        PlaylistStore.setOrder(id, present + playlist.trackIds.filterNot { it in present })
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIdx = items.indexOfFirst { it.id == from.key }
        val toIdx = items.indexOfFirst { it.id == to.key }
        if (fromIdx >= 0 && toIdx >= 0) {
            items = items.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        CollectionTopBar(playlist.name, onBack) {
            PlaylistMenu(
                playlist, onRename = { renaming = true }, onDelete = { deleting = true },
                // Удалить сами файлы треков и затем микс/плейлист
                onDeleteWithTracks = {
                    UiEvents.delete.value = DeleteRequest.tracks(items, playlist.name, after = { PlaylistStore.delete(id) })
                },
            )
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            item(key = "header") {
                CollectionHeader(
                    artUri = items.firstOrNull()?.uri,
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    title = playlist.name,
                    subtitle = "${if (playlist.isMix) tr(R.string.mix) else tr(R.string.playlist)} • ${tracksCount(items.size)} • ${totalDuration(items)}",
                    onPlay = { MusicController.playTracks(items, coverUri = cover) },
                    onShuffle = { MusicController.playTracks(items, shuffle = true, coverUri = cover) },
                ) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            MusicController.playTracks(items, coverUri = cover)
                            MusicController.setRepeatMode(Player.REPEAT_MODE_ALL)
                            Toast.makeText(context, tr(R.string.playlist_repeat_forever), Toast.LENGTH_SHORT).show()
                        }, enabled = items.isNotEmpty()) {
                            Icon(Icons.Rounded.AllInclusive, null)
                            Spacer(Modifier.width(6.dp))
                            Text(tr(R.string.on_repeat))
                        }
                        TextButton(onClick = { adding = true }) {
                            Icon(Icons.Rounded.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text(tr(R.string.add_tracks))
                        }
                    }
                    if (items.size > 1) {
                        Text(
                            tr(R.string.drag_tracks_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(items, key = { it.id }) { t ->
                ReorderableItem(reorderState, key = t.id) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag")
                    Surface(
                        shadowElevation = elevation,
                        color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background,
                    ) {
                        TrackRow(
                            track = t,
                            isCurrent = t.id == currentId,
                            onClick = { MusicController.playTracks(items, items.indexOf(t), coverUri = cover) },
                            leading = {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.draggableHandle(onDragStopped = { persistOrder() }),
                                ) {
                                    Icon(Icons.Rounded.DragHandle, tr(R.string.drag), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            extraMenu = { dismiss ->
                                DropdownMenuItem(
                                    text = { Text(tr(R.string.remove_from_playlist)) },
                                    leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null) },
                                    onClick = {
                                        dismiss()
                                        PlaylistStore.removeTrack(id, t.id)
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    if (renaming) {
        TextInputDialog(tr(R.string.rename_playlist), playlist.name, tr(R.string.action_save),
            onConfirm = { PlaylistStore.rename(id, it) }, onDismiss = { renaming = false })
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(tr(R.string.delete_name_q, playlist.name)) },
            text = { Text(tr(R.string.files_stay)) },
            confirmButton = {
                TextButton(onClick = { deleting = false; PlaylistStore.delete(id); onBack() }) { Text(tr(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(tr(R.string.action_cancel)) } },
        )
    }
    if (adding) {
        AddTracksDialog(
            all = allTracks,
            alreadyIn = playlist.trackIds.toSet(),
            onConfirm = { PlaylistStore.addTracks(id, it) },
            onDismiss = { adding = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTracksDialog(all: List<Track>, alreadyIn: Set<Long>, onConfirm: (List<Long>) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(linkedSetOf<Long>()) }
    val candidates = remember(all, alreadyIn, query) {
        all.filter { it.id !in alreadyIn && (query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true)) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.systemBarsPadding()) {
                TopAppBar(
                    title = { Text(trn(R.plurals.tracks_selected, selected.size)) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, tr(R.string.action_close)) } },
                    actions = {
                        Button(
                            enabled = selected.isNotEmpty(),
                            onClick = { onConfirm(selected.toList()); onDismiss() },
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text(tr(R.string.action_add)) }
                    },
                )
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text(tr(R.string.search)) }, singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LazyColumn(Modifier.fillMaxSize()) {
                    items(candidates, key = { it.id }) { t ->
                        val checked = t.id in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = LinkedHashSet(selected).apply { if (checked) remove(t.id) else add(t.id) }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            MediaArt(t.uri, Modifier.padding(start = 8.dp).size(44.dp))
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${t.artist} • ${formatDuration(t.durationMs)}",
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
