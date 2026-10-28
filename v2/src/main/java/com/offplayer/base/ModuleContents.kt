package com.offplayer.base

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ---------- Общие элементы ----------

/** Заголовок модуля (когда модуль показывает его сам: лента, карточки). Уступает место кнопкам основы в углу карточки. */
@Composable
fun ModuleTitle(text: String) {
    val env = LocalModuleEnv.current
    ScreenTitle(text, Modifier.padding(start = 20.dp + env.reserveTopStart, end = 20.dp + env.reserveTopEnd, top = 16.dp, bottom = 4.dp))
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
        for (k in listOf(0.46f, 0.6f, 0.74f)) drawCircle(Color.White.copy(alpha = 0.16f), r * k, c, style = Stroke(r * 0.025f))
        val ringR = r * 0.88f
        val colors = listOf(Color(0xFF8E7CFF), Color(0xFFFF7A59), Color(0xFFFFC24B), Color(0xFF5B4BDB))
        colors.forEachIndexed { i, col ->
            drawArc(col, startAngle = -45f + i * 90f + 2f, sweepAngle = 86f, useCenter = false,
                topLeft = Offset(c.x - ringR, c.y - ringR), size = Size(ringR * 2, ringR * 2), style = Stroke(r * 0.16f))
        }
        drawCircle(Coral, r * 0.32f, c)
        val p = Path().apply {
            moveTo(c.x - r * 0.1f, c.y - r * 0.17f); lineTo(c.x - r * 0.1f, c.y + r * 0.17f); lineTo(c.x + r * 0.17f, c.y); close()
        }
        drawPath(p, Color(0xFF2A1208))
    }
}

/**
 * Обложка-заглушка с графичным узором (вместо одноцветных квадратов): солнце, диагональ,
 * пластинка, полосы, точки, луна — по номеру, в цвете трека.
 */
@Composable
fun CoverArt(id: Int, color: Color, size: Dp, corner: Dp, modifier: Modifier = Modifier) {
    val light = lerp(color, Color.White, 0.55f)
    val deep = lerp(color, Color.Black, 0.45f)
    Canvas(modifier.size(size).clip(RoundedCornerShape(corner))) {
        val w = this.size.width
        val h = this.size.height
        when (id % 6) {
            0 -> { drawRect(color); drawCircle(lerp(color, Color(0xFFFFC24B), 0.6f), w * 0.46f, Offset(w * 0.5f, h * 1.1f)) }
            1 -> {
                drawRect(deep)
                drawPath(Path().apply { moveTo(w, 0f); lineTo(w, h); lineTo(0f, h); close() }, color)
                drawCircle(light, w * 0.16f, Offset(w * 0.72f, h * 0.28f))
            }
            2 -> {
                drawRect(Color(0xFF161616))
                for (k in 1..9) drawCircle(Color(0xFF262626), w * 0.05f * k, center, style = Stroke(w * 0.012f))
                drawCircle(color, w * 0.18f, center)
                drawCircle(Color(0xFF151515), w * 0.03f, center)
            }
            3 -> { drawRect(deep); for (k in 0 until 6) drawRect(color, Offset(0f, h * (k * 2 + 1) / 12f), Size(w, h / 12f)) }
            4 -> { drawRect(color); drawCircle(light, w * 0.15f, Offset(w * 0.3f, h * 0.35f)); drawCircle(light, w * 0.09f, Offset(w * 0.66f, h * 0.66f)) }
            else -> {
                drawRect(deep)
                drawCircle(light, w * 0.28f, Offset(w * 0.62f, h * 0.4f))
                drawCircle(deep, w * 0.24f, Offset(w * 0.72f, h * 0.32f))
            }
        }
    }
}

/** Строка в общем блоке-списке: у первой скруглён верх, у последней — низ. */
@Composable
private fun BlockRow(index: Int, count: Int, onClick: () -> Unit, content: @Composable () -> Unit) {
    val pal = LocalPalette.current
    val top = if (index == 0) 24.dp else 0.dp
    val bottom = if (index == count - 1) 24.dp else 0.dp
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(top, top, bottom, bottom)).background(pal.surface).bouncyClick(onClick),
    ) {
        content()
        if (index != count - 1) InsetDivider()
    }
}

// ---------- Музыка ----------

