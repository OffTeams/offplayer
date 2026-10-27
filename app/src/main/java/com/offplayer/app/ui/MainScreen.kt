package com.offplayer.app.ui

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import com.offplayer.app.download.DownloadResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.offplayer.app.ui.theme.AppShapes
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offplayer.app.data.AppSettings
import com.offplayer.app.data.MediaRepository
import com.offplayer.app.playback.MusicController
import com.offplayer.app.ui.download.DownloadScreen
import com.offplayer.app.ui.music.MiniPlayer
import com.offplayer.app.ui.music.MusicScreen
import com.offplayer.app.ui.music.NowPlayingScreen
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import com.offplayer.app.ui.theme.LocalDarkTheme
import com.offplayer.app.ui.video.findActivity
import com.offplayer.app.ui.video.VideoPlayerScreen
import com.offplayer.app.ui.video.VideoMiniPlayer
import com.offplayer.app.ui.video.VideoScreen
import com.offplayer.app.video.VideoController
import kotlinx.coroutines.launch

enum class MainTab(val label: String, val icon: ImageVector) {
    MUSIC("Музыка", Icons.Rounded.LibraryMusic),
    PODCASTS("Подкасты", Icons.Rounded.Podcasts),
    VIDEO("Видео", Icons.Rounded.VideoLibrary),
    DOWNLOADS("Загрузки", Icons.Rounded.Download),
}

