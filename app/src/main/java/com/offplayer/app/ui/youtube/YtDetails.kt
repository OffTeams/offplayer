package com.offplayer.app.ui.youtube

import androidx.compose.foundation.layout.aspectRatio

import androidx.compose.ui.focus.focusRequester

import androidx.compose.material.icons.automirrored.rounded.ArrowBack

import androidx.compose.foundation.lazy.itemsIndexed

import androidx.compose.ui.draw.drawBehind

import com.offplayer.app.tr
import com.offplayer.app.R

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withLink
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.offplayer.app.video.VideoController
import com.offplayer.app.youtube.NpSource
import com.offplayer.app.youtube.YouTubeAccount
import com.offplayer.app.youtube.YouTubeActions
import com.offplayer.app.youtube.YouTubeNav
import com.offplayer.app.youtube.YouTubeStreams
import com.offplayer.app.youtube.YtComment
import com.offplayer.app.youtube.YtRating
import com.offplayer.app.youtube.YtVideo
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfo

/** Аватар: круглое изображение, пока нет — значок человека. */
@Composable
fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(Color.Gray.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Person, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(size * 0.6f))
        if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
    }
}

/** Убирает HTML из текстов YouTube (описание, комментарии). */
private fun plain(html: String?): String = html.orEmpty()
    .replace(Regex("<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), "")
    .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">")

/** Дата публикации: «7 июл. 2026 г.» вместо сырой ISO-строки, которую иногда отдаёт YouTube. */
private fun dateText(info: StreamInfo): String? {
    val raw = info.textualUploadDate?.takeIf { it.isNotBlank() }
    if (raw != null && !Regex("""^\d{4}-\d{2}-\d{2}""").containsMatchIn(raw)) return raw
    val date = runCatching { info.uploadDate?.offsetDateTime()?.toLocalDate() }.getOrNull()
        ?: raw?.let { runCatching { java.time.OffsetDateTime.parse(it).toLocalDate() }.getOrNull() } ?: return raw
    return date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
        .withLocale(com.offplayer.app.L.ctx.resources.configuration.locales[0]))
}

/** Ссылки в тексте нажимаются и открываются, как в YouTube. */
private val URL_RE = Regex("""https?://[^\s<>"]+[^\s<>".,;:!?)\]]""")

@Composable
private fun linkified(text: String): androidx.compose.ui.text.AnnotatedString {
    val color = MaterialTheme.colorScheme.primary
    return remember(text, color) {
        androidx.compose.ui.text.buildAnnotatedString {
            var last = 0
            URL_RE.findAll(text).forEach { m ->
                append(text.substring(last, m.range.first))
                withLink(androidx.compose.ui.text.LinkAnnotation.Url(m.value, androidx.compose.ui.text.TextLinkStyles(androidx.compose.ui.text.SpanStyle(color = color)))) { append(m.value) }
                last = m.range.last + 1
            }
            append(text.substring(last))
        }
    }
}

private fun channelIdOf(info: StreamInfo?): String? = info?.uploaderUrl?.substringAfterLast("/channel/", "")?.takeIf { it.startsWith("UC") }

/** Комментарии ролика: общий список для карточки на странице и для листа. */
@Stable
class CommentsState(val youtubeId: String) {
    val items = mutableStateListOf<YtComment>()
    var total by mutableStateOf<String?>(null)
    var next by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    var started by mutableStateOf(false)

    /** Комментарий из оповещения: первая страница запрашивается по ссылке на него, затем открывается его ветка. */
    var linkParams: String? = null
    var focusId: String? = null
    /** Открыть ветку сразу (из плеера) или показать ответ прямо в списке под комментарием (страница комментариев). */
    var openFocusedThread = true
    /** Ответ, открытый по ссылке, и id его комментария — в списке он показан сразу под ним. */
    var linkedReply by mutableStateOf<YtComment?>(null)
    var linkedParent by mutableStateOf<String?>(null)
    private var pageById: Map<String, YtComment> = emptyMap()