@Composable
fun MusicModule() {
    val env = LocalModuleEnv.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var queueOpen by rememberSaveable { mutableStateOf(false) }
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
    LazyColumn(Modifier.fillMaxSize(), state = trackedListState(), contentPadding = PaddingValues(bottom = env.listBottom + 8.dp)) {
        if (env.showTitle) item { ModuleTitle("Музыка") }
        item { GlassSearchField("Поиск в медиатеке", Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) }
        if (track != null) item { NowPlayingCard(track) { expanded = true } }
        item { SectionHeader("Продолжить", "Все") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(FAKE_TRACKS.take(6).reversed(), key = { it.id }) { t ->
                    Column(Modifier.width(136.dp).block(RoundedCornerShape(22.dp)).bouncyClick { Playback.play(t) }.padding(8.dp)) {
                        CoverArt(t.id, t.color, 120.dp, 16.dp)
                        Text(t.title, Modifier.padding(start = 4.dp, top = 8.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, Modifier.padding(start = 4.dp, bottom = 4.dp), color = pal.contentSecondary, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item { CaptionHeader("Все треки · ${FAKE_TRACKS.size}", "Перемешать") { Playback.play(FAKE_TRACKS.random()) } }
        itemsIndexed(FAKE_TRACKS, key = { _, t -> t.id }) { i, t ->
            val current = track?.id == t.id
            BlockRow(i, FAKE_TRACKS.size, onClick = { Playback.play(t) }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(t.id, t.color, 44.dp, 12.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(t.title, color = if (current) pal.accent else pal.content, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, color = pal.contentSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (current) EqualizerBars(Playback.playing, pal.accent)
                    else Text(formatTime(t.durationSec.toFloat()), color = pal.contentSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

/** Карточка «Сейчас играет» на градиенте (стекло): обложка, название, пауза, прогресс. */
@Composable
private fun NowPlayingCard(track: FakeTrack, onOpen: () -> Unit) {
    val pal = LocalPalette.current
    val on = pal.onGradient
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp)
            .glass(RoundedCornerShape(26.dp)).bouncyClick(onOpen).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverArt(track.id, track.color, 76.dp, 18.dp, Modifier.shadow(8.dp, RoundedCornerShape(18.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text("СЕЙЧАС ИГРАЕТ", color = on.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
                Text(track.title, color = on, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = on.copy(alpha = 0.85f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PlaySquareButton(Playback.playing, 56.dp) { Playback.playing = !Playback.playing }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(Playback.trackPos), color = on.copy(alpha = 0.85f), fontSize = 12.sp)
            Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(if (pal.gradient) Color.White.copy(alpha = 0.28f) else pal.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(Playback.trackPos / track.durationSec).height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (pal.gradient) Color.White else pal.accent))
            }
            Text(formatTime(track.durationSec.toFloat()), color = on.copy(alpha = 0.85f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun MusicPlayer(track: FakeTrack, queueOpen: Boolean, onToggleQueue: () -> Unit, onCollapse: () -> Unit) {
    val env = LocalModuleEnv.current
    val pal = LocalPalette.current
    val on = pal.onGradient
    // Очередь закрывается, пока карточка улетает из стопки, и раскрывается, когда она снова впереди
    val queueTarget = if (queueOpen) (1f - env.stackProgress * 2.2f).coerceIn(0f, 1f) else 0f
    val queueFactor by animateFloatAsState(queueTarget, tunedSpring(), label = "queue")
    var liked by rememberSaveable(track.id) { mutableStateOf(false) }
    val upNext = remember(track.id) {
        val i = FAKE_TRACKS.indexOfFirst { it.id == track.id }
        (1..5).map { FAKE_TRACKS[(i + it) % FAKE_TRACKS.size] }
    }

    val header = @Composable {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp + env.reserveTopStart, end = 8.dp + env.reserveTopEnd, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BouncyIconButton(onClick = onCollapse) { Icon(Icons.Rounded.KeyboardArrowDown, "Свернуть", Modifier.size(28.dp), tint = on) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                GlassChip {
                    EqualizerBars(Playback.playing, on, height = 11.dp)
                    Text("Микс «${track.artist}»", Modifier.padding(start = 8.dp), color = on, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            BouncyIconButton(onClick = {}) { Icon(Icons.Rounded.MoreVert, "Ещё", tint = on) }
        }
    }
    val cover = @Composable { m: Modifier ->
        Box(m, contentAlignment = Alignment.Center) {
            BoxWithConstraints(Modifier.fillMaxWidth(0.82f).aspectRatio(1f, matchHeightConstraintsFirst = true)) {
                CoverArt(track.id, track.color, maxWidth, 38.dp,
                    Modifier.shadow(if (Prefs.lightMode.value) 0.dp else 26.dp, RoundedCornerShape(38.dp)))
            }
        }
    }
    val info = @Composable {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = on, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp))
                    Text(track.artist, color = on.copy(alpha = 0.85f), fontSize = 16.sp)
                }
                GlassIconButton(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "В избранное", 48.dp) { liked = !liked }
            }
            ThickSeekBar(Playback.trackPos / track.durationSec, onGradient = true, Modifier.padding(top = 14.dp)) {
                Playback.trackPos = it * track.durationSec
            }
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime(Playback.trackPos), color = on.copy(alpha = 0.85f), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(formatTime(track.durationSec.toFloat()), color = on.copy(alpha = 0.85f), fontSize = 13.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.SkipPrevious, "Предыдущий", 64.dp, 30.dp) { Playback.next(-1) }
                Spacer(Modifier.width(22.dp))
                PlaySquareButton(Playback.playing, 92.dp) { Playback.playing = !Playback.playing }
                Spacer(Modifier.width(22.dp))
                GlassIconButton(Icons.Rounded.SkipNext, "Следующий", 64.dp, 30.dp) { Playback.next(1) }
            }
        }
    }
    val tiles = @Composable {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Таймер сна", Modifier.weight(1f), top = { Icon(Icons.Rounded.Bedtime, null, tint = pal.accent) }) {}
            Tile("Скорость", Modifier.weight(1f), top = { Text("1×", color = pal.accent, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }) {}
            Tile("Очередь · ${upNext.size}", Modifier.weight(1f),
                top = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = pal.accent) }, onClick = onToggleQueue)
        }
    }
    val queue = @Composable {
        AnimatedVisibility(queueFactor > 0.02f, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).block().padding(vertical = 4.dp)) {
                upNext.forEachIndexed { i, t ->
                    Row(Modifier.fillMaxWidth().bouncyClick { Playback.play(t) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        CoverArt(t.id, t.color, 40.dp, 10.dp)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(t.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, color = pal.contentSecondary, fontSize = 13.sp)
                        }
                    }
                    if (i != upNext.lastIndex) InsetDivider(66.dp)
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(pal.base)) {
        val landscape = maxWidth > maxHeight
        // В плеере цвет трека держится до самих кнопок управления, плитки внизу ложатся на его край
        HeaderGradient(maxHeight, solid = if (landscape) 0.6f else 0.82f)
        if (landscape) {
            Row(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
                cover(Modifier.weight(1f).fillMaxHeight().padding(vertical = 16.dp))
                Column(Modifier.weight(1.2f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 12.dp, bottom = 16.dp)) {
                    header()
                    info()
                    Spacer(Modifier.height(16.dp))
                    queue()
                    tiles()
                }
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(bottom = if (env.reserveBottomStart + env.reserveBottomEnd > 0.dp) 52.dp else 12.dp)) {
                header()
                cover(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp))
                Column(Modifier.padding(horizontal = 24.dp)) { info() }
                Spacer(Modifier.height(18.dp))
                Column(Modifier.padding(horizontal = 16.dp)) {
                    queue()
                    tiles()
                }
            }
        }
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
                Text("Только звук", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (showTime) {
            Text(
                "${formatTime(Playback.videoPos)} / ${formatTime(video.durationSec.toFloat())}",
                color = Color.White, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
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
        if (maxWidth > maxHeight) {
            // Альбомная: видео с кнопками слева, список справа
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1.4f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    if (env.showTitle) ModuleTitle("Видео")
                    if (video != null) VideoPlayerBlock(video)
                }
                VideoList(video, Modifier.weight(1f).fillMaxHeight(), withTitle = false)
            }
        } else {
            VideoList(video, Modifier.fillMaxSize(), withTitle = env.showTitle, header = { if (video != null) VideoPlayerBlock(video) })
        }
    }
}

/** Видео и кнопки под ним — на градиенте. */
@Composable
private fun VideoPlayerBlock(video: FakeVideo) {
    val pal = LocalPalette.current
    val on = pal.onGradient
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        VideoSurface(video, Modifier.fillMaxWidth().aspectRatio(16f / 9f).shadow(16.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)))
        Text(video.title, Modifier.padding(top = 12.dp), color = on, fontSize = 19.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(video.channel, color = on.copy(alpha = 0.85f), fontSize = 14.sp)
        ThickSeekBar(Playback.videoPos / video.durationSec, onGradient = true, Modifier.padding(top = 8.dp)) {
            Playback.videoPos = it * video.durationSec
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlaySquareButton(Playback.videoPlaying, 52.dp) { Playback.videoPlaying = !Playback.videoPlaying }
            Spacer(Modifier.weight(1f))
            GlassIconButton(if (Playback.audioOnly) Icons.Rounded.Videocam else Icons.Rounded.Headphones,
                if (Playback.audioOnly) "Вернуть картинку" else "Только звук") { Playback.audioOnly = !Playback.audioOnly }
            GlassIconButton(Icons.Rounded.PictureInPictureAlt, "Картинка в картинке") { Playback.pipRequest = "video" }
            GlassIconButton(Icons.Rounded.Fullscreen, "Во весь экран") { Playback.fullscreen = true }
        }
    }
}

@Composable
private fun VideoList(video: FakeVideo?, modifier: Modifier, withTitle: Boolean, header: @Composable () -> Unit = {}) {
    val env = LocalModuleEnv.current
    val pal = LocalPalette.current
    LazyColumn(modifier, state = trackedListState(), contentPadding = PaddingValues(bottom = env.listBottom + 8.dp)) {
        if (withTitle) item { ModuleTitle("Видео") }
        item { header() }
        item { CaptionHeader("Все видео · ${FAKE_VIDEOS.size}") }
        itemsIndexed(FAKE_VIDEOS, key = { _, v -> v.id }) { i, v ->
            BlockRow(i, FAKE_VIDEOS.size, onClick = { Playback.playVideo(v) }) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(118.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.BottomEnd) {
                        CoverArt(v.id + 2, v.color, 118.dp, 12.dp)
                        Text(formatTime(v.durationSec.toFloat()), color = Color.White, fontSize = 11.sp,
                            modifier = Modifier.padding(4.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            color = if (video?.id == v.id) pal.accent else pal.content)
                        Text(v.channel, color = pal.contentSecondary, fontSize = 13.sp)
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
    val pal = LocalPalette.current
    LazyColumn(Modifier.fillMaxSize(), state = trackedListState(), contentPadding = PaddingValues(bottom = env.listBottom + 8.dp)) {
        if (env.showTitle) item { ModuleTitle(spec.title) }
        item { GlassSearchField("Поиск: ${spec.title.lowercase()}", Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) }
        item { SectionHeader("Подборки", "Все") }
        item {
            // Горизонтальная карусель — проверка, что жесты основы не мешают жестам модуля
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(8) { i ->
                    Column(Modifier.width(136.dp).block(RoundedCornerShape(22.dp)).bouncyClick {}.padding(8.dp)) {
                        CoverArt(i, lerp(spec.color, Color.Black, i * 0.05f), 120.dp, 16.dp)
                        Text("Подборка ${i + 1}", Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
        item { CaptionHeader("Все · 20") }
        items(20) { i ->
            BlockRow(i, 20, onClick = {}) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(pal.accentContainer), contentAlignment = Alignment.Center) {
                        Icon(spec.icon, null, tint = pal.accent)
                    }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("${spec.title}: элемент ${i + 1}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Модуль-пустышка для проверки основы", color = pal.contentSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SearchModule() {
    val pal = LocalPalette.current
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        GlassSearchField("Название трека, видео, подкаста", Modifier.padding(horizontal = 16.dp))
        Text(
            "Модуль «Поиск» — пустышка. Здесь будет поиск по YouTube, SoundCloud и другим платформам.",
            Modifier.padding(20.dp), color = pal.onGradient.copy(alpha = 0.85f),
        )
    }
}

@Composable
fun DownloadsScreen() {
    val pal = LocalPalette.current
    val progress = rememberInfiniteTransition(label = "dl").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "p",
    ).value
    val items = listOf(
        Triple("Северное сияние", "в Музыку", FAKE_TRACKS[0]),
        Triple("Как устроена пластинка", "в Видео", FAKE_TRACKS[2]),
        Triple("Выпуск 42", "в Подкасты", FAKE_TRACKS[5]),
    )
    Column(Modifier.fillMaxSize()) {
        Text("«Загрузка» — служебный модуль: он не занимает вкладку и сохраняет скачанное в хранилища других модулей.",
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = pal.onGradient.copy(alpha = 0.85f), fontSize = 14.sp)
        CaptionHeader("Загружается · ${items.size}")
        items.forEachIndexed { i, (name, dest, t) ->
            BlockRow(i, items.size, onClick = {}) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(t.id, t.color, 44.dp, 12.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(dest, color = pal.contentSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (progress + i * 0.3f) % 1f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = pal.accent, trackColor = pal.surfaceVariant, drawStopIndicator = {}, gapSize = 0.dp,
                        )
                    }
                }
            }
        }
    }
}
