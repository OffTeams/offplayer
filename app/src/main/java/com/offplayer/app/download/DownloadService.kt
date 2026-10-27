package com.offplayer.app.download

import com.offplayer.app.tr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.net.wifi.WifiManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.offplayer.app.MainActivity
import com.offplayer.app.R
import com.offplayer.app.ui.DownloadDoneEvent
import com.offplayer.app.ui.UiEvents
import com.offplayer.app.data.MediaRepository
import com.offplayer.app.data.PlaylistStore
import com.offplayer.app.data.VideoLibraryStore
import com.offplayer.app.model.MediaThumb
import com.offplayer.app.playback.ThumbBitmapLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Фоновая служба: выполняет очередь загрузок по одной и показывает прогресс в уведомлении. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var worker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, tr(R.string.tab_downloads), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, tr(R.string.download_finished), NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(tr(R.string.preparing), 0f, true),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (worker?.isActive != true) {
            // Пока идёт очередь, процессор и Wi-Fi не засыпают: иначе с выключенным экраном
            // или в фоне yt-dlp «замирает» и загрузка останавливается
            acquireLocks()
            worker = scope.launch {
                val ticker = launch {
                    while (isActive) {
                        Downloader.runningTask()?.let { t ->
                            val title = t.title ?: t.url
                            val n = buildNotification(title, t.progress, t.progress <= 0f)
                            runCatching { NotificationManagerCompat.from(this@DownloadService).notify(NOTIFICATION_ID, n) }
                        }
                        delay(700)
                    }
                }
                try {
                    Downloader.ensureInit(applicationContext)
                    while (true) {
                        val next = Downloader.nextQueued() ?: break
                        Downloader.run(applicationContext, next)
                        Downloader.tasks.value.firstOrNull { it.id == next.id }?.let { notifyFinished(it) }
                    }
                } catch (t: Throwable) {
                    Downloader.failPending(tr(R.string.ytdlp_error, t.message?.take(150) ?: ""))
                } finally {
                    ticker.cancel()
                    releaseLocks()
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(title: String, progress: Float, indeterminate: Boolean) =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(if (indeterminate) tr(R.string.downloading) else "${(progress * 100).toInt()}%")
            .setProgress(100, (progress * 100).toInt(), indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 1, Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_DOWNLOADS),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .build()

    /** Итог загрузки: нажатие ведёт к треку/видео (с подсветкой) или открывает микс/список. */
    private suspend fun notifyFinished(task: DownloadTask) {
        if (task.status != DownloadStatus.DONE && task.status != DownloadStatus.FAILED) return
        val done = task.status == DownloadStatus.DONE
        val result = task.result
        // Приложение открыто — вместо уведомления в шторке короткая плашка внутри приложения
        if (UiEvents.appVisible) {
            UiEvents.downloadDone.tryEmit(
                DownloadDoneEvent(
                    title = task.title ?: task.url,
                    message = if (done) task.message ?: tr(R.string.download_finished) else task.message ?: tr(R.string.download_failed),
                    success = done,
                    result = result.takeIf { done },
                )
            )
            return
        }
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply {
                if (done && result != null) {
                    action = MainActivity.ACTION_REVEAL
                    putExtra(MainActivity.EXTRA_REVEAL_KIND, result.kind.name)
                    putExtra(MainActivity.EXTRA_REVEAL_ID, result.id)
                } else {
                    action = MainActivity.ACTION_OPEN_DOWNLOADS
                }
            }
        // Превью: обложка трека / кадр видео (для микса и списка — первого элемента)
        val largeIcon = if (done && result != null) runCatching { previewFor(result) }.getOrNull() else null
        val n = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(if (done) task.title ?: tr(R.string.download_finished) else tr(R.string.download_failed))
            .setContentText(task.message ?: task.url)
            .setLargeIcon(largeIcon)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, task.id.hashCode(), intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(task.id.hashCode(), n) }
    }

    private suspend fun previewFor(result: DownloadResult): android.graphics.Bitmap? {
        val thumb = when (result.kind) {
            DownloadResult.Kind.TRACK, DownloadResult.Kind.PODCAST -> MediaRepository.track(result.id.toLong())?.let { MediaThumb(it.uri, false) }
            DownloadResult.Kind.VIDEO -> MediaRepository.video(result.id.toLong())?.let { MediaThumb(it.uri, true) }
            DownloadResult.Kind.MIX -> PlaylistStore.get(result.id)?.trackIds
                ?.firstNotNullOfOrNull { MediaRepository.track(it) }?.let { MediaThumb(it.uri, false) }
            DownloadResult.Kind.LIST -> VideoLibraryStore.lists.value.firstOrNull { it.id == result.id }?.videoIds
                ?.firstNotNullOfOrNull { MediaRepository.video(it) }?.let { MediaThumb(it.uri, true) }
        } ?: return null
        return ThumbBitmapLoader.load(this, thumb)
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private fun acquireLocks() {
        if (wakeLock?.isHeld != true) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OFFPlayer:download")
                .apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
        }
        if (wifiLock?.isHeld != true) {
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_HIGH_PERF else WifiManager.WIFI_MODE_FULL
            wifiLock = runCatching {
                applicationContext.getSystemService(WifiManager::class.java)
                    .createWifiLock(mode, "OFFPlayer:download")
                    .apply { setReferenceCounted(false); acquire() }
            }.getOrNull()
        }
    }

    private fun releaseLocks() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        wifiLock = null
    }

    override fun onDestroy() {
        releaseLocks()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "downloads"
        const val CHANNEL_DONE = "downloads_done"
        const val NOTIFICATION_ID = 42
    }
}