    suspend fun more() {
        if (loading || (started && next == null)) return
        loading = true
        runCatching { YouTubeActions.comments(youtubeId, next, if (!started) linkParams else null) }.onSuccess { page ->
            items += page.items
            if (page.total != null) total = page.total
            next = page.next
            if (!started) pageById = page.byId
        }
        started = true
        loading = false
        // Ответ на комментарий: id вида «родитель.ответ» — открываем ветку родителя
        focusId?.let { f ->
            focusId = null
            val parent = f.substringBefore('.')
            val target = items.firstOrNull { it.id == parent || it.id == f }
            if (openFocusedThread) target?.let { openThread(it) }
            else if (f != parent) { linkedParent = parent; linkedReply = pageById[f] }
        }
    }

    /** Раскрытые ветки ответов: id комментария → ответы, токен продолжения ветки, идёт ли загрузка. */
    val replies = androidx.compose.runtime.mutableStateMapOf<String, List<YtComment>>()
    val repliesNext = androidx.compose.runtime.mutableStateMapOf<String, String?>()
    val repliesLoading = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()

    /** Открыть ветку (первое нажатие) или догрузить следующие ответы. */
    suspend fun loadReplies(c: YtComment) {
        if (repliesLoading[c.id] == true) return
        val token = if (c.id in replies) repliesNext[c.id] ?: return else c.repliesToken ?: return
        repliesLoading[c.id] = true
        runCatching { YouTubeActions.replies(token) }.onSuccess { page ->
            replies[c.id] = replies[c.id].orEmpty() + page.items
            repliesNext[c.id] = page.next
        }
        repliesLoading[c.id] = false
    }

    /** Открытая ветка ответов — отдельная страница «Ответы» внутри листа комментариев, как в YouTube. */
    var thread by mutableStateOf<YtComment?>(null)

    suspend fun openThread(c: YtComment) {
        thread = c
        if (c.id !in replies) loadReplies(c)
    }
}

/** Оценка и подписка: начальное состояние берётся с YouTube (если выполнен вход). */
@Stable
class RatingState(private val youtubeId: String) {
    var rating by mutableStateOf(YtRating.NONE)
    var subscribed by mutableStateOf(false)

    suspend fun load() {
        if (!YouTubeAccount.signedIn.value) return
        runCatching { YouTubeActions.state(youtubeId) }.onSuccess { (r, s) ->
            rating = r
            if (s != null) subscribed = s
        }
    }

    suspend fun rate(r: YtRating) {
        val old = rating
        rating = if (rating == r) YtRating.NONE else r
        runCatching { YouTubeActions.rate(youtubeId, rating) }.onFailure { rating = old }
    }

    suspend fun toggleSubscribe(channelId: String) {
        val on = !subscribed
        subscribed = on
        runCatching { YouTubeActions.subscribe(channelId, on) }.onFailure { subscribed = !on }
    }
}

