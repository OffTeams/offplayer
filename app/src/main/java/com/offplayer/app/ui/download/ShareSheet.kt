package com.offplayer.app.ui.download

import com.offplayer.app.tr
import com.offplayer.app.R

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.offplayer.app.data.AppSettings
import com.offplayer.app.download.DownloadKind
import com.offplayer.app.download.DownloadOptions
import com.offplayer.app.download.Downloader
import com.offplayer.app.ui.formatDuration
import com.offplayer.app.ui.formatSize
import com.offplayer.app.ui.theme.AppShapes
import kotlinx.coroutines.launch

/** Вариант загрузки в окне «Поделиться». */
private data class ShareOption(val kind: DownloadKind, val key: String, val label: String, val note: String? = null)

/**
 * Окно загрузки поверх YouTube / YouTube Music в стиле OFFPlayer: сведения о ролике,
 * аудио (MP3 / M4A / Opus) и видео (доступные качества) с примерным размером,
 * «Сохранить как» для аудио и весь плейлист — если ссылка на плейлист.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(url: String, onClose: () -> Unit, onOpenApp: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var info by remember { mutableStateOf<Downloader.ShareInfo?>(null) }
    var quick by remember { mutableStateOf<Downloader.QuickInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    // Название и превью приходят почти сразу (oEmbed), размеры вариантов — следом
    LaunchedEffect(url) { quick = Downloader.fetchQuickInfo(url) }
    LaunchedEffect(url) {
        info = Downloader.fetchShareInfo(context, url)
        loading = false
    }

    // По умолчанию — то, что выбрано в настройках загрузок
    val music = url.contains("music.youtube.com")
    var selected by remember {
        mutableStateOf(if (music) "a:${AppSettings.audioFormat.value}" else "v:${AppSettings.videoHeight.value}")
    }
    var asPodcast by remember { mutableStateOf(false) }
    val isPlaylist = Downloader.isPlaylistUrl(url)
    var wholePlaylist by remember { mutableStateOf(Downloader.isPlaylistPage(url) && AppSettings.wholePlaylist.value) }

    val audioOptions = listOf(
        ShareOption(DownloadKind.AUDIO, "a:mp3", "MP3", tr(R.string.fmt_mp3_note)),
        ShareOption(DownloadKind.AUDIO, "a:m4a", "M4A", tr(R.string.fmt_m4a_note)),
        ShareOption(DownloadKind.AUDIO, "a:opus", "Opus", tr(R.string.fmt_opus_note)),
    )
    // Только качества, которые реально есть у ролика
    val heights = info?.heights.orEmpty()
    val videoOptions = buildList {
        val avail = if (heights.isEmpty()) listOf(360, 720, 1080) else heights.filter { it >= 240 }.distinct()
        avail.forEach { h -> add(ShareOption(DownloadKind.VIDEO, "v:$h", if (h >= 2160) "4K" else "${h}p")) }
        add(ShareOption(DownloadKind.VIDEO, "v:0", tr(R.string.quality_best)))
    }
    var tab by remember { mutableStateOf(if (selected.startsWith("a:")) DownloadKind.AUDIO else DownloadKind.VIDEO) }
    val options = if (tab == DownloadKind.AUDIO) audioOptions else videoOptions
    // При переключении вкладки выбор остаётся в её пределах
    if (options.none { it.key == selected }) {
        selected = if (tab == DownloadKind.AUDIO) "a:${AppSettings.audioFormat.value}".takeIf { k -> audioOptions.any { it.key == k } } ?: "a:mp3"
        else videoOptions.firstOrNull { it.key == "v:${AppSettings.videoHeight.value}" }?.key ?: "v:0"
    }
    val chosen = options.firstOrNull { it.key == selected } ?: options.first()
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle(Modifier.padding(top = 8.dp, bottom = 4.dp)) },
    ) {
        // Не выше 3/5 экрана — окно не заходит под статус-бар
        Column(
            Modifier
                .heightIn(max = maxHeight)
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
        ) {
            // Шапка: превью, название, канал
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(88.dp)
                        .aspectRatio(16f / 9f)
                        .clip(AppShapes.Thumb)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    (info?.thumbnail ?: quick?.thumbnail)?.let {
                        AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                    }
                    info?.durationSec?.takeIf { it > 0 }?.let {
                        Text(
                            formatDuration(it * 1000),
                            style = MaterialTheme.typography.labelSmall,
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f), AppShapes.Badge)
                                .padding(horizontal = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        info?.title ?: quick?.title ?: if (loading) tr(R.string.dl_fetching_info)
                        else if (com.offplayer.app.download.Downloader.isTikTokUrl(url)) tr(R.string.download_from_tiktok)
                        else tr(R.string.download_from_youtube),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        info?.channel ?: quick?.channel ?: url,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (loading) {
                androidx.compose.material3.LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    gapSize = 0.dp,
                )
            } else {
                Spacer(Modifier.height(10.dp))
            }

            // Переключатель «Аудио / Видео»: варианты показываются только для выбранного
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                SegmentedButton(
                    selected = tab == DownloadKind.AUDIO,
                    onClick = { tab = DownloadKind.AUDIO },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = { Icon(Icons.Rounded.MusicNote, null, Modifier.size(18.dp)) },
                ) { Text(tr(R.string.audio)) }
                SegmentedButton(
                    selected = tab == DownloadKind.VIDEO,
                    onClick = { tab = DownloadKind.VIDEO },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = { Icon(Icons.Rounded.Movie, null, Modifier.size(18.dp)) },
                ) { Text(tr(R.string.tab_video)) }
            }
            Spacer(Modifier.height(6.dp))

            // Варианты прокручиваются внутри окна, кнопка «Скачать» всегда видна
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEach { o ->
                    val sizeKey = if (o.kind == DownloadKind.AUDIO) o.key.removePrefix("a:") else "v" + o.key.removePrefix("v:")
                    OptionRow(o, selected == o.key, info?.sizes?.get(sizeKey)) { selected = o.key }
                }
                if (tab == DownloadKind.AUDIO && !(isPlaylist && wholePlaylist)) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(tr(R.string.save_as), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SheetChip(tr(R.string.save_as_music), !asPodcast) { asPodcast = false }
                        SheetChip(tr(R.string.save_as_podcast), asPodcast) { asPodcast = true }
                    }
                }
                if (isPlaylist) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { wholePlaylist = !wholePlaylist }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (tab == DownloadKind.AUDIO) tr(R.string.whole_playlist_as_mix) else tr(R.string.whole_playlist_as_list),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = wholePlaylist, onCheckedChange = { wholePlaylist = it }, colors = com.offplayer.app.ui.appSwitchColors())
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenApp) { Text(tr(R.string.open_offplayer)) }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        val kind = chosen.kind
                        val audioFormat = if (kind == DownloadKind.AUDIO) chosen.key.removePrefix("a:") else AppSettings.audioFormat.value
                        val height = if (kind == DownloadKind.VIDEO) chosen.key.removePrefix("v:").toInt() else AppSettings.videoHeight.value
                        val whole = isPlaylist && wholePlaylist
                        Downloader.enqueue(
                            context.applicationContext, url, kind, audioFormat, height,
                            DownloadOptions(
                                playlist = whole,
                                chapters = kind == DownloadKind.VIDEO && AppSettings.saveChapters.value,
                                sponsorBlock = kind == DownloadKind.VIDEO && AppSettings.sbOnDownload.value,
                                extraAudioLangs = if (kind == DownloadKind.VIDEO) AppSettings.extraAudioLangs.value.toList() else emptyList(),
                                subLangs = if (kind == DownloadKind.VIDEO) AppSettings.subLangs.value.toList() else emptyList(),
                                autoSubs = AppSettings.autoSubs.value,
                                podcast = kind == DownloadKind.AUDIO && !whole && asPodcast,
                            ),
                        )
                        Toast.makeText(context, tr(R.string.download_added), Toast.LENGTH_SHORT).show()
                        scope.launch { sheet.hide() }.invokeOnCompletion { onClose() }
                    },
                    modifier = Modifier.height(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    ),
                ) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr(R.string.action_download), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun OptionRow(option: ShareOption, selected: Boolean, sizeBytes: Long?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(AppShapes.Card)
            .background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(option.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        option.note?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        sizeBytes?.takeIf { it > 0 }?.let {
            Text("≈ ${formatSize(it)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}
@Composable
private fun SheetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
    )
}
