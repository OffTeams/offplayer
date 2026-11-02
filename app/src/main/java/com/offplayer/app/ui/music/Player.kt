package com.offplayer.app.ui.music

import com.offplayer.app.ui.pinchPeek

import com.offplayer.app.tr
import com.offplayer.app.R

import com.offplayer.app.ui.theme.AppShapes
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clipToBounds
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.offplayer.app.data.MediaRepository
import com.offplayer.app.data.AppSettings
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Velocity
import com.offplayer.app.data.MediaRepository.UNKNOWN_ALBUM
import com.offplayer.app.model.MediaThumb
import com.offplayer.app.playback.MusicController
import com.offplayer.app.playback.PlayerUiState
import com.offplayer.app.playback.QueueEntry
import com.offplayer.app.ui.MediaArt
import com.offplayer.app.ui.UiEvents
import com.offplayer.app.ui.formatDuration
import com.offplayer.app.ui.theme.LocalDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun MiniPlayer(state: PlayerUiState, onExpand: () -> Unit) {
    val tracks by MediaRepository.tracks.collectAsStateWithLifecycle()
    val track = remember(state.trackId, tracks) { state.trackId?.let { MediaRepository.track(it) } }
    val artColors = rememberArtColors(track?.uri)
    // Видео вместо обложки: если у трека есть ролик на YouTube и есть интернет; цвета фона — по кадру
    val musicVideo by AppSettings.musicVideo.collectAsStateWithLifecycle()
    val netOnline by com.offplayer.app.data.NetworkState.online.collectAsStateWithLifecycle()
    val videoId = remember(track?.id) { track?.let { com.offplayer.app.youtube.YtLyrics.videoIdOf(it) } }
    val videoOn = musicVideo && videoId != null && netOnline
    var frameColors by remember(track?.id) { mutableStateOf<ArtColors?>(null) }
    val colors = if (videoOn) frameColors ?: artColors else artColors
    val accent = colors.palette(LocalDarkTheme.current).accent
    com.offplayer.app.ui.MiniBar(
        title = state.title,
        subtitle = state.artist,
        progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
        accent = accent,
        onOpen = onExpand,
        // Свайп по мини-плееру: справа налево — следующий трек, слева направо — предыдущий
        onSwipe = { dir -> if (dir > 0) MusicController.next() else MusicController.previous() },
        art = {
            AnimatedContent(
                targetState = track?.uri,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "miniArt",
            ) { uri -> MediaArt(uri, Modifier.size(44.dp), shape = AppShapes.Thumb) }
        },
    ) {
        IconButton(onClick = MusicController::togglePlay) {
            PlayPauseIcon(state.isPlaying, Modifier.size(30.dp))
        }
        IconButton(onClick = MusicController::stop) {
            Icon(Icons.Rounded.Close, tr(R.string.stop_listening))
        }
    }
}

