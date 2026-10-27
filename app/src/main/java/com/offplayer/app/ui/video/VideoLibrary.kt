package com.offplayer.app.ui.video

import com.offplayer.app.tr
import com.offplayer.app.trn
import com.offplayer.app.R

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offplayer.app.data.MediaRepository
import com.offplayer.app.data.SponsorBlock
import com.offplayer.app.data.VideoLibraryStore
import com.offplayer.app.data.VideoMetaStore
import com.offplayer.app.model.Video
import com.offplayer.app.ui.EmptyState
import com.offplayer.app.ui.MediaArt
import com.offplayer.app.ui.TextInputDialog
import com.offplayer.app.ui.formatDuration
import com.offplayer.app.ui.formatSize
import com.offplayer.app.ui.music.CollectionTopBar
import com.offplayer.app.ui.plural
import com.offplayer.app.ui.theme.AppShapes
import com.offplayer.app.video.VideoController
import sh.calvin.reorderable.ReorderableItem
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import com.offplayer.app.model.MediaThumb
import com.offplayer.app.ui.DeleteRequest
import com.offplayer.app.ui.UiEvents
import sh.calvin.reorderable.rememberReorderableLazyListState

fun videosCount(n: Int) = trn(R.plurals.videos, n)

private fun totalVideoDuration(videos: List<Video>): String {
    val min = (videos.sumOf { it.durationMs } / 60_000).toInt()
    return if (min >= 60) tr(R.string.hours_minutes, min / 60, min % 60) else trn(R.plurals.minutes, min)
}

// ---------- Разделы «Списки» и «Категории» ----------

@Composable
fun VideoListsSection(query: String, onOpen: (String) -> Unit) {
    val lists by VideoLibraryStore.lists.collectAsStateWithLifecycle()
    val videos by MediaRepository.videos.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val shown = remember(lists, query) { lists.filter { query.isBlank() || it.name.contains(query, true) } }

    CollectionList(
        createLabel = tr(R.string.new_list),
        onCreate = { creating = true },
        emptyTitle = tr(R.string.no_lists),
        emptyText = tr(R.string.no_lists_desc),
        isEmpty = shown.isEmpty(),
    ) {
        items(shown, key = { it.id }) { l ->
            val present = remember(l, videos) { l.videoIds.mapNotNull { MediaRepository.video(it) } }
            CollectionRow(present.firstOrNull(), l.name, tr(R.string.list) + " • ${totalVideoDuration(present)}", present.size, Icons.AutoMirrored.Rounded.PlaylistPlay) { onOpen(l.id) }
        }
    }
    if (creating) {
        TextInputDialog(tr(R.string.new_list), "", tr(R.string.action_create), onConfirm = { VideoLibraryStore.createList(it) }, onDismiss = { creating = false })
    }
}

@Composable
fun VideoCategoriesSection(query: String, onOpen: (String) -> Unit) {
    val categories by VideoLibraryStore.categories.collectAsStateWithLifecycle()
    val videos by MediaRepository.videos.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val shown = remember(categories, query) { categories.filter { query.isBlank() || it.name.contains(query, true) } }

    CollectionList(
        createLabel = tr(R.string.new_category),
        onCreate = { creating = true },
        emptyTitle = tr(R.string.no_categories),
        emptyText = tr(R.string.no_categories_desc),
        isEmpty = shown.isEmpty(),
    ) {
        items(shown, key = { it.id }) { c ->
            val inCat = remember(c, videos) { c.videoIds.mapNotNull { MediaRepository.video(it) }.sortedByDescending { it.dateAdded } }
            CollectionRow(inCat.firstOrNull(), c.name, tr(R.string.category) + " • ${totalVideoDuration(inCat)}", inCat.size, Icons.Rounded.Label) { onOpen(c.id) }
        }
    }
    if (creating) {
        TextInputDialog(tr(R.string.new_category), "", tr(R.string.action_create), onConfirm = { VideoLibraryStore.createCategory(it) }, onDismiss = { creating = false })
    }
}

@Composable
private fun CollectionList(
    createLabel: String,
    onCreate: () -> Unit,
    emptyTitle: String,
    emptyText: String,
    isEmpty: Boolean,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val createButton = @Composable {
        OutlinedButton(
            onClick = onCreate,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(6.dp))
            Text(createLabel)
        }
    }
    if (isEmpty) {
        // Пусто: подсказка по центру оставшейся области
        Column(Modifier.fillMaxSize()) {
            createButton()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                EmptyState(Icons.AutoMirrored.Rounded.PlaylistPlay, emptyTitle, emptyText)
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { createButton() }
        content()
    }
}

