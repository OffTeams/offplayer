package com.offplayer.app.youtube

import com.offplayer.app.L
import com.offplayer.app.data.LibraryStore
import com.offplayer.app.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Строка текста; startMs — момент, когда её поют (null — текст без таймингов). */
data class LyricLine(val text: String, val startMs: Long?)

class Lyrics(val lines: List<LyricLine>) {
    val synced: Boolean get() = lines.any { it.startMs != null }
}

/**
 * Текст песен с YouTube Music. Строки с таймингами отдаёт мобильный клиент YouTube Music —
 * но только для «песни» (аудиоверсии), а не для клипа, поэтому при необходимости песня ищется по названию.
 */
object YtLyrics {
    private const val VERSION = "8.30.52"
    private val cache = HashMap<Long, Lyrics?>()

    suspend fun forTrack(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        synchronized(cache) { if (cache.containsKey(track.id)) return@withContext cache[track.id] }
        val known = knownId(track)
        var best = known?.let { runCatching { fetch(it) }.getOrNull() }
        // У клипа часто нет таймингов — ищем песню на YouTube Music
        if (best == null || !best.synced) {
            val found = runCatching { searchSong(track) }.getOrNull()
            if (found != null && found != known) runCatching { fetch(found) }.getOrNull()?.let { l -> if (best == null || l.synced) best = l }
        }
        synchronized(cache) { cache[track.id] = best }
        best
    }

    /** ID ролика, из которого трек: онлайн-плейлист, имя скачанного файла («… [ID].mp3») или ссылка-источник. */
    fun videoIdOf(track: Track): String? = knownId(track)

    private fun knownId(track: Track): String? =
        OnlineStore.item(track.id)?.ytId
            ?: LibraryStore.all().firstOrNull { it.id == track.id }?.file?.let { Regex("""\[([\w-]{11})]""").find(it)?.groupValues?.get(1) }
            ?: track.channelUrl?.let { Regex("""(?:v=|youtu\.be/)([\w-]{11})""").find(it)?.groupValues?.get(1) }

    private fun fetch(videoId: String): Lyrics? {
        val next = call("next", JSONObject().put("videoId", videoId))
        val browseId = Regex(""""browseId":"(MPLYt[^"]+)"""").find(next.toString())?.groupValues?.get(1) ?: return null
        val page = call("browse", JSONObject().put("browseId", browseId))
        val timed = find(page, "timedLyricsData") as? JSONArray
        if (timed != null && timed.length() > 0) {
            val lines = (0 until timed.length()).mapNotNull { i ->
                val o = timed.optJSONObject(i) ?: return@mapNotNull null
                val text = o.optString("lyricLine").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                LyricLine(text, o.optJSONObject("cueRange")?.optString("startTimeMilliseconds")?.toLongOrNull())
            }
            if (lines.isNotEmpty()) return Lyrics(lines)
        }
        // Текст без таймингов (описание вкладки «Текст»)
        val plain = Regex(""""description":\{"runs":\[\{"text":"((?:[^"\\]|\\.)*)"""").find(page.toString())?.groupValues?.get(1)
            ?.let { JSONObject("{\"t\":\"$it\"}").getString("t") } ?: return null
        return Lyrics(plain.lines().map { LyricLine(it, null) })
    }

    /**
     * Песня на YouTube Music для трека. Песни есть не везде — результат поиска принимается, только если
     * совпадают и название, и исполнитель; иначе null, и вкладка «Текст» не появляется (чужой текст хуже, чем никакого).
     */
    private fun searchSong(track: Track): String? {
        // Фильтр «Песни» поиска YouTube Music
        val r = call("search", JSONObject().put("query", "${track.title} ${track.artist}").put("params", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"))
        val wantTitle = norm(track.title)
        val wantArtists = artists(track.artist)
        return songs(r).take(5).firstOrNull { (_, title, artist) ->
            val t = norm(title)
            val titleOk = t.isNotEmpty() && (t == wantTitle || t.contains(wantTitle) || wantTitle.contains(t))
            // Неизвестный исполнитель (нет тега) — сверяем только название
            val artistOk = wantArtists.isEmpty() || artists(artist).any { it in wantArtists } ||
                wantArtists.any { w -> norm(artist).contains(w) }
            titleOk && artistOk
        }?.first
    }

    /** Песни из ответа поиска: (videoId, название, исполнитель). */
    private fun songs(root: JSONObject): List<Triple<String, String, String>> {
        val out = ArrayList<Triple<String, String, String>>()
        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val item = node.optJSONObject("musicResponsiveListItemRenderer")
                    if (item != null) {
                        val id = Regex(""""videoId":"([\w-]{11})"""").find(item.toString())?.groupValues?.get(1)
                        val cols = item.optJSONArray("flexColumns")
                        fun col(i: Int): String {
                            val runs = cols?.optJSONObject(i)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")?.optJSONArray("runs") ?: return ""
                            return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
                        }
                        if (id != null) out += Triple(id, col(0), col(1).substringBefore(" • "))
                        return
                    }
                    for (k in node.keys()) walk(node.opt(k))
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
            }
        }
        walk(root)
        return out
    }

    /** Название без скобок, «feat.» и знаков: «Believer (Official Audio)» → «believer». */
    private fun norm(s: String): String = s.lowercase()
        .replace(Regex("""\([^)]*\)|\[[^]]*]"""), " ")
        .replace(Regex("""\b(feat|ft)\.?\s.*$"""), " ")
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .trim()

    /** Исполнители по отдельности: «A, B & C», «A x B», «A feat. B». */
    private fun artists(s: String): Set<String> =
        s.lowercase().split(Regex("""\s*(,|&|\bx\b|\bfeat\.?|\bft\.?|\band\b|\bи\b)\s*"""))
            .map { norm(it) }.filter { it.isNotEmpty() && it != "неизвестный исполнитель" && it != "unknown artist" }.toSet()

    private fun find(node: Any?, key: String): Any? {
        when (node) {
            is JSONObject -> {
                if (node.has(key)) return node.opt(key)
                for (k in node.keys()) find(node.opt(k), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) find(node.opt(i), key)?.let { return it }
        }
        return null
    }

    private fun call(endpoint: String, body: JSONObject): JSONObject {
        val locale = L.ctx.resources.configuration.locales[0]
        body.put("context", JSONObject().put("client", JSONObject()
            .put("clientName", "ANDROID_MUSIC").put("clientVersion", VERSION)
            .put("androidSdkVersion", 34).put("osName", "Android").put("osVersion", "14")
            .put("hl", locale.language.ifEmpty { "en" }).put("gl", locale.country.ifEmpty { "US" })))
        val conn = URL("https://music.youtube.com/youtubei/v1/$endpoint?prettyPrint=false").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "com.google.android.apps.youtube.music/$VERSION (Linux; U; Android 14) gzip")
            conn.setRequestProperty("X-YouTube-Client-Name", "21")
            conn.setRequestProperty("X-YouTube-Client-Version", VERSION)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return JSONObject(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }
}
