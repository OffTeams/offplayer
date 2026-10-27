package com.offplayer.app.ui.video

import com.offplayer.app.ui.theme.AppShapes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.MoreVert
import com.offplayer.app.ui.DeleteRequest
import com.offplayer.app.ui.UiEvents
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.offplayer.app.data.MediaRepository
import com.offplayer.app.model.Video
import com.offplayer.app.ui.EmptyState
import com.offplayer.app.ui.LocalScreenBackEnabled
import com.offplayer.app.ui.MediaArt
import com.offplayer.app.ui.formatDuration
import com.offplayer.app.ui.formatSize
import com.offplayer.app.video.VideoController
import com.offplayer.app.data.VideoMetaStore
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Label
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import com.offplayer.app.data.DeviceMediaMode
import com.offplayer.app.download.DownloadResult
import com.offplayer.app.model.DeviceFolder
import com.offplayer.app.ui.DeviceFolderHeader
import com.offplayer.app.ui.DevicePermissionPrompt
import kotlinx.coroutines.delay

enum class VideoSort(val label: String) { NEWEST("Сначала новые"), OLDEST("Сначала старые"), TITLE("По названию"), LONGEST("Самые длинные"), LARGEST("Самые большие") }

enum class VideoSection(val label: String) { ALL("Все"), LISTS("Списки"), CATEGORIES("Категории"), DEVICE("На устройстве") }

private sealed interface VideoDest {
    data class ListDest(val id: String) : VideoDest
    data class CategoryDest(val id: String) : VideoDest
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen() {
    val context = LocalContext.current
    val videos by MediaRepository.videos.collectAsStateWithLifecycle()
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(VideoSort.NEWEST) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf(VideoSection.ALL) }
    var dest by remember { mutableStateOf<VideoDest?>(null) }
    val deviceMode by AppSettings.deviceMedia.collectAsStateWithLifecycle()
    val sections = remember(deviceMode) {
        VideoSection.entries.filter { it != VideoSection.DEVICE || deviceMode == DeviceMediaMode.SEPARATE }
    }
    LaunchedEffect(sections) { if (section !in sections) section = VideoSection.ALL }
    var highlight by remember { mutableStateOf<Long?>(null) }

    // Переход из уведомления о загрузке: видео — подсветить в «Все», список — открыть
    val reveal by UiEvents.reveal.collectAsStateWithLifecycle()
    LaunchedEffect(reveal) {
        val r = reveal ?: return@LaunchedEffect
        when (r.kind) {
            DownloadResult.Kind.VIDEO -> {
                dest = null; searching = false; query = ""; folder = null
                section = VideoSection.ALL
                highlight = r.id.toLongOrNull()
            }
            DownloadResult.Kind.LIST -> {
                searching = false; query = ""
                section = VideoSection.LISTS
                dest = VideoDest.ListDest(r.id)
            }
            else -> return@LaunchedEffect
        }
        UiEvents.reveal.value = null
    }
    LaunchedEffect(highlight) {
        if (highlight != null) {
            delay(3000)
            highlight = null
        }
    }

    val backEnabled = LocalScreenBackEnabled.current
    BackHandler(backEnabled && dest != null) { dest = null }
    BackHandler(backEnabled && dest == null && searching) { searching = false; query = "" }

    when (val d = dest) {
        is VideoDest.ListDest -> { VideoListScreen(d.id, onBack = { dest = null }); return }
        is VideoDest.CategoryDest -> { VideoCategoryScreen(d.id, onBack = { dest = null }); return }
        null -> Unit
    }

    val folders = remember(videos) { videos.map { it.folder }.distinct().sorted() }
    val shown = remember(videos, folder, sort, query) {
        videos
            .filter { (folder == null || it.folder == folder) && (query.isBlank() || it.title.contains(query, true)) }
            .let { list ->
                when (sort) {
                    VideoSort.NEWEST -> list.sortedByDescending { it.dateAdded }
                    VideoSort.OLDEST -> list.sortedBy { it.dateAdded }
                    VideoSort.TITLE -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                    VideoSort.LONGEST -> list.sortedByDescending { it.durationMs }
                    VideoSort.LARGEST -> list.sortedByDescending { it.size }
                }
            }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            expandedHeight = 52.dp, // компактнее стандартных 64dp
            title = {
                if (searching) {
                    TextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("Поиск видео") }, singleLine = true,
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("Видео", fontWeight = FontWeight.Bold)
                }
            },
            actions = {
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                    Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, "Поиск")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        // Разделы: все видео, списки, категории — и сортировка справа (для раздела «Все»)
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                contentPadding = PaddingValues(start = 16.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(sections) { s -> FolderChip(s.label, section == s) { section = s } }
            }
            AnimatedVisibility(visible = section == VideoSection.ALL, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.padding(end = 4.dp)) { VideoSortMenu(sort, onSort = { sort = it }) }
            }
        }
        Spacer(Modifier.height(4.dp))

        when (section) {
            VideoSection.LISTS -> {
                VideoListsSection(query) { dest = VideoDest.ListDest(it) }
                return@Column
            }
            VideoSection.CATEGORIES -> {
                VideoCategoriesSection(query) { dest = VideoDest.CategoryDest(it) }
                return@Column
            }
            VideoSection.DEVICE -> {
                DeviceVideoSection(query)
                return@Column
            }
            VideoSection.ALL -> Unit
        }

        // Внутри «Все» — фильтр по папкам
        if (folders.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FolderChip("Все папки", folder == null, small = true) { folder = null } }
                items(folders) { f -> FolderChip(f, folder == f, small = true) { folder = f } }
            }
        }

        if (videos.isEmpty()) {
            EmptyState(
                Icons.Rounded.VideoLibrary, "Видео пока нет",
                "Скачайте видео во вкладке «Загрузки» или включите в настройках загрузок контент из памяти устройства.",
            )
            return@Column
        }

        val gridState = rememberLazyGridState()
        LaunchedEffect(highlight, shown) {
            val i = shown.indexOfFirst { it.id == highlight }
            if (highlight != null && i >= 0) gridState.animateScrollToItem(i)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(300.dp),
            state = gridState,
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(shown, key = { _, v -> v.id }) { index, v ->
                VideoCard(v, highlighted = v.id == highlight) { VideoController.play(shown, index) }
            }
        }
    }
}

