package com.offplayer.app.ui.podcast

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.rounded.Category
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offplayer.app.data.AppSettings
import com.offplayer.app.data.LibraryStore
import com.offplayer.app.data.MediaRepository
import com.offplayer.app.data.PodcastProgress
import com.offplayer.app.model.Track
import com.offplayer.app.playback.MusicController
import com.offplayer.app.playback.PlayerUiState
import com.offplayer.app.ui.DeleteRequest
import com.offplayer.app.ui.EmptyState
import com.offplayer.app.ui.LocalScreenBackEnabled
import com.offplayer.app.ui.MiniBar
import com.offplayer.app.ui.UiEvents
import com.offplayer.app.ui.formatDuration
import com.offplayer.app.ui.music.EqualizerBars
import com.offplayer.app.ui.music.PlayPauseIcon
import com.offplayer.app.ui.music.PlayerPalette
import com.offplayer.app.ui.music.SeekBar
import com.offplayer.app.ui.music.SleepTimerButton
import com.offplayer.app.ui.theme.AppShapes
import com.offplayer.app.ui.theme.LocalDarkTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------- Тематика и обложка ----------

/** Тематика длинного аудио — по ней рисуется обложка. */
enum class PodcastKind(val label: String, val plural: String, val colors: List<Color>) {
    TALK("Разговорный подкаст", "Разговорные", listOf(Color(0xFF6A3FD0), Color(0xFF2B1B5E))),
    AUDIOBOOK("Аудиокнига", "Аудиокниги", listOf(Color(0xFFB7793A), Color(0xFF4A2C14))),
    NEWS("Новости", "Новости", listOf(Color(0xFF2F6FB0), Color(0xFF13304F))),
    TECH("Техника", "Техника", listOf(Color(0xFF1C9A8A), Color(0xFF0D3B37))),
    GAMING("Игры", "Игры", listOf(Color(0xFFD0405E), Color(0xFF3E1230))),
    ;

    companion object {
        private val keywords = mapOf(
            GAMING to listOf("игр", "game", "gaming", "стрим", "stream", "прохожден", "летсплей", "let's play", "minecraft", "dota",
                "cs2", "playstation", "xbox", "nintendo", "steam", "киберспорт", "esports", "гейм"),
            TECH to listOf("техн", "tech", "iphone", "android", "гаджет", "gadget", "смартфон", "smartphone", "нейросет", " ии ",
                " ai ", "программ", "coding", "code", "разработ", "компьютер", "computer", "apple", "samsung", "it-", " it ", "обзор"),
            NEWS to listOf("новост", "news", "итоги", "сводк", "дайджест", "digest", "выпуск новостей", "политик", "событи", "репортаж"),
            AUDIOBOOK to listOf("аудиокниг", "audiobook", "audio book", "глава", "chapter", "книг", "роман", "рассказ", "повесть",
                "читает", "сказк", "novel"),
        )

        /** Тематика: выбранная вручную, иначе — по названию (и каналу); по умолчанию — разговорный подкаст. */
        fun of(track: Track, overrides: Map<Long, String> = PodcastProgress.kinds.value): PodcastKind {
            overrides[track.id]?.let { name -> entries.firstOrNull { it.name == name }?.let { return it } }
            val text = " ${track.title} ${track.artist} ${track.channel.orEmpty()} ".lowercase()
            return keywords.entries.firstOrNull { (_, words) -> words.any { it in text } }?.key ?: TALK
        }
    }
}

/**
 * Обложка подкаста в стиле длинного аудио: градиент тематики, крупный символ
 * (два микрофона, раскрытая книга, газета, микросхема, геймпад) и мягкие круги-волны.
 */
