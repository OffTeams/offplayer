package com.offplayer.app.ui.music

import com.offplayer.app.ui.theme.AppShapes
import androidx.compose.ui.draw.clip
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offplayer.app.data.MediaRepository
import com.offplayer.app.data.AppSettings
import com.offplayer.app.data.DeviceMediaMode
import com.offplayer.app.download.DownloadResult
import com.offplayer.app.model.DeviceFolder
import com.offplayer.app.ui.DeviceFolderHeader
import com.offplayer.app.ui.DevicePermissionPrompt
import com.offplayer.app.ui.UiEvents
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import com.offplayer.app.data.PlaylistStore
import com.offplayer.app.model.Playlist
import com.offplayer.app.model.Track
import com.offplayer.app.playback.MusicController
import com.offplayer.app.ui.EmptyState
import com.offplayer.app.ui.LocalScreenBackEnabled
import com.offplayer.app.ui.MediaArt
import com.offplayer.app.ui.TextInputDialog
import com.offplayer.app.ui.TrackRow
import com.offplayer.app.ui.plural
import com.offplayer.app.ui.tracksCount

enum class MusicSection(val label: String) {
    TRACKS("Треки"), ALBUMS("Альбомы"), ARTISTS("Исполнители"), PLAYLISTS("Плейлисты"), MIXES("Миксы"), DEVICE("На устройстве")
}

enum class TrackSort(val label: String) {
    TITLE("Название"), ARTIST("Исполнитель"), ALBUM("Альбом"), DATE_ADDED("Дата добавления"), DURATION("Длительность")
}

sealed interface MusicDest {
    data class AlbumDest(val albumId: Long, val name: String) : MusicDest
    data class ArtistDest(val name: String) : MusicDest
    data class PlaylistDest(val id: String) : MusicDest
}

data class AlbumInfo(val albumId: Long, val name: String, val artist: String, val tracks: List<Track>)
data class ArtistInfo(val name: String, val tracks: List<Track>)

