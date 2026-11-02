package com.offplayer.app

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import com.offplayer.app.data.AppSettings
import com.offplayer.app.playback.MusicController
import com.offplayer.app.download.DownloadResult
import com.offplayer.app.ui.MainScreen
import com.offplayer.app.ui.RevealRequest
import com.offplayer.app.ui.UiEvents
import com.offplayer.app.ui.DownloadDoneEvent
import com.offplayer.app.ui.theme.OffPlayerTheme
import com.offplayer.app.ui.video.resetWindowBrightness
import com.offplayer.app.video.VideoController
import kotlinx.coroutines.launch

/**
 * Единственная Activity приложения. Видеоплеер — экран внутри неё, поэтому
 * в «картинку в картинке» уходит всё приложение целиком и в списке недавних оно одно.
 */
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(L.wrap(newBase))
    }

    private var sharedUrl by mutableStateOf<String?>(null)
    private var openPlayerTick by mutableIntStateOf(0)
    private var inPip by mutableStateOf(false)
    /** Устройство повернули не так, как сейчас показано видео (при включённом автоповороте). */
    private var rotateHint by mutableStateOf(false)
    private var orientationListener: OrientationEventListener? = null

    private var videoOpen = false
    /** Видео открывали — только тогда при закрытии возвращаем ориентацию «как было до видео». */
    private var videoWasOpen = false
    private var orientationLockedByUser = false
    private var videoController: MediaController? = null

    private val pipSupported by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val c = videoController ?: return
            when (intent.action) {
                ACTION_PIP_PLAY_PAUSE -> if (c.isPlaying) c.pause() else c.play()
                ACTION_PIP_NEXT -> c.seekToNext()
                ACTION_PIP_PREV -> c.seekToPrevious()
            }
        }
    }

    private val videoListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_VIDEO_SIZE_CHANGED)) updatePipParams()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            autoOrientation(videoSize)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        MusicController.connect(this)
        VideoController.connect(this)
        if (savedInstanceState == null) handleIntent(intent)

        ContextCompat.registerReceiver(
            this, pipReceiver,
            IntentFilter().apply {
                addAction(ACTION_PIP_PLAY_PAUSE); addAction(ACTION_PIP_NEXT); addAction(ACTION_PIP_PREV)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        lifecycleScope.launch {
            VideoController.controller.collect { c ->
                videoController?.removeListener(videoListener)
                videoController = c
                c?.addListener(videoListener)
                updatePipParams()
            }
        }
        lifecycleScope.launch {
            VideoController.open.collect { open ->
                videoOpen = open
                onVideoModeChanged(open)
            }
        }

        setContent {
            OffPlayerTheme {
                MainScreen(
                    sharedUrl = sharedUrl,
                    onSharedUrlConsumed = { sharedUrl = null },
                    openPlayerTick = openPlayerTick,
                    inPip = inPip,
                    pipSupported = pipSupported,
                    onEnterPip = ::enterPip,
                    onRotate = { rotateHint = false; toggleOrientation() },
                    rotateHint = rotateHint,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                URL_REGEX.find(text)?.value?.let { sharedUrl = it }
            }
            Intent.ACTION_VIEW -> intent.data?.let { VideoController.playUri(this, it) }
            ACTION_OPEN_PLAYER -> openPlayerTick++
            ACTION_OPEN_VIDEO -> VideoController.show()
            ACTION_OPEN_DOWNLOADS -> UiEvents.openDownloads.value++
            // Уведомление об оповещении YouTube: ролик — в плеер, Shorts — в ленту Shorts, иначе — канал
            ACTION_OPEN_YOUTUBE -> {
                val video = intent.getStringExtra(EXTRA_YT_VIDEO)
                val channel = intent.getStringExtra(EXTRA_YT_CHANNEL)
                when {
                    video != null && intent.getBooleanExtra(EXTRA_YT_SHORT, false) -> {
                        com.offplayer.app.youtube.YouTubeNav.shortRequest.value = video
                        com.offplayer.app.youtube.YouTubeNav.openShortsTab.value++
                    }
                    // Ответ на комментарий — страница комментариев во вкладке YouTube, ролик не запускается
                    video != null && intent.getStringExtra(EXTRA_YT_COMMENT) != null -> {
                        com.offplayer.app.youtube.YouTubeNav.commentsPage.value = com.offplayer.app.youtube.YouTubeNav.LinkedComment(
                            video, intent.getStringExtra(EXTRA_YT_COMMENT)!!, intent.getStringExtra(EXTRA_YT_PARAMS),
                        )
                        com.offplayer.app.youtube.YouTubeNav.openYoutubeTab.value++
                    }
                    video != null -> lifecycleScope.launch {
                        runCatching {
                            com.offplayer.app.youtube.YouTubeStreams.resolve(
                                this@MainActivity,
                                com.offplayer.app.youtube.YtVideo(video, "", "", null, null, "", "https://i.ytimg.com/vi/$video/hqdefault.jpg"),
                            )
                        }.onSuccess { VideoController.playStream(it) }
                    }
                    channel != null -> com.offplayer.app.youtube.YouTubeNav.openChannel(channel, "")
                }
            }
            ACTION_REVEAL -> {
                val kind = runCatching { DownloadResult.Kind.valueOf(intent.getStringExtra(EXTRA_REVEAL_KIND) ?: "") }.getOrNull()
                val id = intent.getStringExtra(EXTRA_REVEAL_ID)
                if (kind != null && id != null) UiEvents.reveal.value = RevealRequest(kind, id)
            }
        }
    }

    // ---- Полноэкранный режим видео ----

    private fun onVideoModeChanged(open: Boolean) {
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        if (open) {
            videoWasOpen = true
            applyVideoBars()
            orientationLockedByUser = false
            // Запоминаем ориентацию до открытия видео — после закрытия плеера вернём её
            originalOrientation = resources.configuration.orientation
            // Пропорции известны заранее (из медиатеки) — поворачиваем сразу, без промежуточного кадра
            val aspect = VideoController.expectedAspect
            if (aspect > 0f) applyAspect(aspect) else videoController?.videoSize?.let(::autoOrientation)
            startOrientationWatch()
        } else {
            insets.show(WindowInsetsCompat.Type.systemBars())
            stopOrientationWatch()
            // При запуске приложения видео не было — ориентацию не трогаем: как держат устройство, так и остаётся
            if (videoWasOpen) restoreOrientation()
            resetWindowBrightness()
        }
        updatePipParams()
    }

    /**
     * Системные панели при открытом плеере: на весь экран (альбомная ориентация, обычное видео) — скрыты;
     * страница ролика YouTube в портретной ориентации — строка состояния видна, как в YouTube.
     */
    private fun applyVideoBars() {
        if (!videoOpen) return
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val portraitPage = VideoController.watchPage && !inPip &&
            resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        if (portraitPage) {
            insets.show(WindowInsetsCompat.Type.statusBars())
            insets.hide(WindowInsetsCompat.Type.navigationBars())
        } else insets.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyVideoBars()
    }

    /** Включён ли автоповорот экрана в системе (а не заблокирована ориентация). */
    private fun autoRotateOn(): Boolean =
        Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    /**
     * Следим за положением телефона: если автоповорот включён, а телефон держат не так,
     * как сейчас показано видео, — в углу плеера появляется кнопка поворота.
     * При заблокированной ориентации подсказку поворота показывает сама система.
     */
    private fun startOrientationWatch() {
        if (orientationListener != null) return
        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(deg: Int) {
                if (deg == ORIENTATION_UNKNOWN || !videoOpen || inPip || !autoRotateOn()) {
                    if (rotateHint) rotateHint = false
                    return
                }
                val physical = when (deg) {
                    in 0..25, in 335..359, in 155..205 -> Configuration.ORIENTATION_PORTRAIT
                    in 65..115, in 245..295 -> Configuration.ORIENTATION_LANDSCAPE
                    else -> return // промежуточный наклон — не решаем
                }
                val mismatch = physical != resources.configuration.orientation
                if (mismatch != rotateHint) rotateHint = mismatch
            }
        }.also { if (it.canDetectOrientation()) it.enable() }
    }

    private fun stopOrientationWatch() {
        orientationListener?.disable()
        orientationListener = null
        rotateHint = false
    }

    private fun autoOrientation(size: VideoSize) {
        if (!videoOpen || orientationLockedByUser || inPip || size.width == 0 || size.height == 0) return
        if (!AppSettings.videoAutoRotate.value) return
        applyAspect(size.width * size.pixelWidthHeightRatio / size.height)
    }

    /**
     * Широкие ролики (16:9, 4:3 и т. п.) — альбомная ориентация; квадратные и вертикальные — портретная.
     */
    private fun applyAspect(aspect: Float) {
        if (!videoOpen || orientationLockedByUser || inPip || !AppSettings.videoAutoRotate.value) return
        // Работает и при заблокированном автоповороте: видео само выбирает ориентацию
        requestedOrientation = if (aspect > 1.1f) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

    private var originalOrientation = Configuration.ORIENTATION_PORTRAIT
    private var restoreListener: OrientationEventListener? = null

    /**
     * После закрытия/сворачивания плеера экран возвращается в ориентацию, что была до видео.
     * Фиксация снимается, как только телефон сам окажется в этом положении (дальше — обычный автоповорот).
     */
    private fun restoreOrientation() {
        restoreListener?.disable()
        if (!autoRotateOn()) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            return
        }
        val wantLandscape = originalOrientation == Configuration.ORIENTATION_LANDSCAPE
        requestedOrientation = if (wantLandscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        restoreListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(deg: Int) {
                if (deg == ORIENTATION_UNKNOWN || videoOpen) return
                val physicalLandscape = deg in 65..115 || deg in 245..295
                val physicalPortrait = deg in 0..25 || deg in 335..359
                if ((wantLandscape && physicalLandscape) || (!wantLandscape && physicalPortrait)) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    disable()
                    restoreListener = null
                }
            }
        }.also { if (it.canDetectOrientation()) it.enable() else requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    private fun toggleOrientation() {
        orientationLockedByUser = true
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        requestedOrientation = if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    // ---- Картинка в картинке ----

    private fun pipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        val c = videoController
        val vs = c?.videoSize
        if (vs != null && vs.width > 0 && vs.height > 0) {
            val ratio = (vs.width * vs.pixelWidthHeightRatio / vs.height).coerceIn(0.42f, 2.39f)
            builder.setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
        }
        val playing = c?.isPlaying == true
        builder.setActions(
            listOf(
                remoteAction(ACTION_PIP_PREV, android.R.drawable.ic_media_previous, tr(R.string.action_back), 1),
                if (playing) remoteAction(ACTION_PIP_PLAY_PAUSE, android.R.drawable.ic_media_pause, tr(R.string.action_pause), 2)
                else remoteAction(ACTION_PIP_PLAY_PAUSE, android.R.drawable.ic_media_play, tr(R.string.action_play), 2),
                remoteAction(ACTION_PIP_NEXT, android.R.drawable.ic_media_next, tr(R.string.action_forward), 3),
            )
        )
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setAutoEnterEnabled(videoOpen && AppSettings.videoAutoPip.value && playing)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun remoteAction(action: String, icon: Int, title: String, requestCode: Int): RemoteAction {
        val pi = PendingIntent.getBroadcast(
            this, requestCode, Intent(action).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteAction(Icon.createWithResource(this, icon), title, title, pi)
    }

    private fun updatePipParams() {
        if (!pipSupported) return
        runCatching { setPictureInPictureParams(pipParams()) }
    }

    private fun enterPip() {
        if (!pipSupported) {
            Toast.makeText(this, tr(R.string.pip_unsupported), Toast.LENGTH_SHORT).show()
            return
        }
        if (videoOpen) runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // На Android 12+ это делает setAutoEnterEnabled
        if (Build.VERSION.SDK_INT < 31 && pipSupported && videoOpen &&
            AppSettings.videoAutoPip.value && videoController?.isPlaying == true
        ) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) {
            // Окно «картинка в картинке» закрыли крестиком — останавливаем видео
            VideoController.close(stopPlayback = true)
        }
    }

    override fun onStart() {
        super.onStart()
        UiEvents.appVisible = true
    }

    override fun onResume() {
        super.onResume()
        // Вернулись из настроек «Установка неизвестных приложений» — продолжаем обновление
        com.offplayer.app.update.AppUpdater.onPermissionResult(this)
    }

    override fun onStop() {
        super.onStop()
        UiEvents.appVisible = false
        if (videoOpen && !isInPictureInPictureMode && !AppSettings.videoBackground.value) {
            videoController?.pause()
        }
    }

    override fun onDestroy() {
        stopOrientationWatch()
        restoreListener?.disable()
        unregisterReceiver(pipReceiver)
        videoController?.removeListener(videoListener)
        if (isFinishing && videoOpen) VideoController.close(stopPlayback = !AppSettings.videoBackground.value)
        super.onDestroy()
    }

    companion object {
        const val ACTION_OPEN_YOUTUBE = "com.offplayer.app.OPEN_YOUTUBE"
        const val EXTRA_YT_VIDEO = "yt_video"
        const val EXTRA_YT_SHORT = "yt_short"
        const val EXTRA_YT_CHANNEL = "yt_channel"
        const val EXTRA_YT_COMMENT = "yt_comment"
        const val EXTRA_YT_PARAMS = "yt_params"
        const val ACTION_OPEN_PLAYER = "com.offplayer.app.OPEN_PLAYER"
        const val ACTION_OPEN_VIDEO = "com.offplayer.app.OPEN_VIDEO"
        const val ACTION_OPEN_DOWNLOADS = "com.offplayer.app.OPEN_DOWNLOADS"
        /** Показать результат загрузки: трек/видео с подсветкой или открыть микс/список. */
        const val ACTION_REVEAL = "com.offplayer.app.REVEAL"
        const val EXTRA_REVEAL_KIND = "kind"
        const val EXTRA_REVEAL_ID = "id"
        private const val ACTION_PIP_PLAY_PAUSE = "com.offplayer.app.pip.PLAY_PAUSE"
        private const val ACTION_PIP_NEXT = "com.offplayer.app.pip.NEXT"
        private const val ACTION_PIP_PREV = "com.offplayer.app.pip.PREV"
        private val URL_REGEX = Regex("""https?://\S+""")
    }
}