@Composable
fun NowPlayingScreen(state: PlayerUiState, onCollapse: () -> Unit, onStop: () -> Unit = MusicController::stop) {
    val context = LocalContext.current
    val tracks by MediaRepository.tracks.collectAsStateWithLifecycle()
    val track = remember(state.trackId, tracks) { state.trackId?.let { MediaRepository.track(it) } }
    val artColors = rememberArtColors(track?.uri)
    // Видео вместо обложки: если у трека есть ролик на YouTube и есть интернет; цвета фона — по кадру
    val musicVideo by AppSettings.musicVideo.collectAsStateWithLifecycle()
    val netOnline by com.offplayer.app.data.NetworkState.online.collectAsStateWithLifecycle()
    val videoId = remember(track?.id) { track?.let { com.offplayer.app.youtube.YtLyrics.videoIdOf(it) } }
    val videoOn = musicVideo && videoId != null && netOnline
    var frameColors by remember(track?.id) { mutableStateOf<ArtColors?>(null) }
    val colors = if (videoOn) frameColors ?: artColors else artColors
    // Градиент из цветов обложки, уходящий в чёрный (тёмная тема) или белый (светлая)
    val pal = colors.palette(LocalDarkTheme.current)
    val showSleep by AppSettings.showSleepTimer.collectAsStateWithLifecycle()
    val showAddPl by AppSettings.showAddToPlaylist.collectAsStateWithLifecycle()
    val showSource by AppSettings.saveSource.collectAsStateWithLifecycle()
    var scrub by remember { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var drag by remember { mutableFloatStateOf(0f) }
    // Очередь под плеером можно свернуть нажатием на «Очередь • N»
    var queueOpen by remember { mutableStateOf(true) }
    val queueArrow by animateFloatAsState(if (queueOpen) 0f else -90f, label = "queueArrow")
    // Вкладки под плеером: «Очередь» (открыта по умолчанию) и «Текст» — если песня есть на YouTube Music
    var lyricsTab by remember { mutableStateOf(false) }
    val online by com.offplayer.app.data.NetworkState.online.collectAsStateWithLifecycle()
    val lyrics by androidx.compose.runtime.produceState<com.offplayer.app.youtube.Lyrics?>(null, track?.id, online) {
        value = null
        value = if (online && track != null) runCatching { com.offplayer.app.youtube.YtLyrics.forTrack(track) }.getOrNull() else null
    }
    LaunchedEffect(lyrics) { if (lyrics == null) lyricsTab = false }
    // Строка, которую поют сейчас, — как в YouTube Music
    val activeLine = lyrics?.takeIf { it.synced }?.lines?.indexOfLast { (it.startMs ?: Long.MAX_VALUE) <= state.positionMs } ?: -1
    val centerOffset = with(LocalDensity.current) { (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp / 3).roundToPx() }
    LaunchedEffect(activeLine, lyricsTab, queueOpen) {
        if (lyricsTab && queueOpen && activeLine >= 0 && !listState.isScrollInProgress) listState.animateScrollToItem(1 + activeLine, -centerOffset)
    }
    val collapseThreshold = with(LocalDensity.current) { 140.dp.toPx() }
    val duration = state.durationMs.coerceAtLeast(1)
    val progress = (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val onCollapseState by rememberUpdatedState(onCollapse)

    // Направление смены пластинки: вперёд по очереди или назад
    var prevIndex by remember { mutableIntStateOf(state.currentIndex) }
    val forward = state.currentIndex >= prevIndex
    SideEffect { prevIndex = state.currentIndex }

    // Появление пластинки при открытии плеера
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }

    // Свайп вниз сворачивает плеер — когда очередь прокручена к началу (экран плеера целиком виден)
    val collapse = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (drag > 0f && available.y < 0f) {
                    val next = (drag + available.y).coerceAtLeast(0f)
                    val used = next - drag
                    drag = next
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    drag += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drag <= 0f) return Velocity.Zero
                if (drag > collapseThreshold || available.y > 1500f) onCollapseState()
                else animate(drag, 0f, animationSpec = spring(dampingRatio = 0.75f)) { v, _ -> drag = v }
                return available
            }
        }
    }

    // Очередь: стабильные ключи даже для повторяющихся треков (mediaId + номер вхождения)
    var entries by remember(state.queue) {
        val seen = HashMap<String, Int>()
        mutableStateOf(state.queue.map { e ->
            val n = (seen[e.mediaId] ?: 0) + 1
            seen[e.mediaId] = n
            KeyedEntry("${e.mediaId}#$n", e)
        })
    }
    val canReorder = !state.shuffle
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIdx = entries.indexOfFirst { it.key == from.key }
        val toIdx = entries.indexOfFirst { it.key == to.key }
        if (fromIdx >= 0 && toIdx >= 0) {
            entries = entries.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
            MusicController.moveQueueItem(fromIdx, toIdx)
        }
    }

    // Плеер рисуется поверх Scaffold без Surface — задаём цвет содержимого явно
    CompositionLocalProvider(LocalContentColor provides pal.content) {
        BoxWithConstraints(
            Modifier
                .offset { IntOffset(0, drag.roundToInt()) }
                .fillMaxSize()
                .clip(RoundedCornerShape(if (drag > 1f) 24.dp else 0.dp))
                .background(pal.base)
                .clickable(enabled = false) {} // не пропускаем нажатия к экрану под плеером
                .nestedScroll(collapse)
        ) {
            AnimatedBackground(pal, track?.uri, state.isPlaying)

            Column(Modifier.fillMaxSize()) {
            // Шапка закреплена под статус-баром: при прокрутке очереди она не уезжает вверх
            Box(Modifier.statusBarsPadding()) {
            // Шапка
            Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse) { Icon(Icons.Rounded.KeyboardArrowDown, tr(R.string.action_collapse), Modifier.size(32.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (showSource) {
                        NowPlayingSource(track, state.isPlaying, pal)
                    }
                }
                // Видео вместо обложки — включается и выключается здесь же
                if (videoId != null && netOnline) BouncyIconButton(onClick = { AppSettings.setMusicVideo(!musicVideo) }) {
                    Icon(if (musicVideo) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, tr(R.string.music_video))
                }
                if (showAddPl) {
                    BouncyIconButton(onClick = { state.trackId?.let { UiEvents.addToPlaylist.value = listOf(it) } }) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, tr(R.string.to_playlist))
                    }
                }
                if (showSleep) SleepTimerButton(state, pal)
                // Завершить прослушивание — плеер закрывается, очередь очищается
                BouncyIconButton(onClick = onStop) {
                    Icon(Icons.Rounded.Close, tr(R.string.stop_listening))
                }
            }
            }
            BoxWithConstraints(Modifier.weight(1f).clipToBounds()) {
            val pageHeight = maxHeight
            // Обложка (или видео вместо неё) — отдельно, чтобы в альбомной ориентации встать слева от управления
            val cover: @Composable (Modifier) -> Unit = { m ->
                        Box(
                            m
                                .padding(vertical = 8.dp)
                                .graphicsLayer {
                                    val a = appear.value
                                    alpha = a.coerceIn(0f, 1f)
                                    scaleX = 0.6f + 0.4f * a
                                    scaleY = 0.6f + 0.4f * a
                                    rotationZ = (1f - a) * -40f
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            // Видео вместо обложки можно рассмотреть щипком — после отпускания оно возвращается на место
                            if (videoOn) MusicVideo(videoId!!, state.isPlaying, onColors = { frameColors = it },
                                modifier = Modifier.fillMaxSize().then(Modifier.pinchPeek(com.offplayer.app.ui.rememberPeekState())))
                            else CoverCarousel(
                                trackKey = state.mediaId,
                                artUri = track?.uri,
                                title = state.title,
                                artist = state.artist,
                                isPlaying = state.isPlaying,
                                progress = progress,
                                accent = pal.accent,
                                forward = forward,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

            }
            // Альбомная ориентация (телефон боком, планшет): обложка слева, управление и вкладки справа
            val wide = maxWidth > maxHeight * 1.2f
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item(key = "player") {
                    Row(Modifier.fillMaxWidth().height(pageHeight).navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                    if (wide) cover(Modifier.weight(1f).fillMaxHeight())
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = if (wide) Arrangement.Center else Arrangement.Top,
                    ) {
                        if (!wide) cover(Modifier.weight(1f).fillMaxWidth())

                        // Исполнитель уже виден в ссылке-источнике сверху — в панели только название
                        ControlsPanel(
                            state, pal, forward, progress, duration, scrub, { scrub = it }, context,
                            showArtist = !(showSource && track?.channelUrl != null),
                        )

                        // «Очередь • N» и (если есть) «Текст»: нажатие на открытую вкладку сворачивает её,
                        // на другую — плавно сменяет содержимое ниже плеера
                        val hasLyrics = lyrics != null
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                        Row(
                            // С вкладкой «Текст» каждая кнопка — по центру своей половины ширины
                            (if (hasLyrics) Modifier.weight(1f).wrapContentWidth(Alignment.CenterHorizontally) else Modifier)
                                .padding(top = 8.dp, bottom = 6.dp)
                                .clip(CircleShape)
                                .background(if (hasLyrics && queueOpen && !lyricsTab) pal.content.copy(alpha = 0.1f) else Color.Transparent)
                                .clickable {
                                    if (queueOpen && !lyricsTab) {
                                        queueOpen = false
                                        scope.launch { listState.animateScrollToItem(0) }
                                    } else {
                                        // Экран остаётся на месте — очередь просто появляется ниже
                                        lyricsTab = false
                                        queueOpen = true
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, Modifier.size(18.dp), tint = pal.contentSecondary)
                            Spacer(Modifier.width(6.dp))
                            Text(tr(R.string.queue_n, state.queue.size), style = MaterialTheme.typography.labelLarge, color = pal.contentSecondary)
                            Icon(
                                Icons.Rounded.KeyboardArrowDown, if (queueOpen) tr(R.string.queue_collapse) else tr(R.string.queue_show),
                                Modifier.size(18.dp).rotate(if (lyricsTab) -90f else queueArrow), tint = pal.contentSecondary,
                            )
                        }
                        if (hasLyrics) Row(
                            // С вкладкой «Текст» каждая кнопка — по центру своей половины ширины
                            (if (hasLyrics) Modifier.weight(1f).wrapContentWidth(Alignment.CenterHorizontally) else Modifier)
                                .padding(top = 8.dp, bottom = 6.dp)
                                .clip(CircleShape)
                                .background(if (queueOpen && lyricsTab) pal.content.copy(alpha = 0.1f) else Color.Transparent)
                                .clickable {
                                    if (queueOpen && lyricsTab) {
                                        queueOpen = false
                                        scope.launch { listState.animateScrollToItem(0) }
                                    } else {
                                        lyricsTab = true
                                        queueOpen = true
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Lyrics, null, Modifier.size(18.dp), tint = pal.contentSecondary)
                            Spacer(Modifier.width(6.dp))
                            Text(tr(R.string.lyrics), style = MaterialTheme.typography.labelLarge, color = pal.contentSecondary)
                        }
                        }
                    }
                    }
                }

                // Очередь продолжает экран плеера — прокрутка вниз
                // Текст песни вместо очереди: текущая строка яркая, остальные приглушены; нажатие — перемотка к строке
                if (queueOpen && lyricsTab) lyrics?.let { l ->
                    items(l.lines.size, key = { "lyric_$it" }) { i ->
                        val line = l.lines[i]
                        val lineColor by androidx.compose.animation.animateColorAsState(
                            if (!l.synced || i == activeLine) pal.content else pal.content.copy(alpha = 0.4f), label = "lyricColor",
                        )
                        Text(
                            line.text,
                            color = lineColor,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 30.sp,
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .clickable(enabled = line.startMs != null) { line.startMs?.let { MusicController.seekTo(it) } }
                                .padding(horizontal = 28.dp, vertical = 10.dp),
                        )
                    }
                }
                if (queueOpen && !lyricsTab) items(entries, key = { it.key }) { ke ->
                    ReorderableItem(reorderState, key = ke.key, enabled = canReorder) { isDragging ->
                        val isCurrent = ke.entry.itemIndex == state.currentIndex
                        val qTrack = remember(ke.entry.mediaId) { ke.entry.mediaId.toLongOrNull()?.let { MediaRepository.track(it) } }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .clip(AppShapes.Thumb)
                                .background(
                                    when {
                                        isDragging -> pal.content.copy(alpha = 0.14f)
                                        isCurrent -> pal.content.copy(alpha = 0.08f)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { MusicController.skipTo(ke.entry.itemIndex) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (canReorder) {
                                IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                                    Icon(Icons.Rounded.DragHandle, tr(R.string.drag), tint = pal.contentSecondary)
                                }
                            } else {
                                Spacer(Modifier.width(16.dp))
                            }
                            Box(contentAlignment = Alignment.Center) {
                                MediaArt(qTrack?.uri, Modifier.size(44.dp))
                                if (isCurrent) {
                                    Box(
                                        Modifier
                                            .size(44.dp)
                                            .background(Color.Black.copy(alpha = 0.55f), AppShapes.Thumb),
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Icons.Rounded.GraphicEq, null, tint = pal.accent) }
                                }
                            }
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    ke.entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (isCurrent) pal.accent else pal.content,
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                Text(
                                    ke.entry.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = pal.contentSecondary,
                                )
                            }
                            if (!isCurrent) {
                                IconButton(onClick = { MusicController.removeQueueItem(ke.entry.itemIndex) }) {
                                    Icon(Icons.Rounded.Close, tr(R.string.remove_from_queue), tint = pal.contentSecondary)
                                }
                            }
                        }
                    }
                }
                // Без очереди плеер ровно в экран — прокручивать нечего
                if (queueOpen) item(key = "end") { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
            }
            }
            }
        }
    }
}

