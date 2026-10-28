package com.offplayer.base

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// ---------- Общие элементы ----------

@Composable
fun ModuleTitle(text: String) {
    val env = LocalModuleEnv.current
    // Если в верхнем углу карточки кнопки основы — заголовок просто уступает им место в своей строке
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 20.dp + env.reserveTopStart, end = 20.dp + env.reserveTopEnd, top = 16.dp, bottom = 8.dp),
    )
}

/** Фирменная пластинка: диск с дорожками, кольцо из четырёх модулей, коралловый ярлык с play. */
@Composable
fun VinylLogo(size: Dp, rotating: Boolean = false, modifier: Modifier = Modifier) {
    val angle = if (rotating && !Prefs.lightMode.value) {
        rememberInfiniteTransition(label = "vinyl").animateFloat(
            0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "angle",
        ).value
    } else 0f
    Canvas(modifier.size(size).rotate(angle)) {
        val r = this.size.minDimension / 2
        val c = center
        drawCircle(Color(0xFF1E1C24), r * 0.98f, c)
        for (k in listOf(0.46f, 0.6f, 0.74f)) {
            drawCircle(Color.White.copy(alpha = 0.16f), r * k, c, style = Stroke(r * 0.025f))
        }
        val ringR = r * 0.88f
        val sw = r * 0.16f
        val colors = listOf(Color(0xFF8E7CFF), Color(0xFFFF7A59), Color(0xFFFFC24B), Color(0xFF5B4BDB))
        colors.forEachIndexed { i, col ->
            drawArc(
                col, startAngle = -45f + i * 90f + 2f, sweepAngle = 86f, useCenter = false,
                topLeft = Offset(c.x - ringR, c.y - ringR), size = Size(ringR * 2, ringR * 2), style = Stroke(sw),
            )
        }
        drawCircle(Coral, r * 0.32f, c)
        val p = Path().apply {
            moveTo(c.x - r * 0.1f, c.y - r * 0.17f)
            lineTo(c.x - r * 0.1f, c.y + r * 0.17f)
            lineTo(c.x + r * 0.17f, c.y)
            close()
        }
        drawPath(p, Color(0xFF2A1208))
    }
}

@Composable
private fun Cover(color: Color, size: Dp, corner: Dp = 10.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(corner))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(color, lerp(color, Color.Black, 0.45f)))),
    )
}

// ---------- Музыка ----------

@Composable
fun MusicModule() {
    val env = LocalModuleEnv.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var queueOpen by rememberSaveable { mutableStateOf(true) }
    val track = Playback.track

    if (expanded && track != null) {
        // Развёрнутый плеер забирает весь экран: шапка и панель основы прячутся
        DisposableEffect(env.front, env.inCard) {
            Playback.immersive = env.front && !env.inCard
            onDispose { Playback.immersive = false }
        }
        BackHandler(env.front) { expanded = false }
        MusicPlayer(track, queueOpen, onToggleQueue = { queueOpen = !queueOpen }, onCollapse = { expanded = false })
        return
    }

    val pal = LocalPalette.current
    Column(Modifier.fillMaxSize()) {
        if (env.showTitle) ModuleTitle("Музыка")
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = if (track != null) 8.dp else env.listBottom)) {
            items(FAKE_TRACKS, key = { it.id }) { t ->
                val current = track?.id == t.id
                Row(
                    Modifier.fillMaxWidth().bouncyClick { Playback.play(t) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(t.color, 48.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(t.title, color = if (current) pal.accent else pal.content, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal)
                        Text("${t.artist} • ${formatTime(t.durationSec.toFloat())}", style = MaterialTheme.typography.bodySmall,
                            color = pal.contentSecondary)
                    }
                    if (current) EqualizerBars(Playback.playing, pal.accent)
                }
            }
        }
        if (track != null) MiniPlayer(track) { expanded = true }
    }
}

@Composable
private fun MiniPlayer(track: FakeTrack, onOpen: () -> Unit) {
    val env = LocalModuleEnv.current
    val pal = LocalPalette.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth()
            .padding(start = 8.dp + env.reserveBottomStart, end = 8.dp + env.reserveBottomEnd, top = 8.dp, bottom = 8.dp)
            .clip(shape).background(accentFill(), shape)
            .bouncyClick(onOpen).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(track.color, 42.dp, 10.dp)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(track.artist, style = MaterialTheme.typography.bodySmall, color = pal.contentSecondary)
        }
        BouncyIconButton(onClick = { Playback.playing = !Playback.playing }) { PlayPauseIcon(Playback.playing) }
        BouncyIconButton(onClick = { Playback.next(1) }) { Icon(Icons.Rounded.SkipNext, "Следующий") }
    }
}

