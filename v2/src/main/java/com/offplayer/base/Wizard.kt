package com.offplayer.base

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val STEPS = 5

/** Временная «Главная» первого запуска: язык интерфейса R — русский, выбор стиля, цвета, производительность. */
@Composable
fun FirstRunWizard() {
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(step > 0) { step-- }
    val context = LocalContext.current
    val weak = remember { isWeakDevice(context) }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
        // Индикатор шагов
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            repeat(STEPS) { i ->
                Box(Modifier.height(4.dp).weight(1f).clip(CircleShape)
                    .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest))
            }
        }
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(tunedSpring()) { if (forward) it / 2 else -it / 2 } + fadeIn()) togetherWith
                    (slideOutHorizontally(tunedSpring()) { if (forward) -it / 2 else it / 2 } + fadeOut())
            },
            modifier = Modifier.weight(1f),
            label = "wizard",
        ) { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (s) {
                    0 -> WelcomeStep()
                    1 -> StyleStep()
                    2 -> ColorStep()
                    3 -> PerformanceStep(weak)
                    else -> DoneStep()
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) TextButton(onClick = { step-- }) { Text("Назад") }
            Spacer(Modifier.weight(1f))
            Button(onClick = { if (step < STEPS - 1) step++ else Prefs.firstRunDone.value = true }) {
                Text(when (step) { 0 -> "Начать"; STEPS - 1 -> "Готово"; else -> "Далее" })
            }
        }
    }
}

private fun isWeakDevice(context: Context): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    return am.isLowRamDevice || mem.totalMem < 4L * 1024 * 1024 * 1024 || Build.VERSION.SDK_INT < 31
}

@Composable
private fun StepTitle(title: String, text: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun WelcomeStep() {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        VinylLogo(180.dp, rotating = true)
        Spacer(Modifier.height(28.dp))
        Text("OFFPlayer 2.0", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Основа, которую вы собираете сами: модули добавляют вкладки, а вид главного экрана вы выбираете под себя.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text("Макет 2.0.0.2.1R — модули-пустышки для проверки дизайна", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun StyleStep() {
    StepTitle("Стиль главного экрана", "Его можно сменить в любой момент в настройках.")
    ShellStyle.entries.forEach { s ->
        val sel = Prefs.shell.value == s
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(20.dp))
                .border(if (sel) 2.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(20.dp))
                .clickable { Prefs.shell.value = s }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StylePreview(s)
            Column(Modifier.padding(start = 14.dp)) {
                Text(s.title, style = MaterialTheme.typography.titleMedium)
                Text(s.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Мини-экран 9:16 со схемой стиля. */
@Composable
private fun StylePreview(s: ShellStyle) {
    val fg = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier.width(54.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        when (s) {
            ShellStyle.CLASSIC -> {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(34.dp, 4.dp).background(fg.copy(alpha = 0.4f), CircleShape))
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(fg.copy(alpha = 0.15f)).padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    repeat(4) { i -> Box(Modifier.size(6.dp).background(if (i == 0) accent else fg.copy(alpha = 0.5f), CircleShape)) }
                }
            }
            ShellStyle.DOTS -> Column(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(5) { i -> Box(Modifier.size(5.dp).background(if (i == 2) accent else fg.copy(alpha = 0.5f), CircleShape)) }
            }
            ShellStyle.CARDS -> Box(Modifier.fillMaxSize().padding(9.dp)) {
                Box(Modifier.fillMaxSize().rotate(7f).background(fg.copy(alpha = 0.25f), RoundedCornerShape(4.dp)))
                Box(Modifier.fillMaxSize().rotate(-5f).background(fg.copy(alpha = 0.35f), RoundedCornerShape(4.dp)))
                Box(Modifier.fillMaxSize().background(accent.copy(alpha = 0.8f), RoundedCornerShape(4.dp)))
            }
        }
    }
}

@Composable
private fun ColorStep() {
    StepTitle("Цвета", "По умолчанию OFFPlayer берёт цвета из обоев, а во время музыки подстраивается под трек.")
    Chips(ColorMode.entries, Prefs.colorMode.value, { it.title }) { Prefs.colorMode.value = it }
    Spacer(Modifier.height(8.dp))
    Text(Prefs.colorMode.value.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (Prefs.colorMode.value == ColorMode.CLASSIC) {
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ClassicPreset.entries.forEach { p ->
                val sel = Prefs.preset.value == p
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Color(p.background))
                        .border(if (sel) 3.dp else 1.dp, if (sel) Coral else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable { Prefs.preset.value = p },
                )
            }
        }
        Text(Prefs.preset.value.title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun PerformanceStep(weak: Boolean) {
    StepTitle(
        "Производительность",
        if (weak) "Похоже, устройство не самое мощное. Советуем включить лёгкий режим — его можно выключить в любой момент."
        else "Лёгкий режим заменяет размытие, градиенты и живые задние карточки на простые решения.",
    )
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { Prefs.lightMode.value = !Prefs.lightMode.value }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Лёгкий режим", style = MaterialTheme.typography.titleMedium)
            Text("Для слабых устройств и экономии батареи", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(Prefs.lightMode.value, { Prefs.lightMode.value = it })
    }
}

@Composable
private fun DoneStep() {
    StepTitle("Всё готово", "На старте установлены модули «Загрузка», «Музыка» и «Видео». Остальные — в настройках, раздел «Модули».")
    listOf(Catalog.downloads, Catalog.music, Catalog.video).forEach { m ->
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(m.color.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(m.icon, null, tint = m.color)
            }
            Text(m.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 14.dp))
        }
    }
}