/**
 * «Сейчас играет трек от:» и под ним — оформленная ссылка-«плашка» на канал автора
 * (или на сам ролик, если канал узнать не удалось). Без интернета плашка остаётся,
 * но с значком «нет сети» и неактивна. У треков без источника шапка пустая.
 */
@Composable
private fun NowPlayingSource(track: com.offplayer.app.model.Track?, isPlaying: Boolean, pal: PlayerPalette) {
    val url = track?.channelUrl ?: return
    val online by com.offplayer.app.data.NetworkState.online.collectAsStateWithLifecycle()
    val isChannel = !url.contains("watch?v=") && !url.contains("youtu.be/")
    // Эквалайзер стоит сбоку от обеих строк — и надписи, и ссылки
    Row(verticalAlignment = Alignment.CenterVertically) {
        EqualizerBars(isPlaying, pal.accent, height = 30.dp, bars = 5, barWidth = 4.dp)
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                tr(R.string.now_playing).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = pal.contentSecondary,
                letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.5f,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            SourceChip(track, url, isChannel, online, pal)
        }
    }
}

/** Оформленная ссылка на источник; без интернета — неактивна и со значком «нет сети». */
@Composable
private fun SourceChip(
    track: com.offplayer.app.model.Track,
    url: String,
    isChannel: Boolean,
    online: Boolean,
    pal: PlayerPalette,
) {
    val context = LocalContext.current
    val alpha = if (online) 1f else 0.55f
    Row(
        Modifier
            .clip(CircleShape)
            .background(pal.content.copy(alpha = 0.06f))
            .border(1.dp, pal.content.copy(alpha = 0.12f), CircleShape)
            .clickable(enabled = online) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure { Toast.makeText(context, tr(R.string.link_open_failed), Toast.LENGTH_SHORT).show() }
            }
            .padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Значок приложения, в котором откроется ссылка (YouTube / YouTube Music)
        SourceAppIcon(url, Modifier.size(20.dp).alpha(alpha))
        Spacer(Modifier.width(6.dp))
        Text(
            track.channel ?: when {
                com.offplayer.app.download.Downloader.isTikTokUrl(url) -> tr(R.string.tiktok_video)
                isChannel -> tr(R.string.author_channel)
                else -> tr(R.string.youtube_video)
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = pal.content.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            if (online) Icons.AutoMirrored.Rounded.OpenInNew else Icons.Rounded.WifiOff,
            if (online) tr(R.string.action_open) else tr(R.string.err_no_internet),
            tint = pal.contentSecondary, modifier = Modifier.size(14.dp),
        )
    }
}
/**
 * Значок приложения, которое откроет ссылку: установленный YouTube Music (для music.youtube.com)
 * или YouTube; если их нет — тот, что система выбрала для ссылок, иначе — нарисованный логотип YouTube.
 */
