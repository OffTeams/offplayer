package com.offplayer.app.youtube

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Переходы из плеера и Shorts в остальное приложение: «Скачать» ведёт на вкладку «Загрузки» OFFPlayer
 * (вместо загрузки YouTube), аватар — на страницу канала во вкладке YouTube.
 */
object YouTubeNav {
    val downloadRequest = MutableStateFlow<String?>(null)
    val channelRequest = MutableStateFlow<Pair<String, String>?>(null)
    /** Открыта лента Shorts: верх и низ экрана чёрные в любой теме. */
    val shortsOpen = MutableStateFlow(false)

    /** Shorts, который нужно открыть в ленте Shorts (например, из уведомлений). */
    val shortRequest = MutableStateFlow<String?>(null)

    /** Растёт при нажатии на уже открытую вкладку YouTube в нижнем меню — лента возвращается к началу «Главной». */
    val homeTop = MutableStateFlow(0)

    /** Растёт, когда нужно открыть вкладку YouTube на ленте Shorts (уведомление о Shorts). */
    val openShortsTab = MutableStateFlow(0)

    fun download(url: String) {
        downloadRequest.value = url
    }

    fun openChannel(id: String, name: String) {
        channelRequest.value = id to name
    }
}
