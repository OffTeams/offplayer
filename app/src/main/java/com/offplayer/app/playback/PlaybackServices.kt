package com.offplayer.app.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.offplayer.app.MainActivity
import com.offplayer.app.data.AppSettings
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Общая основа для фоновых плееров. Музыка и видео живут в разных сервисах
 * с собственными сессиями и очередями, поэтому не смешиваются.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
abstract class BasePlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    protected abstract val sessionId: String
    protected abstract val contentType: Int
    protected abstract fun sessionActivityIntent(): Intent

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(contentType)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(AppSettings.pauseOnNoisy.value)
            // Ролики YouTube играют по сети: Wi-Fi не должен засыпать при выключенном экране
            .setWakeMode(C.WAKE_MODE_NETWORK)
            // Онлайн-ролики YouTube (раздельные видео и звук), остальное — как раньше
            .setMediaSourceFactory(com.offplayer.app.youtube.StreamMediaSourceFactory(this))
            .build()
        val activity = PendingIntent.getActivity(
            this, sessionId.hashCode(), sessionActivityIntent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        watchSettings(player)
        session = MediaSession.Builder(this, player)
            .setId(sessionId)
            .setSessionActivity(activity)
            .setCallback(SessionCallback)
            // Обложки треков и кадры видео для уведомления (файлы лежат во внутренней памяти)
            .setBitmapLoader(CacheBitmapLoader(ThumbBitmapLoader(this)))
            .build()
    }

    // Настройка «пауза при отключении наушников» применяется сразу, без перезапуска
    private val scope = MainScope()

    private fun watchSettings(player: ExoPlayer) {
        scope.launch { AppSettings.pauseOnNoisy.collect { player.setHandleAudioBecomingNoisy(it) } }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** Контроллер присылает элементы без URI — восстанавливаем его из requestMetadata. */
    private object SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.map { item ->
                val uri = item.requestMetadata.mediaUri
                if (item.localConfiguration == null && uri != null) item.buildUpon().setUri(uri).build() else item
            }.toMutableList()
        )
    }
}

class MusicService : BasePlaybackService() {
    override val sessionId = "music"
    override val contentType = C.AUDIO_CONTENT_TYPE_MUSIC
    override fun sessionActivityIntent() = Intent(this, MainActivity::class.java)
        .setAction(MainActivity.ACTION_OPEN_PLAYER)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
}

class VideoService : BasePlaybackService() {
    override val sessionId = "video"
    override val contentType = C.AUDIO_CONTENT_TYPE_MOVIE
    override fun sessionActivityIntent() = Intent(this, MainActivity::class.java)
        .setAction(MainActivity.ACTION_OPEN_VIDEO)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
