package com.offplayer.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.offplayer.app.data.AppSettings
import com.offplayer.app.data.LibraryStore
import com.offplayer.app.data.MediaRepository
import com.offplayer.app.data.PlaylistStore
import com.offplayer.app.data.VideoLibraryStore
import com.offplayer.app.data.VideoMetaStore
import com.offplayer.app.image.MediaThumbFetcher

class OffPlayerApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        PlaylistStore.init(this)
        VideoLibraryStore.init(this)
        VideoMetaStore.init(this)
        LibraryStore.init(this)
        com.offplayer.app.data.PodcastProgress.init(this)
        com.offplayer.app.data.NetworkState.init(this)
        MediaRepository.start(this)
        // Фоновая проверка актуальности плагина загрузки (при подключении к сети и ежедневно)
        com.offplayer.app.download.PluginUpdater.init(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(MediaThumbFetcher.Key())
            add(MediaThumbFetcher.Factory())
        }
        .crossfade(true)
        .build()
}
