package com.offplayer.app.ui

import com.offplayer.app.tr
import com.offplayer.app.R

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
import androidx.compose.material.icons.rounded.SmartDisplay
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

enum class MainTab(@androidx.annotation.StringRes private val res: Int, val icon: ImageVector) {
    MUSIC(R.string.tab_music, Icons.Rounded.LibraryMusic),
    PODCASTS(R.string.tab_podcasts, Icons.Rounded.Podcasts),
    /** Необязательная вкладка — включается в настройках; стоит посередине меню. */
    YOUTUBE(R.string.tab_youtube, Icons.Rounded.SmartDisplay),
    VIDEO(R.string.tab_video, Icons.Rounded.VideoLibrary),
    DOWNLOADS(R.string.tab_downloads, Icons.Rounded.Download);

    val label: String get() = tr(res)
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
    val youtubeTab by AppSettings.youtubeTab.collectAsStateWithLifecycle()
    // Вкладка YouTube: включена в настройках и есть интернет (без сети прячется и возвращается сама)
    val online by com.offplayer.app.data.NetworkState.online.collectAsStateWithLifecycle()
    val tabs = remember(youtubeTab, online) { MainTab.entries.filter { it != MainTab.YOUTUBE || (youtubeTab && online) } }
    // Вкладка при запуске — из настроек (если она выключена — первая)
    val startTab = remember { MainTab.entries.firstOrNull { it.name == AppSettings.startTab.value }?.takeIf { it in tabs } ?: MainTab.MUSIC }
    val pagerState = rememberPagerState(initialPage = tabs.indexOf(startTab).coerceAtLeast(0)) { tabs.size }
    val tab = tabs.getOrElse(pagerState.currentPage) { tabs.last() }
    fun pageOf(t: MainTab) = tabs.indexOf(t).coerceAtLeast(0)
    // Вкладку YouTube включили или выключили — остаёмся на той же вкладке, хотя её номер сдвинулся
    var lastTab by rememberSaveable { mutableStateOf(startTab) }
    // Вложенный свайп (Shorts внутри YouTube) может оставить вкладки на полпути — доводим до ближайшей
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && kotlin.math.abs(pagerState.currentPageOffsetFraction) > 0.001f) {
            pagerState.animateScrollToPage(pagerState.currentPage)
        }
    }
    LaunchedEffect(tab) { lastTab = tab }
    val landscapeMain = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(tabs) { if (tabs.getOrNull(pagerState.currentPage) != lastTab) pagerState.scrollToPage(pageOf(lastTab)) }
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
        scope.launch { pagerState.scrollToPage(pageOf(target)) }
    }
    val openDownloads by UiEvents.openDownloads.collectAsStateWithLifecycle()
    LaunchedEffect(openDownloads) {
        if (openDownloads > 0) {
            playerExpanded = false
            if (videoOpen) VideoController.close(stopPlayback = false)
            pagerState.scrollToPage(pageOf(MainTab.DOWNLOADS))
        }
    }

    // «Скачать» в плеере ролика YouTube и в Shorts — на вкладку «Загрузки»; ролик сворачивается и играет дальше
    val ytDownload by com.offplayer.app.youtube.YouTubeNav.downloadRequest.collectAsStateWithLifecycle()
    LaunchedEffect(ytDownload) {
        val url = ytDownload ?: return@LaunchedEffect
        com.offplayer.app.youtube.YouTubeNav.downloadRequest.value = null
        pendingUrl = url
        if (videoOpen) VideoController.close(stopPlayback = false)
        pagerState.scrollToPage(pageOf(MainTab.DOWNLOADS))
    }
    // Уведомление о Shorts — вкладка YouTube, лента Shorts открывается самой вкладкой
    val ytShortsTab by com.offplayer.app.youtube.YouTubeNav.openShortsTab.collectAsStateWithLifecycle()
    LaunchedEffect(ytShortsTab) {
        if (ytShortsTab == 0) return@LaunchedEffect
        if (videoOpen) VideoController.close(stopPlayback = false)
        if (MainTab.YOUTUBE in tabs) pagerState.scrollToPage(pageOf(MainTab.YOUTUBE))
    }
    // Аватар канала в плеере — страница канала во вкладке YouTube (сам переход делает вкладка)
    val ytChannel by com.offplayer.app.youtube.YouTubeNav.channelRequest.collectAsStateWithLifecycle()
    LaunchedEffect(ytChannel) {
        if (ytChannel == null) return@LaunchedEffect
        if (videoOpen) VideoController.close(stopPlayback = false)
        if (MainTab.YOUTUBE in tabs) pagerState.scrollToPage(pageOf(MainTab.YOUTUBE))
    }

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            pendingUrl = sharedUrl
            playerExpanded = false
            pagerState.scrollToPage(pageOf(MainTab.DOWNLOADS))
            onSharedUrlConsumed()
        }
    }
    LaunchedEffect(openPlayerTick) {
        if (openPlayerTick > 0) {
            playerExpanded = true
            pagerState.scrollToPage(pageOf(if (playingPodcast) MainTab.PODCASTS else MainTab.MUSIC))
        }
    }
    LaunchedEffect(player.hasMedia) { if (!player.hasMedia) playerExpanded = false }

    // Иконки статус-бара: тёмные на светлой теме, светлые на тёмной и поверх видео
    val shortsOpen by com.offplayer.app.youtube.YouTubeNav.shortsOpen.collectAsStateWithLifecycle()
    val shortsChrome = shortsOpen && tab == MainTab.YOUTUBE
    val darkChrome = LocalDarkTheme.current || videoOpen || shortsChrome
    // Shorts всегда в портретной ориентации; ушли из Shorts — ориентация снова свободная (если не открыт видеоплеер)
    LaunchedEffect(shortsChrome) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        // Планшет может смотреть Shorts и в альбомной ориентации — ролик вписывается по высоте
        val tablet = activity.resources.configuration.smallestScreenWidthDp >= 600
        if (shortsChrome && !tablet) activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else if (!VideoController.open.value) activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
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
                        // Свёрнутый ролик YouTube играет дальше — его мини-плеер и на вкладке YouTube
                        // В ленте Shorts мини-плеер прячется под меню, как на вкладке «Музыка»
                        visible = (tab == MainTab.VIDEO || (tab == MainTab.YOUTUBE && !shortsChrome)) && videoUi.hasMedia && !videoOpen,
                        enter = slideInVertically(tween(350)) { it } + expandVertically(tween(350), expandFrom = Alignment.Top) + fadeIn(tween(250)),
                        exit = slideOutVertically(tween(300)) { it } + shrinkVertically(tween(300), shrinkTowards = Alignment.Top) + fadeOut(tween(250)),
                    ) {
                        VideoMiniPlayer(videoUi)
                    }
                    CompactNavBar(
                        tabs = tabs,
                        black = shortsChrome,
                        selected = tab,
                        onSelect = { t ->
                            // Повторное нажатие на вкладку YouTube — к началу «Главной», как в приложении YouTube
                            if (t == tab && t == MainTab.YOUTUBE) com.offplayer.app.youtube.YouTubeNav.homeTop.value++
                            scope.launch { pagerState.animateScrollToPage(pageOf(t)) }
                        },
                        modifier = Modifier.zIndex(1f),
                    )
                }
            },
        ) { padding ->
            // Свайп влево/вправо переключает вкладки
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = tabs.size - 1, // вкладки не теряют состояние
                key = { tabs.getOrElse(it) { MainTab.DOWNLOADS }.name },
                // В альбомной ориентации экран широкий — вкладка перелистывается уже на 20% ширины, а не на половине
                flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapPositionalThreshold = if (landscapeMain) 0.2f else 0.5f,
                ),
                modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            ) { page ->
                val active = page == pagerState.currentPage && !playerExpanded && !videoOpen
                CompositionLocalProvider(LocalScreenBackEnabled provides active) {
                    when (tabs.getOrElse(page) { MainTab.DOWNLOADS }) {
                        MainTab.MUSIC -> MusicScreen()
                        MainTab.VIDEO -> VideoScreen()
                        MainTab.PODCASTS -> com.offplayer.app.ui.podcast.PodcastScreen()
                        MainTab.YOUTUBE -> com.offplayer.app.ui.youtube.YouTubeScreen(active = active) { url ->
                            // Как «Поделиться» из приложения YouTube: ссылка уходит на вкладку «Загрузки»
                            pendingUrl = url
                            scope.launch { pagerState.animateScrollToPage(pageOf(MainTab.DOWNLOADS)) }
                        }
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
                    // Ролик YouTube сворачивается на вкладку YouTube, файл — на вкладку «Видео»
                    val stream = com.offplayer.app.youtube.YouTubeStreams.isStream(videoUi.videoId)
                    val target = if (stream && MainTab.YOUTUBE in tabs) MainTab.YOUTUBE else MainTab.VIDEO
                    scope.launch { pagerState.animateScrollToPage(pageOf(target)) }
                },
            )
        }
    }

    // Плашка «Загрузка завершена» внутри приложения (вместо уведомления в шторке)
    DownloadDoneBanner()
    // Проверка обновлений приложения и окно «Доступна новая версия»
    com.offplayer.app.update.UpdateDialogHost()

    BackHandler(playerExpanded && !videoOpen) { playerExpanded = false }
    // Настройки — поверх всего, выезжают справа
    com.offplayer.app.ui.settings.SettingsHost()
    BackHandler(videoOpen) { closeVideo() }

    DeleteMediaHost()
    StorageMoveHost()
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
                        Text(tr(R.string.action_open), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
private fun CompactNavBar(tabs: List<MainTab>, selected: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier, black: Boolean = false) {
    // Содержимое центрируется по всей высоте панели вместе с системной зоной жестов,
    // поэтому отступы над значками и под подписями одинаковые
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // В ленте Shorts меню чёрное в любой теме, как в YouTube
    Surface(color = if (black) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        // При навигации жестами полоска системы низкая и почти касается подписей —
        // тогда значки и подписи стоят над ней с собственным отступом
        val gestureNav = navInset in 1.dp..32.dp
        Row(
            Modifier
                .fillMaxWidth()
                .height(if (gestureNav) 54.dp + navInset else 52.dp + navInset)
                // Значки чуть отступают от верхнего края панели
                .padding(top = 4.dp, bottom = if (gestureNav) navInset else 0.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { t ->
                val isSelected = t == selected
                val color by animateColorAsState(
                    when {
                        black -> if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)
                        isSelected -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
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
                    Icon(t.icon, null, tint = color, modifier = Modifier.size(22.dp).scale(scale))
                    Spacer(Modifier.height(1.dp))
                    Text(
                        t.label,
                        color = color,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