@Composable
private fun FolderChip(label: String, selected: Boolean, small: Boolean = false, onClick: () -> Unit) {
    if (small) {
        // Второй уровень фильтра (папки) — тональные чипы, чтобы не спорить с разделами
        FilterChip(
            selected = selected, onClick = onClick, label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        )
    } else {
        FilterChip(
            selected = selected, onClick = onClick, label = { Text(label) },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.inverseSurface, selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface),
        )
    }
}

@Composable
private fun VideoCard(video: Video, highlighted: Boolean = false, onClick: () -> Unit) {
    val flash by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
        tween(600), label = "flash",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(flash, AppShapes.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box {
            MediaArt(
                video.uri,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                isVideo = true,
                shape = AppShapes.Card,
                elevation = 6.dp,
            )
            Text(
                formatDuration(video.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.8f), AppShapes.Badge)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        Row(Modifier.padding(top = 10.dp, start = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                val res = if (video.height > 0) " • ${minOf(video.width, video.height)}p" else ""
                Text(
                    "${video.folder} • ${formatSize(video.size)}$res",
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VideoMenu(video)
        }
    }
}

/** «На устройстве»: видео из памяти телефона, сгруппированные по папкам. */
@Composable
private fun DeviceVideoSection(query: String) {
    val context = LocalContext.current
    val all by MediaRepository.deviceVideos.collectAsStateWithLifecycle()
    if (!MediaRepository.hasPermission(context)) {
        DevicePermissionPrompt()
        return
    }
    val groups = remember(all, query) {
        all.filter { query.isBlank() || it.title.contains(query, true) }
            .groupBy { it.deviceFolder ?: DeviceFolder("", "primary:") }
            .toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER) { it.path })
    }
    if (groups.isEmpty()) {
        EmptyState(Icons.Rounded.VideoLibrary, "На устройстве нет видео", "Видео из памяти телефона появятся здесь, сгруппированные по папкам.")
        return
    }
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
        groups.forEach { (folder, list) ->
            val open = folder.documentId !in collapsed
            item(key = "f:" + folder.documentId) {
                DeviceFolderHeader(folder, videosCount(list.size), open) {
                    collapsed = if (open) collapsed + folder.documentId else collapsed - folder.documentId
                }
            }
            if (open) {
                items(list, key = { it.id }) { v ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { VideoController.play(list, list.indexOf(v), listTitle = folder.name) }
                            .padding(start = 36.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            MediaArt(v.uri, Modifier.width(120.dp).aspectRatio(16f / 9f), isVideo = true, shape = AppShapes.Thumb)
                            DurationBadge(v.durationMs, Modifier.align(Alignment.BottomEnd))
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            Text(formatSize(v.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        VideoMenu(v)
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoMenu(video: Video) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var toList by remember { mutableStateOf(false) }
    var categories by remember { mutableStateOf(false) }
    val meta by VideoMetaStore.meta.collectAsStateWithLifecycle()
    val m = meta[video.id]
    val canSponsorBlock = m?.youtubeId != null || video.youtubeIdFromName != null
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, "Ещё", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Добавить в список") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                onClick = { open = false; toList = true },
            )
            DropdownMenuItem(
                text = { Text("Категории…") },
                leadingIcon = { Icon(Icons.Rounded.Label, null) },
                onClick = { open = false; categories = true },
            )
            if (canSponsorBlock) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(if (m?.segmentsFetchedAt ?: 0L > 0) "Обновить SponsorBlock" else "Загрузить SponsorBlock")
                            if (m != null && m.segmentsFetchedAt > 0) {
                                Text(
                                    "Отрезков: ${m.segments.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    leadingIcon = { Icon(Icons.Rounded.FastForward, null) },
                    onClick = {
                        open = false
                        scope.launch { refreshSponsorBlock(context, video) }
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(if (video.isDevice) "Удалить с устройства" else "Удалить", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    open = false
                    UiEvents.delete.value = DeleteRequest.videos(listOf(video), video.title)
                },
            )
        }
    }
    if (toList) AddToVideoListDialog(listOf(video.id), onDismiss = { toList = false })
    if (categories) CategoriesDialog(video.id, onDismiss = { categories = false })
}

@Composable
private fun VideoSortMenu(sort: VideoSort, onSort: (VideoSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Сортировка") }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            VideoSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label) },
                    trailingIcon = { if (s == sort) Icon(Icons.Rounded.Check, null) },
                    onClick = { onSort(s); open = false },
                )
            }
        }
    }
}
