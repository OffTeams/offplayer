package com.offplayer.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import com.offplayer.app.L
import com.offplayer.app.R
import com.offplayer.app.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class ThemeMode(@StringRes private val res: Int) {
    SYSTEM(R.string.theme_system), LIGHT(R.string.theme_light), DARK(R.string.theme_dark);
    val label: String get() = tr(res)
}

/** Стиль отображения обложки в развёрнутом плеере (листается свайпом). */
enum class CoverStyle(@StringRes private val res: Int) {
    VINYL(R.string.cover_vinyl), FRAME(R.string.cover_frame), CASSETTE(R.string.cover_cassette), POLAROID(R.string.cover_polaroid);
    val label: String get() = tr(res)
}

/** Работа с музыкой и видео из памяти устройства (а не только со скачанным в приложение). */
enum class DeviceMediaMode {
    /** Только контент, скачанный через OFFPlayer. */
    OFF,
    /** Файлы устройства в общих списках «Треки» / «Все». */
    MIXED,
    /** Файлы устройства — в отдельных разделах «На устройстве», по папкам. */
    SEPARATE,
}

/** Порядок воспроизведения по умолчанию. */
enum class PlayOrder(@StringRes private val res: Int, @StringRes private val descRes: Int) {
    IN_ORDER(R.string.order_in_order, R.string.order_in_order_desc),
    SHUFFLE(R.string.order_shuffle, R.string.order_shuffle_desc),
    GENRE_MIX(R.string.order_genre, R.string.order_genre_desc);
    val label: String get() = tr(res)
    val description: String get() = tr(descRes)
}

/** Что делать с отрезком SponsorBlock данной категории. */
enum class SbAction(@StringRes private val res: Int, @StringRes private val descRes: Int) {
    SKIP(R.string.sb_skip, R.string.sb_skip_desc),
    ASK(R.string.sb_ask, R.string.sb_ask_desc),
    OFF(R.string.sb_off, R.string.sb_off_desc);
    val label: String get() = tr(res)
    val description: String get() = tr(descRes)
}

/**
 * Языки, на которых YouTube даёт субтитры (включая автоперевод) и дубляж.
 * Коды — как в yt-dlp / YouTube (iw — иврит, zh-Hans/zh-Hant — китайский).
 */
val DOWNLOAD_LANGUAGE_CODES: List<String> = listOf(
    "af", "ak", "sq", "am", "ar", "hy", "as", "ay", "az", "bn", "eu", "be", "bho", "bs", "bg", "my", "ca",
    "ceb", "zh-Hans", "zh-Hant", "co", "hr", "cs", "da", "dv", "nl", "en", "eo", "et", "ee", "fil", "fi",
    "fr", "gl", "lg", "ka", "de", "el", "gn", "gu", "ht", "ha", "haw", "iw", "hi", "hmn", "hu", "is", "ig",
    "id", "ga", "it", "ja", "jv", "kn", "kk", "km", "rw", "ko", "kri", "ku", "ky", "lo", "la", "lv", "ln",
    "lt", "lb", "mk", "mg", "ms", "ml", "mt", "mi", "mr", "mn", "ne", "nso", "no", "ny", "or", "om", "ps",
    "fa", "pl", "pt", "pa", "qu", "ro", "ru", "sm", "sa", "gd", "sr", "sn", "sd", "si", "sk", "sl", "so",
    "st", "es", "su", "sw", "sv", "tg", "ta", "tt", "te", "th", "ti", "ts", "tr", "tk", "uk", "ur", "ug",
    "uz", "vi", "cy", "fy", "xh", "yi", "yo", "zu",
)

/** Название языка на языке интерфейса, например «Немецкий» / «German». */
fun languageName(code: String): String {
    val ui = L.ctx.resources.configuration.locales[0]
    val name = Locale.forLanguageTag(code).getDisplayName(ui)
    if (name.isBlank() || name.equals(code, ignoreCase = true)) return code
    return name.replaceFirstChar { it.titlecase(ui) }
}
object AppSettings {
    private lateinit var prefs: SharedPreferences

    // ---- Общие ----

    private val _deviceMedia = MutableStateFlow(DeviceMediaMode.OFF)
    val deviceMedia: StateFlow<DeviceMediaMode> = _deviceMedia.asStateFlow()
    fun setDeviceMedia(v: DeviceMediaMode) { _deviceMedia.value = v; prefs.edit().putString("device_media", v.name).apply() }

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()
    fun setThemeMode(v: ThemeMode) { _themeMode.value = v; prefs.edit().putString("theme", v.name).apply() }