/**
 * Страница ролика под видео — как в приложении YouTube: название, канал с подпиской, оценки,
 * «Поделиться», «Скачать» (на вкладку «Загрузки» OFFPlayer), свёрнутые комментарии и лента похожих роликов.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun WatchPage(youtubeId: String, modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(0.dp), showRelated: Boolean = true, sidePanel: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signedIn by YouTubeAccount.signedIn.collectAsStateWithLifecycle()
    var info by remember(youtubeId) { mutableStateOf(YouTubeStreams.info(youtubeId)) }
    LaunchedEffect(youtubeId) { if (info == null) info = runCatching { YouTubeStreams.loadInfo(youtubeId) }.getOrNull() }
    val comments = remember(youtubeId) { WatchSide.commentsFor(youtubeId) }
    LaunchedEffect(youtubeId) { comments.more() }
    val state = remember(youtubeId) { RatingState(youtubeId) }
    LaunchedEffect(youtubeId, signedIn) { state.load() }
    // 0 — нет, 1 — описание, 2 — комментарии. На планшете они открываются в панели справа (WatchSidePanel)
    val localSheet = remember(youtubeId) { mutableStateOf(0) }
    var sheet by (if (sidePanel) WatchSide.sheet else localSheet)
    LaunchedEffect(youtubeId) {
        WatchSide.reset(youtubeId)
        // Из оповещения об ответе — сразу комментарии с нужной веткой
        if (WatchSide.linkOpen) { WatchSide.linkOpen = false; sheet = 2 }
    }
    var opening by remember { mutableStateOf<String?>(null) }
    var saving by remember(youtubeId) { mutableStateOf(false) }
    val related = remember(info) { info?.let { NpSource.related(it) }.orEmpty() }
    val c = MaterialTheme.colorScheme
    val typo = MaterialTheme.typography

    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides c.onSurface) {
    val pageList = androidx.compose.foundation.lazy.rememberLazyListState()
    // Листают страницу ролика — значит, выбирают сами: «Следующее видео» отменяется
    LaunchedEffect(pageList) {
        snapshotFlow { pageList.isScrollInProgress }.collect { if (it) YouTubeNav.relatedScrolled.value++ }
    }
    LazyColumn(modifier.background(c.surface), state = pageList, contentPadding = contentPadding) {
        val i = info
        if (i == null) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else {
            item {
                Column(Modifier.fillMaxWidth().clickable { sheet = 1 }.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
                    Text(i.name, style = typo.titleLarge.copy(fontSize = 19.sp), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val meta = listOfNotNull(
                        "@" + (i.uploaderName ?: "").removePrefix("@").replace(" ", ""),
                        i.viewCount.takeIf { it >= 0 }?.let { NpSource.viewsText(it) },
                        dateText(i),
                    ).joinToString("  ")
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(meta, style = typo.bodyMedium, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Text("  " + tr(R.string.yt_more), style = typo.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item {
                // Ряд действий прокручивается вбок, как в YouTube
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    val channelId = channelIdOf(i)
                    Avatar(i.uploaderAvatars.maxByOrNull { it.height }?.url, 40.dp,
                        Modifier.clickable(enabled = channelId != null) { channelId?.let { YouTubeNav.openChannel(it, i.uploaderName.orEmpty()) } })
                    SubscribePill(state.subscribed) {
                        if (!signedIn || channelId == null) needSignIn(context) else scope.launch { state.toggleSubscribe(channelId) }
                    }
                    LikePill(i.likeCount, state.rating) { r -> if (!signedIn) needSignIn(context) else scope.launch { state.rate(r) } }
                    ActionPill(Icons.Outlined.Share, tr(R.string.yt_share)) { share(context, "https://www.youtube.com/watch?v=$youtubeId") }
                    ActionPill(Icons.Outlined.BookmarkBorder, tr(R.string.yt_save)) { saving = true }
                    ActionPill(Icons.Outlined.Download, tr(R.string.yt_download)) { YouTubeNav.download("https://www.youtube.com/watch?v=$youtubeId") }
                }
            }
            item {
                // Комментарии свёрнуты в карточку с первым комментарием — нажатие открывает лист
                Column(
                    Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(c.surfaceContainerHigh).clickable { sheet = 2 }.padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(R.string.yt_comments), style = typo.titleSmall, fontWeight = FontWeight.Bold)
                        comments.total?.let { Text("  $it", style = typo.bodyMedium, color = c.onSurfaceVariant) }
                    }
                    comments.items.firstOrNull()?.let { first ->
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(first.avatar, 24.dp)
                            Text(first.text, style = typo.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
            }
            // Отступ между комментариями и лентой похожих роликов
            item { androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp)) }
            // Лента похожих роликов под комментариями
            if (showRelated) items(related, key = { "rel_" + it.id }) { v ->
                VideoCard(
                    v, opening = opening == v.id,
                    onClick = {
                        if (opening == null) {
                            opening = v.id
                            scope.launch {
                                runCatching { YouTubeStreams.resolve(context, v) }.onSuccess { VideoController.playStream(it) }
                                    .onFailure { Toast.makeText(context, tr(R.string.yt_play_failed, it.message.orEmpty()), Toast.LENGTH_LONG).show() }
                                opening = null
                            }
                        }
                    },
                    onDownload = { YouTubeNav.download(v.url) },
                    onChannel = v.channelId?.let { id -> { YouTubeNav.openChannel(id, v.channel) } },
                )
            }
        }
    }

    // «Сохранить» — в онлайн-плейлисты (видны в «Музыке» и «Видео», раздел «Онлайн»)
    if (saving) info?.let { i ->
        SaveToPlaylistDialog(
            com.offplayer.app.youtube.OnlineItem(youtubeId, i.name, i.uploaderName.orEmpty(), i.duration, "https://i.ytimg.com/vi/$youtubeId/hqdefault.jpg"),
            onDismiss = { saving = false },
        )
    }
    }
    if (!sidePanel && sheet == 1) info?.let { DescriptionSheet(it, onDismiss = { sheet = 0 }) }
    if (!sidePanel && sheet == 2) CommentsSheet(comments, onDismiss = { sheet = 0 })
}

/** Что открыто в панели справа от видео на планшете: 0 — похожие ролики, 1 — описание, 2 — комментарии. */
internal object WatchSide {
    val sheet = mutableStateOf(0)
    private var id: String? = null
    private var comments: CommentsState? = null

