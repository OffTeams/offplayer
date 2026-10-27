package com.offplayer.app.download

import kotlin.math.exp

/**
 * Прогресс загрузки по этапам (для одного ролика 0..1, для плейлиста — по роликам):
 *  • загрузка — по процентам yt-dlp (видео: сначала картинка, затем звук);
 *  • обработка (конвертация, склейка, обложка, теги) — yt-dlp процентов не сообщает, поэтому прогресс
 *    растёт по оценке времени от размера файла и замедляется к концу этапа, не упираясь в 99 %.
 * Методы вызываются из потока чтения вывода yt-dlp и из таймера — поэтому синхронизированы.
 */
internal class ProgressTracker(private val isAudio: Boolean, private val audioFormat: String) {
    private var index = 1
    private var count = 1
    private var stream = -1
    private var fraction = 0f
    private var bytesMb = 0.0
    private var post = false
    private var phaseStart = 0f
    private var phaseSpan = 0f
    private var phaseTau = 1.0
    private var phaseStartedAt = 0L

    /** Доля шкалы ролика на саму загрузку; остальное — обработка. */
    private val downloadShare = 0.75f

    @Synchronized fun item(): Pair<Int, Int> = index to count
    @Synchronized fun postProcessing(): Boolean = post
    @Synchronized fun overall(): Float = ((index - 1) + fraction) / count.coerceAtLeast(1)

    @Synchronized fun onItem(i: Int, n: Int) {
        if (i != index) {
            stream = -1
            fraction = 0f
            bytesMb = 0.0
            post = false
        }
        index = i
        count = n
    }

    @Synchronized fun onLine(line: String, p: Float?) {
        if (line.startsWith("[download] Destination:")) {
            stream++
            post = false
        }
        SIZE_REGEX.find(line)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: 0.0
            bytesMb += when (m.groupValues[2].uppercase()) {
                "K" -> v / 1024
                "G" -> v * 1024
                else -> v
            }
        }
        val kind = postKind(line)
        if (kind != null) {
            startPhase(kind)
            return
        }
        if (p != null && line.startsWith("[download]") && !post) {
            val target = when {
                isAudio -> p * downloadShare
                stream <= 0 -> p * downloadShare * 0.83f // картинка — основная часть объёма
                else -> downloadShare * 0.83f + p * downloadShare * 0.17f
            }
            fraction = maxOf(fraction, target)
        }
    }

    /** Шаг таймера: продвигает оценку текущего этапа обработки. true — значение изменилось. */
    @Synchronized fun tick(): Boolean {
        if (!post) return false
        val t = (System.currentTimeMillis() - phaseStartedAt) / 1000.0
        val est = phaseStart + phaseSpan * (1 - exp(-t / phaseTau)).toFloat()
        if (est <= fraction + 0.0005f) return false
        fraction = est
        return true
    }

    private fun startPhase(kind: String) {
        post = true
        fraction = maxOf(fraction, downloadShare)
        val left = 0.985f - fraction
        // Тяжёлые этапы (перекодирование, склейка) получают большую часть оставшейся шкалы
        val heavy = kind == "extract" || kind == "merge"
        phaseStart = fraction
        phaseSpan = left * if (heavy) 0.85f else 0.6f
        val mb = bytesMb.coerceAtLeast(1.0)
        // Примерная скорость на телефоне: перекодирование в MP3 — около секунды на мегабайт,
        // перепаковка без перекодирования — в разы быстрее
        val secondsPerMb = when (kind) {
            "extract" -> if (audioFormat == "mp3") 0.9 else 0.12
            "merge" -> 0.05
            else -> 0.04
        }
        // За 2.5τ этап проходит ~92 % своей части шкалы
        phaseTau = (mb * secondsPerMb / 2.5).coerceIn(0.6, 600.0)
        phaseStartedAt = System.currentTimeMillis()
    }

    private fun postKind(line: String): String? = when {
        line.startsWith("[ExtractAudio]") -> "extract"
        line.startsWith("[Merger]") || line.startsWith("[VideoConvertor]") || line.startsWith("[VideoRemuxer]") -> "merge"
        line.startsWith("[EmbedThumbnail]") || line.startsWith("[ThumbnailsConvertor]") || line.startsWith("[Metadata]") ||
            line.startsWith("[EmbedSubtitle]") || line.startsWith("[Fixup") -> "light"
        else -> null
    }

    private companion object {
        val SIZE_REGEX = Regex("""\[download]\s+100(?:\.0+)?% of\s+~?\s*([\d.]+)\s*([KMG])i?B""")
    }
}