    // ---- Музыкальный плеер ----

    private val _coverStyle = MutableStateFlow(CoverStyle.VINYL)
    val coverStyle: StateFlow<CoverStyle> = _coverStyle.asStateFlow()
    fun setCoverStyle(v: CoverStyle) {
        if (_coverStyle.value == v) return
        _coverStyle.value = v
        prefs.edit().putString("cover_style", v.name).apply()
    }

    private val _pauseOnNoisy = MutableStateFlow(true)
    /** Пауза при отключении наушников (музыка и видео). */
    val pauseOnNoisy: StateFlow<Boolean> = _pauseOnNoisy.asStateFlow()
    fun setPauseOnNoisy(v: Boolean) { _pauseOnNoisy.value = v; prefs.edit().putBoolean("pause_noisy", v).apply() }

    /** Обложка: false — «Сменяемая» (листается свайпом), true — всегда выбранный стиль. */
    private val _coverLocked = MutableStateFlow(false)
    val coverLocked: StateFlow<Boolean> = _coverLocked.asStateFlow()
    fun setCoverLocked(v: Boolean) { _coverLocked.value = v; prefs.edit().putBoolean("cover_locked", v).apply() }

    private val _playOrder = MutableStateFlow(PlayOrder.IN_ORDER)
    /** Порядок воспроизведения по умолчанию. */
    val playOrder: StateFlow<PlayOrder> = _playOrder.asStateFlow()
    fun setPlayOrder(v: PlayOrder) { _playOrder.value = v; prefs.edit().putString("play_order", v.name).apply() }

    private val _defaultRepeat = MutableStateFlow(0)
    /** Повтор по умолчанию: 0 — выкл, 2 — очередь по кругу, 1 — один трек (как Player.REPEAT_MODE_*). */
    val defaultRepeat: StateFlow<Int> = _defaultRepeat.asStateFlow()
    fun setDefaultRepeat(v: Int) { _defaultRepeat.value = v; prefs.edit().putInt("default_repeat", v).apply() }

    private val _showSleepTimer = MutableStateFlow(true)
    val showSleepTimer: StateFlow<Boolean> = _showSleepTimer.asStateFlow()
    fun setShowSleepTimer(v: Boolean) { _showSleepTimer.value = v; prefs.edit().putBoolean("show_sleep", v).apply() }

    private val _showAddToPlaylist = MutableStateFlow(true)
    val showAddToPlaylist: StateFlow<Boolean> = _showAddToPlaylist.asStateFlow()
    fun setShowAddToPlaylist(v: Boolean) { _showAddToPlaylist.value = v; prefs.edit().putBoolean("show_add_pl", v).apply() }

    private val _saveSource = MutableStateFlow(true)
    /** Сохранять канал-источник при скачивании и показывать его в плеере. */
    val saveSource: StateFlow<Boolean> = _saveSource.asStateFlow()
    fun setSaveSource(v: Boolean) { _saveSource.value = v; prefs.edit().putBoolean("save_source", v).apply() }

    // ---- Видеоплеер ----

    private val _videoBackground = MutableStateFlow(true)
    /** Продолжать звук видео, когда плеер свернут. */
    val videoBackground: StateFlow<Boolean> = _videoBackground.asStateFlow()
    fun setVideoBackground(v: Boolean) { _videoBackground.value = v; prefs.edit().putBoolean("video_bg", v).apply() }

    private val _videoAutoPip = MutableStateFlow(true)
    /** Автоматически уходить в «картинку в картинке» при нажатии «Домой». */
    val videoAutoPip: StateFlow<Boolean> = _videoAutoPip.asStateFlow()
    fun setVideoAutoPip(v: Boolean) { _videoAutoPip.value = v; prefs.edit().putBoolean("video_auto_pip", v).apply() }

    private val _videoAutoRotate = MutableStateFlow(true)
    /** Поворачивать экран под ориентацию видео при открытии. */
    val videoAutoRotate: StateFlow<Boolean> = _videoAutoRotate.asStateFlow()
    fun setVideoAutoRotate(v: Boolean) { _videoAutoRotate.value = v; prefs.edit().putBoolean("video_auto_rotate", v).apply() }

    private val _videoSpeed = MutableStateFlow(1f)
    /** Скорость, с которой начинается воспроизведение видео. */
    val videoSpeed: StateFlow<Float> = _videoSpeed.asStateFlow()
    fun setVideoSpeed(v: Float) { _videoSpeed.value = v; prefs.edit().putFloat("video_speed", v).apply() }

