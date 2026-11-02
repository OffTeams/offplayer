package com.offplayer.app.ui.youtube

import com.offplayer.app.tr
import com.offplayer.app.R

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import com.offplayer.app.youtube.NpSource
import com.offplayer.app.youtube.YouTubeActions
import com.offplayer.app.youtube.YtRating
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.offplayer.app.video.VideoController
import com.offplayer.app.youtube.StreamMediaSourceFactory
import com.offplayer.app.youtube.YouTubeAccount
import com.offplayer.app.youtube.YouTubeApi
import com.offplayer.app.youtube.YouTubeStreams
import com.offplayer.app.youtube.YtStream
import com.offplayer.app.youtube.YtVideo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Лента Shorts отдельно от роликов: вертикальные страницы во всю высоту, свайп — следующий,
 * ролик повторяется по кругу. Свой плеер (не видеосервис): Shorts не встают в очередь видео
 * и не попадают в уведомление. Следующие два ролика готовятся заранее.
 */
@OptIn(UnstableApi::class)
@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ShortsFeed(active: Boolean, onDownload: (String) -> Unit, onChannel: (id: String, name: String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var sheet by remember { mutableStateOf(ShortSheet.NONE) }
    var sheetFor by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val signedIn by YouTubeAccount.signedIn.collectAsStateWithLifecycle()
    val videoOpen by VideoController.open.collectAsStateWithLifecycle()

    val ids = remember { mutableStateListOf<String>() }
    val streams = remember { mutableStateMapOf<String, YtStream>() }
    var continuation by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    fun loadMore() {
        if (loading) return
        loading = true
        scope.launch {
            runCatching { YouTubeApi.shorts(continuation) }
                .onSuccess { (list, next) ->
                    list.forEach { if (it !in ids) ids += it }
                    continuation = next
                    failed = false
                }
                .onFailure { if (ids.isEmpty()) failed = true }
            loading = false
        }
    }

    // Вход меняет подборку: после него лента подстраивается под аккаунт
    LaunchedEffect(signedIn) {
        ids.clear()
        streams.clear()
        continuation = null
        loadMore()
    }

    // Свой плеер у текущего Shorts и у соседних: соседние заранее подготовлены и стоят на паузе на первом кадре,
    // поэтому при листании сразу виден и играет новый ролик, а не его превью
    val players = remember { mutableStateMapOf<String, ExoPlayer>() }
    fun newPlayer(s: YtStream) = ExoPlayer.Builder(context)
        .setMediaSourceFactory(StreamMediaSourceFactory(context))
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            /* handleAudioFocus = */ true,
        )
        .build()
        .apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = false
            setMediaItem(MediaItem.Builder().setMediaId(s.youtubeId).setUri(s.uri).build())
            prepare()
        }
    DisposableEffect(Unit) { onDispose { players.values.forEach { it.release() }; players.clear() } }

    val pager = rememberPagerState { ids.size }
    val currentId = ids.getOrNull(pager.currentPage)

    fun resolve(id: String) = scope.launch {
        if (id in streams) return@launch
        val v = YtVideo(id, "", "", null, null, "", "https://i.ytimg.com/vi/$id/oardefault.jpg", isShort = true)
        runCatching { YouTubeStreams.resolve(context, v, withSponsorBlock = false) }.onSuccess { streams[id] = it }
    }

    // Текущий Shorts — в плеер; следующие два готовятся заранее, у конца ленты подгружается продолжение
    LaunchedEffect(currentId) {
        val id = currentId ?: return@LaunchedEffect
        resolve(id)
        if (pager.currentPage >= ids.size - 3) loadMore()
        ids.getOrNull(pager.currentPage + 1)?.let { resolve(it) }
        ids.getOrNull(pager.currentPage + 2)?.let { resolve(it) }
    }
    // Плееры держим только для текущего и соседних (предыдущий и следующий), остальные освобождаем
    LaunchedEffect(pager.currentPage, ids.size, streams.size) {
        val keep = (pager.currentPage - 1..pager.currentPage + 1).mapNotNull { ids.getOrNull(it) }.toSet()
        players.keys.filter { it !in keep }.forEach { players.remove(it)?.release() }
        keep.forEach { id -> if (id !in players) streams[id]?.let { players[id] = newPlayer(it) } }
    }
    // Играет, только пока лента на экране и поверх не открыт видеоплеер
    // Пауза, поставленная нажатием; сбрасывается при возврате на вкладку и при листании — значок не «залипает»
    var paused by remember { mutableStateOf(false) }
    // Удержание пальцем прячет подписи и кнопки, чтобы видеть кадр целиком
    var uiHidden by remember { mutableStateOf(false) }
    LaunchedEffect(active, videoOpen) { paused = false }
    LaunchedEffect(currentId) { paused = false }
    // Играет только текущий; ушедший с экрана останавливается и возвращается к началу
    val currentPlayer = currentId?.let { players[it] }
    LaunchedEffect(currentId, currentPlayer, active, videoOpen, paused) {
        players.forEach { (id, p) ->
            if (id == currentId) p.playWhenReady = active && !videoOpen && !paused
            else if (p.playWhenReady || p.currentPosition > 0) { p.playWhenReady = false; p.seekTo(0) }
        }
    }

    if (ids.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            if (failed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr(R.string.yt_shorts_empty), color = Color.White)
                    OutlinedButton(onClick = { loadMore() }, modifier = Modifier.padding(top = 12.dp)) { Text(tr(R.string.action_retry), color = Color.White) }
                }
            } else CircularProgressIndicator(color = Color.White)
        }
        return
    }

    // Shorts из уведомлений: встаёт первым в ленту и сразу открывается
    val shortRequest by com.offplayer.app.youtube.YouTubeNav.shortRequest.collectAsStateWithLifecycle()
    LaunchedEffect(shortRequest) {
        val id = shortRequest ?: return@LaunchedEffect
        com.offplayer.app.youtube.YouTubeNav.shortRequest.value = null
        val at = pager.currentPage.coerceAtMost(ids.size)
        ids.remove(id)
        ids.add(at.coerceAtMost(ids.size), id)
        pager.scrollToPage(at.coerceAtMost(ids.size - 1))
    }

    // Планшет в альбомной ориентации: ролик вписан по высоте, описание и комментарии — панелью справа,
    // ролик встаёт по центру оставшейся части экрана
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val sheetId = sheetFor
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    val panelWidth = (maxWidth * 0.36f).coerceIn(320.dp, 480.dp)
    Row(Modifier.fillMaxSize()) {
    Box(Modifier.weight(1f).fillMaxHeight()) {
    VerticalPager(
        state = pager,
        beyondViewportPageCount = 1,
        key = { ids.getOrElse(it) { "" } },
        // Лента обрезается по своим границам — при листании вкладок видео не заходит на соседние
        modifier = Modifier.fillMaxSize().clipToBounds().background(Color.Black),
    ) { page ->
        val id = ids.getOrNull(page) ?: return@VerticalPager
        val stream = streams[id]
        val isCurrent = page == pager.currentPage
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    // Нажатие — пауза / продолжение, как в YouTube
                    onTap = { paused = !paused },
                    onLongPress = { uiHidden = true },
                    onPress = {
                        tryAwaitRelease()
                        uiHidden = false
                    },
                )
            },
        ) {
            AsyncImage("https://i.ytimg.com/vi/$id/oardefault.jpg", null, contentScale = if (landscape) ContentScale.Fit else ContentScale.Crop, modifier = Modifier.fillMaxSize())
            val pagePlayer = players[id]
            if (pagePlayer != null) {
                // Экран есть у каждой подготовленной страницы — первый кадр соседнего Shorts уже на месте
                AndroidView(
                    factory = { ctx ->
                        (android.view.LayoutInflater.from(ctx).inflate(R.layout.shorts_player_view, null) as PlayerView)
                    },
                    update = {
                        if (it.player !== pagePlayer) it.player = pagePlayer
                        it.resizeMode = if (landscape) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (isCurrent && (stream == null)) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            if (isCurrent && paused && !uiHidden) {
                Icon(
                    Icons.Rounded.PlayArrow, null, tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(72.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape).padding(12.dp),
                )
            }

            // Как в приложении YouTube: справа столбец действий, снизу канал с подпиской и название
            if (stream != null && !uiHidden) ShortOverlay(
                youtubeId = id,
                stream = stream,
                onDownload = { onDownload("https://www.youtube.com/shorts/$id") },
                onChannel = onChannel,
                onSheet = { sheet = it; sheetFor = id },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            if (isCurrent && pagePlayer != null) ShortProgress(pagePlayer, Modifier.align(Alignment.BottomCenter))
        }
    }

    // Шапка ленты Shorts поверх видео (панель вкладки YouTube скрыта)
    if (!uiHidden) Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr(R.string.action_back), tint = Color.White) }
        Text("Shorts", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = landscape && sheetId != null && sheet != ShortSheet.NONE,
        enter = androidx.compose.animation.expandHorizontally(expandFrom = Alignment.Start) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.shrinkHorizontally(shrinkTowards = Alignment.Start) + androidx.compose.animation.fadeOut(),
    ) {
        // Последнее открытое остаётся на экране во время анимации закрытия
        var shown by remember { mutableStateOf(ShortSheet.COMMENTS) }
        var shownId by remember { mutableStateOf("") }
        if (sheet != ShortSheet.NONE && sheetId != null) { shown = sheet; shownId = sheetId }
        if (shownId.isNotEmpty()) SidePanelFor(
            shownId, comments = shown == ShortSheet.COMMENTS, onDismiss = { sheet = ShortSheet.NONE },
            modifier = Modifier.fillMaxHeight().width(panelWidth),
        )
    }
    }
    }

    // Описание и комментарии — выезжающий снизу лист, как в YouTube (в альбомной ориентации — панель справа)
    if (sheetId != null && !landscape) when (sheet) {
        ShortSheet.COMMENTS -> CommentsSheetFor(sheetId) { sheet = ShortSheet.NONE }
        ShortSheet.DESCRIPTION -> DescriptionSheetFor(sheetId) { sheet = ShortSheet.NONE }
        ShortSheet.NONE -> Unit
    }
}

