package com.sharkula.gusle

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.sharkula.gusle.data.Favorites
import com.sharkula.gusle.data.MusicLibrary
import com.sharkula.gusle.data.Playlists
import com.sharkula.gusle.data.Songs
import com.sharkula.gusle.playback.LastPlayed
import com.sharkula.gusle.playback.PlayerPlace
import com.sharkula.gusle.playback.PlayingSong
import com.sharkula.gusle.playback.PlayingSongHolder
import com.sharkula.gusle.playback.UnplayableSongs
import com.sharkula.gusle.tile.TileUpdates
import com.sharkula.gusle.ui.components.Announcer
import com.sharkula.gusle.ui.covers.CoverColors
import com.sharkula.gusle.ui.covers.SongCoverFetcher
import com.sharkula.gusle.ui.covers.SongCoverKeyer
import com.sharkula.gusle.ui.songs.SongDeleter
import com.sharkula.gusle.ui.theme.ThemeChoice
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

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
     * The app theme chosen in Settings. Made, and read, as the screen or the tile first needs it: the
     * playback service starts the app alone too, with no screen to color.
     */
    val themeChoice by lazy { ThemeChoice(this, onChoose = tileUpdates::changed) }

    /** Asks for a new tile when what it shows changes. */
    val tileUpdates by lazy { TileUpdates(this) }

    /**
     * The favorite songs. Made, and read, as the screens or the tile first need them; from then on,
     * matched with the songs whenever they're read ([Favorites.matchWith]).
     */
    val favorites by lazy {
        Favorites(this, onChange = tileUpdates::changed).also { favorites ->
            MainScope().launch {
                favorites.load()
                library.songs.collect { songs -> if (songs is Songs.Loaded) favorites.matchWith(songs.byId) }
            }
        }
    }

    /**
     * The playlists. Read as the app starts, for Home's count and the queue's name; from then on,
     * matched with the songs whenever they're read ([Playlists.matchWith]).
     */
    val playlists by lazy {
        Playlists(this).also { playlists ->
            MainScope().launch {
                playlists.load()
                library.songs.collect { songs -> if (songs is Songs.Loaded) playlists.matchWith(songs.byId) }
            }
        }
    }

    /** What screen readers are told that no screen shows, e.g. that a song was deleted. */
    val announcer by lazy { Announcer() }

    /** What follows a song's deletion, which outlives the screens that asked for it. */
    val songDeleter by lazy { SongDeleter(this, library, playing, { favorites }, { playlists }, announcer) }

    /** The secret that lets the tile open Now playing. */
    val nowPlayingKey by lazy { NowPlayingKey(this) }

    override fun onCreate() {
        super.onCreate()
        // Starts reading what played last, so Home can show it as soon as the songs are read.
        playing = PlayingSongHolder(library, lastPlayed, onChange = { tileUpdates.changed() })
        // Read off the main thread meanwhile, so they're there when the songs are.
        playlists
        // A saved queue from a playlist goes by its name now, e.g. once it's renamed: from when it's
        // read, so it also takes a name given before, e.g. as Gusle was recreated during Rename.
        MainScope().launch {
            lastPlayed.load()
            playlists.all.collect(playing::playlistsChanged)
        }
        // The songs read anew may change the tile too, e.g. once there are some to shuffle.
        MainScope().launch { library.songs.drop(1).collect { tileUpdates.changed() } }
        // A song deleted while Gusle's process died under Android's screen: its answer never came.
        MainScope().launch { songDeleter.finishLeftover() }
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

/** Where the playback service's player is, e.g. which playlist entry; see [PlayingSongHolder.place]. */
val Context.playerPlace: StateFlow<PlayerPlace?> get() = (applicationContext as GusleApp).playing.place

/** The app's one [PlayingSongHolder], for where its song is and how long, from any of its contexts. */
val Context.playingSongHolder: PlayingSongHolder get() = (applicationContext as GusleApp).playing

/** The songs the playback service couldn't play; see [UnplayableSongs]. */
val Context.unplayableSongs: UnplayableSongs get() = (applicationContext as GusleApp).unplayable

/** The app's one [Favorites], from any of its contexts. */
val Context.favorites: Favorites get() = (applicationContext as GusleApp).favorites

/** The app's one [Playlists], from any of its contexts. */
val Context.playlists: Playlists get() = (applicationContext as GusleApp).playlists

/** The app's one [Announcer], from any of its contexts. */
val Context.announcer: Announcer get() = (applicationContext as GusleApp).announcer

/** The app's one [SongDeleter], from any of its contexts. */
val Context.songDeleter: SongDeleter get() = (applicationContext as GusleApp).songDeleter

/** The app's one [ThemeChoice], from any of its contexts. */
val Context.themeChoice: ThemeChoice get() = (applicationContext as GusleApp).themeChoice