@Composable
fun PodcastCover(kind: PodcastKind, modifier: Modifier = Modifier, corner: Dp = 16.dp, animate: Boolean = false) {
    val wave by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "wave",
    )
    BoxWithConstraints(
        modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(corner))
            .background(Brush.linearGradient(kind.colors)),
        contentAlignment = Alignment.Center,
    ) {
        val side = minOf(maxWidth, maxHeight)
        // Звуковые волны расходятся из центра (в плеере — анимированно)
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            for (i in 0 until 4) {
                val f = ((i / 4f) + if (animate) wave else 0.15f) % 1f
                drawCircle(
                    Color.White.copy(alpha = 0.16f * (1f - f)),
                    radius = size.minDimension * (0.25f + 0.45f * f),
                    center = c,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.012f),
                )
            }
        }
        val icon = side * 0.42f
        when (kind) {
            PodcastKind.TALK -> Box(contentAlignment = Alignment.Center) {
                // Два микрофона, повёрнутые друг к другу, — разговор
                Icon(Icons.Rounded.Mic, null, tint = Color.White, modifier = Modifier.offset(x = -icon * 0.3f).size(icon).rotate(-14f))
                Icon(Icons.Rounded.Mic, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.offset(x = icon * 0.3f).size(icon).rotate(14f))
            }
            PodcastKind.AUDIOBOOK -> Box(contentAlignment = Alignment.BottomEnd) {
                Icon(Icons.Rounded.AutoStories, null, tint = Color.White, modifier = Modifier.size(icon * 1.15f))
                Icon(
                    Icons.Rounded.Headphones, null, tint = Color(0xFFFFE2B8),
                    modifier = Modifier.size(icon * 0.45f).offset(x = icon * 0.15f, y = icon * 0.1f),
                )
            }
            PodcastKind.NEWS -> Icon(Icons.Rounded.Newspaper, null, tint = Color.White, modifier = Modifier.size(icon * 1.1f))
            PodcastKind.TECH -> Icon(Icons.Rounded.Memory, null, tint = Color.White, modifier = Modifier.size(icon * 1.1f))
            PodcastKind.GAMING -> Icon(Icons.Rounded.SportsEsports, null, tint = Color.White, modifier = Modifier.size(icon * 1.15f))
        }
    }
}

// ---------- Вкладка «Подкасты» ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastScreen() {
    val podcasts by MediaRepository.podcasts.collectAsStateWithLifecycle()
    val positions by PodcastProgress.positions.collectAsStateWithLifecycle()
    val player by MusicController.state.collectAsStateWithLifecycle()
    val overrides by PodcastProgress.kinds.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<PodcastKind?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    BackHandler(LocalScreenBackEnabled.current && searching) { searching = false; query = "" }
    val kinds = remember(podcasts, overrides) { podcasts.associate { it.id to PodcastKind.of(it, overrides) } }
    val shown = remember(podcasts, query, filter, kinds) {
        podcasts.filter {
            (filter == null || kinds[it.id] == filter) &&
                (query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true))
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            expandedHeight = 52.dp,
            title = {
                if (searching) {
                    TextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("Поиск подкастов") }, singleLine = true,
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("Подкасты", fontWeight = FontWeight.Bold)
                }
            },
            actions = {
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                    Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, "Поиск")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        if (podcasts.isEmpty()) {
            EmptyState(
                Icons.Rounded.Podcasts, "Подкастов пока нет",
                "Скачайте длинное аудио во вкладке «Загрузки» и сохраните его как подкаст — оно появится здесь.",
            )
            return@Column
        }
        // Категории: «Все» и только те, что встречаются в списке
        val present = remember(kinds) { PodcastKind.entries.filter { it in kinds.values } }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 4.dp),
        ) {
            item { CategoryChip("Все", filter == null) { filter = null } }
            items(present) { k -> CategoryChip(k.plural, filter == k) { filter = if (filter == k) null else k } }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.id }) { p ->
                PodcastRow(p, kinds[p.id] ?: PodcastKind.TALK, positions[p.id] ?: 0L, isCurrent = player.trackId == p.id)
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
    )
}