fun List<Track>.sortedBy(sort: TrackSort, descending: Boolean): List<Track> {
    val cmp: Comparator<Track> = when (sort) {
        TrackSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        TrackSort.ARTIST -> compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        TrackSort.ALBUM -> compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.album }.thenBy { it.trackNo }
        TrackSort.DATE_ADDED -> compareBy { it.dateAdded }
        TrackSort.DURATION -> compareBy { it.durationMs }
    }
    return sortedWith(if (descending) cmp.reversed() else cmp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen() {
    val tracks by MediaRepository.tracks.collectAsStateWithLifecycle()
    val player by MusicController.state.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf(MusicSection.TRACKS) }
    var dest by remember { mutableStateOf<MusicDest?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val deviceMode by AppSettings.deviceMedia.collectAsStateWithLifecycle()
    val sections = remember(deviceMode) {
        MusicSection.entries.filter { it != MusicSection.DEVICE || deviceMode == DeviceMediaMode.SEPARATE }
    }
    LaunchedEffect(sections) { if (section !in sections) section = MusicSection.TRACKS }
    var highlight by remember { mutableStateOf<Long?>(null) }

    // Переход из уведомления о загрузке: трек — подсветить в «Треках», микс — открыть
    val reveal by UiEvents.reveal.collectAsStateWithLifecycle()
    LaunchedEffect(reveal) {
        val r = reveal ?: return@LaunchedEffect
        when (r.kind) {
            DownloadResult.Kind.TRACK -> {
                dest = null; searching = false; query = ""
                section = MusicSection.TRACKS
                highlight = r.id.toLongOrNull()
            }
            DownloadResult.Kind.MIX -> {
                searching = false; query = ""
                section = MusicSection.MIXES
                dest = MusicDest.PlaylistDest(r.id)
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

    val current = dest
    if (current != null) {
        CollectionScreen(current, tracks, player.trackId, onBack = { dest = null })
        return
    }

    val filtered = remember(tracks, query) {
        if (query.isBlank()) tracks else tracks.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            expandedHeight = 52.dp, // компактнее стандартных 64dp
            title = {
                if (searching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Поиск музыки") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("Музыка", fontWeight = FontWeight.Bold)
                }
            },
            actions = {
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                    Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, "Поиск")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(sections) { s ->
                FilterChip(
                    selected = section == s,
                    onClick = { section = s },
                    label = { Text(s.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                        selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface,
                    ),
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        if (tracks.isEmpty() && section in listOf(MusicSection.TRACKS, MusicSection.ALBUMS, MusicSection.ARTISTS)) {
            EmptyState(
                Icons.Rounded.LibraryMusic, "Музыки пока нет",
                "Скачайте треки во вкладке «Загрузки» или включите в настройках загрузок контент из памяти устройства.",
            )
            return@Column
        }

        when (section) {
            MusicSection.TRACKS -> TracksSection(filtered, player.trackId, highlight)
            MusicSection.DEVICE -> DeviceMusicSection(query, player.trackId)
            MusicSection.ALBUMS -> AlbumsSection(filtered) { dest = MusicDest.AlbumDest(it.albumId, it.name) }
            MusicSection.ARTISTS -> ArtistsSection(filtered) { dest = MusicDest.ArtistDest(it.name) }
            MusicSection.PLAYLISTS -> PlaylistsSection(query, mixes = false) { dest = MusicDest.PlaylistDest(it.id) }
            MusicSection.MIXES -> PlaylistsSection(query, mixes = true) { dest = MusicDest.PlaylistDest(it.id) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TracksSection(tracks: List<Track>, currentId: Long?, highlight: Long?) {
    var sort by rememberSaveable { mutableStateOf(TrackSort.TITLE) }
    var descending by rememberSaveable { mutableStateOf(false) }
    val sorted = remember(tracks, sort, descending) { tracks.sortedBy(sort, descending) }
    val listState = rememberLazyListState()
    LaunchedEffect(highlight, sorted) {
        val i = sorted.indexOfFirst { it.id == highlight }
        if (highlight != null && i >= 0) listState.animateScrollToItem((i + 1 - 2).coerceAtLeast(0)) // +1 — шапка
    }

    // Выбор нескольких треков: долгое нажатие начинает выбор, затем нажатия отмечают треки
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val selecting = selected.isNotEmpty()
    LaunchedEffect(sorted) { selected = selected.filterTo(HashSet()) { id -> sorted.any { it.id == id } } }
    BackHandler(selecting && LocalScreenBackEnabled.current) { selected = emptySet() }
    val chosen = remember(selected, sorted) { sorted.filter { it.id in selected } }

    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        if (selecting) stickyHeader {
            SelectionBar(
                count = selected.size,
                allSelected = selected.size == sorted.size,
                onClose = { selected = emptySet() },
                onSelectAll = { selected = if (selected.size == sorted.size) emptySet() else sorted.map { it.id }.toSet() },
                onPlay = { MusicController.playTracks(chosen, respectOrder = true); selected = emptySet() },
                onQueue = { MusicController.addToQueue(chosen); selected = emptySet() },
                onAddToPlaylist = { UiEvents.addToPlaylist.value = chosen.map { it.id }; selected = emptySet() },
                onDelete = {
                    UiEvents.delete.value = com.offplayer.app.ui.DeleteRequest.tracks(chosen, tracksCount(chosen.size))
                    selected = emptySet()
                },
            )
        }
        if (!selecting) item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { MusicController.playTracks(sorted, shuffle = true) }) {
                    Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Перемешать")
                }
                Spacer(Modifier.weight(1f))
                SortMenu(sort, descending, onSort = { sort = it }, onToggleDirection = { descending = !descending })
            }
            Text(
                tracksCount(sorted.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        itemsIndexed(sorted, key = { _, t -> t.id }) { index, t ->
            TrackRow(
                t, t.id == currentId,
                onClick = {
                    if (selecting) selected = if (t.id in selected) selected - t.id else selected + t.id
                    else MusicController.playTracks(sorted, index)
                },
                highlighted = t.id == highlight,
                selected = if (selecting) t.id in selected else null,
                onLongClick = { selected = selected + t.id },
            )
        }
    }
}

/** «На устройстве»: треки из памяти телефона, сгруппированные по папкам. */
@Composable
private fun DeviceMusicSection(query: String, currentId: Long?) {
    val context = LocalContext.current
    val all by MediaRepository.deviceTracks.collectAsStateWithLifecycle()
    if (!MediaRepository.hasPermission(context)) {
        DevicePermissionPrompt()
        return
    }
    val groups = remember(all, query) {
        all.filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) }
            .groupBy { it.folder ?: DeviceFolder("", "primary:") }
            .toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER) { it.path })
            .mapValues { (_, list) -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }) }
    }
    if (groups.isEmpty()) {
        EmptyState(Icons.Rounded.LibraryMusic, "На устройстве нет музыки", "Аудиофайлы из памяти телефона появятся здесь, сгруппированные по папкам.")
        return
    }
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
        groups.forEach { (folder, list) ->
            val open = folder.documentId !in collapsed
            item(key = "f:" + folder.documentId) {
                DeviceFolderHeader(folder, tracksCount(list.size), open) {
                    collapsed = if (open) collapsed + folder.documentId else collapsed - folder.documentId
                }
            }
            if (open) {
                itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                    TrackRow(
                        t, t.id == currentId,
                        onClick = { MusicController.playTracks(list, i) },
                        modifier = Modifier.padding(start = 20.dp),
                    )
                }
            }
        }
    }
}

/**
 * Панель выбора: «Быстрый плейлист» (временная очередь из выбранного, без создания плейлиста),
 * в очередь, в плейлист, удалить, выбрать все.
 */
@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit,
) {
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 4.dp) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Отменить выбор") }
                Text("Выбрано: $count", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onSelectAll) {
                    Icon(if (allSelected) Icons.Rounded.RemoveDone else Icons.Rounded.SelectAll, "Выбрать все")
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Быстрый плейлист — от двух треков
                SelectionAction(Icons.Rounded.PlayArrow, "Слушать", enabled = count >= 2, onClick = onPlay, modifier = Modifier.weight(1f))
                SelectionAction(Icons.AutoMirrored.Rounded.QueueMusic, "В очередь", onClick = onQueue, modifier = Modifier.weight(1f))
                SelectionAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "В плейлист", onClick = onAddToPlaylist, modifier = Modifier.weight(1f))
                SelectionAction(Icons.Rounded.Delete, "Удалить", onClick = onDelete, modifier = Modifier.weight(1f), danger = true)
            }
        }
    }
}

