package com.sharkula.gusle

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.sharkula.gusle.data.MusicLibrary
import com.sharkula.gusle.ui.covers.SongCoverFetcher
import com.sharkula.gusle.ui.covers.SongCoverKeyer

class GusleApp : Application(), SingletonImageLoader.Factory {
    /** The songs on the watch, shared by the screens and, later, the playback service. */
    val library by lazy { MusicLibrary(this) }

    /** Coil's image loader, taught to load covers from song files. */
    override fun newImageLoader(context: PlatformContext) =
        ImageLoader.Builder(context)
            .components {
                add(SongCoverKeyer())
                add(SongCoverFetcher.Factory())
            }
            .build()
}