    private val _seekStep = MutableStateFlow(10)
    /** Шаг перемотки двойным нажатием, секунды. */
    val seekStep: StateFlow<Int> = _seekStep.asStateFlow()
    fun setSeekStep(v: Int) { _seekStep.value = v; prefs.edit().putInt("seek_step", v).apply() }

    private val _videoFill = MutableStateFlow(false)
    /** Открывать видео в режиме заполнения экрана. */
    val videoFill: StateFlow<Boolean> = _videoFill.asStateFlow()
    fun setVideoFill(v: Boolean) { _videoFill.value = v; prefs.edit().putBoolean("video_fill", v).apply() }

    private val _showChapters = MutableStateFlow(true)
    /** Показывать в видеоплеере кнопку и панель «Эпизоды». */
    val showChapters: StateFlow<Boolean> = _showChapters.asStateFlow()
    fun setShowChapters(v: Boolean) { _showChapters.value = v; prefs.edit().putBoolean("show_chapters", v).apply() }

    // ---- SponsorBlock ----

    private val _sbActions = MutableStateFlow(defaultSbActions())
    /** Действие для каждой категории SponsorBlock. */
    val sbActions: StateFlow<Map<String, SbAction>> = _sbActions.asStateFlow()
    fun setSbAction(category: String, action: SbAction) {
        val next = _sbActions.value + (category to action)
        _sbActions.value = next
        prefs.edit().putString("sb_actions", next.entries.joinToString(",") { "${it.key}:${it.value.name}" }).apply()
    }

    fun sbAction(category: String): SbAction = _sbActions.value[category] ?: SbAction.OFF

    private val _sbOnDownload = MutableStateFlow(true)
    /** Загружать данные SponsorBlock при скачивании видео. */
    val sbOnDownload: StateFlow<Boolean> = _sbOnDownload.asStateFlow()
    fun setSbOnDownload(v: Boolean) { _sbOnDownload.value = v; prefs.edit().putBoolean("sb_download", v).apply() }

    private val _sbNotice = MutableStateFlow(true)
    /** Показывать плашку «Пропущено · Вернуть» после автопропуска. */
    val sbNotice: StateFlow<Boolean> = _sbNotice.asStateFlow()
    fun setSbNotice(v: Boolean) { _sbNotice.value = v; prefs.edit().putBoolean("sb_notice", v).apply() }

    private fun defaultSbActions(): Map<String, SbAction> =
        SponsorBlock.CATEGORIES.keys.associateWith { if (it in SponsorBlock.DEFAULT_ENABLED) SbAction.SKIP else SbAction.OFF }

    // ---- Загрузки ----

    private val _audioFormat = MutableStateFlow("mp3")
    val audioFormat: StateFlow<String> = _audioFormat.asStateFlow()
    fun setAudioFormat(v: String) { _audioFormat.value = v; prefs.edit().putString("audio_format", v).apply() }

    private val _videoHeight = MutableStateFlow(1080)
    /** 0 = лучшее доступное качество. */
    val videoHeight: StateFlow<Int> = _videoHeight.asStateFlow()
    fun setVideoHeight(v: Int) { _videoHeight.value = v; prefs.edit().putInt("video_height", v).apply() }

    private val _saveChapters = MutableStateFlow(true)
    /** Сохранять эпизоды (главы) при скачивании видео. */
    val saveChapters: StateFlow<Boolean> = _saveChapters.asStateFlow()
    fun setSaveChapters(v: Boolean) { _saveChapters.value = v; prefs.edit().putBoolean("save_chapters", v).apply() }

    private val _extraAudioLangs = MutableStateFlow<Set<String>>(emptySet())
    /** Дополнительные аудиодорожки (дубляж) к оригинальной; пусто — только оригинальная. */
    val extraAudioLangs: StateFlow<Set<String>> = _extraAudioLangs.asStateFlow()
    fun setExtraAudioLangs(v: Set<String>) { _extraAudioLangs.value = v; prefs.edit().putStringSet("extra_audio_langs", v).apply() }
    fun toggleExtraAudioLang(lang: String) {
        val next = _extraAudioLangs.value.let { if (lang in it) it - lang else it + lang }
        _extraAudioLangs.value = next
        prefs.edit().putStringSet("extra_audio_langs", next).apply()
    }