    /** Новый ролик — панель снова показывает похожие. */
    fun reset(youtubeId: String) {
        if (id != youtubeId) { id = youtubeId; sheet.value = 0 }
    }

    /** Комментарии одного ролика общие для карточки слева и панели справа — второй раз не загружаются. */
    fun commentsFor(youtubeId: String): CommentsState {
        // Ролик открыт из оповещения об ответе — список заново, со ссылкой на комментарий
        val link = com.offplayer.app.youtube.YouTubeNav.linkedComment?.takeIf { it.videoId == youtubeId }
        if (link != null) {
            com.offplayer.app.youtube.YouTubeNav.linkedComment = null
            linkOpen = true
            return CommentsState(youtubeId).apply { linkParams = link.params; focusId = link.commentId }.also { comments = it }
        }
        return comments?.takeIf { it.youtubeId == youtubeId } ?: CommentsState(youtubeId).also { comments = it }
    }

    /** Открыть комментарии сразу (ролик из оповещения об ответе). */
    var linkOpen = false
}

/** Правая панель страницы ролика на планшете: лента похожих, а при открытом описании или комментариях — они. */
@Composable
fun WatchSidePanel(youtubeId: String, modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(0.dp)) {
    val sheet by WatchSide.sheet
    LaunchedEffect(youtubeId) { WatchSide.reset(youtubeId) }
    androidx.activity.compose.BackHandler(sheet != 0) { WatchSide.sheet.value = 0 }
    if (sheet == 0) { RelatedList(youtubeId, modifier, contentPadding); return }
    val c = MaterialTheme.colorScheme
    var info by remember(youtubeId) { mutableStateOf(YouTubeStreams.info(youtubeId)) }
    LaunchedEffect(youtubeId) { if (info == null) info = runCatching { YouTubeStreams.loadInfo(youtubeId) }.getOrNull() }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides c.onSurface) {
        Column(modifier.background(c.surface).padding(contentPadding).padding(top = 8.dp)) {
            if (sheet == 1) SheetHeader(tr(R.string.yt_description), { WatchSide.sheet.value = 0 })
            else CommentsHeader(WatchSide.commentsFor(youtubeId)) { WatchSide.sheet.value = 0 }
            if (sheet == 1) info.let { i ->
                if (i == null) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else DescriptionContent(i, Modifier.weight(1f))
            } else CommentsContent(WatchSide.commentsFor(youtubeId), Modifier.weight(1f), fill = true)
        }
    }
}

