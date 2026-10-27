package com.offplayer.app.update

import com.offplayer.app.tr
import com.offplayer.app.R

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Новая версия из GitHub Releases. */
data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val sizeBytes: Long,
    val pageUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    /** Файл скачан и проверен — ждём подтверждения установки системой. */
    data class Installing(val info: UpdateInfo) : UpdateState
    /** Нужно разрешить OFFPlayer устанавливать приложения. */
    data class NeedsPermission(val info: UpdateInfo) : UpdateState
    data class Error(val message: String, val info: UpdateInfo? = null) : UpdateState
}

/**
 * Обновление приложения из публичного репозитория OffTeams/offplayer-updates:
 * проверка последнего релиза, загрузка APK под архитектуру телефона, проверка, что файл
 * подписан тем же ключом, что и установленное приложение, и установка через системный установщик.
 */
object AppUpdater {
    private const val REPO = "OffTeams/offplayer-updates"
    private const val PREFS = "app_updater"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_SKIPPED = "skipped_version"
    private const val KEY_AUTO = "auto_check"
    private const val CHECK_INTERVAL_MS = 12 * 60 * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _autoCheck = MutableStateFlow(true)
    val autoCheck: StateFlow<Boolean> = _autoCheck.asStateFlow()

    /** Окно «Доступно обновление» показывается сразу после автоматической проверки. */
    private val _prompt = MutableStateFlow(false)
    val prompt: StateFlow<Boolean> = _prompt.asStateFlow()

    fun init(context: Context) {
        _autoCheck.value = prefs(context).getBoolean(KEY_AUTO, true)
    }

    fun setAutoCheck(context: Context, v: Boolean) {
        _autoCheck.value = v
        prefs(context).edit().putBoolean(KEY_AUTO, v).apply()
    }

    fun currentVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"

    /** Автоматическая проверка при запуске: не чаще раза в 12 часов, пропущенную версию не предлагаем. */
    fun checkIfDue(context: Context) {
        val app = context.applicationContext
        if (!_autoCheck.value) return
        val last = prefs(app).getLong(KEY_LAST_CHECK, 0)
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return
        check(app, manual = false)
    }