@Composable
private fun PodcastRow(track: Track, kind: PodcastKind, positionMs: Long, isCurrent: Boolean) {
    var menu by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    val listened = if (track.durationMs > 0) (positionMs.toFloat() / track.durationMs).coerceIn(0f, 1f) else 0f
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .clickable { MusicController.playPodcast(track) }
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // В списке — настоящее превью аудио; тематическая обложка с анимацией — в плеере
        Box {
            com.offplayer.app.ui.MediaArt(track.uri, Modifier.size(72.dp), shape = AppShapes.Card, icon = Icons.Rounded.Podcasts)
            PodcastCover(kind, Modifier.align(Alignment.BottomEnd).padding(4.dp).size(22.dp), corner = 6.dp)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(
                "${kind.label} • ${track.channel ?: track.artist}",
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        listened >= 0.97f -> "Прослушано"
                        positionMs > 0 -> "Осталось ${formatDuration(track.durationMs - positionMs)}"
                        else -> formatDuration(track.durationMs)
                    },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (positionMs > 0 && listened < 0.97f) {
                    Spacer(Modifier.width(8.dp))
                    LinearProgressIndicator(
                        progress = { listened },
                        modifier = Modifier.width(64.dp).height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        gapSize = 0.dp, drawStopIndicator = {},
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Ещё", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Категория…") },
                    leadingIcon = { Icon(Icons.Rounded.Category, null) },
                    onClick = { menu = false; choosing = true },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; UiEvents.delete.value = DeleteRequest.tracks(listOf(track), track.title) },
                )
            }
        }
    }
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Категория") },
            text = {
                Column {
                    PodcastKind.entries.forEach { k ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(AppShapes.Thumb)
                                .clickable { PodcastProgress.setKind(track.id, k.name); choosing = false }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = k == kind, onClick = null)
                            Spacer(Modifier.width(10.dp))
                            PodcastCover(k, Modifier.size(28.dp), corner = 8.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(k.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text("Закрыть") } },
        )
    }
}

// ---------- Мини-плеер и плеер подкаста ----------

@Composable
fun PodcastMiniPlayer(state: PlayerUiState, track: Track?, onExpand: () -> Unit) {
    val kind = remember(track) { track?.let(PodcastKind::of) ?: PodcastKind.TALK }
    MiniBar(
        title = state.title,
        subtitle = "${kind.label} • ${formatDuration(state.positionMs)} / ${formatDuration(state.durationMs)}",
        progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
        accent = kind.colors.first(),
        onOpen = onExpand,
        art = { com.offplayer.app.ui.MediaArt(track?.uri, Modifier.size(44.dp), shape = AppShapes.Thumb, icon = Icons.Rounded.Podcasts) },
    ) {
        IconButton(onClick = { MusicController.seekBy(-15_000) }) { Icon(Icons.Rounded.Replay, "Назад 15 секунд") }
        IconButton(onClick = MusicController::togglePlay) { PlayPauseIcon(state.isPlaying, Modifier.size(30.dp)) }
        IconButton(onClick = MusicController::stop) { Icon(Icons.Rounded.Close, "Завершить прослушивание") }
    }
}

private val SPEEDS = listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 0.75f)

/**
 * Плеер длинного аудио: тематическая обложка вместо музыкальных стилей,
 * перемотка −15 / +30 секунд, скорость, таймер сна. Позиция запоминается.
 */
