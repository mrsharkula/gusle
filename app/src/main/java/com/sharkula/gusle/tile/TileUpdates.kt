package com.sharkula.gusle.tile

import android.util.Log
import androidx.annotation.MainThread
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.sharkula.gusle.GusleApp
import com.sharkula.gusle.ui.covers.SongCover
import java.time.Instant
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private const val Tag = "TileUpdates"

/**
 * An update is asked for this long after the first change, with every change meanwhile: past a
 * song change's busy first seconds on the watch (see the playback service's notification updates,
 * which wait as long), and so skipping song after song asks once. The tile is rarely on screen
 * then; when it is, a tap on it shows the change at once, and the end of a song is the tile's own
 * refresh (see [NowPlayingTileService]).
 */
private const val UpdateDelayMs = 3_000L

/**
 * A cover the tile couldn't read is tried again this many times, an update ([UpdateDelayMs]) apart,
 * as the screens try theirs again a couple of times (see CoverColors).
 */
private const val CoverRetries = 2

/**
 * Asks the system for a new tile when what it shows changes: the song, whether it plays, where it
 * is after a jump, whether it's a favorite, the songs there are, or the app theme. It remembers how the tile looked when it
 * was last made ([TileLook], [TileRecord]), and asks only when it would look different now: not for
 * what a tap on the tile has shown already, but for whatever the player did after, e.g. moving on
 * to the next song once a jump reached the end. It asks once for every change within
 * [UpdateDelayMs], and never while Gusle's tile isn't on the watch. The system limits how often a
 * tile may be updated, so nothing ticks through here: the tile counts the time itself. The app
 * keeps one. Use it from the main thread.
 */
class TileUpdates(private val app: GusleApp) {
    private val record = TileRecord(app)
    private val scope = MainScope()
    private var update: Job? = null

    /**
     * The resources' version whose cover the watch got without its picture, as it couldn't be
     * read. Only while the tile is on that song: the watch keeps only the latest version's.
     */
    private var missingPicture: String? = null

    /** The cover last tried again ([coverUnread]), and how many times. */
    private var retriedCover: SongCover? = null
    private var coverTries = 0

    /** What the tile shows may have changed. */
    @MainThread
    fun changed() {
        if (update != null) return
        update = scope.launch {
            delay(UpdateDelayMs)
            update = null
            if (isOnWatch() && record.look()?.isLike(lookNow()) != true) {
                TileService.getUpdater(app).requestUpdate(NowPlayingTileService::class.java)
            }
        }
    }

    /** The tile with [tileId] is being made, and looks like [look]. */
    @MainThread
    fun shown(tileId: Int, look: TileLook) {
        scope.launch {
            record.added(tileId)
            record.shown(look)
        }
    }

    /** The tile with [tileId] has been added to the watch's tiles. */
    @MainThread
    fun added(tileId: Int) {
        scope.launch { record.added(tileId) }
    }

    /** The tile with [tileId] has been removed from the watch's tiles. */
    @MainThread
    fun removed(tileId: Int) {
        scope.launch { record.removed(tileId) }
    }

    /**
     * The tile couldn't read [cover]: it left it out, or, with [resourcesVersion], the watch has it
     * by that version without its picture. The tile is made again a little later, a couple of times
     * at most, to read it again; the watch only asks for versions new to it, so then the cover goes
     * by another one ([versioned]).
     */
    @MainThread
    fun coverUnread(cover: SongCover, resourcesVersion: String? = null) {
        if (resourcesVersion != null) missingPicture = resourcesVersion
        if (cover != retriedCover) {
            retriedCover = cover
            coverTries = 0
        }
        if (coverTries++ < CoverRetries) changed()
    }

    /** [content], with its cover by another version if the watch has it without its picture. */
    @MainThread
    internal fun versioned(content: TileContent): TileContent =
        if (content.cover != null && content.resourcesVersion == missingPicture) content.copy(askAgain = true) else content

    /** How the tile would look if it were made now, as [NowPlayingTileService] makes it, its cover read. */
    private suspend fun lookNow(): TileLook {
        val content = TileContent.of(
            app.playing.state(),
            app.library.songsForTile(),
            app.favorites.load(),
            app.tileCoverSizePx(),
            Instant.now(),
        )
        return TileLook.of(versioned(content), app.themeChoice.load().id)
    }

    /**
     * Whether the watch has Gusle's tile. When that's never been recorded, the system is asked,
     * once; if it can't tell, it's taken to be there, as an update too many is better than one
     * missing.
     */
    private suspend fun isOnWatch(): Boolean {
        record.tileIds()?.let { return it.isNotEmpty() }
        val active = TileService.getActiveTilesAsync(app, Dispatchers.IO.asExecutor())
            .awaitOrNull { e -> Log.w(Tag, "Couldn't find Gusle's tiles", e) }
            ?: return true
        val ids = active
            .filter { it.componentName.className == NowPlayingTileService::class.java.name }
            .map { it.instanceId }
            .toSet()
        record.foundTiles(ids)
        return ids.isNotEmpty()
    }
}

/**
 * This future's result once it's done, or null when it failed, which [onFailure] hears of, or was
 * cancelled. Giving up the wait leaves the future alone.
 */
internal suspend fun <T : Any> ListenableFuture<T>.awaitOrNull(onFailure: (Throwable) -> Unit): T? =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                val result = try {
                    if (isCancelled) null else get()
                } catch (e: ExecutionException) {
                    onFailure(e.cause ?: e)
                    null
                }
                continuation.resume(result)
            },
            MoreExecutors.directExecutor(),
        )
    }