/** Колонка похожих роликов — справа от видео на планшете, как в YouTube. */
@Composable
fun RelatedList(youtubeId: String, modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(0.dp)) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember(youtubeId) { mutableStateOf(YouTubeStreams.info(youtubeId)) }
    LaunchedEffect(youtubeId) { if (info == null) info = runCatching { YouTubeStreams.loadInfo(youtubeId) }.getOrNull() }
    val related = remember(info) { info?.let { NpSource.related(it) }.orEmpty() }
    var opening by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    val sideList = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(sideList) {
        snapshotFlow { sideList.isScrollInProgress }.collect { if (it) YouTubeNav.relatedScrolled.value++ }
    }
    LazyColumn(modifier.background(MaterialTheme.colorScheme.surface), state = sideList, contentPadding = contentPadding) {
        items(related, key = { "side_" + it.id }) { v ->
            Column(Modifier.padding(horizontal = 8.dp)) {
                VideoCard(
                    v, opening = opening == v.id, rounded = true,
                    onClick = {
                        if (opening == null) {
                            opening = v.id
                            scope.launch {
                                runCatching { YouTubeStreams.resolve(context, v) }.onSuccess { VideoController.playStream(it) }
                                opening = null
                            }
                        }
                    },
                    onDownload = { YouTubeNav.download(v.url) },
                    onChannel = v.channelId?.let { id -> { YouTubeNav.openChannel(id, v.channel) } },
                )
            }
        }
    }
    }
}

fun needSignIn(context: android.content.Context) =
    Toast.makeText(context, tr(R.string.yt_need_sign_in), Toast.LENGTH_SHORT).show()

fun share(context: android.content.Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}

@Composable
private fun SubscribePill(subscribed: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Text(
        tr(if (subscribed) R.string.yt_subscribed else R.string.yt_subscribe),
        color = if (subscribed) c.onSurface else c.inverseOnSurface, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (subscribed) c.surfaceContainerHigh else c.inverseSurface)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun LikePill(likes: Long, rating: YtRating, onRate: (YtRating) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clickable { onRate(YtRating.LIKE) }.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (rating == YtRating.LIKE) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp, tr(R.string.yt_like), Modifier.size(20.dp))
            val n = likes.takeIf { it >= 0 }?.let { it + if (rating == YtRating.LIKE) 1 else 0 }
            if (n != null) Text(NpSource.viewsCompact(n), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
        }
        Box(Modifier.width(1.dp).height(20.dp).background(c.outlineVariant))
        Box(Modifier.clickable { onRate(YtRating.DISLIKE) }.padding(horizontal = 14.dp, vertical = 9.dp)) {
            Icon(if (rating == YtRating.DISLIKE) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown, tr(R.string.yt_dislike), Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ActionPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Лист «Описание»: название, цифры (лайки, просмотры, дата), канал и полный текст. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DescriptionSheet(info: StreamInfo, onDismiss: () -> Unit) {
    val typo = MaterialTheme.typography
    val c = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), sheetMaxWidth = androidx.compose.ui.unit.Dp.Unspecified, containerColor = c.surface, modifier = Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBarsIgnoringVisibility).padding(top = 8.dp)) {
        SheetHeader(tr(R.string.yt_description), onDismiss)
        DescriptionContent(info)
    }
}

/** Описание: название, цифры, канал и полный текст — в листе на телефоне и в панели справа на планшете. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DescriptionContent(info: StreamInfo, modifier: Modifier = Modifier) {
    val typo = MaterialTheme.typography
    val c = MaterialTheme.colorScheme
    run {
        Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(info.name, style = typo.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                info.likeCount.takeIf { it >= 0 }?.let { Stat(NpSource.viewsCompact(it), tr(R.string.yt_like)) }
                info.viewCount.takeIf { it >= 0 }?.let { Stat(NpSource.viewsCompact(it), tr(R.string.yt_views_label)) }
                dateText(info)?.let { Stat(it, "") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Avatar(info.uploaderAvatars.maxByOrNull { it.height }?.url, 40.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(info.uploaderName.orEmpty(), style = typo.titleSmall, fontWeight = FontWeight.SemiBold)
                    info.uploaderSubscriberCount.takeIf { it >= 0 }?.let {
                        Text(tr(R.string.yt_subscribers, NpSource.viewsCompact(it)), style = typo.bodySmall, color = c.onSurfaceVariant)
                    }
                }
            }
            Text(linkified(plain(info.description?.content)), style = typo.bodyMedium, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.surfaceContainerHigh).padding(12.dp).fillMaxWidth())
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Шапка комментариев: «Комментарии» или, в открытой ветке, «← Ответы». */
@Composable
private fun CommentsHeader(comments: CommentsState, onDismiss: () -> Unit) {
    val thread = comments.thread
    if (thread == null) SheetHeader(tr(R.string.yt_comments), onDismiss)
    else SheetHeader(tr(R.string.yt_replies_title), onDismiss, onBack = { comments.thread = null })
}

