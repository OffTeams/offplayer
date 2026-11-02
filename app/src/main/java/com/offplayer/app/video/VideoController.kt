package com.offplayer.app.video

import com.offplayer.app.tr
import com.offplayer.app.R

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.offplayer.app.model.Video
import com.offplayer.app.playback.VideoService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.media3.common.Player
import com.offplayer.app.playback.ThumbBitmapLoader
import com.offplayer.app.data.AppSettings
import com.offplayer.app.data.SbAction
import com.offplayer.app.data.VideoMetaStore
import com.offplayer.app.model.SponsorSegment
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Клиент видеосервиса. Видеоплеер — это экран внутри MainActivity (а не отдельная Activity),
 * поэтому приложение всегда одно в списке недавних, в том числе в режиме «картинка в картинке».
 */
/** Состояние для мини-плеера видео, играющего в фоне. */
data class VideoUiState(
    val hasMedia: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val uri: Uri? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** id видео в MediaStore — по нему берутся эпизоды и отрезки SponsorBlock. */
    val videoId: Long? = null,
    /** Отрезок SponsorBlock, внутри которого сейчас позиция (для кнопки «Пропустить»). */
    val activeSegment: SponsorSegment? = null,
    /** Очередь видео (например, проигрываемый список) и текущая позиция в ней. */
    val queue: List<VideoQueueEntry> = emptyList(),
    val currentIndex: Int = 0,
    /** Название проигрываемого списка — заголовок панели «Список». */
    val queueTitle: String? = null,
)

data class VideoQueueEntry(val videoId: Long?, val uri: Uri?, val title: String)

object VideoController {
    private lateinit var appContext: Context
    private var connecting = false
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private val scope = MainScope()
    private var progressJob: Job? = null