@Composable
private fun MusicPlayer(track: FakeTrack, queueOpen: Boolean, onToggleQueue: () -> Unit, onCollapse: () -> Unit) {
    val env = LocalModuleEnv.current
    val pal = LocalPalette.current
    // Очередь закрывается, пока карточка улетает из стопки, и раскрывается, когда она снова впереди
    val queueTarget = if (queueOpen) (1f - env.stackProgress * 2.2f).coerceIn(0f, 1f) else 0f
    val queueFactor by animateFloatAsState(queueTarget, tunedSpring(), label = "queue")
    val upNext = remember(track.id) {
        val i = FAKE_TRACKS.indexOfFirst { it.id == track.id }
        (1..5).map { FAKE_TRACKS[(i + it) % FAKE_TRACKS.size] }
    }
    // Как в 1.x: шапка со стрелкой «свернуть» и плашкой «Сейчас играет» с эквалайзером по центру
    val header = @Composable {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(start = env.reserveTopStart, end = env.reserveTopEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BouncyIconButton(onClick = onCollapse) { Icon(Icons.Rounded.KeyboardArrowDown, "Свернуть", Modifier.size(32.dp)) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EqualizerBars(Playback.playing, pal.accent, height = 11.dp)
                    Text("Сейчас играет", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Spacer(Modifier.width(48.dp))
        }
    }
    val cover = @Composable { m: Modifier ->
        // Обложка вписывается и по ширине, и по высоте — в альбомной не вылезает за экран
        Box(m, contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxWidth(0.84f).aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .shadow(if (Prefs.lightMode.value) 0.dp else 24.dp, RoundedCornerShape(28.dp), ambientColor = pal.accent, spotColor = pal.accent),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.matchParentSize().clip(RoundedCornerShape(28.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(track.color, lerp(track.color, Color.Black, 0.5f)))))
                VinylLogo(170.dp, rotating = Playback.playing)
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            // Альбомная: обложка слева, сведения, кнопки и очередь — справа с прокруткой
            Row(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                cover(Modifier.weight(1f).fillMaxHeight().padding(vertical = 12.dp))
                Column(
                    Modifier.weight(1.2f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    header()
                    PlayerInfo(track, queueOpen, onToggleQueue, queueFactor, upNext)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                header()
                cover(Modifier.weight(1f).fillMaxWidth())
                PlayerInfo(track, queueOpen, onToggleQueue, queueFactor, upNext)
            }
        }
    }
}

@Composable
private fun PlayerInfo(track: FakeTrack, queueOpen: Boolean, onToggleQueue: () -> Unit, queueFactor: Float, upNext: List<FakeTrack>) {
    val env = LocalModuleEnv.current
    val pal = LocalPalette.current
    val arrow by animateFloatAsState(if (queueOpen) 0f else -90f, label = "queueArrow")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Text(track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, color = pal.contentSecondary, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        SeekBar(Playback.trackPos / track.durationSec) { Playback.trackPos = it * track.durationSec }
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(Playback.trackPos), style = MaterialTheme.typography.labelSmall, color = pal.contentSecondary)
            Spacer(Modifier.weight(1f))
            Text(formatTime(track.durationSec.toFloat()), style = MaterialTheme.typography.labelSmall, color = pal.contentSecondary)
        }
        Row(
            Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BouncyIconButton(onClick = { Playback.next(-1) }, size = 56.dp) { Icon(Icons.Rounded.SkipPrevious, "Предыдущий", Modifier.size(36.dp)) }
            PlayButton(Playback.playing) { Playback.playing = !Playback.playing }
            BouncyIconButton(onClick = { Playback.next(1) }, size = 56.dp) { Icon(Icons.Rounded.SkipNext, "Следующий", Modifier.size(36.dp)) }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onToggleQueue).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Очередь • ${upNext.size}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium)
            Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.rotate(arrow))
        }
        Column(Modifier.fillMaxWidth().height(270.dp * queueFactor)) {
            upNext.forEach { t ->
                Row(Modifier.fillMaxWidth().bouncyClick { Playback.play(t) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(t.color, 42.dp, 8.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, style = MaterialTheme.typography.bodySmall, color = pal.contentSecondary)
                    }
                }
            }
        }
        Spacer(Modifier.height(if (env.reserveBottomStart + env.reserveBottomEnd > 0.dp) 52.dp else 8.dp))
    }
}

// ---------- Видео ----------

