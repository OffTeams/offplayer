package com.offplayer.app.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.offplayer.app.data.SponsorBlock
import com.offplayer.app.model.Chapter
import com.offplayer.app.model.SponsorSegment
import com.offplayer.app.ui.formatDuration
import com.offplayer.app.ui.theme.Accent
import com.offplayer.app.ui.theme.AppShapes
import android.net.Uri
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.offplayer.app.model.MediaThumb
import com.offplayer.app.data.MediaRepository
import com.offplayer.app.video.VideoQueueEntry
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.mutableIntStateOf
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.HeadsetOff
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material3.Switch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.media3.common.Player
import com.offplayer.app.data.AppSettings
import androidx.compose.material.icons.rounded.PlayArrow

/** Фирменный зелёный SponsorBlock — для отметок на полосе и плашек. */
val SponsorGreen = Color(0xFF00D400)

private val PanelColor = Color(0xF0121212)

/**
 * Панель «Эпизоды» в духе YouTube: встроена в плеер (примыкает к краю, без «парящей» карточки),
 * у каждого эпизода — кадр из видео в момент его начала, время поверх кадра и название.
 * Текущий эпизод подсвечен, под ним — прогресс внутри эпизода. Всё работает офлайн.
 *
 * landscape = true — панель справа на всю высоту; false — снизу, как лист.
 */