    private val _subLangs = MutableStateFlow<Set<String>>(emptySet())
    /** Языки субтитров, которые встраиваются в видео при скачивании; пусто — без субтитров. */
    val subLangs: StateFlow<Set<String>> = _subLangs.asStateFlow()
    fun setSubLangs(v: Set<String>) { _subLangs.value = v; prefs.edit().putStringSet("sub_langs", v).apply() }
    fun toggleSubLang(lang: String) {
        val next = _subLangs.value.let { if (lang in it) it - lang else it + lang }
        _subLangs.value = next
        prefs.edit().putStringSet("sub_langs", next).apply()
    }

    private val _autoSubs = MutableStateFlow(false)
    /** Брать автоматические субтитры YouTube, если обычных нет. */
    val autoSubs: StateFlow<Boolean> = _autoSubs.asStateFlow()
    fun setAutoSubs(v: Boolean) { _autoSubs.value = v; prefs.edit().putBoolean("auto_subs", v).apply() }

    private val _wholePlaylist = MutableStateFlow(true)
    /** По ссылке на страницу плейлиста скачивать его целиком. */
    val wholePlaylist: StateFlow<Boolean> = _wholePlaylist.asStateFlow()
    fun setWholePlaylist(v: Boolean) { _wholePlaylist.value = v; prefs.edit().putBoolean("whole_playlist", v).apply() }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _deviceMedia.value = runCatching { DeviceMediaMode.valueOf(prefs.getString("device_media", null) ?: "") }
            .getOrDefault(DeviceMediaMode.OFF)
        _themeMode.value = runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "") }.getOrDefault(ThemeMode.SYSTEM)
        _coverStyle.value = runCatching { CoverStyle.valueOf(prefs.getString("cover_style", null) ?: "") }.getOrDefault(CoverStyle.VINYL)
        _pauseOnNoisy.value = prefs.getBoolean("pause_noisy", true)
        _coverLocked.value = prefs.getBoolean("cover_locked", false)
        _playOrder.value = runCatching { PlayOrder.valueOf(prefs.getString("play_order", null) ?: "") }.getOrDefault(PlayOrder.IN_ORDER)
        _defaultRepeat.value = prefs.getInt("default_repeat", 0)
        _showSleepTimer.value = prefs.getBoolean("show_sleep", true)
        _showAddToPlaylist.value = prefs.getBoolean("show_add_pl", true)
        _saveSource.value = prefs.getBoolean("save_source", true)
        _videoBackground.value = prefs.getBoolean("video_bg", true)
        _videoAutoPip.value = prefs.getBoolean("video_auto_pip", true)
        _videoAutoRotate.value = prefs.getBoolean("video_auto_rotate", true)
        _videoSpeed.value = prefs.getFloat("video_speed", 1f)
        _seekStep.value = prefs.getInt("seek_step", 10)
        _videoFill.value = prefs.getBoolean("video_fill", false)
        _showChapters.value = prefs.getBoolean("show_chapters", true)
        _sbOnDownload.value = prefs.getBoolean("sb_download", true)
        _sbNotice.value = prefs.getBoolean("sb_notice", true)
        _sbActions.value = prefs.getString("sb_actions", null)?.let { s ->
            defaultSbActions() + s.split(',').mapNotNull { p ->
                val (k, v) = p.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                runCatching { k to SbAction.valueOf(v) }.getOrNull()
            }
        } ?: legacySbActions()
        _audioFormat.value = prefs.getString("audio_format", "mp3") ?: "mp3"
        _videoHeight.value = prefs.getInt("video_height", 1080)
        _saveChapters.value = prefs.getBoolean("save_chapters", true)
        _extraAudioLangs.value = prefs.getStringSet("extra_audio_langs", null)?.toSet() ?: emptySet()
        _subLangs.value = prefs.getStringSet("sub_langs", null)?.toSet() ?: emptySet()
        _autoSubs.value = prefs.getBoolean("auto_subs", false)
        _wholePlaylist.value = prefs.getBoolean("whole_playlist", true)
    }

    /** Прежние настройки (набор категорий + общий автопропуск) → действия по категориям. */
    private fun legacySbActions(): Map<String, SbAction> {
        val old = prefs.getStringSet("sb_categories", null) ?: return defaultSbActions()
        val auto = prefs.getBoolean("sb_auto", true)
        return SponsorBlock.CATEGORIES.keys.associateWith {
            when {
                it !in old -> SbAction.OFF
                auto -> SbAction.SKIP
                else -> SbAction.ASK
            }
        }
    }
}
