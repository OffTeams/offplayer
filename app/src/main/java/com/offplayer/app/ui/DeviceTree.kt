package com.offplayer.app.ui

import com.offplayer.app.tr
import com.offplayer.app.R

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.offplayer.app.data.MediaRepository
import com.offplayer.app.model.DeviceFolder
import com.offplayer.app.ui.theme.AppShapes

/** Открывает папку в файловом менеджере устройства (системные «Файлы» или сторонний менеджер). */
fun openFolder(context: Context, folder: DeviceFolder) {
    val docUri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", folder.documentId)
    @Suppress("DEPRECATION")
    val path = "${Environment.getExternalStorageDirectory().absolutePath}/${folder.path}"
    val attempts = listOf(
        Intent(Intent.ACTION_VIEW).setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR),
        Intent("android.provider.action.BROWSE").setData(docUri),
        Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(path), "resource/folder"),
    )
    for (intent in attempts) {
        try {
            context.startActivity(
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
    Toast.makeText(context, tr(R.string.no_file_manager, folder.path), Toast.LENGTH_LONG).show()
}

/**
 * Заголовок папки в разделе «На устройстве»: значок, имя и путь. Нажатие открывает папку
 * в файловом менеджере, стрелка справа сворачивает/разворачивает её содержимое.
 */
@Composable
fun DeviceFolderHeader(folder: DeviceFolder, count: String, expanded: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val arrow by animateFloatAsState(if (expanded) 0f else -90f, label = "arrow")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(AppShapes.Card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggle) {
            Icon(Icons.Rounded.KeyboardArrowDown, if (expanded) tr(R.string.action_collapse) else tr(R.string.action_expand), Modifier.rotate(arrow))
        }
        Row(
            Modifier
                .weight(1f)
                .clip(AppShapes.Thumb)
                .clickable { openFolder(context, folder) }
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    folder.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "${folder.path.ifEmpty { "/" }} • $count",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.OpenInNew, tr(R.string.open_in_file_manager),
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp).size(18.dp),
            )
        }
    }
}

/** Доступ к файлам устройства отозван — предложить выдать снова. */
@Composable
fun DevicePermissionPrompt() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        MediaRepository.refreshAsync(context)
    }
    Box(Modifier.fillMaxSize()) {
        EmptyState(
            Icons.Rounded.FolderOff, tr(R.string.no_device_access),
            tr(R.string.no_device_access_desc),
            action = { Button(onClick = { launcher.launch(MediaRepository.mediaPermissions()) }) { Text(tr(R.string.action_allow)) } },
        )
    }
}