    private val _ui = MutableStateFlow(VideoUiState())
    val ui: StateFlow<VideoUiState> = _ui.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync()
    }

    private fun sync() {
        val c = _controller.value
        if (c == null) {
            _ui.value = VideoUiState()
            return
        }
        val item = c.currentMediaItem
        val uri = item?.let(::uriOf)
        // mediaId — id видео в медиатеке; у файлов, открытых из других приложений, — сама ссылка
        val videoId = item?.mediaId?.toLongOrNull()
        if (videoId != _ui.value.videoId) lastSkipped = null
        _ui.value = VideoUiState(
            hasMedia = item != null,
            isPlaying = c.isPlaying,
            title = c.mediaMetadata.title?.toString().orEmpty(),
            uri = uri,
            positionMs = c.currentPosition,
            durationMs = c.duration.coerceAtLeast(0),
            videoId = videoId,
            activeSegment = _ui.value.activeSegment.takeIf { videoId == _ui.value.videoId },
            queue = (0 until c.mediaItemCount).map { i ->
                val m = c.getMediaItemAt(i)
                VideoQueueEntry(m.mediaId.toLongOrNull(), uriOf(m), m.mediaMetadata.title?.toString().orEmpty())
            },
            currentIndex = c.currentMediaItemIndex,
            queueTitle = queueTitle,
        )
    }

    private fun uriOf(item: MediaItem): Uri? =
        item.requestMetadata.mediaUri ?: item.mediaId.takeIf { it.contains(':') }?.let(Uri::parse)

    private var queueTitle: String? = null

    /** Убирает из очереди удалённые видео; если очередь опустела — закрывает плеер. */
    /** Файл видео переехал в другое место хранения — элемент очереди переводится на новый адрес с той же позиции. */
    fun relinkMedia(id: Long, uri: android.net.Uri) {
        val c = _controller.value ?: return
        for (i in 0 until c.mediaItemCount) {
            val item = c.getMediaItemAt(i)
            if (item.mediaId.toLongOrNull() != id) continue
            val current = i == c.currentMediaItemIndex
            val pos = c.currentPosition
            c.replaceMediaItem(
                i,
                item.buildUpon().setUri(uri)
                    .setRequestMetadata(androidx.media3.common.MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
                    .build(),
            )
            if (current && c.currentMediaItemIndex == i && c.currentPosition != pos) c.seekTo(i, pos)
        }
    }

    fun removeMedia(ids: Set<Long>) {
        val c = _controller.value ?: return
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (c.getMediaItemAt(i).mediaId.toLongOrNull() in ids) c.removeMediaItem(i)
        }
        if (c.mediaItemCount == 0) close(stopPlayback = true)
    }

    /** Перейти к видео из очереди (панель «Список» в плеере). */
    fun skipTo(index: Int) = withController {
        it.seekToDefaultPosition(index)
        it.play()
    }

    fun togglePlay() = withController { if (it.isPlaying) it.pause() else it.play() }

    // ---- SponsorBlock ----

    private var lastSkipped: SponsorSegment? = null
    private val _skipped = MutableSharedFlow<SponsorSegment>(extraBufferCapacity = 4)
    /** Отрезки, пропущенные автоматически, — для плашки «Пропущено · Вернуть». */
    val skipped: SharedFlow<SponsorSegment> = _skipped.asSharedFlow()

    /** Ручной пропуск текущего отрезка (когда автопропуск выключен). */
    fun skipSegment(segment: SponsorSegment) = withController {
        lastSkipped = segment
        it.seekTo(segment.endMs)
    }

    /** «Вернуть» после автопропуска: отрезок больше не пропускается до смены видео. */
    fun undoSkip(segment: SponsorSegment) = withController {
        lastSkipped = segment
        it.seekTo(segment.startMs)
    }

    /** Проверка позиции: пропуск отрезков работает и при фоновом воспроизведении. */
    private fun checkSegments(ctl: MediaController) {
        val pos = ctl.currentPosition
        val vid = _ui.value.videoId
        // У каждой категории своё действие: пропустить сразу, предложить кнопку или не трогать
        val seg = vid?.let { VideoMetaStore.get(it) }?.segments
            ?.firstOrNull { AppSettings.sbAction(it.category) != SbAction.OFF && pos >= it.startMs && pos < it.endMs - 500 }
        val action = seg?.let { AppSettings.sbAction(it.category) }
        if (seg != null && action == SbAction.SKIP && seg != lastSkipped) {
            lastSkipped = seg
            ctl.seekTo(seg.endMs)
            if (AppSettings.sbNotice.value) _skipped.tryEmit(seg)
            _ui.update { it.copy(positionMs = seg.endMs, activeSegment = null) }
            return
        }
        // Кнопка «Пропустить» — только для категорий «Спрашивать» (и для отрезка, к которому вернулись)
        val ask = seg?.takeIf { action == SbAction.ASK }
        _ui.update { it.copy(positionMs = pos, durationMs = ctl.duration.coerceAtLeast(0), activeSegment = ask) }
    }

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    /** Показан ли экран видеоплеера. */
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()

    fun connect(context: Context) {
        appContext = context.applicationContext
        if (_controller.value != null || connecting) return
        connecting = true
        val token = SessionToken(appContext, ComponentName(appContext, VideoService::class.java))
        val future = MediaController.Builder(appContext, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    _controller.value = null
                    progressJob?.cancel()
                    sync()
                }
            })
            .buildAsync()
        future.addListener({
            connecting = false
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            _controller.value = c
            c.addListener(listener)
            sync()
            progressJob?.cancel()
            progressJob = scope.launch {
                while (isActive) {
                    delay(250)
                    val ctl = _controller.value ?: break
                    if (ctl.isPlaying) checkSegments(ctl)
                }
            }
            pending.toList().forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = _controller.value
        if (c != null) {
            block(c)
        } else {
            pending += block
            if (::appContext.isInitialized) connect(appContext)
        }
    }

    /** listTitle — название списка/категории, если воспроизводится подборка. */
    /** Пропорции открываемого видео (ширина/высота) — ориентация экрана выбирается ещё до первого кадра. */
    @Volatile var expectedAspect: Float = 0f
        private set

    /**
     * Играет ролик YouTube: экран не поворачивается сам — открывается вертикальная страница ролика
     * (видео сверху, описание и комментарии под ним), на весь экран — поворотом телефона.
     */
    @Volatile var watchPage: Boolean = false
        private set

    fun play(videos: List<Video>, index: Int, listTitle: String? = null) {
        watchPage = false
        queueTitle = listTitle
        expectedAspect = videos.getOrNull(index)?.let { v -> if (v.width > 0 && v.height > 0) v.width.toFloat() / v.height else 0f } ?: 0f
        playItems(videos.map { videoItem(it.id.toString(), it.uri, it.title) }, index)
    }

    /** Ролик YouTube, играющий онлайн: обложка — превью YouTube, эпизоды и SponsorBlock — по его playerId. */
    fun playStream(stream: com.offplayer.app.youtube.YtStream) {
        queueTitle = null
        watchPage = true
        // Как в YouTube: обычный ролик открывается на весь экран в альбомной ориентации, вертикальный — в портретной
        expectedAspect = if (stream.width > 0 && stream.height > 0) stream.width.toFloat() / stream.height else 16f / 9f
        val item = MediaItem.Builder()
            .setMediaId(stream.playerId.toString())
            .setUri(stream.uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(stream.uri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(stream.title)
                    .setArtist(stream.channel)
                    .apply { stream.thumbnail?.let { setArtworkUri(Uri.parse(it)) } }
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build()
            )
            .build()
        playItems(listOf(item), 0)
    }

    /** Видео, открытое из другого приложения («Открыть с помощью»). */
    fun playUri(context: Context, uri: Uri) {
        queueTitle = null
        watchPage = false
        expectedAspect = 0f
        playItems(listOf(videoItem(uri.toString(), uri, displayName(context, uri))), 0)
    }

    private fun playItems(items: List<MediaItem>, start: Int) {
        if (items.isEmpty()) return
        _open.value = true
        withController { c ->
            c.setMediaItems(items, start.coerceIn(0, items.lastIndex), 0L)
            c.setPlaybackSpeed(AppSettings.videoSpeed.value)
            c.prepare()
            c.play()
        }
    }

    /** Вернуться к видео, которое играет в фоне (например, из уведомления). */
    fun show() {
        if ((_controller.value?.mediaItemCount ?: 0) > 0) _open.value = true
    }

    fun close(stopPlayback: Boolean) {
        val wasOpen = _open.value
        _open.value = false
        if (stopPlayback) {
            // Остановка — после того как экран плеера уедет, чтобы он не мигал пустым
            scope.launch {
                if (wasOpen) delay(340)
                if (!_open.value) _controller.value?.run {
                    stop()
                    clearMediaItems()
                }
            }
        }
    }

    private fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.substringBeforeLast('.') else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: tr(R.string.tab_video)

    private fun videoItem(mediaId: String, uri: Uri, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                // Кадр из видео — превью в уведомлении
                .setArtworkUri(ThumbBitmapLoader.artworkUri(uri, isVideo = true))
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build()
        )
        .build()
}
