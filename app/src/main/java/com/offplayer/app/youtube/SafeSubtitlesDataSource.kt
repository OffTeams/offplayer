package com.offplayer.app.youtube

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Загрузка субтитров YouTube (timedtext): ответ читается целиком, и если YouTube ничего не отдал
 * (ошибка, пустой ответ, не WebVTT) — плееру отдаются пустые субтитры. Так выбранный, но
 * недоступный язык просто ничего не показывает, а не останавливает дорожку субтитров с ошибкой.
 * Остальные адреса (видео и звук) идут как обычно.
 */
@UnstableApi
class SafeSubtitlesDataSource(private val upstream: DataSource) : DataSource {
    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        if (!dataSpec.uri.toString().contains("timedtext")) {
            current = upstream
            return upstream.open(dataSpec)
        }
        val body = runCatching {
            upstream.open(dataSpec.buildUpon().setPosition(0).setLength(androidx.media3.common.C.LENGTH_UNSET.toLong()).build())
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = upstream.read(buf, 0, buf.size)
                if (n == androidx.media3.common.C.RESULT_END_OF_INPUT) break
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }.getOrNull()
        runCatching { upstream.close() }
        val ok = body != null && String(body, 0, minOf(body.size, 16), Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r').startsWith("WEBVTT")
        val data = if (ok) body!! else EMPTY_VTT
        val source = ByteArrayDataSource(data)
        current = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = current?.read(buffer, offset, length) ?: androidx.media3.common.C.RESULT_END_OF_INPUT

    override fun getUri(): Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

    override fun close() {
        current?.close()
        current = null
    }

    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = SafeSubtitlesDataSource(upstream.createDataSource())
    }

    private companion object {
        val EMPTY_VTT = "WEBVTT\n\n".toByteArray()
    }
}
