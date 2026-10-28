package com.offplayer.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

data class FakeTrack(val id: Int, val title: String, val artist: String, val color: Color, val durationSec: Int)
data class FakeVideo(val id: Int, val title: String, val channel: String, val color: Color, val durationSec: Int)

val FAKE_TRACKS = listOf(
    FakeTrack(1, "Северное сияние", "Ночной экспресс", Color(0xFF5B4BDB), 214),
    FakeTrack(2, "Тёплый ветер", "Лето в городе", Color(0xFFFF7A59), 187),
    FakeTrack(3, "Пластинка", "OFF Orchestra", Color(0xFFFFC24B), 243),
    FakeTrack(4, "Глубина", "Синий кит", Color(0xFF1F8FA6), 302),
    FakeTrack(5, "Неон", "Ночные огни", Color(0xFFD94BC0), 199),
    FakeTrack(6, "Мята", "Утро", Color(0xFF2EC4B6), 176),
    FakeTrack(7, "Кирпичная стена", "Старый двор", Color(0xFFB5523B), 228),
    FakeTrack(8, "Космос", "Орбита", Color(0xFF3D5A80), 265),
    FakeTrack(9, "Лаванда", "Поле", Color(0xFF8E7CFF), 208),
    FakeTrack(10, "Янтарь", "Балтика", Color(0xFFE09A2D), 191),
    FakeTrack(11, "Лес", "Тропа", Color(0xFF4F8A3C), 233),
    FakeTrack(12, "Песок", "Пустыня", Color(0xFFC9A27E), 254),
)

val FAKE_VIDEOS = listOf(
    FakeVideo(1, "Как устроена пластинка", "Звук и техника", Color(0xFF5B4BDB), 734),
    FakeVideo(2, "Закат над морем — 4K", "Природа", Color(0xFFFF7A59), 1201),
    FakeVideo(3, "Модульные синтезаторы за 10 минут", "Синтез", Color(0xFF2EC4B6), 612),
    FakeVideo(4, "Город ночью", "Таймлапсы", Color(0xFF3D5A80), 455),
    FakeVideo(5, "Осенний лес", "Природа", Color(0xFFE09A2D), 980),
    FakeVideo(6, "Обзор наушников", "Звук и техника", Color(0xFF8E7CFF), 845),
)

/** Имитация воспроизведения: музыка, видео, «картинка в картинке». */
object Playback {
    var track by mutableStateOf<FakeTrack?>(null)
    var playing by mutableStateOf(false)
    var trackPos by mutableFloatStateOf(0f)

    var video by mutableStateOf<FakeVideo?>(null)
    var videoPlaying by mutableStateOf(false)
    var videoPos by mutableFloatStateOf(0f)
    /** Экран выключили — картинка остановлена, идёт только звук. */
    var audioOnly by mutableStateOf(false)
    /** Видео развёрнуто на весь экран. */
    var fullscreen by mutableStateOf(false)

    /** Модуль попросил «картинку в картинке»; оболочка проигрывает анимацию и вызывает [enterPip]. */
    var pipRequest by mutableStateOf<String?>(null)
    /** Модуль, ушедший во внутреннюю «картинку в картинке». */
    var pipModule by mutableStateOf<String?>(null)
    /** Модуль, который возвращается из «картинки в картинке» (для анимации появления). */
    var returning by mutableStateOf<String?>(null)
    /** Модуль просит весь экран (шапка и панели прячутся). */
    var immersive by mutableStateOf(false)

    fun play(t: FakeTrack) {
        if (track?.id != t.id) trackPos = 0f
        track = t; playing = true
    }

    fun next(step: Int) {
        val cur = track ?: return
        val i = FAKE_TRACKS.indexOfFirst { it.id == cur.id }
        play(FAKE_TRACKS[(i + step).mod(FAKE_TRACKS.size)])
    }

    fun playVideo(v: FakeVideo) {
        if (video?.id != v.id) videoPos = 0f
        video = v; videoPlaying = true; audioOnly = false
    }

    /** Модуль уходит в окошко «картинка в картинке» внутри приложения. */
    fun enterPip(moduleId: String) {
        pipModule = moduleId
        if (Nav.currentId == moduleId) Nav.currentId = null
    }

    fun closePip() {
        val id = pipModule ?: return
        pipModule = null
        returning = id
    }
}

/** Тикает позиции воспроизведения. */
@Composable
fun PlaybackTicker() {
    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            Playback.track?.let { t ->
                if (Playback.playing) {
                    Playback.trackPos += 0.25f
                    if (Playback.trackPos >= t.durationSec) Playback.next(1)
                }
            }
            Playback.video?.let { v ->
                if (Playback.videoPlaying) Playback.videoPos = (Playback.videoPos + 0.25f) % v.durationSec
            }
        }
    }
}

fun formatTime(sec: Float): String {
    val s = sec.toInt()
    return "%d:%02d".format(s / 60, s % 60)
}
