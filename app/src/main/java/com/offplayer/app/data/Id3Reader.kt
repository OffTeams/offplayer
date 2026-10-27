package com.offplayer.app.data

import java.io.DataInputStream
import java.io.File
import java.nio.charset.Charset

/**
 * Минимальный разбор тегов ID3v2.3/2.4 (MP3): название, исполнитель, альбом, номер трека.
 *
 * Нужен потому, что системный MediaMetadataRetriever на некоторых прошивках (например, Samsung)
 * неверно декодирует Unicode в тегах и возвращает «????» вместо японских/русских названий.
 */
object Id3Reader {
    data class Tags(val title: String?, val artist: String?, val album: String?, val trackNo: Int?, val genre: String? = null)

    private val WANTED = setOf("TIT2", "TPE1", "TPE2", "TALB", "TRCK", "TCON")

    fun read(file: File): Tags? = runCatching {
        DataInputStream(file.inputStream().buffered()).use { input ->
            val header = ByteArray(10)
            input.readFully(header)
            if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return null
            val major = header[3].toInt()
            if (major != 3 && major != 4) return null
            val flags = header[5].toInt()
            val tagSize = synchsafe(header, 6)
            var pos = 0
            // Расширенный заголовок пропускаем
            if (flags and 0x40 != 0) {
                val b = ByteArray(4)
                input.readFully(b)
                val extSize = if (major == 4) synchsafe(b, 0) - 4 else int32(b, 0)
                input.skipFully(extSize)
                pos += 4 + extSize
            }
            val found = HashMap<String, String>()
            val frameHeader = ByteArray(10)
            while (pos + 10 <= tagSize && found.size < WANTED.size) {
                input.readFully(frameHeader)
                pos += 10
                if (frameHeader[0].toInt() == 0) break // паддинг
                val id = String(frameHeader, 0, 4, Charsets.ISO_8859_1)
                val size = if (major == 4) synchsafe(frameHeader, 4) else int32(frameHeader, 4)
                if (size <= 0 || pos + size > tagSize) break
                if (id in WANTED && size < 64 * 1024) {
                    val data = ByteArray(size)
                    input.readFully(data)
                    decodeText(data)?.let { found[id] = it }
                } else {
                    input.skipFully(size)
                }
                pos += size
            }
            if (found.isEmpty()) return null
            Tags(
                title = found["TIT2"],
                artist = found["TPE1"] ?: found["TPE2"],
                album = found["TALB"],
                trackNo = found["TRCK"]?.substringBefore('/')?.trim()?.toIntOrNull(),
                genre = cleanGenre(found["TCON"]),
            )
        }
    }.getOrNull()

    /** Текстовый фрейм: первый байт — кодировка; несколько значений (ID3v2.4) разделены нулём. */
    private fun decodeText(data: ByteArray): String? {
        if (data.isEmpty()) return null
        val charset: Charset = when (data[0].toInt()) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16 // с BOM
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> return null
        }
        return String(data, 1, data.size - 1, charset)
            .split('\u0000')
            .map { it.trim().removePrefix("﻿") }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(", ")
            .ifEmpty { null }
    }

    private fun synchsafe(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or
            ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun int32(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun DataInputStream.skipFully(n: Int) {
        var left = n.toLong()
        while (left > 0) {
            val s = skip(left)
            if (s <= 0) { if (read() < 0) return; left-- } else left -= s
        }
    }

    /** Жанр без числовых кодов ID3v1 вида «(13)». */
    fun cleanGenre(raw: String?): String? =
        raw?.replace(Regex("""^\(\d+\)"""), "")?.trim()?.takeIf { it.isNotEmpty() && !it.all(Char::isDigit) }

    /** Строка испорчена неверной декодировкой: «????????» вместо символов. */
    fun looksBroken(s: String?): Boolean {
        if (s.isNullOrEmpty()) return false
        val q = s.count { it == '?' || it == '�' }
        return q >= 3 && q * 3 >= s.count { !it.isWhitespace() }
    }
}
