package com.offplayer.app.youtube

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.MessageDigest

/**
 * Аккаунт YouTube. Вход выполняется на странице Google во встроенном браузере;
 * после него у нас есть только cookie youtube.com — они лежат в хранилище WebView на устройстве
 * и уходят лишь на youtube.com (запросы списков) и в yt-dlp (получение потоков).
 */
object YouTubeAccount {
    const val ORIGIN = "https://www.youtube.com"

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    fun refresh() {
        _signedIn.value = sapisid() != null
        if (!_signedIn.value) _avatar.value = null
    }

    private val _avatar = MutableStateFlow<String?>(null)
    /** Фотография профиля аккаунта — для кнопки аккаунта во вкладке YouTube. */
    val avatar: StateFlow<String?> = _avatar.asStateFlow()

    /** Меню аккаунта (как на сайте) — в нём фотография профиля. */
    suspend fun loadAvatar() {
        if (!_signedIn.value || _avatar.value != null) return
        val menu = runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { YouTubeApi.authPost("account/account_menu", org.json.JSONObject()) }
        }.getOrNull() ?: return
        _avatar.value = Regex(""""url":"(https:\\?/\\?/yt3\.[^"]+)"""").find(menu.toString())?.groupValues?.get(1)?.replace("\\/", "/")
    }

    private fun cookies(): Map<String, String> {
        val raw = runCatching { CookieManager.getInstance().getCookie(ORIGIN) }.getOrNull() ?: return emptyMap()
        return raw.split(';').mapNotNull { p ->
            val i = p.indexOf('=')
            if (i <= 0) null else p.substring(0, i).trim() to p.substring(i + 1).trim()
        }.toMap()
    }

    private fun sapisid(): String? = cookies().let { it["SAPISID"] ?: it["__Secure-3PAPISID"] }

    // ---- Несколько аккаунтов: Google держит их в одной сессии, выбор — номером authuser, как на сайте ----

    /** Аккаунт сессии: номер, имя, @псевдоним и фото. */
    data class Account(val index: Int, val name: String, val handle: String?, val photo: String?)

    private val prefs by lazy { com.offplayer.app.L.ctx.getSharedPreferences("youtube_account", Context.MODE_PRIVATE) }
    private val _authUser = MutableStateFlow(-1)
    /** Номер активного аккаунта в сессии Google. */
    val authUser: StateFlow<Int> get() {
        if (_authUser.value < 0) _authUser.value = prefs.getInt("auth_user", 0)
        return _authUser.asStateFlow()
    }
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    /** Меняется при смене аккаунта — по нему вкладка YouTube перезагружает ленты. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun switchTo(index: Int) {
        if (index == authUser.value) return
        _authUser.value = index
        prefs.edit().putInt("auth_user", index).apply()
        _avatar.value = _accounts.value.firstOrNull { it.index == index }?.photo
        _version.value++
    }

    /** Все аккаунты сессии: номера 0, 1, 2… опрашиваются, пока не начнут повторяться или не кончатся. */
    suspend fun loadAccounts() {
        if (!_signedIn.value) { _accounts.value = emptyList(); return }
        val found = ArrayList<Account>()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            for (i in 0 until 6) {
                val menu = runCatching { YouTubeApi.callAs(i, "account/account_menu", org.json.JSONObject()) }.getOrNull() ?: break
                val s = menu.toString()
                val name = Regex(""""accountName":\{"simpleText":"([^"]+)"""").find(s)?.groupValues?.get(1) ?: break
                val handle = Regex(""""channelHandle":\{"simpleText":"([^"]+)"""").find(s)?.groupValues?.get(1)
                // Неизвестный номер Google отдаёт как аккаунт по умолчанию — повтор значит, что аккаунты кончились
                if (found.any { it.name == name && it.handle == handle }) break
                val photo = Regex(""""url":"(https:\\?/\\?/yt3\.[^"]+)"""").find(s)?.groupValues?.get(1)?.replace("\\/", "/")
                found += Account(i, name, handle, photo)
            }
        }
        _accounts.value = found
        if (found.isNotEmpty() && found.none { it.index == authUser.value }) switchTo(found.first().index)
    }

    /** Заголовки авторизованного запроса к youtubei: cookie + SAPISIDHASH (как у сайта YouTube). */
    fun authHeaders(index: Int = authUser.value): Map<String, String> {
        val all = cookies()
        val sid = all["SAPISID"] ?: all["__Secure-3PAPISID"] ?: return emptyMap()
        val ts = System.currentTimeMillis() / 1000
        val hash = MessageDigest.getInstance("SHA-1").digest("$ts $sid $ORIGIN".toByteArray()).joinToString("") { "%02x".format(it) }
        return mapOf(
            "Cookie" to all.entries.joinToString("; ") { "${it.key}=${it.value}" },
            "Authorization" to "SAPISIDHASH ${ts}_$hash",
            "X-Origin" to ORIGIN,
            "X-Goog-AuthUser" to index.coerceAtLeast(0).toString(),
        )
    }

    /** cookies.txt (формат Netscape) для yt-dlp; null — не выполнен вход. */
    fun cookiesFile(context: Context): File? {
        val all = cookies()
        if (all["SAPISID"] == null && all["__Secure-3PAPISID"] == null) return null
        val f = File(context.cacheDir, "yt_cookies.txt")
        val expiry = System.currentTimeMillis() / 1000 + 30L * 24 * 3600
        f.writeText(buildString {
            append("# Netscape HTTP Cookie File\n")
            all.forEach { (k, v) -> append(".youtube.com\tTRUE\t/\tTRUE\t$expiry\t$k\t$v\n") }
        })
        return f
    }

    fun signOut() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        _signedIn.value = false
        _accounts.value = emptyList()
        _authUser.value = 0
        prefs.edit().putInt("auth_user", 0).apply()
        _version.value++
    }
}
