package com.sharkula.gusle.tile

import android.content.Context
import android.view.WindowManager
import androidx.media3.common.Player
import com.sharkula.gusle.data.FavoriteSongs
import com.sharkula.gusle.data.MusicLibrary
import com.sharkula.gusle.data.Songs
import com.sharkula.gusle.playback.PlayerState
import com.sharkula.gusle.playback.PlayingSongHolder
import com.sharkula.gusle.playback.knownDurationMs
import com.sharkula.gusle.playback.playsOn
import com.sharkula.gusle.playback.songId
import com.sharkula.gusle.ui.covers.SongCover
import com.sharkula.gusle.ui.nowplaying.JumpStep
import com.sharkula.gusle.ui.nowplaying.coverSizePx
import com.sharkula.gusle.ui.theme.AppTheme
import java.time.Instant
import kotlin.math.abs

/**
 * Two tiles of a playing song look the same while their times are this close (ms), e.g. apart by
 * the moment a song takes to start after a tap on the tile, while the tile's time counts on and the
 * player's doesn't.
 */
private const val ClockToleranceMs = 2_000L

/** Between the parts of [TileLook.still]: a character no title has. */
private const val Separator = "\u001F"

/** The state of this player, e.g. the tile's controller right after a tap; null when it has no song. */
internal fun Player.state(): PlayerState? =
    currentMediaItem?.songId?.let { songId ->
        PlayerState(songId, isPlaying = playsOn, positionMs = currentPosition, durationMs = knownDurationMs)
    }

/**
 * The state of the service's player, or of the song it comes back to, without starting the service:
 * what the tile shows unless a tap on it just changed it. Null when there's no song.
 */
internal suspend fun PlayingSongHolder.state(): PlayerState? {
    load()
    return now()
}

/**
 * The songs on the watch, read first unless a read has found some: one that failed or found none,
 * e.g. while the watch was still indexing them after a restart, is tried again, as the playback
 * service does.
 */
internal suspend fun MusicLibrary.songsForTile(): Songs {
    val now = songs.value
    if (now is Songs.Loaded && now.list.isNotEmpty()) return now
    // This read's result, not what's there before it ends.
    readIfIdle().join()
    return songs.value
}

/**
 * The size the tile reads covers at: Now playing's, from the same width, the whole screen's, which
 * on a watch is its window's.
 */
internal fun Context.tileCoverSizePx(): Int =
    coverSizePx(getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds.width())

/**
 * What the tile shows ([shows]), and the [cover] behind a song, read [coverSizePx] across; null for
 * none. [askAgain] when the watch has the cover without its picture, so it goes by another version.
 */
internal data class TileContent(
    val shows: TileShows,
    val cover: SongCover?,
    val coverSizePx: Int,
    val askAgain: Boolean = false,
) {
    /** The version of the resources it needs: the icons, and the cover, if any. */
    val resourcesVersion: String get() = TileResources.version(cover, coverSizePx, askAgain)

    /** This without the cover, e.g. while it can't be read: a song shows over black. */
    fun withoutCover(): TileContent =
        TileContent((shows as? TileShows.Song)?.copy(hasCover = false) ?: shows, cover = null, coverSizePx)

    companion object {
        /**
         * What the tile shows of [player] (null: no song) at [at], from [songs]: its song, with the
         * cover behind it and whether it's among the [favorites], or nothing to play when there's
         * none, or it's gone from the watch.
         */
        fun of(player: PlayerState?, songs: Songs, favorites: FavoriteSongs, coverSizePx: Int, at: Instant): TileContent {
            val loaded = songs as? Songs.Loaded
            val song = player?.songId?.let { loaded?.byId?.get(it) }
            if (player == null || song == null) {
                return TileContent(TileShows.Nothing(canShuffle = loaded?.list?.isNotEmpty() == true), null, coverSizePx)
            }
            val shows = TileShows.Song(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                isPlaying = player.isPlaying,
                positionMs = player.positionMs,
                durationMs = player.durationMsOf(song),
                at = at,
                // A song without a cover gets a black dot, which doesn't show (TileResources).
                hasCover = true,
                isFavorite = song in favorites,
            )
            return TileContent(shows, SongCover(song), coverSizePx)
        }
    }
}

/**
 * What the tile looks like, kept to tell whether a new one would look different ([TileUpdates]):
 * [still] is all that stands still on it, and while a song's time counts on by the watch's clock,
 * [countsFromMs] is when that song would have started, had it played straight through (ms since the
 * epoch), which the time counts from.
 */
data class TileLook(val still: String, val countsFromMs: Long?) {
    /** Whether a tile that looks like [other] looks the same as this one: its time, near enough. */
    fun isLike(other: TileLook): Boolean =
        still == other.still && if (countsFromMs == null || other.countsFromMs == null) {
            countsFromMs == other.countsFromMs
        } else {
            abs(countsFromMs - other.countsFromMs) <= ClockToleranceMs
        }

    companion object {
        /**
         * How the tile looks with [content] on it, in the colors of the cover or, without its own,
         * of the app [theme], a song's jump buttons by [jumpStep]. The cover's colors come with the
         * cover, so its resources' version stands for both.
         */
        internal fun of(content: TileContent, theme: AppTheme, jumpStep: JumpStep): TileLook {
            val shows = content.shows
            val parts = when (shows) {
                is TileShows.Nothing -> listOf("nothing", shows.canShuffle)
                is TileShows.Song -> listOf(
                    "song",
                    // Its heart's tap names it.
                    shows.songId,
                    shows.title,
                    shows.artist,
                    shows.hasCover,
                    shows.isPlaying,
                    shows.isFavorite,
                    shows.durationS,
                    // A paused song's time stands still; a playing one's counts.
                    if (shows.counts) "counts" else shows.positionS,
                    // Its jump buttons' icons.
                    jumpStep.id,
                )
            }
            val countsFrom = (shows as? TileShows.Song)?.takeIf { it.counts }?.startedAt?.toEpochMilli()
            return TileLook((parts + content.resourcesVersion + theme.id).joinToString(Separator), countsFrom)
        }
    }
}
