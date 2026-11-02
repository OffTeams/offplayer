package com.offplayer.app.youtube

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import kotlin.math.min

/**
 * Загрузка потоков YouTube кусками: каждый запрос — не больше [CHUNK] байт через параметр «range=» в адресе.
 * Если просить файл целиком, YouTube через несколько секунд урезает скорость до скорости воспроизведения,
 * и ролик подвисает при перемотке и на плохой сети. Так же делает NewPipe.
 * Полный размер берётся из параметра «clen» адреса; без него — обычная загрузка.
 */
@OptIn(UnstableApi::class)
class ChunkedDataSource(private val upstream: HttpDataSource) : BaseDataSource(/* isNetwork = */ true) {
    private var spec: DataSpec? = null
    private var position = 0L
    private var end = 0L
    private var chunkLeft = 0L
    private var passthrough = false

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        transferInitializing(dataSpec)
        val total = dataSpec.uri.getQueryParameter("clen")?.toLongOrNull()
        if (total == null || dataSpec.uri.host?.endsWith("googlevideo.com") != true) {
            passthrough = true
            val len = upstream.open(dataSpec)
            transferStarted(dataSpec)
            return len
        }
        passthrough = false
        position = dataSpec.position
        end = if (dataSpec.length != C.LENGTH_UNSET.toLong()) position + dataSpec.length else total
        openChunk()
        transferStarted(dataSpec)
        return end - position
    }

    private fun openChunk() {
        val base = spec!!
        val last = min(position + CHUNK, end) - 1
        val uri = base.uri.buildUpon().appendQueryParameter("range", "$position-$last").build()
        upstream.close()
        upstream.open(base.buildUpon().setUri(uri).setPosition(0).setLength(C.LENGTH_UNSET.toLong()).build())
        chunkLeft = last - position + 1
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (passthrough) return upstream.read(buffer, offset, length).also { if (it > 0) bytesTransferred(it) }
        if (position >= end) return C.RESULT_END_OF_INPUT
        if (chunkLeft <= 0) openChunk()
        val n = upstream.read(buffer, offset, min(length.toLong(), chunkLeft).toInt())
        if (n == C.RESULT_END_OF_INPUT) {
            // Сервер отдал кусок короче запрошенного — продолжаем со следующего
            chunkLeft = 0
            return if (position >= end) C.RESULT_END_OF_INPUT else read(buffer, offset, length)
        }
        position += n
        chunkLeft -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = spec?.uri

    override fun close() {
        if (spec != null) {
            spec = null
            upstream.close()
            transferEnded()
        }
    }

    class Factory(private val http: HttpDataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(http.createDataSource())
    }

    private companion object {
        /** 1 МБ: быстрый старт и перемотка, а запросов при этом немного. */
        const val CHUNK = 1L shl 20
    }
}
