package com.offplayer.app.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yausername.youtubedl_android.YoutubeDL
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Фоновая проверка актуальности плагина загрузки (yt-dlp). Python и ffmpeg встроены
 * в приложение и обновляются вместе с ним — обновлять отдельно можно только yt-dlp.
 *
 *  • при появлении сети — разовая проверка (не чаще раза в 3 часа, чтобы смена Wi-Fi/мобильной сети её не дёргала);
 *  • если устройство всё время в сети — ежедневно около 04:00.
 */
object PluginUpdater {
    private const val PERIODIC = "plugin_update_daily"
    private const val ON_CONNECT = "plugin_update_on_connect"
    private const val PREFS = "plugin_updater"
    private const val KEY_LAST = "last_check"
    private val MIN_INTERVAL_MS = TimeUnit.HOURS.toMillis(3)

    private val networkConstraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun init(context: Context) {
        val app = context.applicationContext
        scheduleDaily(app)
        // Разовая проверка при каждом подключении к сети
        runCatching {
            val cm = app.getSystemService(ConnectivityManager::class.java)
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = checkSoon(app)
            })
        }
    }

    /** Ежедневная проверка около 04:00 (WorkManager выполнит её, когда есть сеть). */
    private fun scheduleDaily(context: Context) {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 4); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Разовая проверка, если с прошлой прошло достаточно времени. */
    fun checkSoon(context: Context) {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST, 0)
        if (System.currentTimeMillis() - last < MIN_INTERVAL_MS) return
        val request = OneTimeWorkRequestBuilder<UpdateWorker>().setConstraints(networkConstraint).build()
        WorkManager.getInstance(context).enqueueUniqueWork(ON_CONNECT, ExistingWorkPolicy.KEEP, request)
    }

    fun isOnline(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }.getOrDefault(false)

    /** Обновление yt-dlp. Используется и фоновой проверкой, и кнопкой «Обновить и повторить». */
    suspend fun update(context: Context): Boolean {
        Downloader.ensureInit(context)
        val ok = runCatching { YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext) }.isSuccess
        if (ok) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_LAST, System.currentTimeMillis()).apply()
        }
        return ok
    }

    fun pluginVersion(context: Context): String? =
        runCatching { YoutubeDL.getInstance().version(context.applicationContext) }.getOrNull()
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Во время загрузки плагин не трогаем — проверим чуть позже
        if (Downloader.runningTask() != null) return Result.retry()
        return if (PluginUpdater.update(applicationContext)) Result.success() else Result.retry()
    }
}
