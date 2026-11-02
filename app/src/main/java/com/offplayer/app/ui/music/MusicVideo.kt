package com.offplayer.app.ui.music

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.offplayer.app.playback.MusicController
import com.offplayer.app.youtube.ChunkedDataSource
import com.offplayer.app.youtube.YouTubeStreams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Видео вместо обложки в музыкальном плеере. Звук по-прежнему идёт из музыкального плеера,
 * а здесь — только картинка (без звука и без своих кнопок), которая держится в такт треку:
 * пауза и перемотка — общими кнопками плеера. Раз в пару секунд по кадру пересчитываются цвета фона.
 */
@OptIn(UnstableApi::class)
@Composable
fun MusicVideo(youtubeId: String, isPlaying: Boolean, onColors: (ArtColors) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var ready by remember(youtubeId) { mutableStateOf(false) }
    var texture by remember { mutableStateOf<TextureView?>(null) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    // Поток видео — тот же, что у онлайн-плейлистов (NewPipe), качается кусками
    LaunchedEffect(youtubeId) {
        ready = false
        val parts = withContext(Dispatchers.IO) { runCatching { YouTubeStreams.partsBlocking(youtubeId) }.getOrNull() } ?: return@LaunchedEffect
        val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).apply { parts.ua?.let { setUserAgent(it) } }
        player.setMediaSource(ProgressiveMediaSource.Factory(ChunkedDataSource.Factory(http)).createMediaSource(MediaItem.fromUri(parts.video)))
        player.prepare()
        player.seekTo(MusicController.state.value.positionMs)
        ready = true
    }
    LaunchedEffect(isPlaying, ready) { player.playWhenReady = isPlaying && ready }
    // Картинка держится в такт звуку: заметное расхождение (перемотка, буферизация) — подстраиваемся
    LaunchedEffect(youtubeId) {
        while (isActive) {
            delay(700)
            if (!ready) continue
            val target = MusicController.state.value.positionMs
            if (abs(player.currentPosition - target) > 900) player.seekTo(target)
        }
    }
    // Цвета фона — по текущему кадру, как по обложке
    LaunchedEffect(youtubeId, texture) {
        while (isActive) {
            delay(2000)
            val frame = texture?.takeIf { ready && it.isAvailable }?.getBitmap(96, 54) ?: continue
            onColors(withContext(Dispatchers.Default) { artColorsOf(frame) })
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp)).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it); texture = it } },
                modifier = Modifier.fillMaxSize(),
            )
            if (!ready) CircularProgressIndicator(color = Color.White)
        }
    }
}