@Composable
private fun SheetHeader(title: String, onDismiss: () -> Unit, onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = if (onBack != null) 4.dp else 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr(R.string.action_back)) }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, tr(R.string.action_close)) }
    }
    HorizontalDivider(Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** Лист «Комментарии»: список с аватарами и лайками, внизу поле своего комментария. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CommentsSheet(comments: CommentsState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), sheetMaxWidth = androidx.compose.ui.unit.Dp.Unspecified, containerColor = MaterialTheme.colorScheme.surface, modifier = Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBarsIgnoringVisibility).padding(top = 8.dp)) {
        CommentsHeader(comments, onDismiss)
        CommentsContent(comments)
    }
}

/** Список комментариев и поле своего; [fill] — занять всю высоту (панель справа на планшете). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CommentsContent(comments: CommentsState, modifier: Modifier = Modifier, fill: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signedIn by YouTubeAccount.signedIn.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    LaunchedEffect(comments) { comments.more() }
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.distinctUntilChanged()
            .collect { if (it >= comments.items.size - 4) comments.more() }
    }
    run {
        Column(modifier.fillMaxWidth().imePadding()) {
            val lineColor = MaterialTheme.colorScheme.outlineVariant
            var text by remember { mutableStateOf("") }
            val inputFocus = remember { androidx.compose.ui.focus.FocusRequester() }
            // Ответ: поле получает фокус; ответ на ответ начинается с @автора, как в YouTube
            val replyTo: (YtComment?) -> Unit = { target ->
                if (!signedIn) needSignIn(context)
                else {
                    if (target != null && target.author.isNotEmpty()) text = target.author.let { if (it.startsWith("@")) "$it " else "@$it " }
                    scope.launch { kotlinx.coroutines.delay(150); runCatching { inputFocus.requestFocus() } }
                }
            }
            // Назад из ветки ответов — к списку комментариев
            androidx.activity.compose.BackHandler(comments.thread != null) { comments.thread = null }
            LazyColumn(Modifier.fillMaxWidth().then(if (fill) Modifier.weight(1f) else Modifier.weight(1f, fill = false).height(520.dp)), state = list) {
                val thread = comments.thread
                if (thread != null) {
                    // Ветка ответов, как в YouTube: комментарий сверху, ответы под ним, соединены линиями дерева
                    val replies = comments.replies[thread.id].orEmpty()
                    val more = comments.repliesNext[thread.id] != null
                    val busy = comments.repliesLoading[thread.id] == true
                    item { Box(Modifier.treeTrunk(lineColor, replies.isNotEmpty() || busy)) { CommentRow(thread, onReply = { replyTo(null) }) } }
                    itemsIndexed(replies) { i, r ->
                        val last = i == replies.lastIndex && !more && !busy
                        Box(Modifier.treeBranch(lineColor, last)) { Box(Modifier.padding(start = 44.dp)) { CommentRow(r, avatarSize = 24.dp, onReply = { replyTo(r) }) } }
                    }
                    if (busy) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
                    else if (more) item {
                        Text(
                            tr(R.string.yt_more_replies), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            modifier = Modifier.padding(start = 56.dp, top = 4.dp, bottom = 12.dp).clip(RoundedCornerShape(12.dp))
                                .clickable { scope.launch { comments.loadReplies(thread) } }.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                } else items(comments.items) { c ->
                    CommentRow(c, onReply = { scope.launch { comments.openThread(c) }; replyTo(null) })
                    // Ответ из оповещения — сразу под своим комментарием, как в YouTube
                    val linked = comments.linkedReply
                    if (linked != null && comments.linkedParent == c.id) {
                        Text(tr(R.string.yt_linked_reply), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 60.dp, top = 2.dp))
                        Box(Modifier.treeBranch(lineColor, true)) { Box(Modifier.padding(start = 44.dp)) { CommentRow(linked, avatarSize = 24.dp, onReply = { scope.launch { comments.openThread(c) }; replyTo(linked) }) } }
                    }
                    // «Ответы: N» открывает ветку отдельной страницей
                    if (c.replies.isNotEmpty() && c.repliesToken != null) Text(
                        tr(R.string.yt_replies_s, c.replies),
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        modifier = Modifier.padding(start = 56.dp, bottom = 8.dp).clip(RoundedCornerShape(12.dp))
                            .clickable { scope.launch { comments.openThread(c) } }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
                if (comments.thread == null && comments.loading) item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                }
            }
            var sending by remember { mutableStateOf(false) }
            val send = {
                val t = text.trim()
                if (!signedIn) needSignIn(context)
                else if (t.isNotEmpty() && !sending) {
                    sending = true
                    scope.launch {
                        val thread = comments.thread
                        val params = thread?.replyParams
                        runCatching {
                            if (thread != null) YouTubeActions.postReply(params ?: error("Replies are turned off"), t)
                            else YouTubeActions.postComment(comments.youtubeId, t)
                        }
                            .onSuccess {
                                val mine = YtComment(tr(R.string.yt_you), YouTubeAccount.avatar.value, t, "", "", "")
                                if (thread != null) comments.replies[thread.id] = comments.replies[thread.id].orEmpty() + mine
                                else comments.items.add(0, mine)
                                text = ""
                            }
                            .onFailure { Toast.makeText(context, tr(R.string.yt_comment_failed, it.message.orEmpty()), Toast.LENGTH_LONG).show() }
                        sending = false
                    }
                }
            }
            TextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text(tr(if (comments.thread != null) R.string.yt_your_reply else R.string.yt_add_comment)) },
                trailingIcon = {
                    if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else if (text.isNotBlank()) IconButton(onClick = send) { Icon(Icons.AutoMirrored.Rounded.Send, tr(R.string.yt_send)) }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().padding(12.dp).focusRequester(inputFocus),
            )
        }
    }
}

@Composable
private fun CommentRow(c: YtComment, avatarSize: androidx.compose.ui.unit.Dp = 32.dp, onReply: (() -> Unit)? = null) {
    val typo = MaterialTheme.typography
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Avatar(c.avatar, avatarSize)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(listOf(c.author, c.date).filter { it.isNotEmpty() }.joinToString(" • "), color = muted, style = typo.labelMedium, maxLines = 1)
            Text(linkified(plain(c.text)), style = typo.bodyMedium, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ThumbUp, null, tint = muted, modifier = Modifier.size(16.dp))
                    if (c.likes.isNotEmpty()) Text(c.likes, color = muted, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                }
                Icon(Icons.Outlined.ThumbDown, null, tint = muted, modifier = Modifier.size(16.dp))
                // Ответить: открывает ветку и поле ответа
                Icon(Icons.AutoMirrored.Outlined.Comment, tr(R.string.yt_reply), tint = muted,
                    modifier = Modifier.size(16.dp).then(if (onReply != null) Modifier.clickable(onClick = onReply) else Modifier))
            }
        }
    }
}

/** Ствол дерева ответов: линия от аватара комментария вниз (x — центр аватара 32 dp при отступе 16 dp). */
private fun Modifier.treeTrunk(color: Color, show: Boolean) = drawBehind {
    if (!show) return@drawBehind
    val x = 32.dp.toPx()
    drawLine(color, androidx.compose.ui.geometry.Offset(x, 10.dp.toPx() + 36.dp.toPx()), androidx.compose.ui.geometry.Offset(x, size.height), 1.5.dp.toPx())
}