/**
 * Строка списка как в библиотеке YouTube: превью первого видео со «стопкой» карточек за ним
 * и плашкой с количеством видео, справа — название и описание.
 */
@Composable
private fun CollectionRow(first: Video?, title: String, subtitle: String, count: Int, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StackedThumb(first, count, icon, Modifier.width(168.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(videosCount(count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Превью подборки: две «карточки» за кадром (как стопка плейлиста YouTube) и плашка ≡ N. */
@Composable
private fun StackedThumb(first: Video?, count: Int, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth(0.84f)
                .height(3.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
        )
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .fillMaxWidth(0.92f)
                .height(3.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
        )
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth()) {
            MediaArt(
                first?.uri,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                isVideo = true,
                shape = AppShapes.Thumb,
                icon = icon,
            )
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.8f), AppShapes.Badge)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("$count", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Плашка длительности поверх превью. */
@Composable
fun DurationBadge(durationMs: Long, modifier: Modifier = Modifier) {
    Text(
        formatDuration(durationMs),
        style = MaterialTheme.typography.labelSmall, color = Color.White,
        modifier = modifier
            .padding(4.dp)
            .background(Color.Black.copy(alpha = 0.8f), AppShapes.Badge)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

// ---------- Экран списка ----------

@Composable
fun VideoListScreen(id: String, onBack: () -> Unit) {
    val lists by VideoLibraryStore.lists.collectAsStateWithLifecycle()
    val allVideos by MediaRepository.videos.collectAsStateWithLifecycle()
    val ui by VideoController.ui.collectAsStateWithLifecycle()
    val list = lists.firstOrNull { it.id == id }
    if (list == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var items by remember(list.videoIds, allVideos) { mutableStateOf(list.videoIds.mapNotNull { MediaRepository.video(it) }) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    val persistOrder = {
        val present = items.map { it.id }
        VideoLibraryStore.setListOrder(id, present + list.videoIds.filterNot { it in present })
    }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val f = items.indexOfFirst { it.id == from.key }
        val t = items.indexOfFirst { it.id == to.key }
        if (f >= 0 && t >= 0) items = items.toMutableList().apply { add(t, removeAt(f)) }
    }

    Column(Modifier.fillMaxSize()) {
        CollectionTopBar(list.name, onBack) {
            CollectionMenu(
                onRename = { renaming = true },
                onDelete = { deleting = true },
                deleteLabel = tr(R.string.delete_list),
                onDeleteWithVideos = {
                    UiEvents.delete.value = DeleteRequest.videos(items, list.name, after = { VideoLibraryStore.deleteList(id) })
                },
            )
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            item(key = "header") {
                CollectionHeader(items, list.name, tr(R.string.list), onAdd = { adding = true }, hint = items.size > 1)
            }
            itemsIndexed(items, key = { _, v -> v.id }) { i, v ->
                ReorderableItem(reorderState, key = v.id) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag")
                    Surface(
                        shadowElevation = elevation,
                        color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background,
                        // Долгое нажатие на строку — перетаскивание, как в плейлистах YouTube
                        modifier = Modifier.longPressDraggableHandle(onDragStopped = { persistOrder() }),
                    ) {
                        VideoRow(
                            v,
                            index = i + 1,
                            isCurrent = v.id == ui.videoId,
                            onClick = { VideoController.play(items, i, listTitle = list.name) },
                            removeLabel = tr(R.string.remove_from_list),
                            onRemove = { VideoLibraryStore.removeFromList(id, v.id) },
                        )
                    }
                }
            }
        }
    }

    if (renaming) TextInputDialog(tr(R.string.rename_list), list.name, tr(R.string.action_save), onConfirm = { VideoLibraryStore.renameList(id, it) }, onDismiss = { renaming = false })
    if (deleting) ConfirmDelete(tr(R.string.delete_list_q, list.name), onConfirm = { VideoLibraryStore.deleteList(id); onBack() }, onDismiss = { deleting = false })
    if (adding) AddVideosDialog(allVideos, list.videoIds.toSet(), onConfirm = { VideoLibraryStore.addToList(id, it) }, onDismiss = { adding = false })
}

// ---------- Экран категории ----------

@Composable
fun VideoCategoryScreen(id: String, onBack: () -> Unit) {
    val categories by VideoLibraryStore.categories.collectAsStateWithLifecycle()
    val allVideos by MediaRepository.videos.collectAsStateWithLifecycle()
    val ui by VideoController.ui.collectAsStateWithLifecycle()
    val cat = categories.firstOrNull { it.id == id }
    if (cat == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val items = remember(cat, allVideos) { cat.videoIds.mapNotNull { MediaRepository.video(it) }.sortedByDescending { it.dateAdded } }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        CollectionTopBar(cat.name, onBack) {
            CollectionMenu(
                onRename = { renaming = true },
                onDelete = { deleting = true },
                deleteLabel = tr(R.string.delete_category),
                onDeleteWithVideos = {
                    UiEvents.delete.value = DeleteRequest.videos(items, cat.name, after = { VideoLibraryStore.deleteCategory(id) })
                },
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "header") {
                CollectionHeader(items, cat.name, tr(R.string.category), onAdd = { adding = true })
            }
            itemsIndexed(items, key = { _, v -> v.id }) { i, v ->
                VideoRow(
                    v,
                    index = i + 1,
                    isCurrent = v.id == ui.videoId,
                    onClick = { VideoController.play(items, i, listTitle = cat.name) },
                    removeLabel = tr(R.string.remove_from_category),
                    onRemove = { VideoLibraryStore.setInCategory(id, v.id, false) },
                )
            }
        }
    }

    if (renaming) TextInputDialog(tr(R.string.rename_category), cat.name, tr(R.string.action_save), onConfirm = { VideoLibraryStore.renameCategory(id, it) }, onDismiss = { renaming = false })
    if (deleting) ConfirmDelete(tr(R.string.delete_category_q, cat.name), onConfirm = { VideoLibraryStore.deleteCategory(id); onBack() }, onDismiss = { deleting = false })
    if (adding) AddVideosDialog(allVideos, cat.videoIds, onConfirm = { ids -> ids.forEach { VideoLibraryStore.setInCategory(id, it, true) } }, onDismiss = { adding = false })
}

/**
 * Шапка как у плейлиста YouTube: размытый фон из превью, крупное превью,
 * название, сведения и кнопки «Смотреть всё» / «Перемешать».
 */
@Composable
private fun CollectionHeader(items: List<Video>, title: String, kind: String, onAdd: () -> Unit, hint: Boolean = false) {
    val first = items.firstOrNull()
    val bg = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth()) {
        // Размытый фон из первого видео, плавно переходящий в цвет экрана
        if (first != null) {
            AsyncImage(
                model = MediaThumb(first.uri, isVideo = true),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .blur(48.dp)
                    .alpha(0.55f),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(bg.copy(alpha = 0.2f), bg.copy(alpha = 0.75f), bg)))
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            MediaArt(
                first?.uri,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                isVideo = true,
                shape = AppShapes.Card,
                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                elevation = 12.dp,
            )
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                "$kind • ${videosCount(items.size)} • ${totalVideoDuration(items)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { VideoController.play(items, 0, listTitle = title) },
                    enabled = items.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(4.dp))
                    Text(tr(R.string.watch_all))
                }
                OutlinedButton(
                    onClick = { VideoController.play(items.shuffled(), 0, listTitle = title) },
                    enabled = items.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr(R.string.order_shuffle))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onAdd) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text(tr(R.string.add_videos))
                }
                if (hint) {
                    Text(
                        tr(R.string.hold_to_drag),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Строка видео в подборке: номер, превью 16:9 с длительностью, название и размер. */
@Composable
private fun VideoRow(
    video: Video,
    index: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
    removeLabel: String,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            if (isCurrent) {
                Icon(Icons.Rounded.PlayArrow, tr(R.string.now_playing), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            } else {
                Text("$index", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box {
            MediaArt(video.uri, Modifier.width(140.dp).aspectRatio(16f / 9f), isVideo = true, shape = AppShapes.Thumb)
            DurationBadge(video.durationMs, Modifier.align(Alignment.BottomEnd))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(formatSize(video.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, tr(R.string.more), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(removeLabel) },
                    leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null) },
                    onClick = { menu = false; onRemove() },
                )
                DropdownMenuItem(
                    text = { Text(if (video.isDevice) tr(R.string.delete_from_device) else tr(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; UiEvents.delete.value = DeleteRequest.videos(listOf(video), video.title) },
                )
            }
        }
    }
}

@Composable
private fun CollectionMenu(onRename: () -> Unit, onDelete: () -> Unit, deleteLabel: String, onDeleteWithVideos: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, tr(R.string.more)) }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(tr(R.string.rename)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { open = false; onRename() })
            DropdownMenuItem(text = { Text(deleteLabel) }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { open = false; onDelete() })
            DropdownMenuItem(
                text = { Text(tr(R.string.delete_with_videos), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDeleteWithVideos() },
            )
        }
    }
}

@Composable
private fun ConfirmDelete(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(tr(R.string.videos_stay)) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(tr(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.action_cancel)) } },
    )
}

// ---------- Диалоги ----------

/** «Добавить в список» из меню видео. */
@Composable
fun AddToVideoListDialog(videoIds: List<Long>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lists by VideoLibraryStore.lists.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.add_to_list)) },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(lists, key = { it.id }) { l ->
                        Text(
                            l.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    VideoLibraryStore.addToList(l.id, videoIds)
                                    Toast.makeText(context, tr(R.string.added_to, l.name), Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                .padding(vertical = 14.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                if (lists.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    label = { Text(tr(R.string.new_list)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = newName.isNotBlank(), onClick = {
                val l = VideoLibraryStore.createList(newName, videoIds)
                Toast.makeText(context, tr(R.string.list_created, l.name), Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text(tr(R.string.action_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.action_cancel)) } },
    )
}

/** Отметить категории видео (можно несколько). */
@Composable
fun CategoriesDialog(videoId: Long, onDismiss: () -> Unit) {
    val categories by VideoLibraryStore.categories.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.categories)) },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(categories, key = { it.id }) { c ->
                        val checked = videoId in c.videoIds
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { VideoLibraryStore.setInCategory(c.id, videoId, !checked) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(c.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (categories.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    label = { Text(tr(R.string.new_category)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            if (newName.isNotBlank()) {
                TextButton(onClick = { VideoLibraryStore.createCategory(newName, setOf(videoId)); newName = "" }) { Text(tr(R.string.action_create)) }
            } else {
                TextButton(onClick = onDismiss) { Text(tr(R.string.done)) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddVideosDialog(all: List<Video>, alreadyIn: Set<Long>, onConfirm: (List<Long>) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(linkedSetOf<Long>()) }
    val candidates = remember(all, alreadyIn, query) {
        all.filter { it.id !in alreadyIn && (query.isBlank() || it.title.contains(query, true)) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.systemBarsPadding()) {
                TopAppBar(
                    title = { Text(trn(R.plurals.tracks_selected, selected.size)) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, tr(R.string.action_close)) } },
                    actions = {
                        Button(enabled = selected.isNotEmpty(), onClick = { onConfirm(selected.toList()); onDismiss() }, modifier = Modifier.padding(end = 8.dp)) {
                            Text(tr(R.string.action_add))
                        }
                    },
                )
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text(tr(R.string.search)) }, singleLine = true,
                    shape = AppShapes.Card,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LazyColumn(Modifier.fillMaxSize()) {
                    items(candidates, key = { it.id }) { v ->
                        val checked = v.id in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selected = LinkedHashSet(selected).apply { if (checked) remove(v.id) else add(v.id) } }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            MediaArt(v.uri, Modifier.padding(start = 8.dp).width(96.dp).aspectRatio(16f / 9f), isVideo = true, shape = AppShapes.Thumb)
                            Text(
                                v.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Загрузить отрезки SponsorBlock для уже скачанного видео (нужен ID ролика). */
suspend fun refreshSponsorBlock(context: Context, video: Video) {
    val youtubeId = VideoMetaStore.get(video.id)?.youtubeId ?: video.youtubeIdFromName
    if (youtubeId == null) {
        Toast.makeText(context, tr(R.string.sb_no_id), Toast.LENGTH_SHORT).show()
        return
    }
    val segments = SponsorBlock.fetch(youtubeId)
    if (segments == null) {
        Toast.makeText(context, tr(R.string.sb_no_connection), Toast.LENGTH_SHORT).show()
        return
    }
    VideoMetaStore.updateSegments(video.id, youtubeId, segments)
    val msg = if (segments.isEmpty()) tr(R.string.sb_none_found)
    else tr(R.string.sb_saved_n, segments.size)
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