private enum class ShortSheet { NONE, DESCRIPTION, COMMENTS }

@Composable
private fun ShortOverlay(
    youtubeId: String,
    stream: YtStream,
    onDownload: () -> Unit,
    onChannel: (String, String) -> Unit,
    onSheet: (ShortSheet) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signedIn by YouTubeAccount.signedIn.collectAsStateWithLifecycle()
    val info = remember(youtubeId, stream) { YouTubeStreams.info(youtubeId) }
    // Оценка и подписка — с YouTube, как в оригинальном приложении
    val st = remember(youtubeId) { RatingState(youtubeId) }
    LaunchedEffect(youtubeId, signedIn) { st.load() }
    val rating = st.rating
    val subscribed = st.subscribed
    val needSignIn = { needSignIn(context) }
    val rate = { r: YtRating -> if (!signedIn) needSignIn() else scope.launch { st.rate(r) } }
    Box(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 64.dp, bottom = 18.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val channelId = stream.channelId
                    Row(
                        Modifier.clickable(enabled = channelId != null) { channelId?.let { onChannel(it, stream.channel) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(info?.uploaderAvatars?.maxByOrNull { it.height }?.url, 32.dp)
                        Text(
                            "@" + stream.channel.removePrefix("@"), color = Color.White, style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp).weight(1f, fill = false),
                        )
                    }
                    Text(
                        tr(if (subscribed) R.string.yt_subscribed else R.string.yt_subscribe),
                        color = if (subscribed) Color.White else Color.Black, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (subscribed) Color.White.copy(alpha = 0.25f) else Color.White)
                            .clickable {
                                val cid = channelId
                                if (!signedIn || cid == null) needSignIn() else scope.launch { st.toggleSubscribe(cid) }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                if (stream.title.isNotEmpty()) {
                    Text(
                        stream.title, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp).clickable { onSheet(ShortSheet.DESCRIPTION) },
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val likes = info?.likeCount?.takeIf { it >= 0 }?.let { it + if (rating == YtRating.LIKE) 1 else 0 }
                ShortAction(if (rating == YtRating.LIKE) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp, likes?.let { NpSource.viewsCompact(it) } ?: tr(R.string.yt_like)) { rate(YtRating.LIKE) }
                ShortAction(if (rating == YtRating.DISLIKE) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown, tr(R.string.yt_dislike)) { rate(YtRating.DISLIKE) }
                ShortAction(Icons.AutoMirrored.Outlined.Comment, tr(R.string.yt_comments)) { onSheet(ShortSheet.COMMENTS) }
                ShortAction(Icons.Outlined.Share, tr(R.string.yt_share)) { share(context, "https://www.youtube.com/shorts/$youtubeId") }
                ShortAction(Icons.Rounded.Download, tr(R.string.yt_download), onClick = onDownload)
            }
        }
    }
}

/** Кнопка столбца Shorts: значок без фона с подписью под ним, как в YouTube. */
@Composable
private fun ShortAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Тонкая полоска хода ролика по нижнему краю. */
@Composable
private fun ShortProgress(player: ExoPlayer, modifier: Modifier) {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(player) {
        while (true) {
            val d = player.duration
            progress = if (d > 0) (player.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(100)
        }
    }
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.fillMaxWidth().height(2.dp),
        color = Color.White,
        trackColor = Color.White.copy(alpha = 0.25f),
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