@Composable
private fun SourceAppIcon(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember(url) {
        val pm = context.packageManager
        val music = url.contains("music.youtube.com")
        val candidates = if (com.offplayer.app.download.Downloader.isTikTokUrl(url)) {
            // TikTok — глобальная и азиатская сборки
            listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
        } else if (music) {
            listOf("com.google.android.apps.youtube.music", "app.revanced.android.apps.youtube.music", "com.google.android.youtube")
        } else {
            listOf("com.google.android.youtube", "app.revanced.android.youtube", "com.vanced.android.youtube")
        }
        val drawable = candidates.firstNotNullOfOrNull { pkg -> runCatching { pm.getApplicationIcon(pkg) }.getOrNull() }
            ?: runCatching {
                val info = pm.resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                info?.activityInfo?.packageName?.takeIf { it != "android" }?.let { pm.getApplicationIcon(it) }
            }.getOrNull()
        drawable?.let { runCatching { it.toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    }
    if (icon != null) {
        androidx.compose.foundation.Image(icon, null, modifier.clip(CircleShape))
    } else {
        Canvas(modifier) {
            // Логотип YouTube: красная скруглённая плашка с белым треугольником
            val h = size.height * 0.72f
            val top = (size.height - h) / 2
            drawRoundRect(Color(0xFFFF0033), topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(size.width, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.3f))
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.40f, top + h * 0.28f)
                lineTo(size.width * 0.66f, top + h * 0.5f)
                lineTo(size.width * 0.40f, top + h * 0.72f)
                close()
            }
            drawPath(path, Color.White)
        }
    }
}

