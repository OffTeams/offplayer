package com.offplayer.app.ui.video

import com.offplayer.app.tr

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.offplayer.app.R
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.HeadsetOff
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.offplayer.app.data.AppSettings
import com.offplayer.app.data.SbAction
import com.offplayer.app.ui.theme.Accent
import com.offplayer.app.ui.theme.AppShapes
import com.offplayer.app.video.VideoController
import com.offplayer.app.data.VideoMetaStore
import com.offplayer.app.model.SponsorSegment
import androidx.media3.ui.DefaultTimeBar
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Comment
import kotlinx.coroutines.Job
import android.view.GestureDetector
import android.view.LayoutInflater
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.material.icons.rounded.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** EXPAND — свайп вверх по центру: открыть описание ролика YouTube. */
enum class GestureZone { BRIGHTNESS, VOLUME, COLLAPSE, EXPAND }

/** Боковая панель видеоплеера. */
enum class PlayerPanel { NONE, CHAPTERS, QUEUE, DETAILS }

/** Подсказка после двойного нажатия. */
sealed interface TapHint {
    data class Seek(val forward: Boolean, val seconds: Int) : TapHint
    data class Fill(val fill: Boolean) : TapHint
}

/**
 * Обёртка над PlayerView, перехватывающая вертикальные свайпы:
 * левая треть экрана — яркость, правая — громкость, центр — свайп вниз сворачивает плеер.
 * Тапы и горизонтальные движения уходят в PlayerView как обычно.
 */
@SuppressLint("ViewConstructor")
class PlayerGestureLayout(context: Context, val playerView: PlayerView) : FrameLayout(context) {
    interface Listener {
        fun currentLevel(zone: GestureZone): Float
        fun onLevel(zone: GestureZone, level: Float)
        fun onCollapseDrag(offset: Float)
        fun onGestureEnd(zone: GestureZone, collapse: Boolean)
    }

