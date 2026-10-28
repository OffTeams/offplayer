package com.offplayer.base

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Сколько вкладок помещается в классическую панель. */
private const val MAX_TABS = 5

/**
 * Классическая панель: до пяти вкладок внизу, шапка с загрузкой и настройками.
 * Если модулей больше — выдвижная панель свайпом вверх по нижней панели.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassicShell() {
    val tabs = Catalog.tabs()
    val current = Nav.current(tabs)
    val visible = tabs.take(MAX_TABS)
    val extra = tabs.drop(MAX_TABS)
    var drawer by remember { mutableStateOf(false) }
    val chrome = !Playback.immersive

    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(chrome, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            TopAppBar(
                title = { Text(current?.title ?: "OFFPlayer", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    Catalog.headerModules().forEach { m ->
                        IconButton(onClick = { Nav.select(m.id) }) { Icon(m.icon, m.title) }
                    }
                    IconButton(onClick = { Nav.select("downloads") }) { Icon(Catalog.downloads.icon, Catalog.downloads.title) }
                    IconButton(onClick = { Nav.select("settings") }) { Icon(Catalog.settings.icon, Catalog.settings.title) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth().then(if (chrome) Modifier else Modifier.statusBarsPadding())) {
            if (current == null) {
                EmptyTabs()
            } else {
                AnimatedContent(
                    targetState = current,
                    transitionSpec = { fadeIn(tunedSpring()) togetherWith fadeOut(tunedSpring()) },
                    contentKey = { it.id },
                    label = "tab",
                ) { spec ->
                    ModuleHost(spec, ModuleEnv(showTitle = false), Modifier.fillMaxSize())
                }
            }
        }
        AnimatedVisibility(chrome && tabs.isNotEmpty(), enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()) {
            Column(
                Modifier.pointerInput(extra.size) {
                    // Свайп вверх по панели открывает остальные модули
                    detectVerticalDragGestures { change, dy ->
                        if (dy < -14f && extra.isNotEmpty()) {
                            change.consume()
                            drawer = true
                        }
                    }
                },
            ) {
                if (extra.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().clickable { drawer = true }.padding(top = 6.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
                    }
                }
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (Prefs.lightMode.value) 1f else 0.92f),
                ) {
                    visible.forEach { m ->
                        NavigationBarItem(
                            selected = current?.id == m.id,
                            onClick = { Nav.select(m.id) },
                            icon = { Icon(m.icon, null) },
                            label = { Text(m.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer),
                        )
                    }
                }
            }
        }
    }

    if (drawer) {
        ModalBottomSheet(onDismissRequest = { drawer = false }) {
            Text("Ещё модули", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(extra, key = { it.id }) { m ->
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).clickable { Nav.select(m.id); drawer = false }.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(m.color.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center) { Icon(m.icon, null, tint = m.color) }
                        Text(m.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp).width(72.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            Box(Modifier.height(12.dp))
        }
    }
}

@Composable
fun EmptyTabs() {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        VinylLogo(96.dp)
        Text("Нет включённых модулей", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text("Установите или включите модули в настройках", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
