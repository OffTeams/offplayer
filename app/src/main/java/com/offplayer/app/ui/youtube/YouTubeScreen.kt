package com.offplayer.app.ui.youtube

import com.offplayer.app.tr
import com.offplayer.app.R

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.offplayer.app.ui.theme.AppShapes
import com.offplayer.app.video.VideoController
import com.offplayer.app.youtube.YouTubeAccount
import com.offplayer.app.youtube.YouTubeApi
import com.offplayer.app.youtube.YouTubeStreams
import com.offplayer.app.youtube.YtFeed
import com.offplayer.app.youtube.YtVideo
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Состояние одного списка: держится, пока открыт стек экранов, — возврат назад не перезагружает список. */
private class FeedState {
    val items = mutableStateListOf<YtVideo>()
    /** Shorts из этой ленты — строкой над роликами (подписки, история). */
    val shorts = mutableStateListOf<YtVideo>()
    var continuation by mutableStateOf<Any?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var loaded by mutableStateOf(false)
    /** Идёт обновление свайпом вниз (а не подгрузка следующей страницы). */
    var refreshing by mutableStateOf(false)
    val list = androidx.compose.foundation.lazy.grid.LazyGridState()
}

/**
 * Вкладка «YouTube» в интерфейсе OFFPlayer: списки берутся с YouTube, ролики играют в нашем
 * видеоплеере (без рекламы, с SponsorBlock и эпизодами), «Скачать» ведёт на вкладку «Загрузки».
 * Внутри — две страницы, как псевдовкладки: лента и Shorts. Свайп между ними работает так же,
 * как между вкладками приложения, а с крайней страницы уходит на соседнюю вкладку.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun YouTubeScreen(active: Boolean, onDownload: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val signedIn by YouTubeAccount.signedIn.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { YouTubeAccount.refresh() }

    // Страница 0 — лента (главная, подписки, темы, поиск, каналы), 1 — Shorts, 2 — история просмотра
    val pages = androidx.compose.foundation.pager.rememberPagerState { 3 }
    val onShorts = pages.currentPage == 1
    LaunchedEffect(pages.isScrollInProgress) {
        if (!pages.isScrollInProgress && kotlin.math.abs(pages.currentPageOffsetFraction) > 0.001f) pages.animateScrollToPage(pages.currentPage)
    }
    // Стек ленты: корень — «Главная», «Подписки» или тема, сверху — поиск и каналы
    var stack by remember { mutableStateOf(listOf<YtFeed>(YtFeed.Home)) }
    val states = remember { HashMap<YtFeed, FeedState>() }
    val feed = stack.last()
    val state = states.getOrPut(feed) { FeedState() }
    // Темы главной (чипы) — обновляются вместе с главной
    val chips = remember { mutableStateListOf<Pair<String, String>>() }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    // Уведомления открываются на всю вкладку между верхней строкой и нижним меню
    var notifOpen by remember { mutableStateOf(false) }
    var login by remember { mutableStateOf(false) }
    var addAccount by remember { mutableStateOf(false) }
    val accountVersion by YouTubeAccount.version.collectAsStateWithLifecycle()
    var opening by remember { mutableStateOf<String?>(null) }

    fun loadMore(reset: Boolean = false, target: YtFeed = feed) {
        val s = states.getOrPut(target) { FeedState() }
        if (s.loading || (!reset && s.loaded && s.continuation == null)) return
        s.loading = true
        s.error = null
        scope.launch {
            runCatching {
                // Обновление свайпом: первая страница YouTube почти не меняется, поэтому берём следующую —
                // это новые ролики; ими заменяется весь старый список
                val next = s.continuation
                if (reset && s.refreshing && next != null) {
                    runCatching { YouTubeApi.load(target, next) }.getOrNull()?.takeIf { it.items.isNotEmpty() }
                        ?.let { return@runCatching it }
                }
                YouTubeApi.load(target, if (reset) null else s.continuation)
            }
                .onSuccess { page ->
                    if (reset) { s.items.clear(); s.shorts.clear() }
                    // Shorts живут на своей странице — в списках роликов их нет; в подписках и истории — строкой сверху
                    page.items.forEach { v ->
                        val into = if (v.isShort) s.shorts else s.items
                        if (into.none { it.id == v.id }) into += v
                    }
                    s.continuation = page.continuation?.takeIf { page.items.isNotEmpty() }
                    s.loaded = true
                    if (target == YtFeed.Home && page.chips.isNotEmpty()) {
                        chips.clear()
                        chips += page.chips
                    }
                    // Новая лента начинается с начала, а не с места, где остановились в старой
                    if (reset) s.list.scrollToItem(0)
                }
                .onFailure { s.error = it.message ?: it.javaClass.simpleName }
            s.loading = false
            s.refreshing = false
        }
    }

    // Вход или выход меняют ленты — их нужно загрузить заново
    LaunchedEffect(signedIn, accountVersion) {
        // Состояния обнуляются на месте, а не заменяются: экран держит ссылки на них, и новая
        // загрузка должна попасть в тот же объект, иначе вкладка останется пустой (так было при запуске)
        states.values.forEach { s ->
            if (!s.loading) {
                s.items.clear()
                s.shorts.clear()
                s.loaded = false
                s.continuation = null
                s.error = null
            }
        }
        chips.clear()
        stack = listOf(YtFeed.Home)
    }
    LaunchedEffect(feed, signedIn, accountVersion) {
        val s = states.getOrPut(feed) { FeedState() }
        val needsAccount = (feed == YtFeed.Home || feed == YtFeed.Subscriptions || feed is YtFeed.Chip) && !signedIn
        if (!s.loaded && !needsAccount) loadMore(reset = true)
    }
    // Бесконечная прокрутка: за 5 роликов до конца подгружается следующая страница
    LaunchedEffect(state) {
        snapshotFlow { state.list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (last >= state.items.size - 5 && state.items.isNotEmpty()) loadMore() }
    }

    BackHandler(active && (onShorts || pages.currentPage == 2)) { scope.launch { pages.animateScrollToPage(0) } }
    // Нажатие на открытую вкладку YouTube в нижнем меню: закрыть всё поверх и вернуть «Главную» к началу
    val homeTop by com.offplayer.app.youtube.YouTubeNav.homeTop.collectAsStateWithLifecycle()
    LaunchedEffect(homeTop) {
        if (homeTop == 0) return@LaunchedEffect
        notifOpen = false
        searchOpen = false
        stack = listOf(YtFeed.Home)
        pages.animateScrollToPage(0)
        states[YtFeed.Home]?.list?.animateScrollToItem(0)
    }
    val shortsTab by com.offplayer.app.youtube.YouTubeNav.openShortsTab.collectAsStateWithLifecycle()
    LaunchedEffect(shortsTab) {
        if (shortsTab == 0) return@LaunchedEffect
        notifOpen = false
        pages.animateScrollToPage(1)
    }
    // Shorts из строки (подписки, история) открывается в ленте Shorts
    val openShort: (YtVideo) -> Unit = { v ->
        com.offplayer.app.youtube.YouTubeNav.shortRequest.value = v.id
        scope.launch { pages.animateScrollToPage(1) }
    }
    // Каналы из подписок — строка аватаров над лентой «Подписки»
    val subChannels = remember { mutableStateListOf<com.offplayer.app.youtube.YtChannel>() }
    LaunchedEffect(signedIn, accountVersion, feed == YtFeed.Subscriptions) {
        if (signedIn && feed == YtFeed.Subscriptions) runCatching { YouTubeApi.subscribedChannels() }.onSuccess {
            subChannels.clear(); subChannels += it
        }
    }
    // История открывается своей страницей справа от Shorts — загружается, когда до неё долистали
    // История перезагружается при каждом открытии и после закрытия плеера — в ней сразу видно только что просмотренное
    val playerOpen by VideoController.open.collectAsStateWithLifecycle()
    LaunchedEffect(pages.currentPage == 2, signedIn, accountVersion, playerOpen) {
        if (playerOpen) return@LaunchedEffect
        // Отметка о просмотре доходит до YouTube не мгновенно
        kotlinx.coroutines.delay(1500)
        if (pages.currentPage == 2 && signedIn) {
            val s = states.getOrPut(YtFeed.History) { FeedState() }
            if (!s.loading) { s.refreshing = false; loadMore(reset = true, target = YtFeed.History) }
        }
    }
    BackHandler(active && !onShorts && searchOpen) { searchOpen = false }
    BackHandler(active && !onShorts && !searchOpen && stack.size > 1) { stack = stack.dropLast(1) }
    BackHandler(active && !onShorts && notifOpen && !searchOpen) { notifOpen = false }
    // Канал, открытый из плеера или из Shorts
    val channelRequest by com.offplayer.app.youtube.YouTubeNav.channelRequest.collectAsStateWithLifecycle()
    LaunchedEffect(channelRequest) {
        val (id, name) = channelRequest ?: return@LaunchedEffect
        com.offplayer.app.youtube.YouTubeNav.channelRequest.value = null
        notifOpen = false
        stack = stack + YtFeed.Channel(id, name)
        pages.scrollToPage(0)
    }
    // Shorts на экране — нижнее меню и строка состояния становятся чёрными
    LaunchedEffect(onShorts, active) { com.offplayer.app.youtube.YouTubeNav.shortsOpen.value = active && onShorts }
    // Приложение открыли (или вернулись в него) — лента обновляется сама, как в YouTube, чтобы вкладка не была пустой
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_START) {
        YouTubeAccount.refresh()
        val s = states[feed]
        val root = feed == YtFeed.Home || feed == YtFeed.Subscriptions || feed is YtFeed.Chip
        if (root && (s == null || !s.loaded || s.items.isEmpty())) loadMore(reset = true)
        else if (root && s != null && !s.loading) { s.refreshing = true; loadMore(reset = true) }
    }

    val play: (YtVideo) -> Unit = { v ->
        if (opening == null) {
            opening = v.id
            scope.launch {
                runCatching { YouTubeStreams.resolve(context, v) }
                    .onSuccess { VideoController.playStream(it) }
                    .onFailure { Toast.makeText(context, tr(R.string.yt_play_failed, it.message?.lineSequence()?.lastOrNull { l -> l.isNotBlank() } ?: ""), Toast.LENGTH_LONG).show() }
                opening = null
            }
        }
    }
    val search = {
        val q = query.trim()
        if (q.isNotEmpty()) {
            focus.clearFocus()
            searchOpen = false
            notifOpen = false
            val target = YtFeed.Search(q)
            states.remove(target)
            stack = stack.filterNot { it is YtFeed.Search } + target
        }
    }

    androidx.compose.foundation.pager.HorizontalPager(
        state = pages,
        beyondViewportPageCount = 1,
        // Из канала или поиска на Shorts не листаем — сначала «назад»
        userScrollEnabled = stack.size == 1 && !notifOpen,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        if (page == 2) {
            HistoryPage(
                state = states.getOrPut(YtFeed.History) { FeedState() },
                signedIn = signedIn,
                opening = opening,
                onSignIn = { login = true },
                onBack = { scope.launch { pages.animateScrollToPage(0) } },
                onMore = { loadMore(target = YtFeed.History) },
                onPlay = play,
                onShort = openShort,
                onDownload = onDownload,
                onChannel = { id, name -> stack = stack + YtFeed.Channel(id, name); scope.launch { pages.animateScrollToPage(0) } },
            )
            return@HorizontalPager
        }
        if (page == 1) {
            // Shorts: всё чёрное, включая полосу под строкой состояния
            Box(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
                ShortsFeed(
                    active = active && onShorts,
                    onDownload = onDownload,
                    onChannel = { id, name -> stack = stack + YtFeed.Channel(id, name); scope.launch { pages.animateScrollToPage(0) } },
                    onBack = { scope.launch { pages.animateScrollToPage(0) } },
                )
            }
            return@HorizontalPager
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Широкий экран (планшет или альбомная ориентация): сетка карточек и поиск значком, как в YouTube
            val wide = maxWidth >= 600.dp
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                // Верхняя строка как в YouTube: значок и «OFFTube» слева; поиск, колокольчик и аккаунт справа.
                // Поиск открывается значком — поле занимает строку целиком
                Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (stack.size > 1 || searchOpen) {
                        IconButton(onClick = { if (searchOpen) searchOpen = false else stack = stack.dropLast(1) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr(R.string.action_back))
                        }
                    } else {
                        Icon(Icons.Rounded.SmartDisplay, null, Modifier.padding(start = 8.dp, end = 4.dp).size(30.dp), tint = Color(0xFFFF0033))
                        Text("OFFTube", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                    }
                    if (searchOpen) {
                        SearchField(query, onChange = { query = it }, onSearch = search, autoFocus = true, modifier = Modifier.weight(1f).height(38.dp))
                    } else {
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { searchOpen = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Search, tr(R.string.yt_search_hint), Modifier.size(24.dp)) }
                        NotificationsButton(signedIn, selected = notifOpen, onSignIn = { login = true }, onOpen = { notifOpen = !notifOpen })
                    }
                    AccountButton(signedIn, onSignIn = { login = true }, onAddAccount = { addAccount = true })
                }

                if (notifOpen) {
                    NotificationsPage(Modifier.weight(1f).fillMaxWidth(), onShort = { id ->
                        // Shorts открывается в своей ленте, а не в обычном плеере
                        notifOpen = false
                        com.offplayer.app.youtube.YouTubeNav.shortRequest.value = id
                        scope.launch { pages.animateScrollToPage(1) }
                    }) { id, title, thumb ->
                        play(YtVideo(id, title, "", null, null, "", thumb ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"))
                    }
                } else {
                when (feed) {
                    YtFeed.Home, YtFeed.Subscriptions, is YtFeed.Chip -> Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(top = 2.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Без запаса под касание сверху и снизу — теги ближе к верхней строке
                        @Composable
                        fun chip(selected: Boolean, label: String, onClick: () -> Unit) = androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp) { FilterChip(
                            selected = selected, onClick = onClick, label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                                selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface,
                            ),
                        ) }
                        chip(feed == YtFeed.Home, tr(R.string.yt_home)) {
                            // «Главная» всегда открывается с начала ленты, повторное нажатие — тоже наверх
                            stack = listOf(YtFeed.Home)
                            scope.launch { states[YtFeed.Home]?.list?.animateScrollToItem(0) }
                        }
                        chip(false, tr(R.string.yt_shorts)) { scope.launch { pages.animateScrollToPage(1) } }
                        // История — своя страница, открывается так же, как Shorts
                        chip(false, tr(R.string.yt_history)) { scope.launch { pages.animateScrollToPage(2) } }
                        chip(feed == YtFeed.Subscriptions, tr(R.string.yt_subscriptions)) { stack = listOf(YtFeed.Subscriptions) }
                        // Темы с YouTube — сразу за «Подписками», как в оригинальном приложении
                        chips.forEach { (title, token) ->
                            chip((feed as? YtFeed.Chip)?.title == title, title) {
                                val target = YtFeed.Chip(title, token)
                                states.remove(target)
                                stack = listOf(target)
                            }
                        }
                    }
                    is YtFeed.Search -> Text(
                        tr(R.string.yt_results_for, feed.query), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    is YtFeed.Channel -> Text(
                        feed.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    YtFeed.Shorts, YtFeed.History -> Unit
                }

                val needsAccount = (feed == YtFeed.Home || feed == YtFeed.Subscriptions || feed is YtFeed.Chip) && !signedIn
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        needsAccount -> SignInPrompt(onSignIn = { login = true })
                        state.items.isEmpty() && state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        state.items.isEmpty() && state.error != null -> Message(tr(R.string.yt_load_failed, state.error.orEmpty()), tr(R.string.action_retry)) { loadMore(reset = true) }
                        state.items.isEmpty() && state.loaded -> Message(tr(R.string.yt_empty), null) {}
                        // Потянуть ленту вниз — обновить, как в YouTube
                        else -> androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                            isRefreshing = state.loading && state.items.isNotEmpty() && state.refreshing,
                            onRefresh = { state.refreshing = true; loadMore(reset = true) },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // На телефоне — одна колонка, на широком экране — сетка, как в YouTube для планшетов
                            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(if (wide) 300.dp else 600.dp),
                                state = state.list,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = if (wide) 12.dp else 0.dp, end = if (wide) 12.dp else 0.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(if (wide) 12.dp else 0.dp),
                            ) {
                                // Подписки: сверху каналы, затем Shorts с них одной строкой, и только потом ролики
                                if (feed == YtFeed.Subscriptions) {
                                    if (subChannels.isNotEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                        ChannelsRow(subChannels) { ch -> stack = stack + YtFeed.Channel(ch.id, ch.name) }
                                    }
                                    if (state.shorts.isNotEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                        ShortsRow(state.shorts, openShort)
                                    }
                                }
                                items(state.items.size, key = { state.items[it].id }) { i ->
                                    val v = state.items[i]
                                    VideoCard(
                                        v, opening = opening == v.id,
                                        onClick = { play(v) },
                                        onDownload = { onDownload(v.url) },
                                        onChannel = v.channelId?.let { id -> { stack = stack + YtFeed.Channel(id, v.channel) } },
                                        rounded = wide,
                                    )
                                }
                                if (state.loading && !state.refreshing) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }

    if (login) SignInDialog(onDone = {
        login = false
        YouTubeAccount.refresh()
    })
    if (addAccount) SignInDialog(url = ADD_ACCOUNT_URL, onDone = {
        addAccount = false
        YouTubeAccount.refresh()
        scope.launch { YouTubeAccount.loadAccounts() }
    })
}

/** Строка поиска высотой 42 dp; на широком экране открывается значком и сразу получает фокус. */
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, onSearch: () -> Unit, autoFocus: Boolean, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }
    androidx.compose.foundation.text.BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.primary),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxSize().clip(RoundedCornerShape(21.dp)).background(c.surfaceContainerHigh).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = c.onSurfaceVariant)
                Box(Modifier.weight(1f).padding(start = 8.dp)) {
                    if (query.isEmpty()) Text(tr(R.string.yt_search_hint), color = c.onSurfaceVariant, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                    inner()
                }
                if (query.isNotEmpty()) IconButton(onClick = { onChange("") }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.Close, tr(R.string.action_clear_all), Modifier.size(18.dp))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = modifier.focusRequester(focus),
    )
}