/** «Стеклянная» панель с названием, полосой перемотки и кнопками управления. */
@Composable
private fun ControlsPanel(
    state: PlayerUiState,
    pal: PlayerPalette,
    forward: Boolean,
    progress: Float,
    duration: Long,
    scrub: Float?,
    onScrub: (Float?) -> Unit,
    context: android.content.Context,
    showArtist: Boolean = true,
) {
    val panelShape = AppShapes.Large
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .softGlow(Color.Black, 14.dp, 24.dp, strength = if (pal.dark) 0.9f else 0.35f)
            .clip(panelShape)
            .background(
                if (pal.dark) {
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f)))
                } else {
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.50f)))
                }
            )
            .border(1.dp, pal.content.copy(alpha = if (pal.dark) 0.12f else 0.06f), panelShape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = state.title to state.artist,
            transitionSpec = {
                val dir = if (forward) 1 else -1
                (slideInVertically(tween(400)) { it / 2 * dir } + fadeIn(tween(400))) togetherWith
                    (slideOutVertically(tween(300)) { -it / 2 * dir } + fadeOut(tween(250)))
            },
            modifier = Modifier.fillMaxWidth(),
            label = "title",
        ) { (title, artist) ->
            Column {
                Text(
                    title, maxLines = 1,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.basicMarquee(),
                )
                if (showArtist) {
                    Text(
                        artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        color = pal.contentSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SeekBar(
            progress = progress,
            accent = pal.accent,
            content = pal.content,
            onScrub = onScrub,
            onSeek = { MusicController.seekTo((it * duration).toLong()) },
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                formatDuration(scrub?.let { (it * duration).toLong() } ?: state.positionMs),
                style = MaterialTheme.typography.labelMedium, color = pal.contentSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelMedium, color = pal.contentSecondary)
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToggleButton(Icons.Rounded.Shuffle, tr(R.string.order_shuffle), state.shuffle, pal.accent, MusicController::toggleShuffle)
            BouncyIconButton(onClick = MusicController::previous, size = 60.dp) {
                Icon(Icons.Rounded.SkipPrevious, tr(R.string.previous), Modifier.size(42.dp))
            }
            PlayButton(state.isPlaying, pal)
            BouncyIconButton(onClick = MusicController::next, size = 60.dp) {
                Icon(Icons.Rounded.SkipNext, tr(R.string.next), Modifier.size(42.dp))
            }
            ToggleButton(
                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                tr(R.string.repeat), state.repeatMode != Player.REPEAT_MODE_OFF, pal.accent,
            ) {
                val msg = when (MusicController.cycleRepeat()) {
                    Player.REPEAT_MODE_ALL -> tr(R.string.repeat_all_on)
                    Player.REPEAT_MODE_ONE -> tr(R.string.repeat_one_on)
                    else -> tr(R.string.repeat_off)
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
/** Живой фон: медленно плывущие цветные пятна из палитры обложки + размытая обложка. */
@Composable
private fun AnimatedBackground(pal: PlayerPalette, artUri: android.net.Uri?, isPlaying: Boolean) {
    val transition = rememberInfiniteTransition(label = "bg")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "phase",
    )
    val energy by animateFloatAsState(if (isPlaying) 1f else 0.6f, tween(1200), label = "energy")

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        fun blob(color: Color, cx: Float, cy: Float, radius: Float) = drawCircle(
            brush = Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(cx, cy), radius = radius),
            radius = radius, center = Offset(cx, cy),
        )
        blob(pal.blobTop.copy(alpha = 0.95f), w * (0.25f + 0.15f * cos(phase)), h * (0.22f + 0.08f * sin(phase)), w * 1.0f)
        blob(pal.blobAccent.copy(alpha = pal.blobAccent.alpha * energy), w * (0.8f + 0.18f * sin(phase * 2)), h * (0.45f + 0.1f * cos(phase)), w * 0.75f)
        blob(pal.blobBottom.copy(alpha = 0.85f), w * (0.35f + 0.2f * cos(phase + 2f)), h * (0.88f + 0.05f * sin(phase * 2)), w * 1.1f)
    }
    if (artUri != null) {
        val blurAlpha = when {
            Build.VERSION.SDK_INT < 31 -> if (pal.dark) 0.12f else 0.08f
            pal.dark -> 0.30f
            else -> 0.22f
        }
        AsyncImage(
            model = MediaThumb(artUri, false),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(90.dp)
                .alpha(blurAlpha),
        )
    }
    // Градиент уходит в чёрный (тёмная тема) или в белый (светлая) — внизу под элементами управления
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to pal.base.copy(alpha = 0.20f),
                    0.55f to pal.base.copy(alpha = 0.12f),
                    1f to pal.base.copy(alpha = 0.82f),
                )
            )
    )
}