/** Имитация видео: движущиеся пятна цвета ролика; в режиме «только звук» картинка стоит. */
@Composable
fun VideoSurface(video: FakeVideo, modifier: Modifier = Modifier, showTime: Boolean = true) {
    val running = Playback.videoPlaying && !Playback.audioOnly
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        var last = 0L
        while (running) {
            withFrameNanos { now ->
                if (last != 0L) t += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }
    Box(modifier.background(lerp(video.color, Color.Black, 0.6f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            listOf(0f, 2.1f, 4.2f).forEachIndexed { i, ph ->
                val x = w * (0.5f + 0.32f * cos(t * (0.5f + i * 0.17f) + ph))
                val y = h * (0.5f + 0.3f * sin(t * (0.7f + i * 0.11f) + ph))
                drawCircle(lerp(video.color, Color.White, i * 0.18f).copy(alpha = 0.55f), h * (0.28f + 0.05f * i), Offset(x, y))
            }
        }
        if (Playback.audioOnly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Headphones, null, tint = Color.White)
                Text("Только звук", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (showTime) {
            Text(
                "${formatTime(Playback.videoPos)} / ${formatTime(video.durationSec.toFloat())}",
                color = Color.White, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun VideoModule() {
    val env = LocalModuleEnv.current
    val video = Playback.video
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Альбомная ориентация (и широкие экраны): видео с кнопками слева, список справа
        if (maxWidth > maxHeight) {
            Column(Modifier.fillMaxSize()) {
                if (env.showTitle) ModuleTitle("Видео")
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    if (video != null) {
                        Column(Modifier.weight(1.4f).fillMaxHeight()) { VideoPlayerBlock(video, Modifier.weight(1f, fill = false)) }
                    }
                    VideoList(video, Modifier.weight(1f).fillMaxHeight())
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                if (env.showTitle) ModuleTitle("Видео")
                if (video != null) VideoPlayerBlock(video, Modifier)
                VideoList(video, Modifier.weight(1f))
            }
        }
    }
}

/** Видео и кнопки под ним; картинка вписывается и по ширине, и по высоте. */
@Composable
private fun VideoPlayerBlock(video: FakeVideo, surfaceModifier: Modifier) {
    VideoSurface(
        video,
        surfaceModifier.fillMaxWidth().padding(horizontal = 12.dp)
            .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(14.dp)),
    )
    SeekBar(Playback.videoPos / video.durationSec, Modifier.padding(horizontal = 12.dp)) { Playback.videoPos = it * video.durationSec }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BouncyIconButton(onClick = { Playback.videoPlaying = !Playback.videoPlaying }) { PlayPauseIcon(Playback.videoPlaying) }
        Text(video.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        BouncyIconButton(onClick = { Playback.audioOnly = !Playback.audioOnly }) {
            Icon(if (Playback.audioOnly) Icons.Rounded.Videocam else Icons.Rounded.Headphones,
                if (Playback.audioOnly) "Вернуть картинку" else "Только звук")
        }
        BouncyIconButton(onClick = { Playback.pipRequest = "video" }) {
            Icon(Icons.Rounded.PictureInPictureAlt, "Картинка в картинке")
        }
        BouncyIconButton(onClick = { Playback.fullscreen = true }) { Icon(Icons.Rounded.Fullscreen, "Во весь экран") }
    }
}

@Composable
private fun VideoList(video: FakeVideo?, modifier: Modifier) {
    val env = LocalModuleEnv.current
    run {
        LazyColumn(modifier, contentPadding = PaddingValues(bottom = env.listBottom)) {
            items(FAKE_VIDEOS, key = { it.id }) { v ->
                Row(
                    Modifier.fillMaxWidth().bouncyClick { Playback.playVideo(v) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))
                        .background(lerp(v.color, Color.Black, 0.35f)), contentAlignment = Alignment.BottomEnd) {
                        Text(formatTime(v.durationSec.toFloat()), color = Color.White, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(4.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            color = if (video?.id == v.id) LocalPalette.current.accent else LocalPalette.current.content,
                            fontWeight = if (video?.id == v.id) FontWeight.SemiBold else FontWeight.Normal)
                        Text(v.channel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ---------- Пустышки ----------

@Composable
fun PlaceholderModule(id: String) {
    val spec = Catalog.byId(id) ?: return
    val env = LocalModuleEnv.current
    Column(Modifier.fillMaxSize()) {
        if (env.showTitle) ModuleTitle(spec.title)
        // Горизонтальная карусель — проверка, что жесты основы не мешают жестам модуля
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(8) { i ->
                val c = lerp(spec.color, Color.Black, i * 0.06f)
                Box(Modifier.size(width = 132.dp, height = 92.dp).bouncyClick {}.clip(RoundedCornerShape(18.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(c, lerp(c, Color.Black, 0.5f)))),
                    contentAlignment = Alignment.BottomStart) {
                    Text("Подборка ${i + 1}", color = Color.White, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(8.dp))
                }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = env.listBottom)) {
            items(20) { i ->
                Row(Modifier.fillMaxWidth().bouncyClick {}.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(spec.color.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                        Icon(spec.icon, null, tint = spec.color)
                    }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("${spec.title}: элемент ${i + 1}")
                        Text("Модуль-пустышка для проверки основы", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun SearchModule() {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text("Название трека, видео, подкаста") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (query.isBlank()) "Модуль «Поиск» — пустышка. Здесь будет поиск по YouTube, SoundCloud и другим платформам."
            else "Результаты для «$query» появятся в настоящем модуле.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun DownloadsScreen() {
    val progress = rememberInfiniteTransition(label = "dl").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "p",
    ).value
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Модуль «Загрузка» — служебный: он не занимает вкладку и сохраняет скачанное в хранилища других модулей.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf("Северное сияние → Музыка", "Как устроена пластинка → Видео", "Выпуск 42 → Подкасты").forEachIndexed { i, name ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp)) {
                Text(name, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { ((progress + i * 0.3f) % 1f) }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
            }
        }
    }
}
