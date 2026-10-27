package com.offplayer.app.ui.video

import com.offplayer.app.tr
import com.offplayer.app.R

import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.offplayer.app.ui.MediaArt
import com.offplayer.app.ui.MiniBar
import com.offplayer.app.ui.theme.AppShapes
import com.offplayer.app.video.VideoController
import com.offplayer.app.video.VideoUiState

/**
 * Мини-плеер видео, которое играет в фоне: видео продолжает идти в маленьком окошке,
 * пауза и полное закрытие без шторки уведомлений. Тап или свайп вверх — вернуться к видео.
 */
@Composable
fun VideoMiniPlayer(state: VideoUiState) {
    MiniBar(
        title = state.title,
        subtitle = if (state.isPlaying) tr(R.string.video_playing) else tr(R.string.video_paused),
        progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
        accent = MaterialTheme.colorScheme.primary,
        onOpen = VideoController::show,
        art = {
            Box(
                Modifier
                    .width(88.dp)
                    .height(50.dp)
                    .clip(AppShapes.Thumb)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                // Пока кадр не пришёл (или нет контроллера) — статичное превью
                MediaArt(state.uri, Modifier.fillMaxSize(), isVideo = true, shape = AppShapes.Thumb)
                LiveVideo()
            }
        },
    ) {
        IconButton(onClick = VideoController::togglePlay) {
            Icon(
                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (state.isPlaying) tr(R.string.action_pause) else tr(R.string.action_play),
                Modifier.size(30.dp),
            )
        }
        IconButton(onClick = { VideoController.close(stopPlayback = true) }) {
            Icon(Icons.Rounded.Close, tr(R.string.close_video))
        }
    }
}

/** Видео в окошке мини-плеера: TextureView подключается к тому же плееру, что и полноэкранный. */
@Composable
private fun LiveVideo() {
    val controller by VideoController.controller.collectAsStateWithLifecycle()
    val c = controller ?: return
    val context = LocalContext.current
    val view = remember(context) { TextureView(context) }
    var aspect by remember { mutableFloatStateOf(16f / 9f) }

    DisposableEffect(c, view) {
        fun applySize(size: VideoSize) {
            if (size.width > 0 && size.height > 0) aspect = size.width * size.pixelWidthHeightRatio / size.height
        }
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) = applySize(videoSize)
        }
        applySize(c.videoSize)
        c.addListener(listener)
        c.setVideoTextureView(view)
        onDispose {
            c.removeListener(listener)
            // Снимает поверхность, только если она всё ещё наша (полноэкранный плеер мог её забрать)
            c.clearVideoTextureView(view)
        }
    }
    AndroidView(
        factory = { view },
        modifier = Modifier.aspectRatio(aspect.coerceIn(0.3f, 3f), matchHeightConstraintsFirst = aspect < 16f / 9f),
    )
}
