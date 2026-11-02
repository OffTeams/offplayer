package com.offplayer.app.youtube

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.offplayer.app.MainActivity
import com.offplayer.app.R
import com.offplayer.app.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Оповещения YouTube как уведомления приложения: раз в 15 минут (так часто разрешает Android)
 * проверяется число новых оповещений аккаунта, и непрочитанные показываются уведомлениями.
 * Нажатие на уведомление открывает ролик в плеере OFFPlayer.
 */
class YtNotifyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        if (!YtNotifications.pushEnabled(app) || !com.offplayer.app.data.AppSettings.youtubeTab.value) return Result.success()
        // Cookie аккаунта лежат в хранилище WebView — оно поднимается в главном потоке
        withContext(Dispatchers.Main) {
            runCatching { android.webkit.CookieManager.getInstance() }
            YouTubeAccount.refresh()
        }
        if (!YouTubeAccount.signedIn.value) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        runCatching {
            // Список проверяется всегда: счётчик непросмотренных обнуляется, когда оповещения открывают
            // в приложении YouTube или на сайте, — по нему новые оповещения терялись
            val fresh = YtNotifications.listForPush(app)
            YtNotifications.refreshUnseen()
            val shown = fresh.take(MAX_AT_ONCE)
            shown.forEach { post(app, it) }
            if (shown.isNotEmpty()) postSummary(app, shown)
        }
        return Result.success()
    }

    private suspend fun post(context: Context, n: YtNotification) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, tr(R.string.yt_notifications_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_YOUTUBE)
            .putExtra(MainActivity.EXTRA_YT_VIDEO, n.videoId)
            .putExtra(MainActivity.EXTRA_YT_SHORT, n.isShort)
            .putExtra(MainActivity.EXTRA_YT_CHANNEL, n.channelId)
            .putExtra(MainActivity.EXTRA_YT_COMMENT, n.commentId)
            .putExtra(MainActivity.EXTRA_YT_PARAMS, n.watchParams)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val key = n.videoId ?: n.channelId ?: n.text
        val pi = PendingIntent.getActivity(context, key.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val avatar = n.avatar?.let { url -> withContext(Dispatchers.IO) { runCatching { java.net.URL(url).openStream().use { BitmapFactory.decodeStream(it) } }.getOrNull() } }
        val preview = n.thumbnail?.let { url -> withContext(Dispatchers.IO) { runCatching { java.net.URL(url).openStream().use { BitmapFactory.decodeStream(it) } }.getOrNull() } }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_youtube)
            .setContentTitle("YouTube")
            .setContentText(n.text)
            .setStyle(
                if (preview != null) NotificationCompat.BigPictureStyle().bigPicture(preview).setSummaryText(n.text)
                else NotificationCompat.BigTextStyle().bigText(n.text)
            )
            .setLargeIcon(avatar)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setGroup(GROUP)
        runCatching { nm.notify(key.hashCode(), builder.build()) }
    }

    /**
     * Сводное уведомление группы: без него Android показывает каждую проверку отдельной стопкой.
     * С ним все оповещения YouTube — одна группа, новые добавляются к старым.
     */
    private fun postSummary(context: Context, latest: List<YtNotification>) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_YOUTUBE)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, SUMMARY_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val summary = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_youtube)
            .setContentTitle("YouTube")
            .setContentText(latest.first().text)
            .setStyle(NotificationCompat.InboxStyle().also { s -> latest.forEach { s.addLine(it.text) } })
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setContentIntent(pi)
            .setAutoCancel(true)
        runCatching { nm.notify(SUMMARY_ID, summary.build()) }
    }

    companion object {
        private const val SUMMARY_ID = 0x7907
        private const val CHANNEL = "youtube_notifications"
        private const val GROUP = "youtube"
        private const val WORK = "youtube_notifications"
        private const val MAX_AT_ONCE = 5

        /** Включает или выключает фоновую проверку — по настройке и по включённой вкладке YouTube. */
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            if (!YtNotifications.pushEnabled(context) || !com.offplayer.app.data.AppSettings.youtubeTab.value) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val request = PeriodicWorkRequestBuilder<YtNotifyWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