@Composable
fun MainScreen(
    sharedUrl: String?,
    onSharedUrlConsumed: () -> Unit,
    openPlayerTick: Int,
    inPip: Boolean,
    pipSupported: Boolean,
    onEnterPip: () -> Unit,
    onRotate: () -> Unit,
    rotateHint: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { MainTab.entries.size }
    val tab = MainTab.entries[pagerState.currentPage]
    var playerExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val player by MusicController.state.collectAsStateWithLifecycle()
    val videoOpen by VideoController.open.collectAsStateWithLifecycle()
    val videoUi by VideoController.ui.collectAsStateWithLifecycle()
    val addToPlaylist by UiEvents.addToPlaylist.collectAsStateWithLifecycle()
    // Подкаст играет через тот же плеер, но со своим мини-плеером (на вкладке «Подкасты») и своим экраном
    val podcasts by MediaRepository.podcasts.collectAsStateWithLifecycle()
    // Последнее состояние с треком: пока плеер и мини-плеер уезжают после остановки,
    // они показывают этот трек, а не пустой экран
    var shown by remember { mutableStateOf(player) }
    if (player.hasMedia) shown = player
    val currentTrack = remember(shown.trackId, podcasts) { shown.trackId?.let { MediaRepository.track(it) } }
    val playingPodcast = currentTrack?.isPodcast == true
    // Закрытие плеера: сначала плавно уезжает экран, затем останавливается воспроизведение
    val stopPlayer: () -> Unit = {
        playerExpanded = false
        scope.launch {
            kotlinx.coroutines.delay(380)
            MusicController.stop()
        }
    }

    // Доступ к памяти устройства не нужен: по умолчанию приложение работает только со своей медиатекой.
    // Спрашиваем лишь уведомления (Android 13+) — для плеера и загрузок.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val migration by MediaRepository.migration.collectAsStateWithLifecycle()

    // Нажатие на уведомление о загрузке: закрыть плееры и перейти на нужную вкладку
    val reveal by UiEvents.reveal.collectAsStateWithLifecycle()
    LaunchedEffect(reveal) {
        val r = reveal ?: return@LaunchedEffect
        playerExpanded = false
        if (videoOpen) VideoController.close(stopPlayback = false)
        val target = when (r.kind) {
            DownloadResult.Kind.TRACK, DownloadResult.Kind.MIX -> MainTab.MUSIC
            DownloadResult.Kind.VIDEO, DownloadResult.Kind.LIST -> MainTab.VIDEO
            DownloadResult.Kind.PODCAST -> MainTab.PODCASTS
        }
        if (r.kind == DownloadResult.Kind.PODCAST) UiEvents.reveal.value = null
        // Вкладка сама сбросит запрос — переход не должен прерываться вместе с эффектом
        scope.launch { pagerState.scrollToPage(target.ordinal) }
    }
    val openDownloads by UiEvents.openDownloads.collectAsStateWithLifecycle()
    LaunchedEffect(openDownloads) {
        if (openDownloads > 0) {
            playerExpanded = false
            if (videoOpen) VideoController.close(stopPlayback = false)
            pagerState.scrollToPage(MainTab.DOWNLOADS.ordinal)
        }
    }

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            pendingUrl = sharedUrl
            playerExpanded = false
            pagerState.scrollToPage(MainTab.DOWNLOADS.ordinal)
            onSharedUrlConsumed()
        }
    }
    LaunchedEffect(openPlayerTick) {
        if (openPlayerTick > 0) {
            playerExpanded = true
            pagerState.scrollToPage(if (playingPodcast) MainTab.PODCASTS.ordinal else MainTab.MUSIC.ordinal)
        }
    }
    LaunchedEffect(player.hasMedia) { if (!player.hasMedia) playerExpanded = false }

    // Иконки статус-бара: тёмные на светлой теме, светлые на тёмной и поверх видео
    val darkChrome = LocalDarkTheme.current || videoOpen
    LaunchedEffect(darkChrome) {
        val activity = context.findActivity() as? ComponentActivity ?: return@LaunchedEffect
        val style = if (darkChrome) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    // Открытие плеера убирает фокус с полей ввода — иначе всплывает клавиатура
    val focusManager = LocalFocusManager.current
    LaunchedEffect(playerExpanded, videoOpen) {
        if (playerExpanded || videoOpen) focusManager.clearFocus(force = true)
    }

    val closeVideo = { VideoController.close(stopPlayback = !AppSettings.videoBackground.value) }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                // Тень у всего нижнего блока (мини-плеер + меню) — ложится только на контент выше
                Column(Modifier.shadow(14.dp)) {
                    // Перенос загрузок прошлых версий во внутреннюю память
                    AnimatedVisibility(visible = migration != null) {
                        Text(
                            migration.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    // Мини-плеер живёт только на вкладке «Музыка»: на других вкладках
                    // он плавно «ныряет» под нижнее меню и выезжает обратно при возвращении
                    AnimatedVisibility(
                        visible = tab == MainTab.MUSIC && player.hasMedia && !playingPodcast,
                        enter = slideInVertically(tween(350)) { it } + expandVertically(tween(350), expandFrom = Alignment.Top) + fadeIn(tween(250)),
                        exit = slideOutVertically(tween(300)) { it } + shrinkVertically(tween(300), shrinkTowards = Alignment.Top) + fadeOut(tween(250)),
                    ) {
                        MiniPlayer(shown, onExpand = { playerExpanded = true })
                    }
                    AnimatedVisibility(
                        visible = tab == MainTab.PODCASTS && player.hasMedia && playingPodcast,
                        enter = slideInVertically(tween(350)) { it } + expandVertically(tween(350), expandFrom = Alignment.Top) + fadeIn(tween(250)),
                        exit = slideOutVertically(tween(300)) { it } + shrinkVertically(tween(300), shrinkTowards = Alignment.Top) + fadeOut(tween(250)),
                    ) {
                        com.offplayer.app.ui.podcast.PodcastMiniPlayer(shown, currentTrack, onExpand = { playerExpanded = true })
                    }
                    // Видео, играющее в фоне, — свой мини-плеер на вкладке «Видео»
                    AnimatedVisibility(
                        visible = tab == MainTab.VIDEO && videoUi.hasMedia && !videoOpen,
                        enter = slideInVertically(tween(350)) { it } + expandVertically(tween(350), expandFrom = Alignment.Top) + fadeIn(tween(250)),
                        exit = slideOutVertically(tween(300)) { it } + shrinkVertically(tween(300), shrinkTowards = Alignment.Top) + fadeOut(tween(250)),
                    ) {
                        VideoMiniPlayer(videoUi)
                    }
                    CompactNavBar(
                        selected = tab,
                        onSelect = { t -> scope.launch { pagerState.animateScrollToPage(t.ordinal) } },
                        modifier = Modifier.zIndex(1f),
                    )
                }
            },
        ) { padding ->
            // Свайп влево/вправо переключает вкладки
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = MainTab.entries.size - 1, // вкладки не теряют состояние
                key = { it },
                modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            ) { page ->
                val active = page == pagerState.currentPage && !playerExpanded && !videoOpen
                CompositionLocalProvider(LocalScreenBackEnabled provides active) {
                    when (MainTab.entries[page]) {
                        MainTab.MUSIC -> MusicScreen()
                        MainTab.VIDEO -> VideoScreen()
                        MainTab.PODCASTS -> com.offplayer.app.ui.podcast.PodcastScreen()
                        MainTab.DOWNLOADS -> DownloadScreen(pendingUrl, onUrlConsumed = { pendingUrl = null })
                    }
                }
            }
        }

        // Плееры выезжают и уезжают так же плавно, как мини-плеер: сдвиг вниз с мягким растворением
        AnimatedVisibility(
            visible = playerExpanded,
            enter = slideInVertically(tween(350, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(250)),
        ) {
            if (playingPodcast) {
                com.offplayer.app.ui.podcast.PodcastPlayerScreen(shown, currentTrack, onCollapse = { playerExpanded = false }, onStop = stopPlayer)
            } else {
                NowPlayingScreen(shown, onCollapse = { playerExpanded = false }, onStop = stopPlayer)
            }
        }

        // Видеоплеер — поверх всего; в режиме «картинка в картинке» заполняет всё окно
        AnimatedVisibility(
            visible = videoOpen,
            enter = slideInVertically(tween(350, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(250)),
        ) {
            VideoPlayerScreen(
                inPip = inPip,
                pipSupported = pipSupported,
                onClose = closeVideo,
                onPip = onEnterPip,
                onRotate = onRotate,
                rotateHint = rotateHint,
                onStop = { VideoController.close(stopPlayback = true) },
                // Свайп вниз — в мини-плеер на вкладке «Видео», звук продолжает играть
                onMinimize = {
                    VideoController.close(stopPlayback = false)
                    scope.launch { pagerState.animateScrollToPage(MainTab.VIDEO.ordinal) }
                },
            )
        }
    }

    // Плашка «Загрузка завершена» внутри приложения (вместо уведомления в шторке)
    DownloadDoneBanner()

    BackHandler(playerExpanded && !videoOpen) { playerExpanded = false }
    // Настройки — поверх всего, выезжают справа
    com.offplayer.app.ui.settings.SettingsHost()
    BackHandler(videoOpen) { closeVideo() }

    DeleteMediaHost()
    // Палитры текущего и соседних треков считаются заранее — цвет готов до показа обложки
    com.offplayer.app.ui.music.PrefetchArtColors(player)

    addToPlaylist?.let { ids ->
        AddToPlaylistDialog(ids, onDismiss = { UiEvents.addToPlaylist.value = null })
    }
}