    fun check(context: Context, manual: Boolean = true) {
        val app = context.applicationContext
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Checking
            val result = runCatching { fetchLatest(app) }
            prefs(app).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            val info = result.getOrElse {
                _state.value = if (manual) UpdateState.Error(tr(R.string.upd_check_failed)) else UpdateState.Idle
                return@launch
            }
            val newer = info != null && isNewer(info.version, currentVersion(app))
            when {
                !newer -> _state.value = if (manual) UpdateState.UpToDate else UpdateState.Idle
                else -> {
                    _state.value = UpdateState.Available(info!!)
                    val skipped = prefs(app).getString(KEY_SKIPPED, null)
                    if (manual || skipped != info.version) _prompt.value = true
                }
            }
        }
    }

    /** «Позже»: окно закрывается, эта версия больше сама не предлагается (в настройках остаётся). */
    fun dismissPrompt(context: Context) {
        _prompt.value = false
        (_state.value as? UpdateState.Available)?.let {
            prefs(context).edit().putString(KEY_SKIPPED, it.info.version).apply()
        }
    }

    fun showPrompt() {
        _prompt.value = true
    }

    /** Скачать и установить. */
    fun download(context: Context, info: UpdateInfo) {
        val app = context.applicationContext
        if (job?.isActive == true && _state.value is UpdateState.Downloading) return
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(info)
            return
        }
        job = scope.launch {
            _state.value = UpdateState.Downloading(info, 0f)
            val dir = File(app.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { if (it.name != info.apkName) it.delete() }
            val file = File(dir, info.apkName)
            try {
                downloadTo(info.apkUrl, file, info.sizeBytes) { p -> _state.value = UpdateState.Downloading(info, p) }
                verify(app, file, info)
                _state.value = UpdateState.Installing(info)
                install(app, file)
            } catch (t: Throwable) {
                file.delete()
                _state.value = UpdateState.Error(t.message ?: tr(R.string.upd_download_failed), info)
            }
        }
    }

    /** Экран системных настроек «Установка неизвестных приложений» для OFFPlayer. */
    fun permissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Вернулись из настроек — если разрешение выдано, продолжаем. */
    fun onPermissionResult(context: Context) {
        val s = _state.value as? UpdateState.NeedsPermission ?: return
        if (Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()) download(context, s.info)
    }

    internal fun onInstallFailed(message: String) {
        val info = (_state.value as? UpdateState.Installing)?.info
        _state.value = UpdateState.Error(message, info)
    }

    // ---- Внутреннее ----

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private suspend fun fetchLatest(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "OFFPlayer")
        }
        try {
            if (conn.responseCode == 404) return@withContext null
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            val o = JSONObject(conn.inputStream.bufferedReader().readText())
            val version = o.optString("tag_name").removePrefix("v").trim()
            val assets = o.optJSONArray("assets") ?: return@withContext null
            data class A(val name: String, val url: String, val size: Long)
            val list = (0 until assets.length()).mapNotNull { i ->
                val a = assets.optJSONObject(i) ?: return@mapNotNull null
                A(a.optString("name"), a.optString("browser_download_url"), a.optLong("size"))
            }.filter { it.name.endsWith(".apk") }
            // APK под архитектуру процессора телефона, иначе универсальный
            val pick = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi -> list.firstOrNull { it.name.endsWith("-$abi.apk") } }
                ?: list.firstOrNull { it.name.endsWith("-universal.apk") }
                ?: return@withContext null
            UpdateInfo(
                version = version,
                notes = o.optString("body").trim(),
                apkUrl = pick.url,
                apkName = pick.name,
                sizeBytes = pick.size,
                pageUrl = o.optString("html_url"),
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Сравнение версий вида 1.14.4. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun downloadTo(url: String, file: File, expected: Long, onProgress: (Float) -> Unit) {
        var conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", "OFFPlayer")
        try {
            if (conn.responseCode !in 200..299) error(tr(R.string.upd_server, conn.responseCode))
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: expected
            var done = 0L
            var lastReport = 0L
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0 && done - lastReport > 256 * 1024) {
                            lastReport = done
                            onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (expected > 0 && file.length() != expected) error(tr(R.string.upd_incomplete))
            onProgress(1f)
        } finally {
            conn.disconnect()
        }
    }

    /** Защита от подмены: тот же пакет, более новая версия и та же подпись, что у установленного приложения. */
    private fun verify(context: Context, file: File, info: UpdateInfo) {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: error(tr(R.string.upd_corrupt))
        if (archive.packageName != context.packageName) error(tr(R.string.upd_other_app))
        val installed = pm.getPackageInfo(context.packageName, flags)
        if (versionCode(archive) <= versionCode(installed)) error(tr(R.string.upd_not_newer))
        val a = certs(archive)
        val b = certs(installed)
        if (a.isEmpty() || a.intersect(b).isEmpty()) error(tr(R.string.upd_bad_signature))
    }

    @Suppress("DEPRECATION")
    private fun versionCode(p: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun certs(p: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            val si = p.signingInfo ?: return emptySet()
            if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        } else p.signatures
        return sigs.orEmpty().map { s ->
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    /** Установка через системный установщик (PackageInstaller): пользователь подтверждает в системном окне. */
    private fun install(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(
                context, sessionId, Intent(context, UpdateInstallReceiver::class.java), flags,
            )
            session.commit(pi.intentSender)
        }
    }
}

/** Ответ системного установщика: показать окно подтверждения или сообщить об ошибке. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // приложение перезапустится уже обновлённым
            PackageInstaller.STATUS_FAILURE_ABORTED -> AppUpdater.onInstallFailed(tr(R.string.upd_install_cancelled))
            else -> AppUpdater.onInstallFailed(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.let { tr(R.string.upd_install_failed_msg, it) }
                    ?: tr(R.string.upd_install_failed)
            )
        }
    }
}
