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

    /** Комментарий, который нужно открыть вместе с роликом (из оповещения об ответе). */
    data class LinkedComment(val videoId: String, val commentId: String, val params: String?)
    @Volatile var linkedComment: LinkedComment? = null

    /** Растёт, когда нужно перейти на вкладку YouTube (уведомление об ответе на комментарий). */
    val openYoutubeTab = MutableStateFlow(0)

    /** Растёт, когда листают страницу ролика (похожие под комментариями) — отменяет «Следующее видео». */
    val relatedScrolled = MutableStateFlow(0)

    /** Открыть страницу комментариев ролика (оповещение об ответе): без запуска ролика. */
    val commentsPage = MutableStateFlow<LinkedComment?>(null)

    fun download(url: String) {
        downloadRequest.value = url
    }

    fun openChannel(id: String, name: String) {
        channelRequest.value = id to name
    }
}