/**
 * Короткая плашка сверху о завершении загрузки, пока приложение открыто.
 * Нажатие ведёт к скачанному (как уведомление), сама скрывается через 4 секунды.
 */
@Composable
private fun DownloadDoneBanner() {
    var banner by remember { mutableStateOf<DownloadDoneEvent?>(null) }
    var shown by remember { mutableStateOf<DownloadDoneEvent?>(null) }
    LaunchedEffect(Unit) { UiEvents.downloadDone.collect { banner = it } }
    LaunchedEffect(banner) {
        if (banner != null) {
            shown = banner
            kotlinx.coroutines.delay(4000)
            banner = null
        }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = banner != null,
            enter = slideInVertically(tween(350, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(250)),
        ) {
            val e = shown ?: return@AnimatedVisibility
            Surface(
                shape = AppShapes.Card,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .clickable {
                        e.result?.let { UiEvents.reveal.value = RevealRequest(it.kind, it.id) }
                        banner = null
                    },
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (e.success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null,
                        tint = if (e.success) Color(0xFF34A853) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(26.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            e.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            e.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (e.result != null) {
                        Spacer(Modifier.width(8.dp))
                        Text("Открыть", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/**
 * Компактное нижнее меню в стиле YouTube: 56dp, значок + маленькая подпись,
 * выбранная вкладка выделена цветом и жирной подписью. Боковые отступы — чтобы
 * крайние значки не прижимались к краям экрана.
 */
@Composable
private fun CompactNavBar(selected: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    // Содержимое центрируется по всей высоте панели вместе с системной зоной жестов,
    // поэтому отступы над значками и под подписями одинаковые
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        // При навигации жестами полоска системы низкая и почти касается подписей —
        // тогда значки и подписи стоят над ней с собственным отступом
        val gestureNav = navInset in 1.dp..32.dp
        Row(
            Modifier
                .fillMaxWidth()
                .height(if (gestureNav) 64.dp + navInset else 60.dp + navInset)
                // Значки чуть отступают от верхнего края панели
                .padding(top = 8.dp, bottom = if (gestureNav) navInset else 0.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainTab.entries.forEach { t ->
                val isSelected = t == selected
                val color by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "navColor",
                )
                val scale by animateFloatAsState(if (isSelected) 1.08f else 1f, label = "navScale")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // Без подсветки-«волны»: выбор показывает только цвет значка и подписи
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(t.icon, null, tint = color, modifier = Modifier.size(24.dp).scale(scale))
                    Spacer(Modifier.height(2.dp))
                    Text(
                        t.label,
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