@Composable
fun ChaptersPanel(
    chapters: List<Chapter>,
    positionMs: Long,
    videoUri: Uri?,
    landscape: Boolean,
    contentPadding: PaddingValues,
    onSeek: (Chapter) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = chapters.indexOfLast { positionMs >= it.startMs }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { if (current > 1) listState.scrollToItem(current - 1) }
    val shape = if (landscape) RectangleShape else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    Column(
        modifier
            .background(PanelSurface, shape)
            .drawBehind {
                // Тонкая разделительная линия со стороны видео
                val stroke = 1.dp.toPx()
                if (landscape) drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, 0f), Offset(0f, size.height), stroke)
            }
            .padding(contentPadding),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Эпизоды", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(
                "${current + 1} из ${chapters.size}",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, "Закрыть", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
            }
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
            itemsIndexed(chapters) { i, ch ->
                val isCurrent = i == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isCurrent) Color.White.copy(alpha = 0.08f) else Color.Transparent, AppShapes.Thumb)
                        .clickable { onSeek(ch) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Кадр в момент начала эпизода + время поверх
                    Box(Modifier.width(112.dp).aspectRatio(16f / 9f)) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(AppShapes.Thumb)
                                .background(Color.White.copy(alpha = 0.06f))
                        ) {
                            if (videoUri != null) {
                                AsyncImage(
                                    model = MediaThumb(videoUri, isVideo = true, timeMs = ch.startMs),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Text(
                            formatDuration(ch.startMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.75f), AppShapes.Badge)
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            ch.title,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        if (isCurrent) {
                            // Прогресс внутри текущего эпизода
                            val p = ((positionMs - ch.startMs).toFloat() / (ch.endMs - ch.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { p },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp),
                                color = Accent,
                                trackColor = Color.White.copy(alpha = 0.15f),
                                drawStopIndicator = {},
                            )
                        } else {
                            Text(
                                formatDuration(ch.endMs - ch.startMs),
                                color = Color.White.copy(alpha = 0.55f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Панель «Список» — очередь проигрываемой подборки, устроена как панель эпизодов:
 * превью, длительность, название; текущее видео подсвечено, нажатие переключает.
 */
@Composable
fun QueuePanel(
    title: String,
    queue: List<VideoQueueEntry>,
    currentIndex: Int,
    landscape: Boolean,
    contentPadding: PaddingValues,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { if (currentIndex > 1) listState.scrollToItem(currentIndex - 1) }
    val shape = if (landscape) RectangleShape else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    Column(
        modifier
            .background(PanelSurface, shape)
            .drawBehind {
                val stroke = 1.dp.toPx()
                if (landscape) drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, 0f), Offset(0f, size.height), stroke)
            }
            .padding(contentPadding),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text("${currentIndex + 1} из ${queue.size}", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, "Закрыть", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
            }
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
            itemsIndexed(queue) { i, e ->
                val isCurrent = i == currentIndex
                val duration = e.videoId?.let { MediaRepository.video(it) }?.durationMs
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isCurrent) Color.White.copy(alpha = 0.08f) else Color.Transparent, AppShapes.Thumb)
                        .clickable { onSelect(i) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                        if (isCurrent) Icon(Icons.Rounded.PlayArrow, null, tint = Accent, modifier = Modifier.size(16.dp))
                        else Text("${i + 1}", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.width(112.dp).aspectRatio(16f / 9f)) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(AppShapes.Thumb)
                                .background(Color.White.copy(alpha = 0.06f))
                        ) {
                            if (e.uri != null) {
                                AsyncImage(
                                    model = MediaThumb(e.uri, isVideo = true),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        if (duration != null && duration > 0) {
                            Text(
                                formatDuration(duration),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .background(Color.Black.copy(alpha = 0.75f), AppShapes.Badge)
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        e.title,
                        color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Подсказка двойного нажатия: перемотка на N секунд или режим заполнения экрана. */
@Composable
fun DoubleTapHint(hint: TapHint, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Color.Black.copy(alpha = 0.55f), AppShapes.Pill)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (hint) {
            is TapHint.Seek -> {
                if (!hint.forward) Icon(Icons.Rounded.FastRewind, null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(6.dp))
                Text("${hint.seconds} с", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                if (hint.forward) Icon(Icons.Rounded.FastForward, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            is TapHint.Fill -> {
                Icon(if (hint.fill) Icons.Rounded.Fullscreen else Icons.Rounded.FullscreenExit, null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (hint.fill) "Заполнение экрана" else "Исходный размер",
                    color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/**
 * Настройки плеера в общем стиле приложения: скорость, фоновое воспроизведение,
 * «картинка в картинке». Открываются шестерёнкой в верхней панели.
 */
@Composable
fun PlayerSettingsPanel(
    controller: Player?,
    background: Boolean,
    pipSupported: Boolean,
    onPip: () -> Unit,
    onClose: () -> Unit,
) {
    var speed by remember(controller) { mutableFloatStateOf(controller?.playbackParameters?.speed ?: 1f) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .background(PanelSurface, AppShapes.Large)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(vertical = 12.dp),
        ) {
            Row(Modifier.padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Настройки", color = Color.White, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Закрыть", tint = Color.White.copy(alpha = 0.85f)) }
            }
            Text(
                "Скорость", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                // Одинаковая ширина у всех кнопок скорости
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SPEEDS.forEach { s ->
                    val selected = s == speed
                    Text(
                        "${s.toString().removeSuffix(".0")}×",
                        color = if (selected) Color.Black else Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .clip(AppShapes.Pill)
                            .background(if (selected) Color.White else Color.White.copy(alpha = 0.1f))
                            .clickable {
                                speed = s
                                controller?.setPlaybackSpeed(s)
                            }
                            .padding(vertical = 9.dp),
                    )
                }
            }
            // Аудиодорожки (оригинал и дубляж), если в файле их несколько
            val audioGroups = remember(controller) {
                controller?.currentTracks?.groups?.filter { it.type == C.TRACK_TYPE_AUDIO }.orEmpty()
            }
            if (audioGroups.size > 1) {
                var selected by remember(controller) { mutableIntStateOf(audioGroups.indexOfFirst { it.isSelected }) }
                Text(
                    "Аудиодорожка", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 8.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    audioGroups.forEachIndexed { i, g ->
                        val f = g.getTrackFormat(0)
                        val name = f.label
                            ?: f.language?.takeIf { it != "und" }?.let { java.util.Locale.forLanguageTag(it).getDisplayLanguage(java.util.Locale("ru")) }
                                ?.replaceFirstChar { it.uppercase() }
                            ?: "Дорожка ${i + 1}"
                        val isSel = i == selected
                        Text(
                            if (i == 0) "$name · оригинал" else name,
                            color = if (isSel) Color.Black else Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(AppShapes.Pill)
                                .background(if (isSel) Color.White else Color.White.copy(alpha = 0.1f))
                                .clickable {
                                    selected = i
                                    controller?.let { c ->
                                        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                                            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0))
                                            .build()
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SettingsRow(
                icon = if (background) Icons.Rounded.Headphones else Icons.Rounded.HeadsetOff,
                title = "Воспроизведение в фоне",
                subtitle = "Звук продолжает играть, когда плеер свёрнут",
                onClick = { AppSettings.setVideoBackground(!background) },
            ) {
                Switch(checked = background, onCheckedChange = { AppSettings.setVideoBackground(it) })
            }
            if (pipSupported) {
                SettingsRow(
                    icon = Icons.Rounded.PictureInPictureAlt,
                    title = "Картинка в картинке",
                    subtitle = "Видео в маленьком окне поверх других приложений",
                    onClick = onPip,
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
        }
        trailing()
    }
}

/** Фон панели эпизодов — чуть светлее чёрного фона плеера. */
private val PanelSurface = Color(0xFF141414)

/** Плашка после автопропуска: «Пропущено: Спонсор · Вернуть». */
@Composable
fun SkippedNotice(segment: SponsorSegment, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(PanelColor, AppShapes.Pill)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(SponsorGreen, AppShapes.Pill))
        Text("Пропущено: ${SponsorBlock.label(segment.category)}", color = Color.White, style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier
                .background(Color.White.copy(alpha = 0.12f), AppShapes.Pill)
                .clickable(onClick = onUndo)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Replay, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Вернуть", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Кнопка ручного пропуска, когда автопропуск выключен. */
@Composable
fun SkipSegmentButton(segment: SponsorSegment, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(PanelColor, AppShapes.Pill)
            .border(1.dp, SponsorGreen.copy(alpha = 0.6f), AppShapes.Pill)
            .clickable(onClick = onSkip)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Пропустить: ${SponsorBlock.label(segment.category)}", color = Color.White, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Rounded.FastForward, null, tint = SponsorGreen, modifier = Modifier.size(18.dp))
    }
}