@Composable
private fun SelectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier
            .clip(AppShapes.Card)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

@Composable
private fun SortMenu(sort: TrackSort, descending: Boolean, onSort: (TrackSort) -> Unit, onToggleDirection: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(AppShapes.Pill)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(sort.label + if (descending) " ↓" else " ↑", style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            TrackSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label) },
                    trailingIcon = { if (s == sort) Icon(Icons.Rounded.Check, null) },
                    onClick = { onSort(s); open = false },
                )
            }
            DropdownMenuItem(
                text = { Text(if (descending) "По возрастанию" else "По убыванию") },
                onClick = { onToggleDirection(); open = false },
            )
        }
    }
}

@Composable
private fun AlbumsSection(tracks: List<Track>, onOpen: (AlbumInfo) -> Unit) {
    val albums = remember(tracks) {
        tracks.groupBy { it.albumId }.map { (id, list) ->
            AlbumInfo(id, list.first().album, list.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key ?: "", list.sortedBy { it.trackNo })
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.albumId }) { a ->
            Column(Modifier.clickable { onOpen(a) }) {
                MediaArt(a.tracks.first().uri, Modifier.fillMaxWidth().aspectRatio(1f), shape = AppShapes.Card, icon = Icons.Rounded.Album, elevation = 8.dp)
                Spacer(Modifier.height(8.dp))
                Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(
                    "${a.artist} • ${tracksCount(a.tracks.size)}",
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ArtistsSection(tracks: List<Track>, onOpen: (ArtistInfo) -> Unit) {
    val artists = remember(tracks) {
        tracks.groupBy { it.artist }.map { (name, list) -> ArtistInfo(name, list) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(artists, key = { it.name }) { a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(a) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaArt(a.tracks.first().uri, Modifier.size(56.dp), shape = CircleShape, icon = Icons.Rounded.Person, elevation = 4.dp)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text(tracksCount(a.tracks.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PlaylistsSection(query: String, mixes: Boolean, onOpen: (Playlist) -> Unit) {
    val playlists by PlaylistStore.playlists.collectAsStateWithLifecycle()
    val tracks by MediaRepository.tracks.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    // «Плейлисты» — созданные вами, «Миксы» — плейлисты, скачанные целиком с YouTube / YouTube Music
    val shown = remember(playlists, query, mixes) {
        playlists.filter { it.isMix == mixes && (query.isBlank() || it.name.contains(query, true)) }
    }

    if (mixes && shown.isEmpty()) {
        EmptyState(
            Icons.AutoMirrored.Rounded.PlaylistPlay, "Миксов пока нет",
            "Вставьте во вкладке «Загрузки» ссылку на плейлист YouTube или YouTube Music и включите «Скачать весь плейлист» — он появится здесь.",
        )
        return
    }
    if (!mixes && shown.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedButton(
                onClick = { creating = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Новый плейлист")
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                EmptyState(
                    Icons.AutoMirrored.Rounded.PlaylistPlay, "Плейлистов пока нет",
                    "Создайте плейлист и добавляйте в него треки через меню «⋮» у трека.",
                )
            }
        }
        if (creating) {
            TextInputDialog(
                title = "Новый плейлист", initial = "", confirmLabel = "Создать",
                onConfirm = { PlaylistStore.create(it) }, onDismiss = { creating = false },
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!mixes) item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Новый плейлист")
            }
        }
        items(shown, key = { it.id }) { p ->
            val first = remember(p, tracks) { p.trackIds.firstNotNullOfOrNull { MediaRepository.track(it) } }
            Column(Modifier.clickable { onOpen(p) }) {
                MediaArt(first?.uri, Modifier.fillMaxWidth().aspectRatio(1f), shape = AppShapes.Card, icon = Icons.AutoMirrored.Rounded.PlaylistPlay, elevation = 8.dp)
                Spacer(Modifier.height(8.dp))
                Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(
                    "${if (p.isMix) "Микс" else "Плейлист"} • ${tracksCount(p.trackIds.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (creating) {
        TextInputDialog(
            title = "Новый плейлист", initial = "", confirmLabel = "Создать",
            onConfirm = { PlaylistStore.create(it) }, onDismiss = { creating = false },
        )
    }
}

/** Экран альбома / исполнителя / плейлиста. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CollectionScreen(dest: MusicDest, allTracks: List<Track>, currentId: Long?, onBack: () -> Unit) {
    when (dest) {
        is MusicDest.PlaylistDest -> PlaylistScreen(dest.id, currentId, onBack)
        is MusicDest.AlbumDest -> {
            val list = remember(allTracks, dest) {
                allTracks.filter { it.albumId == dest.albumId }.sortedWith(compareBy<Track> { it.trackNo }.thenBy { it.title })
            }
            SimpleCollection(dest.name, list.firstOrNull()?.artist.orEmpty(), list, currentId, Icons.Rounded.Album, onBack, coverUri = list.firstOrNull()?.uri)
        }
        is MusicDest.ArtistDest -> {
            val list = remember(allTracks, dest) {
                allTracks.filter { it.artist == dest.name }.sortedBy(TrackSort.ALBUM, false)
            }
            SimpleCollection(dest.name, "Исполнитель", list, currentId, Icons.Rounded.Person, onBack, round = true)
        }
    }
}

@Composable
private fun SimpleCollection(
    title: String,
    subtitle: String,
    tracks: List<Track>,
    currentId: Long?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onBack: () -> Unit,
    round: Boolean = false,
    /** Обложка альбома — одна для всей очереди в уведомлении. */
    coverUri: android.net.Uri? = null,
) {
    Column(Modifier.fillMaxSize()) {
        CollectionTopBar(title, onBack)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                CollectionHeader(
                    artUri = tracks.firstOrNull()?.uri, icon = icon, round = round,
                    title = title,
                    subtitle = "$subtitle • ${tracksCount(tracks.size)} • ${totalDuration(tracks)}",
                    onPlay = { MusicController.playTracks(tracks, coverUri = coverUri) },
                    onShuffle = { MusicController.playTracks(tracks, shuffle = true, coverUri = coverUri) },
                )
            }
            itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                TrackRow(t, t.id == currentId, onClick = { MusicController.playTracks(tracks, i, coverUri = coverUri) })
            }
        }
    }
}

fun totalDuration(tracks: List<Track>): String {
    val min = (tracks.sumOf { it.durationMs } / 60_000).toInt()
    return if (min >= 60) "${min / 60} ч ${min % 60} мин" else plural(min, "минута", "минуты", "минут")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionTopBar(title: String, onBack: () -> Unit, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}) {
    TopAppBar(
        expandedHeight = 52.dp, // компактнее стандартных 64dp
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun CollectionHeader(
    artUri: android.net.Uri?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    round: Boolean = false,
    extra: @Composable () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MediaArt(
            artUri, Modifier.size(200.dp),
            shape = if (round) CircleShape else AppShapes.Large,
            icon = icon,
            elevation = 18.dp,
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.Button(
                onClick = onPlay,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface),
            ) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(4.dp))
                Text("Слушать")
            }
            OutlinedButton(onClick = onShuffle) {
                Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Перемешать")
            }
        }
        extra()
    }
}

@Composable
fun PlaylistMenu(playlist: Playlist, onRename: () -> Unit, onDelete: () -> Unit, onDeleteWithTracks: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "Ещё") }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Упорядочить…") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, null) },
                onClick = { open = false; sortOpen = true })
            DropdownMenuItem(text = { Text("Переименовать") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                onClick = { open = false; onRename() })
            DropdownMenuItem(text = { Text(if (playlist.isMix) "Удалить микс" else "Удалить плейлист") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                onClick = { open = false; onDelete() })
            DropdownMenuItem(
                text = { Text("Удалить вместе с треками", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDeleteWithTracks() },
            )
        }
        DropdownMenu(sortOpen, onDismissRequest = { sortOpen = false }) {
            com.offplayer.app.data.PlaylistSort.entries.forEach { s ->
                DropdownMenuItem(text = { Text(s.label) }, onClick = {
                    sortOpen = false
                    PlaylistStore.sort(playlist.id, s)
                })
            }
        }
    }
}
