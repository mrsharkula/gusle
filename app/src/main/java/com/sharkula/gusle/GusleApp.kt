package com.sharkula.gusle

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.sharkula.gusle.data.MusicLibrary
import com.sharkula.gusle.playback.LastPlayed
import com.sharkula.gusle.playback.PlayingSong
import com.sharkula.gusle.playback.PlayingSongHolder
import com.sharkula.gusle.playback.UnplayableSongs
import com.sharkula.gusle.ui.covers.CoverColors
import com.sharkula.gusle.ui.covers.SongCoverFetcher
import com.sharkula.gusle.ui.covers.SongCoverKeyer
import com.sharkula.gusle.ui.theme.ThemeChoice
import kotlinx.coroutines.flow.StateFlow

class GusleApp : Application(), SingletonImageLoader.Factory {
    /** The songs on the watch, shared by the screens and the playback service. */
    val library by lazy { MusicLibrary(this) }

    /** The colors of the songs' covers, shared by the screens. */
    val coverColors by lazy { CoverColors(this) }

    /** The queue the player had last, kept on disk to come back to. */
    val lastPlayed by lazy { LastPlayed(this) }

    /** The song the playback service is playing, or played last, which it shares with the screens. */
    lateinit var playing: PlayingSongHolder
        private set

    /** The songs the playback service couldn't play, which it shares with the screens. */
    val unplayable by lazy { UnplayableSongs() }

    /**
     * The app theme chosen in Settings. Made, and read, as the screen first starts: the playback
     * service starts the app alone too, with no screen to color.
     */
    val themeChoice by lazy { ThemeChoice(this) }

    override fun onCreate() {
        super.onCreate()
        // Starts reading what played last, so Home can show it as soon as the songs are read.
        playing = PlayingSongHolder(library, lastPlayed)
    }

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

/** The song the playback service is playing, or the one it played last; see [PlayingSongHolder]. */
val Context.playingSong: StateFlow<PlayingSong?> get() = (applicationContext as GusleApp).playing.song

/** The songs the playback service couldn't play; see [UnplayableSongs]. */
val Context.unplayableSongs: UnplayableSongs get() = (applicationContext as GusleApp).unplayable

/** The app's one [ThemeChoice], from any of its contexts. */
val Context.themeChoice: ThemeChoice get() = (applicationContext as GusleApp).themeChoice