/** Ветвь к ответу: линия ствола и изгиб к аватару ответа; у последнего ответа ствол заканчивается изгибом. */
private fun Modifier.treeBranch(color: Color, last: Boolean) = drawBehind {
    val x = 32.dp.toPx()
    val avatarY = 10.dp.toPx() + 12.dp.toPx()
    val avatarX = 60.dp.toPx() - 2.dp.toPx()
    val r = 12.dp.toPx()
    val stroke = 1.5.dp.toPx()
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(x, 0f)
        lineTo(x, avatarY - r)
        quadraticTo(x, avatarY, x + r, avatarY)
        lineTo(avatarX, avatarY)
    }
    drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
    if (!last) drawLine(color, androidx.compose.ui.geometry.Offset(x, avatarY - r), androidx.compose.ui.geometry.Offset(x, size.height), stroke)
}

/** Комментарии отдельного ролика (для Shorts): своё состояние списка. */
@Composable
fun CommentsSheetFor(youtubeId: String, onDismiss: () -> Unit) {
    val state = remember(youtubeId) { CommentsState(youtubeId) }
    CommentsSheet(state, onDismiss)
}

/** Описание или комментарии ролика панелью сбоку — для Shorts на планшете в альбомной ориентации. */
@Composable
fun SidePanelFor(youtubeId: String, comments: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    var info by remember(youtubeId) { mutableStateOf(YouTubeStreams.info(youtubeId)) }
    LaunchedEffect(youtubeId) { if (info == null) info = runCatching { YouTubeStreams.loadInfo(youtubeId) }.getOrNull() }
    val state = remember(youtubeId) { CommentsState(youtubeId) }
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides c.onSurface) {
        Column(modifier.background(c.surface).padding(top = 8.dp)) {
            if (comments) CommentsHeader(state, onDismiss) else SheetHeader(tr(R.string.yt_description), onDismiss)
            if (comments) CommentsContent(state, Modifier.weight(1f), fill = true)
            else info.let { i ->
                if (i == null) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else DescriptionContent(i, Modifier.weight(1f))
            }
        }
    }
}

