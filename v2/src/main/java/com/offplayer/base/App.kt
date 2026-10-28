package com.offplayer.base

import android.app.Application
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class OffBaseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }
}

class MainActivity : ComponentActivity() {
    private var inSystemPip by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { OffApp(inSystemPip) }
    }

    /** Свернули приложение во время видео — системная «картинка в картинке». */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Playback.video != null && Playback.videoPlaying && !Playback.audioOnly) {
            runCatching {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inSystemPip = isInPictureInPictureMode
    }

    /** Экран выключили (или закрыли системное окошко) — картинка останавливается, звук продолжает играть. */
    override fun onStop() {
        super.onStop()
        if (Playback.videoPlaying) Playback.audioOnly = true
    }
}

@Composable
fun OffApp(inSystemPip: Boolean) {
    OffTheme {
        PlaybackTicker()
        if (inSystemPip) {
            Playback.video?.let { VideoSurface(it, Modifier.fillMaxSize(), showTime = false) }
            return@OffTheme
        }
        // В оболочках без стопки модуль уходит в окошко сразу; карточная лента сначала проигрывает полёт
        LaunchedEffect(Playback.pipRequest, Prefs.shell.value) {
            val id = Playback.pipRequest ?: return@LaunchedEffect
            if (Prefs.shell.value != ShellStyle.CARDS) {
                Playback.pipRequest = null
                Playback.enterPip(id)
            }
        }
        // Возврат из «картинки в картинке» анимирует только карточная лента; в остальных вкладка просто появляется
        LaunchedEffect(Playback.returning, Prefs.shell.value) {
            if (Playback.returning != null && Prefs.shell.value != ShellStyle.CARDS) Playback.returning = null
        }
        Box(Modifier.fillMaxSize()) {
            LivingBackground()
            if (!Prefs.firstRunDone.value) {
                FirstRunWizard()
            } else {
                when (Prefs.shell.value) {
                    ShellStyle.CLASSIC -> ClassicShell()
                    ShellStyle.DOTS -> DotRailShell()
                    ShellStyle.CARDS -> CardStackShell()
                }
                ServiceOverlay()
                InAppPipWindow()
                FullscreenVideo()
            }
        }
    }
}

/** Показ модуля с окружением, которое сообщает ему основа. */
@Composable
fun ModuleHost(spec: ModuleSpec, env: ModuleEnv, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalModuleEnv provides env) {
        Box(modifier) { spec.content() }
    }
}

/** Служебные экраны (Загрузка, Настройки, Поиск) выезжают справа поверх оболочки. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceOverlay() {
    var lastId by remember { mutableStateOf<String?>(null) }
    Nav.service?.let { lastId = it }
    AnimatedVisibility(
        visible = Nav.service != null,
        enter = slideInHorizontally(tunedSpring()) { it } + fadeIn(),
        exit = slideOutHorizontally(tunedSpring()) { it } + fadeOut(),
    ) {
        val spec = Catalog.byId(lastId) ?: return@AnimatedVisibility
        BackHandler { Nav.service = null }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                if (spec.id != "settings") {
                    TopAppBar(
                        title = { Text(spec.title) },
                        navigationIcon = {
                            IconButton(onClick = { Nav.service = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                    )
                }
                ModuleHost(spec, ModuleEnv(showTitle = false), Modifier.weight(1f))
            }
        }
    }
}

/** Внутренняя «картинка в картинке»: окошко поверх модулей, его можно таскать. */
@Composable
private fun InAppPipWindow() {
    val video = Playback.video
    AnimatedVisibility(
        visible = Playback.pipModule != null && video != null,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.6f),
    ) {
        val v = video ?: return@AnimatedVisibility
        val density = LocalDensity.current
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val ox = remember { Animatable(0f) }
        val oy = remember { Animatable(0f) }
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().padding(12.dp).padding(top = pipTopInset())) {
            val pipW = PIP_WIDTH
            Box(
                Modifier.align(Alignment.TopEnd)
                    .offset { IntOffset(ox.value.roundToInt(), oy.value.roundToInt()) }
                    .width(pipW).aspectRatio(16f / 9f)
                    .shadow(if (Prefs.lightMode.value) 0.dp else 12.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                // Окошко прилипает к ближайшему верхнему или нижнему углу
                                val maxX = with(density) { (maxWidth - pipW).toPx() }
                                val maxY = with(density) { (maxHeight - pipW * 9f / 16f).toPx() }
                                scope.launch { ox.animateTo(if (ox.value < -maxX / 2) -maxX else 0f, tunedSpring()) }
                                scope.launch { oy.animateTo(if (oy.value > maxY / 2) maxY else 0f, tunedSpring()) }
                            },
                        ) { change, drag ->
                            change.consume()
                            scope.launch { ox.snapTo(ox.value + drag.x) }
                            scope.launch { oy.snapTo(oy.value + drag.y) }
                        }
                    },
            ) {
                VideoSurface(v, Modifier.fillMaxSize(), showTime = false)
                Row(Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(bottomStart = 10.dp))) {
                    IconButton(onClick = { Playback.videoPlaying = !Playback.videoPlaying }) {
                        Icon(if (Playback.videoPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Пауза", tint = Color.White)
                    }
                    IconButton(onClick = { Playback.closePip() }) { Icon(Icons.Rounded.OpenInFull, "Вернуть в приложение", tint = Color.White) }
                    IconButton(onClick = { Playback.videoPlaying = false; Playback.closePip() }) {
                        Icon(Icons.Rounded.Close, "Закрыть", tint = Color.White)
                    }
                }
            }
        }
    }
}

val PIP_WIDTH = 220.dp

/** В карточной ленте с кнопками сверху окошко опускается под них, чтобы не закрывать. */
fun pipTopInset(): androidx.compose.ui.unit.Dp =
    if (Prefs.shell.value == ShellStyle.CARDS &&
        (Prefs.cardCorner.value == Corner.TOP_START || Prefs.cardCorner.value == Corner.TOP_END)) 52.dp else 0.dp

@Composable
private fun FullscreenVideo() {
    val video = Playback.video
    AnimatedVisibility(Playback.fullscreen && video != null, enter = fadeIn(), exit = fadeOut()) {
        val v = video ?: return@AnimatedVisibility
        BackHandler { Playback.fullscreen = false }
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            VideoSurface(v, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            IconButton(onClick = { Playback.fullscreen = false }, modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding()) {
                Icon(Icons.Rounded.FullscreenExit, "Выйти из полноэкранного режима", tint = Color.White)
            }
        }
    }
}