/** Колокольчик со значком непросмотренных; открывает и закрывает страницу уведомлений. */
@Composable
private fun NotificationsButton(signedIn: Boolean, selected: Boolean, onSignIn: () -> Unit, onOpen: () -> Unit) {
    val scope = rememberCoroutineScope()
    val unseen by com.offplayer.app.youtube.YtNotifications.unseen.collectAsStateWithLifecycle()
    LaunchedEffect(signedIn) { com.offplayer.app.youtube.YtNotifications.refreshUnseen() }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_START) {
        scope.launch { com.offplayer.app.youtube.YtNotifications.refreshUnseen() }
    }
    IconButton(onClick = { if (!signedIn) onSignIn() else onOpen() }, modifier = Modifier.size(40.dp)) {
        androidx.compose.material3.BadgedBox(badge = {
            if (unseen > 0 && !selected) androidx.compose.material3.Badge { Text(if (unseen > 9) "9+" else unseen.toString()) }
        }) {
            Icon(if (selected) Icons.Rounded.Notifications else Icons.Rounded.NotificationsNone, tr(R.string.yt_notifications), Modifier.size(24.dp))
        }
    }
}

/** Уведомления YouTube на всю вкладку, как в приложении YouTube: аватар, текст, время и превью ролика. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsPage(modifier: Modifier, onShort: (String) -> Unit, onOpen: (id: String, title: String, thumbnail: String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<com.offplayer.app.youtube.YtNotification>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    fun load() {
        error = null
        scope.launch {
            runCatching { com.offplayer.app.youtube.YtNotifications.list() }
                .onSuccess { items = it }
                .onFailure { error = it.message ?: it.javaClass.simpleName }
            refreshing = false
        }
    }
    LaunchedEffect(Unit) { load() }
    val c = MaterialTheme.colorScheme
    Box(modifier) {
        val list = items
        when {
            error != null && list == null -> Message(tr(R.string.yt_load_failed, error.orEmpty()), tr(R.string.action_retry)) { load() }
            list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            list.isEmpty() -> Message(tr(R.string.yt_no_notifications), null) {}
            else -> androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = refreshing, onRefresh = { refreshing = true; load() }, modifier = Modifier.fillMaxSize(),
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val thumbWidth = (maxWidth * 0.28f).coerceIn(112.dp, 200.dp)
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                        item {
                            Text(tr(R.string.yt_notifications), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                        }
                        // Ролики и Shorts — отдельными разделами (заголовки — только если есть и то и другое)
                        val (shorts, videos) = list.partition { it.isShort }
                        val both = shorts.isNotEmpty() && videos.isNotEmpty()
                        val rows: List<Any> = (if (both) listOf<Any>(tr(R.string.yt_videos)) else emptyList()) + videos +
                            (if (both) listOf<Any>(tr(R.string.yt_shorts)) else emptyList()) + shorts
                        items(rows) { e ->
                            if (e is String) Text(e, style = MaterialTheme.typography.titleSmall, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp))
                            else (e as com.offplayer.app.youtube.YtNotification).let { n ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable(enabled = n.videoId != null || n.channelId != null) {
                                        // Ролик — в плеер; оповещение без ролика открывает канал автора
                                        val vid = n.videoId
                                        if (vid != null && n.isShort) onShort(vid)
                                        else if (vid != null) onOpen(vid, n.text, n.thumbnail)
                                        else n.channelId?.let { com.offplayer.app.youtube.YouTubeNav.openChannel(it, "") }
                                    }
                                    .padding(start = 6.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                // Непрочитанное — точка слева, как в YouTube
                                Box(Modifier.padding(top = 22.dp, end = 6.dp).size(5.dp).clip(RoundedCornerShape(3.dp)).background(if (n.read) Color.Transparent else c.primary))
                                Avatar(n.avatar, 48.dp)
                                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                    Text(n.text, style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                    if (n.time.isNotBlank()) Text(n.time, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                }
                                n.thumbnail?.let {
                                    // Превью Shorts вертикальное, как в YouTube
                                    AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = (if (n.isShort) Modifier.width(thumbWidth * 0.4f).aspectRatio(9f / 16f) else Modifier.width(thumbWidth).aspectRatio(16f / 9f)).clip(RoundedCornerShape(10.dp)))
                                }
                            }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountButton(signedIn: Boolean, onSignIn: () -> Unit, onAddAccount: () -> Unit = onSignIn) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val accounts by YouTubeAccount.accounts.collectAsStateWithLifecycle()
    val current by YouTubeAccount.authUser.collectAsStateWithLifecycle()
    LaunchedEffect(signedIn) { YouTubeAccount.loadAccounts() }
    Box {
        IconButton(onClick = { if (signedIn) menu = true else onSignIn() }, modifier = Modifier.padding(end = 2.dp).size(40.dp)) {
            // После входа — фотография профиля, как в YouTube
            val avatar by YouTubeAccount.avatar.collectAsStateWithLifecycle()
            LaunchedEffect(signedIn) { YouTubeAccount.loadAvatar() }
            if (signedIn && avatar != null) Avatar(avatar, 28.dp)
            else Icon(
                if (signedIn) Icons.Rounded.AccountCircle else Icons.Rounded.Person,
                tr(if (signedIn) R.string.yt_account else R.string.yt_sign_in),
                Modifier.size(28.dp),
                tint = if (signedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            // Аккаунты сессии — переключение одним нажатием, как в YouTube
            accounts.forEach { a ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            a.handle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    },
                    leadingIcon = { Avatar(a.photo, 32.dp) },
                    trailingIcon = { if (a.index == current) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { menu = false; YouTubeAccount.switchTo(a.index) },
                )
            }
            DropdownMenuItem(
                text = { Text(tr(R.string.yt_add_account)) },
                leadingIcon = { Icon(Icons.Rounded.PersonAdd, null) },
                onClick = { menu = false; onAddAccount() },
            )
            DropdownMenuItem(
                text = { Text(tr(R.string.yt_sign_out)) },
                onClick = {
                    menu = false
                    YouTubeAccount.signOut()
                    Toast.makeText(context, tr(R.string.yt_signed_out), Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

/** История просмотра: шапка «История», Shorts одной строкой и ниже ролики списком — как в приложении YouTube. */
@Composable
private fun HistoryPage(
    state: FeedState,
    signedIn: Boolean,
    opening: String?,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onPlay: (YtVideo) -> Unit,
    onShort: (YtVideo) -> Unit,
    onDownload: (String) -> Unit,
    onChannel: (id: String, name: String) -> Unit,
) {
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (last >= state.items.size - 5 && state.items.isNotEmpty()) onMore() }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr(R.string.action_back)) }
            Text(tr(R.string.yt_history), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !signedIn -> SignInPrompt(onSignIn)
                state.items.isEmpty() && state.shorts.isEmpty() && state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.items.isEmpty() && state.error != null -> Message(tr(R.string.yt_load_failed, state.error.orEmpty()), tr(R.string.action_retry)) { onMore() }
                state.items.isEmpty() && state.shorts.isEmpty() && state.loaded -> Message(tr(R.string.yt_empty), null) {}
                else -> LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 16.dp)) {
                    if (state.shorts.isNotEmpty()) item { ShortsRow(state.shorts, onShort) }
                    items(state.items, key = { "h_" + it.id }) { v ->
                        VideoRow(
                            v, opening = opening == v.id, onClick = { onPlay(v) },
                            onDownload = { onDownload(v.url) },
                            onChannel = v.channelId?.let { id -> { onChannel(id, v.channel) } },
                        )
                    }
                    if (state.loading && state.items.isNotEmpty()) item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
                    }
                }
            }
        }
    }
}