/** Тонкая полоса прогресса: утолщается при перемотке, ползунок увеличивается. */
@Composable
internal fun SeekBar(progress: Float, accent: Color, content: Color, onScrub: (Float?) -> Unit, onSeek: (Float) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnScrub by rememberUpdatedState(onScrub)
    val shown = if (dragging) dragValue else progress
    val thumb by animateDpAsState(if (dragging) 18.dp else 12.dp, label = "thumb")
    val track by animateDpAsState(if (dragging) 7.dp else 4.dp, label = "track")

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(Unit) {
                detectTapGestures { o -> currentOnSeek((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        dragging = true
                        dragValue = (o.x / size.width).coerceIn(0f, 1f)
                        currentOnScrub(dragValue)
                    },
                    onDragEnd = {
                        dragging = false
                        currentOnSeek(dragValue)
                        currentOnScrub(null)
                    },
                    onDragCancel = {
                        dragging = false
                        currentOnScrub(null)
                    },
                ) { change, dx ->
                    change.consume()
                    dragValue = (dragValue + dx / size.width).coerceIn(0f, 1f)
                    currentOnScrub(dragValue)
                }
            }
    ) {
        val y = size.height / 2
        val th = track.toPx()
        val x = size.width * shown
        drawLine(content.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y), th, StrokeCap.Round)
        if (x > 0f) {
            drawLine(
                Brush.horizontalGradient(listOf(accent.copy(alpha = 0.55f), accent), endX = x.coerceAtLeast(1f)),
                Offset(0f, y), Offset(x, y), th, StrokeCap.Round,
            )
        }
        drawCircle(accent.copy(alpha = 0.35f), radius = thumb.toPx(), center = Offset(x, y))
        drawCircle(content, radius = thumb.toPx() / 2f, center = Offset(x, y))
    }
}