/** Описание отдельного ролика (для Shorts): сведения загружаются при необходимости. */
@Composable
fun DescriptionSheetFor(youtubeId: String, onDismiss: () -> Unit) {
    var info by remember(youtubeId) { mutableStateOf(YouTubeStreams.info(youtubeId)) }
    LaunchedEffect(youtubeId) { if (info == null) info = runCatching { YouTubeStreams.loadInfo(youtubeId) }.getOrNull() }
    info?.let { DescriptionSheet(it, onDismiss) }
}

/**
 * Страница комментариев ролика из оповещения об ответе — как в YouTube: ролик не запускается,
 * сверху — «Комментарии к видео …» с превью (нажатие открывает ролик), ниже — список с ответом по ссылке.
 */
@Composable
fun CommentsPage(videoId: String, commentId: String, params: String?, onBack: () -> Unit, onOpenVideo: (title: String) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val comments = remember(videoId, commentId) {
        CommentsState(videoId).apply { linkParams = params; focusId = commentId; openFocusedThread = false }
    }
    var title by remember(videoId) { mutableStateOf(YouTubeStreams.info(videoId)?.name) }
    LaunchedEffect(videoId) { if (title == null) title = runCatching { YouTubeStreams.loadInfo(videoId).name }.getOrNull() }
    androidx.activity.compose.BackHandler(comments.thread == null, onBack)
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides c.onSurface) {
        Column(modifier.background(c.surface)) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (comments.thread != null) comments.thread = null else onBack() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr(R.string.action_back))
                }
                Text(tr(if (comments.thread != null) R.string.yt_replies_title else R.string.yt_comments),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
            // «Комментарии к видео …» — переход к ролику без автоматического запуска
            Row(
                Modifier.fillMaxWidth().background(c.surfaceContainer).clickable { onOpenVideo(title.orEmpty()) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (title != null) tr(R.string.yt_comments_for, title!!) else tr(R.string.yt_comments),
                    style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                coil.compose.AsyncImage("https://i.ytimg.com/vi/$videoId/mqdefault.jpg", null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.padding(start = 12.dp).width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)))
            }
            CommentsContent(comments, Modifier.weight(1f), fill = true)
        }
    }
}
