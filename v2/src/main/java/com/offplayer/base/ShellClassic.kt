package com.offplayer.base

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Сколько вкладок помещается в классическую панель. */
private const val MAX_TABS = 5

/**
 * Классическая панель: крупный заголовок модуля на градиенте от трека, кнопки «Загрузка» и «Настройки»,
 * плавающая нижняя панель до пяти вкладок; остальные модули — свайпом вверх по панели.
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
    val pal = LocalPalette.current

    BoxWithConstraints(Modifier.fillMaxSize().background(pal.base)) {
        HeaderGradient(maxHeight * 0.62f, Modifier.scrollWithPage())
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(chrome, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ScreenTitle(current?.title ?: "OFFPlayer", Modifier.weight(1f))
                    (Catalog.headerModules() + Catalog.downloads + Catalog.settings).forEach { m ->
                        GlassIconButton(m.icon, m.title) { Nav.select(m.id) }
                    }
                }
            }
            // Без шапки (развёрнутый плеер) модуль рисует до самого верха и сам отступает от строки состояния
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (current == null) EmptyTabs() else AnimatedContent(
                    targetState = current,
                    transitionSpec = { fadeIn(tunedSpring()) togetherWith fadeOut(tunedSpring()) },
                    contentKey = { it.id },
                    label = "tab",
                ) { spec -> ModuleHost(spec, ModuleEnv(showTitle = false, bottomInset = 100.dp), Modifier.fillMaxSize()) }
            }
        }

        // Под плавающей панелью — мягкое затухание, чтобы строки списка не торчали из-под неё
        if (chrome && tabs.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(140.dp)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to pal.base.copy(alpha = 0f), 0.55f to pal.base.copy(alpha = 0.9f), 1f to pal.base,
                    )),
            )
        }
        // Плавающая нижняя панель поверх содержимого; списки модулей докручиваются из-под неё
        AnimatedVisibility(
            chrome && tabs.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            Column(
                Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                    .pointerInput(extra.size) {
                        // Свайп вверх по панели открывает остальные модули
                        detectVerticalDragGestures { change, dy ->
                            if (dy < -14f && extra.isNotEmpty()) { change.consume(); drawer = true }
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (extra.isNotEmpty()) {
                    Box(Modifier.padding(bottom = 6.dp).size(36.dp, 4.dp).clip(CircleShape)
                        .background(pal.contentSecondary.copy(alpha = 0.5f)).clickable { drawer = true })
                }
                Row(
                    Modifier.fillMaxWidth().height(68.dp)
                        .shadow(if (Prefs.lightMode.value) 0.dp else 14.dp, RoundedCornerShape(26.dp))
                        .block(RoundedCornerShape(26.dp)).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    visible.forEach { m ->
                        val selected = current?.id == m.id
                        val tint by animateColorAsState(if (selected) pal.content else pal.contentSecondary, label = "tabTint")
                        val pill by animateColorAsState(if (selected) pal.accentContainer else pal.surface, label = "tabPill")
                        Column(
                            Modifier.weight(1f).bouncyClick { Nav.select(m.id) }
                                .semantics { this.selected = selected; role = Role.Tab },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.width(56.dp).height(30.dp).clip(RoundedCornerShape(15.dp)).background(pill), contentAlignment = Alignment.Center) {
                                Icon(m.icon, null, Modifier.size(22.dp), tint = if (selected) pal.accent else pal.contentSecondary)
                            }
                            Text(m.title, color = tint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    if (drawer) {
        ModalBottomSheet(onDismissRequest = { drawer = false }, containerColor = pal.surface) {
            Text("Ещё модули", color = pal.content, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(extra, key = { it.id }) { m ->
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).bouncyClick { Nav.select(m.id); drawer = false }.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(pal.accentContainer), contentAlignment = Alignment.Center) {
                            Icon(m.icon, null, tint = pal.accent)
                        }
                        Text(m.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp).width(72.dp), textAlign = TextAlign.Center)
                    }
                }
            }
            Box(Modifier.height(12.dp))
        }
    }
}

@Composable
fun EmptyTabs() {
    val pal = LocalPalette.current
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        VinylLogo(96.dp)
        Text("Нет включённых модулей", color = pal.content, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text("Установите или включите модули в настройках", color = pal.contentSecondary)
    }
}
