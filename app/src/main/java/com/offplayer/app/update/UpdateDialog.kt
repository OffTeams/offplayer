package com.offplayer.app.update

import com.offplayer.app.tr
import com.offplayer.app.R

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offplayer.app.ui.formatSize

/**
 * Окно «Доступно обновление»: что нового, размер, «Обновить» → загрузка с прогрессом →
 * системное подтверждение установки. Показывается после проверки (автоматической или вручную).
 */
@Composable
fun UpdateDialogHost() {
    val context = LocalContext.current
    val prompt by AppUpdater.prompt.collectAsStateWithLifecycle()
    val state by AppUpdater.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { AppUpdater.checkAfterLaunch(context) }
    if (!prompt) return

    val info = when (val s = state) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.Installing -> s.info
        is UpdateState.NeedsPermission -> s.info
        is UpdateState.Error -> s.info
        else -> null
    } ?: return

    AlertDialog(
        onDismissRequest = { if (state !is UpdateState.Downloading) AppUpdater.dismissPrompt(context) },
        icon = { Icon(Icons.Rounded.SystemUpdate, null) },
        title = { Text(tr(R.string.upd_available, info.version)) },
        text = {
            Column {
                Text(
                    tr(R.string.upd_current, AppUpdater.currentVersion(context), formatSize(info.sizeBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                if (info.notes.isNotBlank()) {
                    Text(
                        info.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
                when (val s = state) {
                    is UpdateState.Downloading -> {
                        Spacer(Modifier.height(14.dp))
                        Text(tr(R.string.upd_downloading, (s.progress * 100).toInt()), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                    }
                    is UpdateState.Installing -> {
                        Spacer(Modifier.height(14.dp))
                        Text(tr(R.string.upd_confirm), style = MaterialTheme.typography.labelLarge)
                    }
                    is UpdateState.NeedsPermission -> {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            tr(R.string.upd_need_permission),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    is UpdateState.Error -> {
                        Spacer(Modifier.height(14.dp))
                        Text(s.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Downloading, is UpdateState.Installing -> Unit
                is UpdateState.NeedsPermission -> TextButton(onClick = {
                    context.startActivity(AppUpdater.permissionIntent(context))
                }) { Text(tr(R.string.action_allow)) }
                is UpdateState.Error -> TextButton(onClick = { AppUpdater.download(context, info) }) { Text(tr(R.string.action_retry)) }
                else -> TextButton(onClick = { AppUpdater.download(context, info) }) { Text(tr(R.string.action_update)) }
            }
        },
        dismissButton = {
            if (state !is UpdateState.Downloading) {
                TextButton(onClick = { AppUpdater.dismissPrompt(context) }) { Text(tr(R.string.action_later)) }
            }
        },
    )
}