/** Иконка-кнопка, которая пружинисто «проседает» при нажатии. */
@Composable
internal fun BouncyIconButton(onClick: () -> Unit, size: Dp = 48.dp, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.82f else 1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium), label = "press")
    IconButton(onClick = onClick, interactionSource = interaction, modifier = Modifier.size(size).scale(scale)) {
        content()
    }
}

@Composable
private fun ToggleButton(icon: ImageVector, description: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    val tint by androidx.compose.animation.animateColorAsState(if (active) accent else LocalContentColor.current.copy(alpha = 0.85f), label = "tint")
    Box(contentAlignment = Alignment.Center) {
        BouncyIconButton(onClick = onClick) { Icon(icon, description, tint = tint) }
        // Точка под активным режимом
        val dot by animateFloatAsState(if (active) 1f else 0f, label = "dot")
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 2.dp)
                .size(4.dp)
                .scale(dot)
                .background(accent, CircleShape)
        )
    }
}

/** Большая кнопка Play/Pause со свечением в цвет обложки. */
@Composable
private fun PlayButton(isPlaying: Boolean, pal: PlayerPalette) {
    val accent = pal.accent
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "press")
    val glow by animateDpAsState(if (isPlaying) 22.dp else 8.dp, tween(600), label = "glow")
    Box(
        Modifier
            .size(78.dp)
            .scale(scale)
            .shadow(glow, CircleShape, ambientColor = accent, spotColor = accent)
            .clip(CircleShape)
            .background(pal.content)
            .clickable(interactionSource = interaction, indication = null, onClick = MusicController::togglePlay),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides pal.base) {
            PlayPauseIcon(isPlaying, Modifier.size(44.dp))
        }
    }
}

