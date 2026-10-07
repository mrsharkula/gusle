package com.sharkula.gusle

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.sharkula.gusle.data.MusicLibrary
import com.sharkula.gusle.ui.covers.CoverColors
import com.sharkula.gusle.ui.covers.SongCoverFetcher
import com.sharkula.gusle.ui.covers.SongCoverKeyer

class GusleApp : Application(), SingletonImageLoader.Factory {
    /** The songs on the watch, shared by the screens and the playback service. */
    val library by lazy { MusicLibrary(this) }

    /** The colors of the songs' covers, shared by the screens. */
    val coverColors by lazy { CoverColors(this) }

    /** Coil's image loader, taught to load covers from song files. */
    override fun newImageLoader(context: PlatformContext) =
        ImageLoader.Builder(context)
            .components {
                add(SongCoverKeyer())
                add(SongCoverFetcher.Factory())
            }
            .build()
}

/** The app's one [MusicLibrary], from any of its contexts. */
val Context.musicLibrary: MusicLibrary get() = (applicationContext as GusleApp).library

/** The app's one [CoverColors], from any of its contexts. */
val Context.coverColors: CoverColors get() = (applicationContext as GusleApp).coverColors
