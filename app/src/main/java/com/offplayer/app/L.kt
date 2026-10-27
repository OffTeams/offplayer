package com.offplayer.app

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Язык интерфейса. По умолчанию — как в системе; в настройках можно выбрать другой.
 * Строки берутся через [tr] / [trn] из контекста с нужной локалью — это работает и в экранах,
 * и в сервисах (уведомления, загрузки).
 */
object L {
    /** Поддерживаемые языки интерфейса; "" — как в системе. */
    val LANGUAGES = listOf("", "ru", "uk", "en", "de", "es", "fr", "pt", "ko", "ja")

    private const val PREFS = "settings"
    private const val KEY = "app_language"

    lateinit var ctx: Context
        private set

    private val _language = MutableStateFlow("")
    val language: StateFlow<String> = _language.asStateFlow()

    fun saved(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    /** Контекст с выбранным языком (для Application и Activity.attachBaseContext). */
    fun wrap(base: Context): Context {
        val tag = saved(base)
        if (tag.isBlank()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    fun init(app: Context) {
        _language.value = saved(app)
        ctx = wrap(app)
    }

    /** Сменить язык: строки переключаются сразу, открытый экран пересоздаётся. */
    fun set(activity: Activity, tag: String) {
        val app = activity.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).commit()
        _language.value = tag
        ctx = if (tag.isBlank()) {
            // «Как в системе»: берём язык системы, даже если приложение было запущено с другим
            val system = android.content.res.Resources.getSystem().configuration.locales[0]
            Locale.setDefault(system)
            app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(system) })
        } else {
            wrap(app)
        }
        activity.recreate()
    }

    /** Название языка на нём самом: «Русский», «English», «日本語». */
    fun nativeName(tag: String): String {
        if (tag.isBlank()) return ""
        val l = Locale.forLanguageTag(tag)
        return l.getDisplayLanguage(l).replaceFirstChar { it.titlecase(l) }
    }
}

/** Строка на текущем языке интерфейса. */
fun tr(@StringRes id: Int, vararg args: Any): String = L.ctx.getString(id, *args)

/** Строка с числом во множественном числе по правилам языка: «1 трек», «2 трека», «5 треков». */
fun trn(@PluralsRes id: Int, count: Int): String = L.ctx.resources.getQuantityString(id, count, count)