/** Shorts одной строкой: вертикальные карточки с названием и просмотрами поверх превью. */
@Composable
private fun ShortsRow(shorts: List<YtVideo>, onOpen: (YtVideo) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(shorts, key = { "s_" + it.id }) { v ->
            Box(Modifier.width(132.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onOpen(v) }) {
                AsyncImage(v.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        .padding(8.dp),
                ) {
                    Text(v.title, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (v.meta.isNotEmpty()) Text(v.meta, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Каналы из подписок: аватары с подписями, синяя точка — есть новые ролики. */
@Composable
private fun ChannelsRow(channels: List<com.offplayer.app.youtube.YtChannel>, onOpen: (com.offplayer.app.youtube.YtChannel) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(channels, key = { "c_" + it.id }) { ch ->
            Column(
                Modifier.width(72.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpen(ch) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    Avatar(ch.avatar, 56.dp)
                    if (ch.hasNew) Box(
                        Modifier.align(Alignment.BottomEnd).size(14.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(7.dp))
                            .padding(2.dp).background(Color(0xFF3EA6FF), RoundedCornerShape(5.dp)),
                    )
                }
                Text(ch.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp))
            }
        }
    }
}

/** Ролик строкой: превью слева, название, канал и просмотры справа (история просмотра). */
@Composable
private fun VideoRow(v: YtVideo, opening: Boolean, onClick: () -> Unit, onDownload: () -> Unit, onChannel: (() -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    if (saving) SaveToPlaylistDialog(com.offplayer.app.youtube.OnlineStore.itemOf(v), onDismiss = { saving = false })
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val thumbWidth = (maxWidth * 0.42f).coerceAtMost(280.dp)
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.width(thumbWidth).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                AsyncImage(v.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                v.duration?.let {
                    Text(
                        it, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                if (opening) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Color.White)
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(v.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (v.channel.isNotEmpty()) Text(v.channel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (v.meta.isNotEmpty()) Text(v.meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.MoreVert, tr(R.string.more), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(tr(R.string.yt_save)) }, leadingIcon = { Icon(Icons.Rounded.BookmarkBorder, null) }, onClick = { menu = false; saving = true })
                    DropdownMenuItem(text = { Text(tr(R.string.yt_download)) }, leadingIcon = { Icon(Icons.Rounded.Download, null) }, onClick = { menu = false; onDownload() })
                    if (onChannel != null) DropdownMenuItem(text = { Text(tr(R.string.yt_open_channel)) }, leadingIcon = { Icon(Icons.Rounded.AccountCircle, null) }, onClick = { menu = false; onChannel() })
                }
            }
        }
    }
}

@Composable
internal fun VideoCard(v: YtVideo, opening: Boolean, onClick: () -> Unit, onDownload: () -> Unit, onChannel: (() -> Unit)?, rounded: Boolean = false) {
    var menu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    if (saving) SaveToPlaylistDialog(com.offplayer.app.youtube.OnlineStore.itemOf(v), onDismiss = { saving = false })
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 4.dp)) {
        // Превью во всю ширину экрана без скруглений — как в приложении YouTube
        // В сетке широкого экрана превью скруглены, как в YouTube для планшетов
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).then(if (rounded) Modifier.clip(RoundedCornerShape(12.dp)) else Modifier).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(v.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            val badge = when {
                v.isLive -> tr(R.string.yt_live)
                v.isShort -> "Shorts"
                else -> v.duration
            }
            if (badge != null) {
                Text(
                    badge, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .background(if (v.isLive) Color(0xFFCC0000) else Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            if (opening) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
            // Аватар канала слева от названия; нажатие открывает канал
            Avatar(v.avatar, 36.dp, Modifier.padding(end = 12.dp).then(if (onChannel != null) Modifier.clickable(onClick = onChannel) else Modifier))
            Column(Modifier.weight(1f)) {
                Text(v.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = listOf(v.channel, v.meta).filter { it.isNotEmpty() }.joinToString(" • ")
                if (sub.isNotEmpty()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, tr(R.string.more), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(tr(R.string.yt_save)) },
                        leadingIcon = { Icon(Icons.Rounded.BookmarkBorder, null) },
                        onClick = { menu = false; saving = true },
                    )
                    DropdownMenuItem(
                        text = { Text(tr(R.string.yt_download)) },
                        leadingIcon = { Icon(Icons.Rounded.Download, null) },
                        onClick = { menu = false; onDownload() },
                    )
                    if (onChannel != null) DropdownMenuItem(
                        text = { Text(tr(R.string.yt_open_channel)) },
                        leadingIcon = { Icon(Icons.Rounded.AccountCircle, null) },
                        onClick = { menu = false; onChannel() },
                    )
                }
            }
        }
    }
}

@Composable
private fun SignInPrompt(onSignIn: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.AccountCircle, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(tr(R.string.yt_signin_prompt), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSignIn) { Text(tr(R.string.yt_sign_in)) }
    }
}

@Composable
private fun Message(text: String, action: String?, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

/**
 * Вход: страница Google во встроенном браузере. Как только у youtube.com появляется cookie сеанса,
 * окно закрывается. Пароль вводится только на странице Google — приложение его не видит.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SignInDialog(onDone: () -> Unit, url: String = SIGN_IN_URL) {
    var progress by remember { mutableStateOf(true) }
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDone) { Icon(Icons.Rounded.Close, tr(R.string.action_close)) }
                    Text(tr(R.string.yt_sign_in_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (progress) CircularProgressIndicator(Modifier.padding(end = 16.dp).size(20.dp), strokeWidth = 2.dp)
                }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            // Google не пускает вход из «встроенных браузеров» (метка «wv») — представляемся обычным Chrome
                            settings.userAgentString = settings.userAgentString
                                .replace("; wv)", ")")
                                .replace(Regex("""Version/\d+(\.\d+)*\s"""), "")
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                    request.url.scheme != "https" && request.url.scheme != "http"

                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                    progress = true
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    progress = false
                                    CookieManager.getInstance().flush()
                                    val host = url?.let { Uri.parse(it).host }.orEmpty()
                                    // Вернулись на YouTube уже с сеансом — вход выполнен
                                    if (host.endsWith("youtube.com") && !(url == ADD_ACCOUNT_URL && Uri.parse(url).host == host)) {
                                        YouTubeAccount.refresh()
                                        if (YouTubeAccount.signedIn.value) onDone()
                                    }
                                }
                            }
                            loadUrl(url)
                        }
                    },
                    onRelease = { it.destroy() },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}

/** Вход ещё в один аккаунт Google в той же сессии (как «Добавить аккаунт» в YouTube). */
private const val ADD_ACCOUNT_URL = "https://accounts.google.com/AddSession?service=youtube&passive=false" +
    "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Dm%26next%3Dhttps%253A%252F%252Fm.youtube.com%252F"

private const val SIGN_IN_URL = "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true" +
    "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Dm%26next%3Dhttps%253A%252F%252Fm.youtube.com%252F"