@Composable
internal fun PlayPauseIcon(isPlaying: Boolean, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = isPlaying,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = 0.5f), initialScale = 0.5f) + fadeIn(tween(150))) togetherWith
                (scaleOut(tween(120), targetScale = 0.5f) + fadeOut(tween(120)))
        },
        label = "playPause",
    ) { playing ->
        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) tr(R.string.action_pause) else tr(R.string.action_play), modifier)
    }
}

/** Маленький анимированный эквалайзер в шапке плеера. */
@Composable
internal fun EqualizerBars(
    isPlaying: Boolean,
    color: Color,
    height: Dp = 12.dp,
    bars: Int = 3,
    barWidth: Dp = 3.dp,
) {
    val transition = rememberInfiniteTransition(label = "eq")
    val speeds = listOf(420, 560, 360, 610, 470, 390, 530)
    val anims = (0 until bars).map { i ->
        transition.animateFloat(
            initialValue = 0.2f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(speeds[i % speeds.size], easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bar",
        )
    }
    // Плавный старт и остановка: столбики не прыгают, а постепенно оживают/затихают
    val energy by animateFloatAsState(if (isPlaying) 1f else 0f, tween(700), label = "eqEnergy")
    val rest = listOf(0.3f, 0.5f, 0.38f, 0.55f, 0.42f, 0.33f, 0.48f)
    Row(Modifier.height(height), horizontalArrangement = Arrangement.spacedBy(barWidth * 0.6f), verticalAlignment = Alignment.Bottom) {
        anims.forEachIndexed { i, anim ->
            val r = rest[i % rest.size]
            val h = r + (anim.value - r) * energy
            Box(
                Modifier
                    .width(barWidth)
                    .fillMaxHeight(h)
                    .background(color, RoundedCornerShape(barWidth / 2))
            )
        }
    }
}
private data class KeyedEntry(val key: String, val entry: QueueEntry)

