package com.offplayer.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.net.Uri
import com.offplayer.app.data.AppSettings
import com.offplayer.app.data.MediaRepository
import com.offplayer.app.data.PlayOrder
import com.offplayer.app.data.PodcastProgress
import com.offplayer.app.model.Track
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class QueueEntry(
    /** Индекс элемента в плеере (не позиция в списке, если включено перемешивание). */
    val itemIndex: Int,
    val mediaId: String,
    val title: String,
    val artist: String,
)

data class PlayerUiState(
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = -1,
    val sleepAtMs: Long? = null,
    /** Полная длительность заведённого таймера сна — для кольца обратного отсчёта. */
    val sleepTotalMs: Long? = null,
    val sleepEndOfTrack: Boolean = false,
) {
    val hasMedia: Boolean get() = mediaId != null
    val trackId: Long? get() = mediaId?.toLongOrNull()
}

/**
 * coverUri — файл, чья обложка показывается в уведомлении для всей очереди
 * (альбом, микс); null — у каждого трека своя обложка.
 */
fun Track.toMediaItem(coverUri: Uri? = null): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(ThumbBitmapLoader.artworkUri(coverUri ?: uri, isVideo = false))
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build()
    )
    .build()

/** Клиент музыкального сервиса: команды + состояние для UI. */
object MusicController {
    private val scope = MainScope()
    private lateinit var appContext: Context
    private var controller: MediaController? = null
    private var connecting = false
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private var sleepJob: Job? = null
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(
                events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                )
            )
        }
    }

    fun connect(context: Context) {
        appContext = context.applicationContext
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(appContext, ComponentName(appContext, MusicService::class.java))
        val future = MediaController.Builder(appContext, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    this@MusicController.controller = null
                    progressJob?.cancel()
                }
            })
            .buildAsync()
        future.addListener({
            connecting = false
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            pending.toList().forEach { it(c) }
            pending.clear()
            sync(full = true)
            progressJob?.cancel()
            progressJob = scope.launch {
                while (isActive) {
                    delay(400)
                    val ctl = controller ?: break
                    if (ctl.isPlaying) {
                        _state.update {
                            it.copy(positionMs = ctl.currentPosition, durationMs = ctl.duration.coerceAtLeast(0))
                        }
                        // Подкасты: место прослушивания сохраняется примерно раз в 5 секунд
                        if (++tick % 12 == 0) rememberPodcastPosition(ctl)
                    }
                }
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) {
            block(c)
        } else {
            pending += block
            if (::appContext.isInitialized) connect(appContext)
        }
    }

    private fun sync(full: Boolean) {
        val c = controller ?: return
        val md = c.mediaMetadata
        _state.update { s ->
            s.copy(
                mediaId = c.currentMediaItem?.mediaId,
                title = md.title?.toString().orEmpty(),
                artist = md.artist?.toString().orEmpty(),
                isPlaying = c.isPlaying,
                positionMs = c.currentPosition,
                durationMs = c.duration.coerceAtLeast(0),
                repeatMode = c.repeatMode,
                shuffle = c.shuffleModeEnabled,
                currentIndex = c.currentMediaItemIndex,
                queue = if (full) buildQueue(c) else s.queue,
            )
        }
    }

    /** Очередь в фактическом порядке воспроизведения (с учётом перемешивания). */
    private fun buildQueue(c: MediaController): List<QueueEntry> {
        val timeline = c.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val out = ArrayList<QueueEntry>(timeline.windowCount)
        var i = timeline.getFirstWindowIndex(c.shuffleModeEnabled)
        while (i != C.INDEX_UNSET && out.size < timeline.windowCount) {
            val item = c.getMediaItemAt(i)
            out += QueueEntry(
                i, item.mediaId,
                item.mediaMetadata.title?.toString().orEmpty(),
                item.mediaMetadata.artist?.toString().orEmpty(),
            )
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
        }
        return out
    }

    // ---- Команды ----

    /** shuffle = true перемешивает список заранее, чтобы очередь была видна и редактируема. */
    /**
     * Запуск списка. Порядок по умолчанию берётся из настроек: по порядку, перемешать
     * или перемешать по жанрам; при перемешивании выбранный трек играет первым.
     * respectOrder = false — строго как в списке (альбом, плейлист, подкаст).
     */
    fun playTracks(
        tracks: List<Track>,
        startIndex: Int = 0,
        shuffle: Boolean = false,
        coverUri: Uri? = null,
        respectOrder: Boolean = false,
        startPositionMs: Long = 0L,
    ) {
        if (tracks.isEmpty()) return
        val start = startIndex.coerceIn(0, tracks.lastIndex)
        val order = AppSettings.playOrder.value
        val genreMix = order == PlayOrder.GENRE_MIX
        val list = when {
            shuffle -> if (genreMix) genreShuffle(tracks) else tracks.shuffled()
            respectOrder || order == PlayOrder.IN_ORDER || tracks.size < 3 -> tracks
            else -> {
                // Выбранный трек — первым, остальные в случайном порядке
                val rest = tracks.filterIndexed { i, _ -> i != start }
                listOf(tracks[start]) + if (genreMix) genreShuffle(rest, tracks[start].genre) else rest.shuffled()
            }
        }
        val first = if (list === tracks) start else 0
        withController { c ->
            c.shuffleModeEnabled = false
            c.setMediaItems(list.map { it.toMediaItem(coverUri) }, first, startPositionMs)
            c.repeatMode = AppSettings.defaultRepeat.value
            c.prepare()
            c.play()
        }
    }

    /** Подкаст продолжается с того места, где его остановили. */
    fun playPodcast(track: Track) {
        val pos = PodcastProgress.get(track.id).takeIf { track.durationMs <= 0 || it < track.durationMs - 15_000 } ?: 0L
        playTracks(listOf(track), respectOrder = true, startPositionMs = pos)
    }

    /** Завершить прослушивание: остановить и очистить очередь (плеер и мини-плеер скрываются). */
    fun stop() = withController { c ->
        rememberPodcastPosition(c)
        c.stop()
        c.clearMediaItems()
    }

    private fun rememberPodcastPosition(c: MediaController) {
        val id = c.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        if (MediaRepository.track(id)?.isPodcast == true) PodcastProgress.set(id, c.currentPosition)
    }

    /**
     * Перемешивание с чередованием жанров: два трека одного жанра подряд не идут, пока это возможно.
     * Треки без жанра ограничений не имеют.
     */
    private fun genreShuffle(tracks: List<Track>, previousGenre: String? = null): List<Track> {
        fun key(t: Track) = t.genre?.trim()?.lowercase().orEmpty()
        val buckets = tracks.shuffled().groupBy(::key).mapValues { ArrayDeque(it.value) }.toMutableMap()
        val out = ArrayList<Track>(tracks.size)
        var last = previousGenre?.trim()?.lowercase().orEmpty()
        while (buckets.isNotEmpty()) {
            val allowed = buckets.filterKeys { it.isEmpty() || it != last }
            val pool = allowed.ifEmpty { buckets }
            // Чаще берём из больших жанров — тогда в конце не остаётся пачки одного жанра
            val k = pool.maxBy { it.value.size + Random.nextDouble() * 1.5 }.key
            val bucket = buckets.getValue(k)
            out += bucket.removeFirst()
            if (bucket.isEmpty()) buckets.remove(k)
            last = k
        }
        return out
    }

    fun playNext(tracks: List<Track>) = withController { c ->
        if (c.mediaItemCount == 0) {
            playTracks(tracks)
        } else {
            c.addMediaItems(c.currentMediaItemIndex + 1, tracks.map { it.toMediaItem() })
        }
    }

    fun addToQueue(tracks: List<Track>) = withController { c ->
        if (c.mediaItemCount == 0) playTracks(tracks) else c.addMediaItems(tracks.map { it.toMediaItem() })
    }

    private var tick = 0

    fun togglePlay() = withController { c ->
        if (c.isPlaying) {
            rememberPodcastPosition(c)
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() = withController { it.seekToNext() }

    /** Перемотка на deltaMs (подкасты: −15 / +30 с). */
    fun seekBy(deltaMs: Long) = withController { c ->
        val target = (c.currentPosition + deltaMs).coerceIn(0L, c.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        c.seekTo(target)
        _state.update { it.copy(positionMs = target) }
    }

    fun setSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed) }
    fun speed(): Float = controller?.playbackParameters?.speed ?: 1f
    /** Сразу к предыдущему треку (без промежуточной перемотки в начало текущего). */
    fun previous() = withController {
        if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekTo(0)
    }

    fun seekTo(positionMs: Long) = withController {
        it.seekTo(positionMs)
        _state.update { s -> s.copy(positionMs = positionMs) }
    }

    fun skipTo(itemIndex: Int) = withController {
        it.seekToDefaultPosition(itemIndex)
        it.play()
    }

    fun moveQueueItem(from: Int, to: Int) = withController { it.moveMediaItem(from, to) }
    fun removeQueueItem(itemIndex: Int) = withController { it.removeMediaItem(itemIndex) }

    /** Выкл -> бесконечный повтор очереди -> повтор трека. Возвращает новый режим. */
    fun cycleRepeat(): Int {
        val next = when (_state.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        withController { it.repeatMode = next }
        return next
    }

    /** Убирает из очереди удалённые с устройства треки. */
    fun removeMedia(mediaIds: Set<String>) = withController { c ->
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (c.getMediaItemAt(i).mediaId in mediaIds) c.removeMediaItem(i)
        }
    }

    fun setRepeatMode(mode: Int) = withController { it.repeatMode = mode }

    fun toggleShuffle() =withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun setSleepTimer(minutes: Int?) = startSleepTimer(minutes?.let { it * 60_000L }, endOfTrack = false)

    /** Остановить музыку, когда закончится текущий трек. */
    fun setSleepAtEndOfTrack() {
        val c = controller ?: return
        val remaining = (c.duration - c.currentPosition).coerceAtLeast(1_000L)
        startSleepTimer(remaining, endOfTrack = true)
    }

    /** Продлить активный таймер. */
    fun extendSleepTimer(minutes: Int) {
        val at = _state.value.sleepAtMs ?: return
        val remaining = (at - System.currentTimeMillis()).coerceAtLeast(0L)
        val total = (_state.value.sleepTotalMs ?: remaining) + minutes * 60_000L
        startSleepTimer(remaining + minutes * 60_000L, endOfTrack = false, totalOverride = total)
    }

    private fun startSleepTimer(ms: Long?, endOfTrack: Boolean, totalOverride: Long? = null) {
        sleepJob?.cancel()
        runCatching { controller?.volume = 1f }
        if (ms == null) {
            _state.update { it.copy(sleepAtMs = null, sleepTotalMs = null, sleepEndOfTrack = false) }
            return
        }
        _state.update {
            it.copy(sleepAtMs = System.currentTimeMillis() + ms, sleepTotalMs = totalOverride ?: ms, sleepEndOfTrack = endOfTrack)
        }
        sleepJob = scope.launch {
            // В последние секунды громкость плавно затухает, затем пауза
            val fade = minOf(8_000L, ms / 2)
            delay(ms - fade)
            val steps = 40
            repeat(steps) { i ->
                runCatching { controller?.volume = 1f - (i + 1) / steps.toFloat() }
                delay(fade / steps)
            }
            controller?.pause()
            runCatching { controller?.volume = 1f }
            _state.update { it.copy(sleepAtMs = null, sleepTotalMs = null, sleepEndOfTrack = false) }
        }
    }
}