    var listener: Listener? = null
    var gesturesEnabled = true
    /** Любое касание экрана — перезапускает таймер скрытия интерфейса. */
    var onAnyTouch: (() -> Unit)? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN || ev.actionMasked == MotionEvent.ACTION_UP) onAnyTouch?.invoke()
        return super.dispatchTouchEvent(ev)
    }

    private val slop = ViewConfiguration.get(context).scaledTouchSlop * 2
    private var downX = 0f
    private var downY = 0f
    private var zone: GestureZone? = null
    private var startLevel = 0f

    init {
        addView(playerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!gesturesEnabled) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                zone = null
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                // Края сверху/снизу оставляем системным жестам
                val inSystemEdge = downY < height * 0.07f || downY > height * 0.93f
                if (zone == null && !inSystemEdge && abs(dy) > slop && abs(dy) > abs(dx) * 1.5f) {
                    // Края по 20% — яркость и громкость, центральные 60% — свайп сворачивания
                    val z = when {
                        downX < width * 0.2f -> GestureZone.BRIGHTNESS
                        downX > width * 0.8f -> GestureZone.VOLUME
                        dy > 0 -> GestureZone.COLLAPSE
                        else -> GestureZone.EXPAND
                    }
                    zone = z
                    startLevel = listener?.currentLevel(z) ?: 0f
                    downY = ev.y // отсчёт от момента захвата — без скачка
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val z = zone ?: return super.onTouchEvent(ev)
        val dy = ev.y - downY
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> when (z) {
                GestureZone.COLLAPSE -> listener?.onCollapseDrag(dy.coerceAtLeast(0f))
                GestureZone.EXPAND -> Unit
                else -> listener?.onLevel(z, (startLevel - dy / (height * 0.65f)).coerceIn(0f, 1f))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val up = ev.actionMasked == MotionEvent.ACTION_UP
                // collapse — жест доведён: вниз на 18% высоты (свернуть) или вверх на 12% (описание)
                val collapse = (z == GestureZone.COLLAPSE && up && dy > height * 0.18f) || (z == GestureZone.EXPAND && up && dy < -height * 0.12f)
                listener?.onGestureEnd(z, collapse)
                zone = null
            }
        }
        return true
    }
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun VideoPlayerScreen(
    inPip: Boolean,
    pipSupported: Boolean,
    onClose: () -> Unit,
    onPip: () -> Unit,
    onRotate: () -> Unit,
    /** Телефон держат не так, как показано видео, — показать кнопку поворота. */
    rotateHint: Boolean,
    onStop: () -> Unit,
    onMinimize: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val scope = rememberCoroutineScope()
    val controller by VideoController.controller.collectAsStateWithLifecycle()
    val background by AppSettings.videoBackground.collectAsStateWithLifecycle()

    // Эпизоды и SponsorBlock для текущего видео (офлайн-данные)
    val ui by VideoController.ui.collectAsStateWithLifecycle()
    val metaMap by VideoMetaStore.meta.collectAsStateWithLifecycle()
    val showChaptersSetting by AppSettings.showChapters.collectAsStateWithLifecycle()
    val sbActions by AppSettings.sbActions.collectAsStateWithLifecycle()
    val seekStep by AppSettings.seekStep.collectAsStateWithLifecycle()
    val meta = ui.videoId?.let { metaMap[it] }
    val chapters = if (showChaptersSetting) meta?.chapters.orEmpty() else emptyList()
    val currentChapter = chapters.lastOrNull { ui.positionMs >= it.startMs }
    // Боковая панель: эпизоды текущего видео или очередь проигрываемого списка
    var panel by remember { mutableStateOf(PlayerPanel.NONE) }
    val chaptersOpen = panel == PlayerPanel.CHAPTERS
    // Ролик YouTube, играющий онлайн: у него есть описание, оценки и комментарии
    val ytId = ui.videoId?.let { com.offplayer.app.youtube.YouTubeStreams.youtubeIdOf(it) }
    val hasQueue = ui.queue.size > 1
    var skipNotice by remember { mutableStateOf<SponsorSegment?>(null) }
    LaunchedEffect(Unit) { VideoController.skipped.collect { skipNotice = it } }
    LaunchedEffect(skipNotice) {
        if (skipNotice != null) {
            delay(4000)
            skipNotice = null
        }
    }
    LaunchedEffect(ui.videoId) { if (panel == PlayerPanel.CHAPTERS) panel = PlayerPanel.NONE }
    LaunchedEffect(hasQueue) { if (!hasQueue && panel == PlayerPanel.QUEUE) panel = PlayerPanel.NONE }
    // Начала и концы отрезков — отметки на полосе перемотки
    // Отрезки SponsorBlock закрашены на полосе перемотки от начала до конца: полоса рисуется
    // плотным рядом отметок (ширина полосы известна после раскладки)
    var timeBarWidth by remember { mutableIntStateOf(0) }
    val density0 = LocalDensity.current
    val markerTimes = remember(meta, sbActions, ui.durationMs, timeBarWidth) {
        val segs = meta?.segments?.filter { (sbActions[it.category] ?: SbAction.OFF) != SbAction.OFF }.orEmpty()
        val barPx = timeBarWidth - with(density0) { 16.dp.toPx() } // минус поля бегунка по краям
        val markerPx = with(density0) { 4.dp.toPx() }
        if (ui.durationMs <= 0 || barPx <= 0f) {
            segs.flatMap { listOf(it.startMs, it.endMs) }.toLongArray()
        } else {
            val step = (ui.durationMs * markerPx * 0.6f / barPx).toLong().coerceAtLeast(1)
            val half = (ui.durationMs * markerPx / 2f / barPx).toLong()
            segs.flatMap { s ->
                val from = s.startMs + half
                val to = (s.endMs - half).coerceAtLeast(from)
                generateSequence(from) { it + step }.takeWhile { it < to }.toList() + to
            }.toLongArray()
        }
    }

    // Верхняя панель показывается строго вместе со встроенными элементами управления PlayerView
    var controlsVisible by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var touchTick by remember { mutableIntStateOf(0) }
    // Автоскрытие через 3,5 с во время воспроизведения — тем же вызовом, что и по тапу,
    // поэтому анимация одинаковая. Любое касание перезапускает таймер.
    LaunchedEffect(controlsVisible, touchTick, ui.isPlaying, panel, settingsOpen) {
        if (controlsVisible && ui.isPlaying && panel == PlayerPanel.NONE && !settingsOpen) {
            delay(3500)
            playerViewRef?.hideController()
        }
    }
    var title by remember { mutableStateOf("") }
    val indicator = remember { mutableStateOf<Pair<GestureZone, Float>?>(null) }
    val collapseOffset = remember { Animatable(0f) }
    var hideJob by remember { mutableStateOf<Job?>(null) }

    // Свайп вниз из центра: свернуть в мини-плеер, видео продолжает играть в фоне
    val doCollapse by rememberUpdatedState(onMinimize)
    val rotateNow by rememberUpdatedState(onRotate)
    // Описание поверх альбомного видео на планшете (на телефоне описание — в портретной ориентации)
    var tabletDetails by remember { mutableStateOf(false) }
    val tablet = androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp >= 600
    LaunchedEffect(ui.videoId) { tabletDetails = tablet }
    val landscapeNow = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    // Свайп вверх по центру: телефон (меньше 7 дюймов) поворачивается в портрет — там страница ролика;
    // планшет остаётся в альбомной ориентации, описание открывается панелью справа
    val openDetailsNow by rememberUpdatedState {
        if (ytId != null) {
            when {
                // Миниатюра на странице ролика: свайп вверх — во весь экран
                !landscapeNow -> rotateNow()
                // Планшет: со страницы ролика свайп вверх — видео во весь экран, из полноэкранного — страница ролика
                tablet -> tabletDetails = !tabletDetails
                else -> rotateNow()
            }
        }
    }
    androidx.activity.compose.BackHandler(tabletDetails && !tablet) { tabletDetails = false }
    // Кнопка «на весь экран»: на телефоне — поворот, на планшете — страница ролика или видео во весь экран
    // Одно нажатие может прийти дважды (второй раз — с обратным значением): повтор в течение 700 мс пропускаем
    var lastFullscreenAt by remember { mutableStateOf(0L) }
    val fullscreenNow by rememberUpdatedState { wantFull: Boolean ->
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastFullscreenAt < 700) return@rememberUpdatedState
        lastFullscreenAt = now
        if (tablet && ytId != null) tabletDetails = !wantFull
        else if (wantFull != landscapeNow) rotateNow()
    }

    // Двойное нажатие: по краям (зоны яркости/громкости) — перемотка на 10 с, в центре — заполнение экрана
    var fillScreen by remember { mutableStateOf(AppSettings.videoFill.value) }
    val tapHint = remember { mutableStateOf<TapHint?>(null) }
    var tapHintJob by remember { mutableStateOf<Job?>(null) }
    val onDoubleTap by rememberUpdatedState { fx: Float ->
        val c = controller
        val hint = when {
            fx < 0.2f || fx > 0.8f -> {
                val forward = fx > 0.8f
                if (c != null) {
                    val target = (c.currentPosition + (if (forward) 1 else -1) * seekStep * 1000L)
                        .coerceIn(0L, c.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
                    c.seekTo(target)
                }
                // Серия двойных нажатий накапливает время: 10, 20, 30 с…
                val prev = tapHint.value
                val total = if (prev is TapHint.Seek && prev.forward == forward) prev.seconds + seekStep else seekStep
                TapHint.Seek(forward, total)
            }
            else -> {
                fillScreen = !fillScreen
                TapHint.Fill(fillScreen)
            }
        }
        tapHint.value = hint
        tapHintJob?.cancel()
        tapHintJob = scope.launch {
            delay(900)
            tapHint.value = null
        }
    }

    DisposableEffect(controller) {
        val c = controller
        val listener = object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                title = mediaMetadata.title?.toString().orEmpty()
            }
        }
        c?.addListener(listener)
        title = c?.mediaMetadata?.title?.toString().orEmpty()
        onDispose { c?.removeListener(listener) }
    }

    val gestureListener = remember(activity) {
        object : PlayerGestureLayout.Listener {
            override fun currentLevel(zone: GestureZone): Float = when (zone) {
                GestureZone.VOLUME -> {
                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
                }
                GestureZone.BRIGHTNESS -> {
                    val b = activity?.window?.attributes?.screenBrightness ?: -1f
                    if (b >= 0f) b else runCatching {
                        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
                    }.getOrDefault(0.5f).coerceIn(0f, 1f)
                }
                GestureZone.COLLAPSE, GestureZone.EXPAND -> 0f
            }

            override fun onLevel(zone: GestureZone, level: Float) {
                hideJob?.cancel()
                when (zone) {
                    GestureZone.VOLUME -> {
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (level * max).roundToInt(), 0)
                    }
                    GestureZone.BRIGHTNESS -> activity?.window?.let { w ->
                        w.attributes = w.attributes.apply { screenBrightness = level.coerceAtLeast(0.01f) }
                    }
                    GestureZone.COLLAPSE, GestureZone.EXPAND -> Unit
                }
                indicator.value = zone to level
            }

            override fun onCollapseDrag(offset: Float) {
                scope.launch { collapseOffset.snapTo(offset) }
            }

            override fun onGestureEnd(zone: GestureZone, collapse: Boolean) {
                if (zone == GestureZone.EXPAND) {
                    if (collapse) openDetailsNow()
                } else if (zone == GestureZone.COLLAPSE) {
                    // Свернули — плеер уходит вниз как есть (без возврата картинки на место перед закрытием);
                    // не дотянули — картинка плавно возвращается
                    if (collapse) doCollapse() else scope.launch { collapseOffset.animateTo(0f) }
                } else {
                    hideJob = scope.launch {
                        delay(700)
                        indicator.value = null
                    }
                }
            }
        }
    }

    // Безопасная зона: у каждой стороны свой отступ — вырез камеры на этой стороне,
    // но не меньше 20dp, чтобы скруглённые углы экрана не «съедали» элементы управления.
    // Так панель управления растянута почти до краёв.
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safe = WindowInsets.safeDrawing
    // Миниатюра на странице ролика (портрет) — управление почти у краёв; во весь экран — прежние поля
    val slim = ytId != null && !landscapeNow && !inPip
    val cornerPx = with(density) { (if (slim) 6.dp else 20.dp).roundToPx() }
    val startPx = if (inPip) 0 else maxOf(safe.getLeft(density, layoutDirection), cornerPx)
    val edgeEndPx = if (inPip) 0 else maxOf(safe.getRight(density, layoutDirection), cornerPx)
    val topPx = if (inPip) 0 else safe.getTop(density)
    val bottomPx = if (inPip) 0 else safe.getBottom(density) + with(density) { 4.dp.roundToPx() }
    val timelineDropPx = with(density) { 8.dp.roundToPx() }
    val startDp = with(density) { startPx.toDp() }
    val topDp = with(density) { topPx.toDp() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Панель «Эпизоды» не перекрывает видео: видео плавно ужимается и сдвигается
        // (влево в горизонтальной ориентации, вверх — в вертикальной), занимая пустые поля
        val landscape = maxWidth > maxHeight
        // Планшет в альбомной ориентации: видео слева сверху, под ним страница ролика, справа — похожие ролики
        val tabletWatch = tablet && landscape && ytId != null && tabletDetails && !inPip
        val panelWidth = when {
            tabletWatch -> maxWidth * 0.32f
            tabletDetails && ytId != null -> (maxWidth * 0.38f).coerceAtMost(560.dp)
            else -> (maxWidth * 0.36f).coerceAtMost(380.dp)
        }
        val tabletPageHeight = (maxHeight - topDp - (maxWidth - panelWidth) * 9f / 16f).coerceAtLeast(0.dp)
        // Ролик YouTube в вертикальной ориентации — как страница ролика в приложении YouTube:
        // видео 16:9 сверху, под ним название, канал, оценки, описание и комментарии. Повернули — видео на весь экран
        val watchPage = ytId != null && !inPip && (!landscape || tabletDetails)
        val panelHeight = if (watchPage) (maxHeight - topDp - maxWidth * 9f / 16f).coerceAtLeast(0.dp) else maxHeight * 0.5f
        val panelVisible = !inPip && ((panel == PlayerPanel.CHAPTERS && chapters.isNotEmpty()) || (panel == PlayerPanel.QUEUE && hasQueue) || watchPage)
        val endInset by animateDpAsState(if (panelVisible && landscape) panelWidth else 0.dp, tween(350), label = "endInset")
        val bottomInset by animateDpAsState(
            when {
                tabletWatch -> tabletPageHeight
                panelVisible && !landscape -> panelHeight
                else -> 0.dp
            },
            tween(350), label = "bottomInset",
        )
        val watchTopInset by animateDpAsState(if (watchPage) topDp else 0.dp, tween(350), label = "watchTopInset")
        // Справа от управления — край экрана или (при открытых эпизодах) сама панель
        val endPx = if (panelVisible && landscape) with(density) { 12.dp.roundToPx() } else edgeEndPx
        val endDp = with(density) { endPx.toDp() }

        AndroidView(
            factory = { ctx ->
                val pv = (LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView).apply {
                    setShowNextButton(true)
                    setShowPreviousButton(true)
                    // Перемотка — двойным нажатием по краям экрана, отдельные кнопки не нужны
                    setShowRewindButton(false)
                    setShowFastForwardButton(false)
                    setShowSubtitleButton(true)
                    // Кнопка «на весь экран» в панели управления: поворот между альбомной и портретной ориентацией
                    // Обработчик вызывается и при смене значка (setFullscreenButtonState) — поворачиваем,
                    // только если просят не ту ориентацию, что сейчас, иначе экран крутился бы по кругу
                    setFullscreenButtonClickListener { wantFull -> fullscreenNow(wantFull) }
                    keepScreenOn = true
                    // Встроенный автотаймер скрывает интерфейс в два этапа (сначала кнопки, потом полосу).
                    // Отключаем его: скрываем сами тем же способом, что и по тапу — одной анимацией.
                    controllerShowTimeoutMs = 0
                    // Не показывать панели автоматически при старте/буферизации
                    controllerAutoShow = false
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
                        controlsVisible = v == View.VISIBLE
                    })
                }
                playerViewRef = pv
                // Одиночное нажатие показывает/скрывает интерфейс, двойное — перемотка или заполнение экрана.
                // Срабатывает только на «пустом» месте: кнопки панели получают нажатия сами.
                val detector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent) = true
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        if (pv.isControllerFullyVisible) pv.hideController() else pv.showController()
                        return true
                    }
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        if (pv.useController) onDoubleTap(e.x / pv.width.coerceAtLeast(1))
                        return true
                    }
                })
                pv.setOnTouchListener { _, ev -> detector.onTouchEvent(ev); true }
                pv.findViewById<View>(androidx.media3.ui.R.id.exo_progress)?.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    if (v.width != timeBarWidth) timeBarWidth = v.width
                }
                // Полоса перемотки реагирует только на касание самой линии (±8 dp от неё): нажатие рядом
                // с ней — как по пустому месту (скрыть интерфейс), а не перемотка
                pv.findViewById<View>(androidx.media3.ui.R.id.exo_progress)?.let { bar ->
                    val band = 8f * ctx.resources.displayMetrics.density
                    var ignore = false
                    bar.setOnTouchListener { v, ev ->
                        if (ev.actionMasked == MotionEvent.ACTION_DOWN) ignore = abs(ev.y - v.height / 2f) > band
                        if (ignore && ev.actionMasked == MotionEvent.ACTION_UP) pv.hideController()
                        ignore
                    }
                }
                PlayerGestureLayout(ctx, pv).apply { onAnyTouch = { touchTick++ } }
            },
            update = { layout ->
                // Значок кнопки: «развернуть» в портретной ориентации, «свернуть» в альбомной
                layout.playerView.setFullscreenButtonState(if (tablet && ytId != null) !watchPage else landscape)
                layout.listener = gestureListener
                layout.gesturesEnabled = !inPip
                if (layout.playerView.player !== controller) {
                    layout.playerView.player = controller
                    // При открытии интерфейс скрыт — он появляется только по тапу
                    layout.playerView.hideController()
                }
                layout.playerView.useController = !inPip
                layout.playerView.resizeMode = if (fillScreen && !inPip) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                // Скорость и дорожки — в наших настройках плеера, встроенная шестерёнка скрыта
                layout.playerView.findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.visibility = View.GONE
                if (inPip) layout.playerView.hideController()
                // Отступы от выреза только у нижней панели и полосы перемотки: общее затемнение
                // остаётся на весь экран, а тёмную плашку под нижней панелью убираем
                val pv = layout.playerView
                // Отрезки SponsorBlock отмечены на полосе перемотки фирменным зелёным
                if (!markerTimes.contentEquals(pv.getTag(R.id.tag_sb_markers) as? LongArray)) {
                    pv.setTag(R.id.tag_sb_markers, markerTimes)
                    pv.setExtraAdGroupMarkers(markerTimes, BooleanArray(markerTimes.size))
                }
                pv.findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.let { tb ->
                    tb.setAdMarkerColor(SponsorGreen.toArgb())
                    tb.setPlayedAdMarkerColor(SponsorGreen.copy(alpha = 0.6f).toArgb())
                }
                pv.findViewById<View>(androidx.media3.ui.R.id.exo_bottom_bar)?.let { bar ->
                    bar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    bar.setPadding(startPx, 0, endPx, 0)
                }
                pv.findViewById<View>(androidx.media3.ui.R.id.exo_progress)?.let { bar ->
                    (bar.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                        val baseBottom = (bar.getTag(R.id.tag_base_margin) as? Int)
                            ?: lp.bottomMargin.also { bar.setTag(R.id.tag_base_margin, it) }
                        // Полоса перемотки чуть ниже — ближе к времени и кнопкам нижней панели
                        val target = (baseBottom - timelineDropPx + bottomPx).coerceAtLeast(0)
                        if (lp.leftMargin != startPx || lp.rightMargin != endPx || lp.bottomMargin != target) {
                            lp.leftMargin = startPx
                            lp.rightMargin = endPx
                            lp.bottomMargin = target
                            bar.layoutParams = lp
                        }
                    }
                }
                pv.findViewById<View>(androidx.media3.ui.R.id.exo_bottom_bar)?.let { bar ->
                    (bar.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                        if (lp.bottomMargin != bottomPx) {
                            lp.bottomMargin = bottomPx
                            bar.layoutParams = lp
                        }
                    }
                }
            },
            onRelease = { it.playerView.player = null },
            modifier = Modifier
                .fillMaxSize()
                // На странице ролика видео начинается под строкой состояния — не заходит на камеру
                .padding(top = watchTopInset, end = endInset, bottom = bottomInset)
                .graphicsLayer {
                    val p = (collapseOffset.value / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    translationY = collapseOffset.value
                    scaleX = 1f - p * 0.3f
                    scaleY = 1f - p * 0.3f
                    alpha = 1f - p * 0.6f
                },
        )

        // Индикатор яркости / громкости
        indicator.value?.let { (zone, level) ->
            if (!inPip) GestureIndicator(zone, level, Modifier.align(Alignment.Center))
        }
        // Подсказка двойного нажатия: «‹‹ 10 с» слева, «10 с ››» справа, режим заполнения — в центре
        tapHint.value?.let { h ->
            if (!inPip) {
                val align = when {
                    h is TapHint.Seek && h.forward -> Alignment.CenterEnd
                    h is TapHint.Seek -> Alignment.CenterStart
                    else -> Alignment.Center
                }
                DoubleTapHint(h, Modifier.align(align).padding(horizontal = startDp + 24.dp))
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && !inPip,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(end = endInset),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .padding(start = startDp, end = endDp, top = topDp)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, tr(R.string.action_collapse), tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    // Текущий эпизод под названием, как на YouTube
                    currentChapter?.let {
                        Text(
                            it.title, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (hasQueue) {
                    IconButton(onClick = { panel = if (panel == PlayerPanel.QUEUE) PlayerPanel.NONE else PlayerPanel.QUEUE }) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, tr(R.string.list), tint = if (panel == PlayerPanel.QUEUE) Accent else Color.White)
                    }
                }
                if (chapters.isNotEmpty()) {
                    IconButton(onClick = { panel = if (chaptersOpen) PlayerPanel.NONE else PlayerPanel.CHAPTERS }) {
                        Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, tr(R.string.chapters), tint = if (chaptersOpen) Accent else Color.White)
                    }
                }
                // Скорость, фоновое воспроизведение, «картинка в картинке»
                IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Rounded.Settings, tr(R.string.player_settings), tint = Color.White) }
                // Стрелка вниз сворачивает видео в фон, крестик — закрывает полностью
                IconButton(onClick = onStop) { Icon(Icons.Rounded.Close, tr(R.string.close_video), tint = Color.White) }
            }
        }

        if (!inPip) {
            // SponsorBlock: кнопка ручного пропуска (если автопропуск выключен)
            val manual = ui.activeSegment
            AnimatedVisibility(
                visible = manual != null,
                enter = fadeIn() + slideInHorizontally { it / 2 },
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = endDp + 8.dp + endInset, bottom = 112.dp + bottomInset),
            ) {
                manual?.let { seg -> SkipSegmentButton(seg, onSkip = { VideoController.skipSegment(seg) }) }
            }
            // Кнопка поворота в правом нижнем углу — когда телефон повернули при включённом автоповороте
            var rotateVisible by remember { mutableStateOf(false) }
            LaunchedEffect(rotateHint) {
                rotateVisible = rotateHint
                if (rotateHint) {
                    delay(5000)
                    rotateVisible = false
                }
            }
            AnimatedVisibility(
                visible = rotateVisible && !panelVisible,
                enter = fadeIn() + androidx.compose.animation.scaleIn(),
                exit = fadeOut() + androidx.compose.animation.scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = endDp + 8.dp, bottom = (if (manual != null) 170.dp else 112.dp) + bottomInset),
            ) {
                IconButton(
                    onClick = onRotate,
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color.Black.copy(alpha = 0.6f), AppShapes.Pill),
                ) {
                    Icon(Icons.Rounded.ScreenRotation, tr(R.string.rotate_screen), tint = Color.White)
                }
            }
            // …и плашка после автопропуска с возможностью вернуться
            AnimatedVisibility(
                visible = skipNotice != null,
                enter = fadeIn() + slideInHorizontally { -it / 2 },
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = startDp + 8.dp, bottom = 112.dp + bottomInset),
            ) {
                skipNotice?.let { seg ->
                    SkippedNotice(seg, onUndo = {
                        VideoController.undoSkip(seg)
                        skipNotice = null
                    })
                }
            }

            // Планшет: под видео слева — название, кнопки и комментарии (похожие ролики — в колонке справа)
            if (tabletWatch && ytId != null) {
                com.offplayer.app.ui.youtube.WatchPage(
                    ytId, showRelated = false, sidePanel = true,
                    modifier = Modifier.align(Alignment.BottomStart).width(maxWidth - panelWidth).height(tabletPageHeight),
                )
            }
            // Панель «Эпизоды» встаёт на освободившееся место: справа или снизу
            AnimatedVisibility(
                visible = panelVisible,
                enter = (if (landscape) slideInHorizontally(tween(350)) { it } else slideInVertically(tween(350)) { it }) + fadeIn(tween(250)),
                exit = (if (landscape) slideOutHorizontally(tween(350)) { it } else slideOutVertically(tween(350)) { it }) + fadeOut(tween(250)),
                modifier = Modifier.align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter),
            ) {
                // Панель примыкает к краю экрана; внутренние отступы — от выреза, статус-бара и скруглённых углов
                val edgeEndDp = with(density) { edgeEndPx.toDp() }
                val bottomSafeDp = with(density) { bottomPx.toDp() }
                val panelPadding = if (landscape) {
                    PaddingValues(top = topDp + 4.dp, end = (edgeEndDp - 8.dp).coerceAtLeast(0.dp), bottom = bottomSafeDp)
                } else {
                    PaddingValues(top = 4.dp, bottom = bottomSafeDp)
                }
                val panelModifier = if (landscape) {
                    Modifier
                        .fillMaxHeight()
                        .width(panelWidth)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(panelHeight)
                }
                // Последняя открытая панель остаётся на экране во время анимации скрытия
                var shownPanel by remember { mutableStateOf(PlayerPanel.CHAPTERS) }
                if (panel != PlayerPanel.NONE) shownPanel = panel else if (watchPage) shownPanel = PlayerPanel.DETAILS
                if (shownPanel == PlayerPanel.DETAILS && ytId != null && tabletWatch) {
                    // Планшет: справа колонка похожих роликов, а открытые описание или комментарии — вместо неё
                    com.offplayer.app.ui.youtube.WatchSidePanel(ytId, modifier = panelModifier, contentPadding = panelPadding)
                } else if (shownPanel == PlayerPanel.DETAILS && ytId != null) {
                    // Страница ролика под видео: описание раскрывается нажатием, ниже — комментарии
                    com.offplayer.app.ui.youtube.WatchPage(ytId, modifier = panelModifier, contentPadding = panelPadding)
                } else if (shownPanel == PlayerPanel.QUEUE) {
                    QueuePanel(
                        title = ui.queueTitle ?: tr(R.string.queue),
                        queue = ui.queue,
                        currentIndex = ui.currentIndex,
                        landscape = landscape,
                        contentPadding = panelPadding,
                        onSelect = VideoController::skipTo,
                        onClose = { panel = PlayerPanel.NONE },
                        modifier = panelModifier,
                    )
                } else {
                    ChaptersPanel(
                        chapters = chapters,
                        positionMs = ui.positionMs,
                        videoUri = ui.uri,
                        landscape = landscape,
                        contentPadding = panelPadding,
                        onSeek = { ch ->
                            controller?.seekTo(ch.startMs)
                            controller?.play()
                        },
                        onClose = { panel = PlayerPanel.NONE },
                        modifier = panelModifier,
                    )
                }
            }
        }

        // Настройки плеера — поверх всего, в общем стиле приложения
        if (settingsOpen && !inPip) {
            PlayerSettingsPanel(
                controller = controller,
                background = background,
                pipSupported = pipSupported,
                onPip = { settingsOpen = false; onPip() },
                onClose = { settingsOpen = false },
            )
        }
    }
}

@Composable
private fun GestureIndicator(zone: GestureZone, level: Float, modifier: Modifier = Modifier) {
    val icon = when (zone) {
        GestureZone.VOLUME -> if (level <= 0.001f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp
        else -> if (level < 0.5f) Icons.Rounded.BrightnessLow else Icons.Rounded.BrightnessHigh
    }
    Column(
        modifier
            .background(Color.Black.copy(alpha = 0.65f), AppShapes.Card)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text("${(level * 100).roundToInt()}%", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { level },
            modifier = Modifier
                .width(140.dp)
                .height(4.dp),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.25f),
            drawStopIndicator = {},
        )
    }
}

/** Сбрасывает яркость окна к системной после выхода из видео. */
fun Activity.resetWindowBrightness() {
    window.attributes = window.attributes.apply {
        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }
}
