package com.offplayer.app.ui

import com.offplayer.app.tr
import com.offplayer.app.trn
import com.offplayer.app.R

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offplayer.app.data.LibraryStore
import com.offplayer.app.data.MediaRepository
import com.offplayer.app.data.PlaylistStore
import com.offplayer.app.data.VideoLibraryStore
import com.offplayer.app.data.VideoMetaStore
import com.offplayer.app.download.DownloadResult
import com.offplayer.app.download.Downloader
import com.offplayer.app.playback.MusicController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Удаление файлов.
 * Свои файлы приложения (отрицательные id) удаляются напрямую после подтверждения.
 * Файлы устройства: Android 11+ — системный диалог MediaStore.createDeleteRequest,
 * Android 10 — при необходимости системный запрос доступа, Android 8–9 — прямое удаление.
 */
@Composable
fun DeleteMediaHost() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val request by UiEvents.delete.collectAsStateWithLifecycle()
    var awaiting by remember { mutableStateOf<DeleteRequest?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val req = awaiting
        awaiting = null
        if (req == null) return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) {
            // Файлы устройства оставили — но свои уже удалены, их убираем из списков
            onDeleted(context, req, req.ids.filter { it < 0 }.toSet(), complete = false)
        } else if (Build.VERSION.SDK_INT >= 30) {
            onDeleted(context, req, req.ids.toSet(), complete = true) // файлы уже удалены системой
        } else {
            // Android 10: доступ выдан — повторяем удаление
            scope.launch { deleteDevice(context, req) }
        }
    }

    fun deleteDeviceFiles(req: DeleteRequest) {
        if (Build.VERSION.SDK_INT >= 30) {
            val pi = MediaStore.createDeleteRequest(context.contentResolver, req.deviceUris)
            awaiting = req
            launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            scope.launch {
                try {
                    deleteDevice(context, req)
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) {
                        awaiting = req
                        launcher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
                    } else {
                        Toast.makeText(context, tr(R.string.no_file_access), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun start(req: DeleteRequest) {
        scope.launch {
            val own = req.ids.filter { it < 0 }.toSet()
            if (own.isNotEmpty()) LibraryStore.remove(own)
            if (req.deviceUris.isEmpty()) onDeleted(context, req, own, complete = true) else deleteDeviceFiles(req)
        }
    }

    val req = request ?: return
    // Только файлы устройства на Android 11+ — подтверждение покажет сама система
    if (Build.VERSION.SDK_INT >= 30 && req.ids.all { it > 0 }) {
        LaunchedEffect(req) {
            UiEvents.delete.value = null
            start(req)
        }
        return
    }
    val what = when {
        req.count > 1 -> if (req.isVideo) trn(R.plurals.videos, req.count) else tracksCount(req.count)
        else -> "«${req.title}»"
    }
    AlertDialog(
        onDismissRequest = { UiEvents.delete.value = null },
        title = {
            Text(
                when {
                    req.count > 1 -> tr(R.string.delete_files_q)
                    req.isVideo -> tr(R.string.delete_video_q)
                    else -> tr(R.string.delete_track_q)
                }
            )
        },
        text = {
            val fromDevice = req.deviceUris.isNotEmpty()
            Text(tr(if (fromDevice) R.string.delete_body_device else R.string.delete_body_library, what))
        },
        confirmButton = {
            TextButton(onClick = {
                UiEvents.delete.value = null
                start(req)
            }) { Text(tr(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { UiEvents.delete.value = null }) { Text(tr(R.string.action_cancel)) } },
    )
}

private suspend fun deleteDevice(context: Context, req: DeleteRequest) {
    withContext(Dispatchers.IO) {
        req.deviceUris.forEach { context.contentResolver.delete(it, null, null) }
    }
    onDeleted(context, req, req.ids.toSet(), complete = true)
}

private fun onDeleted(context: Context, req: DeleteRequest, ids: Set<Long>, complete: Boolean) {
    if (ids.isEmpty()) return
    if (!req.isVideo) {
        MusicController.removeMedia(ids.map { it.toString() }.toSet())
        PlaylistStore.purge(ids)
    } else {
        VideoLibraryStore.purge(ids)
        VideoMetaStore.remove(ids)
        com.offplayer.app.video.VideoController.removeMedia(ids)
    }
    // Карточки этих загрузок в «Загрузках» больше не нужны
    Downloader.forgetResults(
        setOf(if (req.isVideo) DownloadResult.Kind.VIDEO else DownloadResult.Kind.TRACK),
        ids.map { it.toString() }.toSet(),
    )
    if (complete) req.after?.invoke()
    MediaRepository.refreshAsync(context)
    Toast.makeText(context, if (ids.size > 1) tr(R.string.deleted_files, ids.size) else tr(R.string.deleted), Toast.LENGTH_SHORT).show()
}
