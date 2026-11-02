package com.offplayer.app.ui.youtube

import com.offplayer.app.ui.stableStatusBarsPadding
import com.offplayer.app.tr
import com.offplayer.app.R

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.offplayer.app.playback.MusicController
import com.offplayer.app.video.VideoController
import com.offplayer.app.youtube.NpSource
import com.offplayer.app.youtube.OnlineItem
import com.offplayer.app.youtube.OnlineStore
import com.offplayer.app.youtube.OnlineTab

/** Название вкладки-назначения онлайн-плейлиста. */
fun tabLabel(t: OnlineTab): String = when (t) {
    OnlineTab.MUSIC -> tr(R.string.tab_music)
    OnlineTab.PODCASTS -> tr(R.string.tab_podcasts)
    OnlineTab.VIDEO -> tr(R.string.tab_video)
}

/** «Сохранить в…»: онлайн-плейлисты с отметками и создание нового — как в YouTube. */
@Composable
fun SaveToPlaylistDialog(item: OnlineItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val playlists by OnlineStore.playlists.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    // В какую вкладку попадёт новый плейлист: от неё зависит, как он играет
    var tab by remember { mutableStateOf(OnlineTab.VIDEO) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.yt_save_to)) },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(playlists, key = { it.id }) { p ->
                        val checked = p.items.any { it.ytId == item.ytId }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { OnlineStore.toggle(p.id, item) }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { OnlineStore.toggle(p.id, item) })
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(tabLabel(p.tab), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (playlists.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(tr(R.string.new_playlist)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OnlineTab.entries.forEach { t ->
                        androidx.compose.material3.FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(tabLabel(t)) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val p = OnlineStore.create(name, item, tab)
                Toast.makeText(context, tr(R.string.yt_saved, p.name), Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text(tr(R.string.action_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.done)) } },
    )
}

/** Список онлайн-плейлистов — раздел «Онлайн» во вкладках «Музыка» и «Видео». */
@Composable
fun OnlinePlaylistsList(query: String, tab: OnlineTab, onOpen: (String) -> Unit) {
    val playlists by OnlineStore.playlists.collectAsStateWithLifecycle()
    val shown = playlists.filter { it.tab == tab && (query.isBlank() || it.name.contains(query, true)) }
    if (shown.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Rounded.Cloud, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(12.dp))
            Text(tr(R.string.yt_online_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(shown, key = { it.id }) { p ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(p.id) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    p.items.firstOrNull()?.let { AsyncImage(it.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tr(R.string.yt_online_count, p.items.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Онлайн-плейлист: в «Музыке» играет звуком в музыкальном плеере, в «Видео» — в видеоплеере.
 * Потоки получаются при воспроизведении, поэтому список стартует сразу.
 */
@Composable
fun OnlinePlaylistScreen(id: String, onBack: () -> Unit) {
    val playlists by OnlineStore.playlists.collectAsStateWithLifecycle()
    val p = playlists.firstOrNull { it.id == id }
    BackHandler { onBack() }
    if (p == null) { onBack(); return }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    // Вкладка плейлиста решает, как он играет: «Видео» — с картинкой, «Музыка» — звуком, «Подкасты» — как подкаст
    val play = { index: Int ->
        when (p.tab) {
            OnlineTab.VIDEO -> VideoController.play(p.items.map(OnlineStore::video), index, p.name)
            else -> MusicController.playTracks(p.items.map { OnlineStore.track(it, podcast = p.tab == OnlineTab.PODCASTS) }, startIndex = index, respectOrder = true)
        }
    }
    Column(Modifier.fillMaxSize().then(Modifier.stableStatusBarsPadding())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr(R.string.action_back)) }
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tr(R.string.yt_online_count, p.items.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, tr(R.string.more)) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(tr(R.string.rename)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text(tr(R.string.delete_playlist)) }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; OnlineStore.delete(id); onBack() })
                }
            }
        }
        if (p.items.isNotEmpty()) Button(onClick = { play(0) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(tr(R.string.yt_play_all))
        }
        LazyColumn(Modifier.weight(1f)) {
            items(p.items.size, key = { p.items[it].ytId }) { i ->
                OnlineRow(p.items[i], onClick = { play(i) }, onRemove = { OnlineStore.remove(id, p.items[i].ytId) })
            }
        }
    }
    if (renaming) {
        var name by remember { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(tr(R.string.rename)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { OnlineStore.rename(id, name); renaming = false }) { Text(tr(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(tr(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun OnlineRow(item: OnlineItem, onClick: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(item.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(item.channel.ifEmpty { null }, item.durationSec.takeIf { it > 0 }?.let { NpSource.clock(it) }).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, tr(R.string.more)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(tr(R.string.yt_remove_from_playlist)) }, leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null) }, onClick = { menu = false; onRemove() })
            }
        }
    }
}