@Composable
fun PodcastPlayerScreen(state: PlayerUiState, track: Track?, onCollapse: () -> Unit, onStop: () -> Unit = MusicController::stop) {
    val context = LocalContext.current
    val kind = remember(track) { track?.let(PodcastKind::of) ?: PodcastKind.TALK }
    val dark = LocalDarkTheme.current
    val showSleep by AppSettings.showSleepTimer.collectAsStateWithLifecycle()
    val showSource by AppSettings.saveSource.collectAsStateWithLifecycle()
    val base = if (dark) Color(0xFF0B0B0F) else Color(0xFFF6F5FA)
    val content = if (dark) Color.White else Color(0xFF15151A)
    val pal = PlayerPalette(
        dark = dark, base = base, content = content, contentSecondary = content.copy(alpha = 0.6f),
        accent = kind.colors.first(), blobTop = kind.colors.first(), blobAccent = kind.colors.last(), blobBottom = base,
    )
    var speed by remember(state.mediaId) { mutableFloatStateOf(MusicController.speed()) }
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val threshold = with(LocalDensity.current) { 140.dp.toPx() }
    val duration = state.durationMs.coerceAtLeast(1)
    val progress = (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    var scrub by remember { mutableStateOf<Float?>(null) }

    CompositionLocalProvider(LocalContentColor provides content) {
        Box(
            Modifier
                .offset { IntOffset(0, drag.value.roundToInt()) }
                .fillMaxSize()
                .background(base)
                .background(Brush.verticalGradient(listOf(kind.colors.first().copy(alpha = 0.45f), Color.Transparent)))
                .clickable(enabled = false) {}
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { d -> scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(0f)) } },
                    onDragStopped = { v -> if (drag.value > threshold || v > 1500f) onCollapse() else drag.animateTo(0f, spring(dampingRatio = 0.75f)) },
                )
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCollapse) { Icon(Icons.Rounded.KeyboardArrowDown, "Свернуть", Modifier.size(32.dp)) }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        EqualizerBars(state.isPlaying, pal.accent)
                        Spacer(Modifier.width(8.dp))
                        Text(kind.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = pal.contentSecondary)
                    }
                    if (showSleep) SleepTimerButton(state, pal)
                    IconButton(onClick = onStop) { Icon(Icons.Rounded.Close, "Завершить прослушивание") }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // Превью самого аудио; на паузе чуть уменьшается
                    val scale by animateFloatAsState(if (state.isPlaying) 1f else 0.94f, spring(dampingRatio = 0.6f), label = "scale")
                    com.offplayer.app.ui.MediaArt(
                        track?.uri,
                        Modifier
                            .fillMaxWidth(0.86f)
                            .aspectRatio(1f)
                            .graphicsLayer { scaleX = scale; scaleY = scale },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                        icon = Icons.Rounded.Podcasts,
                        elevation = 18.dp,
                    )
                }
                Text(
                    state.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
                val channel = track?.channel
                if (showSource && channel != null) {
                    Text(
                        channel, style = MaterialTheme.typography.titleSmall, color = pal.accent,
                        textDecoration = if (track.channelUrl != null) TextDecoration.Underline else null,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clip(AppShapes.Badge)
                            .clickable(enabled = track.channelUrl != null) {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(track.channelUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }.onFailure { Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show() }
                            }
                            .padding(horizontal = 4.dp),
                    )
                } else {
                    Text(state.artist, style = MaterialTheme.typography.titleSmall, color = pal.contentSecondary, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(16.dp))
                SeekBar(progress, pal.accent, content, onScrub = { scrub = it }, onSeek = { MusicController.seekTo((it * duration).toLong()) })
                Row(Modifier.fillMaxWidth()) {
                    Text(formatDuration(scrub?.let { (it * duration).toLong() } ?: state.positionMs), style = MaterialTheme.typography.labelMedium, color = pal.contentSecondary)
                    Spacer(Modifier.weight(1f))
                    Text("−" + formatDuration(state.durationMs - state.positionMs), style = MaterialTheme.typography.labelMedium, color = pal.contentSecondary)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    // Скорость — по кругу
                    Text(
                        "${speed.toString().removeSuffix(".0")}×",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(content.copy(alpha = 0.08f))
                            .clickable {
                                speed = SPEEDS[(SPEEDS.indexOf(speed).coerceAtLeast(0) + 1) % SPEEDS.size]
                                MusicController.setSpeed(speed)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    IconButton(onClick = { MusicController.seekBy(-15_000) }, modifier = Modifier.size(56.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Replay, "Назад 15 секунд", Modifier.size(40.dp))
                            Text("15", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                    Box(
                        Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(kind.colors))
                            .clickable(onClick = MusicController::togglePlay),
                        contentAlignment = Alignment.Center,
                    ) {
                        CompositionLocalProvider(LocalContentColor provides Color.White) {
                            PlayPauseIcon(state.isPlaying, Modifier.size(42.dp))
                        }
                    }
                    IconButton(onClick = { MusicController.seekBy(30_000) }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Rounded.Forward30, "Вперёд 30 секунд", Modifier.size(40.dp))
                    }
                    Spacer(Modifier.width(48.dp))
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
