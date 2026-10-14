package com.sharkula.gusle.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SongsFault = PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
private const val OutputFault = PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED

class SessionPlayerTest {
    private val unplayable = UnplayableSongs(now = { 0L })

    /** Songs 10, 20, 30, 40 (A–Z), on [current]. */
    private fun player(current: Int, shuffleOrder: List<Int>? = null): Pair<SessionPlayer, FakePlayer> {
        val fake = FakePlayer(listOf(10L, 20L, 30L, 40L), shuffleOrder)
        fake.current = current
        return SessionPlayer(fake.player, unplayable, nameOf = { source -> "Named $source" }, Random(7)) to fake
    }

    @Test
    fun skipsToTheNextSongInPlayOrder() {
        val (player, fake) = player(current = 1)
        player.songFailed(SongsFault)
        // Prepared again, playing or not as it was: nothing tells it to play.
        assertEquals(listOf("seek 2", "prepare"), fake.done)
        assertEquals(setOf(20L), unplayable.songIds.value)
        assertEquals(UnplayableSongs.Failure(songsFault = true, title = "Song 20", atMs = 0), unplayable.lastFailure.value)
    }

    @Test
    fun followsTheShuffledOrder() {
        // Plays 30, 10, 40, 20.
        val (player, fake) = player(current = 0, shuffleOrder = listOf(2, 0, 3, 1))
        player.songFailed(SongsFault)
        assertEquals(listOf("seek 3", "prepare"), fake.done)
    }

    @Test
    fun skipsSongsAlreadyMarked() {
        unplayable.failed(30L, "Song 30")
        val (player, fake) = player(current = 1)
        player.songFailed(SongsFault)
        assertEquals(listOf("seek 3", "prepare"), fake.done)
    }

    @Test
    fun stopsAfterTheLastSong() {
        val (player, fake) = player(current = 3)
        player.songFailed(SongsFault)
        assertEquals(listOf("pause"), fake.done)
    }

    @Test
    fun stopsWithoutMarkingWhenItsNotTheSongsFault() {
        val (player, fake) = player(current = 1)
        player.songFailed(OutputFault)
        assertEquals(listOf("pause"), fake.done)
        assertTrue(unplayable.songIds.value.isEmpty())
        assertEquals(UnplayableSongs.Failure(songsFault = false, title = null, atMs = 0), unplayable.lastFailure.value)
    }

    @Test
    fun goesBackAfterPrevious() {
        val (player, fake) = player(current = 3)
        player.seekToPrevious()
        player.songFailed(SongsFault)
        assertEquals(listOf("seek 1", "prepare"), fake.done)
    }

    @Test
    fun keepsGoingBackPastMarkedSongs() {
        // On 40, Previous goes to 30, which fails; 20 is marked, so it goes back to 10, which fails too.
        unplayable.failed(20L, "Song 20")
        val (player, fake) = player(current = 3)
        player.seekToPrevious()
        player.songFailed(SongsFault)
        player.songFailed(SongsFault)
        // Nothing is left before 10: it stops there rather than going forward again.
        assertEquals(listOf("seek 0", "prepare", "pause"), fake.done)
    }

    @Test
    fun previousThatStartsTheSongAgainIsntGoingBack() {
        val (player, fake) = player(current = 2)
        fake.previousRestarts = true
        player.seekToPrevious()
        player.songFailed(SongsFault)
        assertEquals(listOf("seek 3", "prepare"), fake.done)
    }

    @Test
    fun goesForwardOnceTheSongPlayed() {
        val (player, fake) = player(current = 3)
        player.seekToPrevious()
        fake.reportState(Player.STATE_READY)
        player.songFailed(SongsFault)
        assertEquals(listOf("seek 3", "prepare"), fake.done)
    }

    @Test
    fun goesForwardAfterANewQueue() {
        // Previous while restored and paused, then a tap that starts a new queue on the same index.
        val (player, fake) = player(current = 3)
        player.seekToPrevious()
        player.setMediaItems(emptyList(), 2, 0)
        player.songFailed(SongsFault)
        assertEquals(listOf("seek 3", "prepare"), fake.done)
    }

    @Test
    fun goesForwardAfterJumpingToASong() {
        val (player, fake) = player(current = 3)
        player.seekToPrevious()
        player.seekToDefaultPosition(1)
        fake.done.clear()
        player.songFailed(SongsFault)
        assertEquals(listOf("seek 2", "prepare"), fake.done)
    }

    @Test
    fun keepsGoingBackAfterSeekingInTheSong() {
        // Previous while restored and paused, then a seek in that song, e.g. from the seek screen.
        val (player, fake) = player(current = 3)
        player.seekToPrevious()
        player.seekTo(2, 60_000)
        fake.done.clear()
        player.songFailed(SongsFault)
        assertEquals(listOf("seek 1", "prepare"), fake.done)
    }

    @Test
    fun goesForwardAfterSeekingInAnotherSong() {
        val (player, fake) = player(current = 3)
        player.seekToPrevious()
        player.seekTo(1, 60_000)
        fake.done.clear()
        player.songFailed(SongsFault)
        assertEquals(listOf("seek 2", "prepare"), fake.done)
    }

    @Test
    fun aSongThatPlaysIsNoLongerMarked() {
        unplayable.failed(20L, "Song 20")
        val (_, fake) = player(current = 1)
        fake.reportState(Player.STATE_READY)
        assertTrue(unplayable.songIds.value.isEmpty())
    }

    @Test
    fun stopsSkippingWhenReleased() {
        val (player, fake) = player(current = 1)
        player.release()
        assertFalse(fake.hasListeners)
    }

    @Test
    fun turningShuffleOnKeepsTheSongFirst() {
        val (player, fake) = player(current = 2)
        player.shuffleModeEnabled = true
        assertEquals(listOf("order", "shuffle on"), fake.done)
        assertEquals(2, fake.shuffleOrder.first())
        assertEquals(listOf(0, 1, 2, 3), fake.shuffleOrder.sorted())
    }

    @Test
    fun drawsANewOrderEachTimeShuffleGoesOn() {
        val (player, fake) = player(current = 0)
        val orders = List(5) {
            player.shuffleModeEnabled = true
            player.shuffleModeEnabled = false
            fake.shuffleOrder
        }
        assertTrue(orders.all { it.first() == 0 })
        assertNotEquals(1, orders.toSet().size)
    }

    @Test
    fun skipsInTheNewOrder() {
        val (player, fake) = player(current = 2)
        player.shuffleModeEnabled = true
        fake.done.clear()
        player.songFailed(SongsFault)
        assertEquals(listOf("seek ${fake.shuffleOrder[1]}", "prepare"), fake.done)
    }

    @Test
    fun keepsTheOrderWhenShuffleIsOnAlready() {
        val (player, fake) = player(current = 0, shuffleOrder = listOf(2, 0, 3, 1))
        player.shuffleModeEnabled = true
        assertEquals(listOf("shuffle on"), fake.done)
    }

    @Test
    fun turnsShuffleOffAsItIs() {
        val (player, fake) = player(current = 0, shuffleOrder = listOf(2, 0, 3, 1))
        player.shuffleModeEnabled = false
        assertEquals(listOf("shuffle off"), fake.done)
    }

    @Test
    fun namesTheQueueAfterWhereItsSongsCameFrom() {
        val (player, fake) = player(current = 0)
        val artist = QueueSource.Artist("sabaton")
        player.restore(fake.items, artist, current = 2, positionMs = 5_000, shuffleOrder = listOf(2, 0, 3, 1))
        assertEquals(artist, player.queueSource)
        assertEquals("Named $artist", fake.playlistTitle)
        assertEquals(listOf(2, 0, 3, 1), fake.shuffleOrder)
        // Renamed, e.g. after the songs are read anew, it's still the same artist's.
        player.nameQueue()
        assertEquals("Named $artist", fake.playlistTitle)
    }

    /** Only the screens set songs, and they note where they came from; songs that don't are All songs. */
    @Test
    fun songsThatDontSayWhereTheyCameFromAreAllSongs() {
        val (player, fake) = player(current = 0)
        player.restore(fake.items, QueueSource.Artist(null), current = 0, positionMs = 0, shuffleOrder = null)
        player.setMediaItems(fake.items, 1, 0)
        assertEquals(QueueSource.AllSongs, player.queueSource)
        assertEquals("Named ${QueueSource.AllSongs}", fake.playlistTitle)
    }

    @Test
    fun forgetsWhereTheSongsCameFromWithThem() {
        val (player, fake) = player(current = 0)
        player.restore(fake.items, QueueSource.Artist("sabaton"), current = 0, positionMs = 0, shuffleOrder = null)
        player.clearMediaItems()
        assertEquals(QueueSource.AllSongs, player.queueSource)
        assertEquals(null, fake.playlistTitle)
    }

    @Test
    fun walksThePlayOrder() {
        val timeline = TestTimeline(4, shuffleOrder = listOf(2, 0, 3, 1))
        assertEquals(listOf(0, 1, 2, 3), timeline.playOrder(shuffled = false))
        assertEquals(listOf(2, 0, 3, 1), timeline.playOrder(shuffled = true))
        assertEquals(emptyList<Int>(), TestTimeline(0, shuffleOrder = null).playOrder(shuffled = true))
    }
}

/**
 * An ExoPlayer with the songs [songIds], played in [shuffleOrder] while shuffled, as far as
 * [SessionPlayer] uses it. It records the seeks, prepares, pauses and shuffle changes that reach it
 * in [done], and the last shuffle order set in [shuffleOrder].
 */
private class FakePlayer(songIds: List<Long>, shuffleOrder: List<Int>?) : InvocationHandler {
    val items = songIds.map { id ->
        MediaItem.Builder()
            .setMediaId(id.toString())
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Song $id").build())
            .build()
    }
    private var timeline = TestTimeline(songIds.size, shuffleOrder)
    private var shuffled = shuffleOrder != null
    private val listeners = mutableListOf<Player.Listener>()
    private var cleared = false
    private var playlistMetadata = MediaMetadata.EMPTY

    var current = 0

    /** Whether Previous starts the song again, as it does a few seconds in. */
    var previousRestarts = false

    val done = mutableListOf<String>()
    var shuffleOrder = emptyList<Int>()
    val player = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java), this) as ExoPlayer
    val hasListeners get() = listeners.isNotEmpty()
    val playlistTitle get() = playlistMetadata.title?.toString()

    fun reportState(state: Int) = listeners.toList().forEach { it.onPlaybackStateChanged(state) }

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        val arg = { i: Int -> args!![i] }
        when (method.name) {
            "addListener" -> listeners += arg(0) as Player.Listener
            "removeListener" -> listeners -= arg(0) as Player.Listener
            "getCurrentMediaItemIndex" -> return current
            "getCurrentMediaItem" -> return items[current]
            "getMediaItemAt" -> return items[arg(0) as Int]
            "getMediaItemCount" -> return if (cleared) 0 else items.size
            "getCurrentTimeline" -> return timeline
            "getShuffleModeEnabled" -> return shuffled
            "setShuffleModeEnabled" -> {
                shuffled = arg(0) as Boolean
                done += if (shuffled) "shuffle on" else "shuffle off"
            }
            "setShuffleOrder" -> {
                val order = arg(0) as ShuffleOrder
                shuffleOrder = generateSequence(order.firstIndex.takeIf { it != C.INDEX_UNSET }) { i ->
                    order.getNextIndex(i).takeIf { it != C.INDEX_UNSET }
                }.toList()
                timeline = TestTimeline(items.size, shuffleOrder)
                done += "order"
            }
            "seekToPrevious", "seekToPreviousMediaItem" -> if (!previousRestarts) {
                current = timeline.getPreviousWindowIndex(current, Player.REPEAT_MODE_OFF, shuffled)
                    .takeIf { it != C.INDEX_UNSET } ?: current
            }
            "seekToDefaultPosition", "seekTo" -> {
                current = arg(0) as Int
                done += "seek $current"
            }
            "setMediaItems" -> current = arg(1) as Int
            "clearMediaItems" -> cleared = true
            "getPlaylistMetadata" -> return playlistMetadata
            "setPlaylistMetadata" -> playlistMetadata = arg(0) as MediaMetadata
            "prepare" -> done += "prepare"
            "pause" -> done += "pause"
            "release" -> Unit
            "hashCode" -> return System.identityHashCode(proxy)
            "equals" -> return proxy === arg(0)
            "toString" -> return "FakePlayer"
            else -> throw UnsupportedOperationException(method.name)
        }
        return null
    }
}

/** A timeline of [count] songs, played in [shuffleOrder] while shuffled. Only its play order works. */
private class TestTimeline(private val count: Int, shuffleOrder: List<Int>?) : Timeline() {
    private val shuffled = shuffleOrder ?: List(count) { it }

    private fun order(shuffleModeEnabled: Boolean) = if (shuffleModeEnabled) shuffled else List(count) { it }

    override fun getWindowCount() = count

    override fun getFirstWindowIndex(shuffleModeEnabled: Boolean) =
        order(shuffleModeEnabled).firstOrNull() ?: C.INDEX_UNSET

    override fun getLastWindowIndex(shuffleModeEnabled: Boolean) =
        order(shuffleModeEnabled).lastOrNull() ?: C.INDEX_UNSET

    override fun getNextWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int {
        val order = order(shuffleModeEnabled)
        return order.getOrNull(order.indexOf(windowIndex) + 1) ?: C.INDEX_UNSET
    }

    override fun getPreviousWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int {
        val order = order(shuffleModeEnabled)
        return order.getOrNull(order.indexOf(windowIndex) - 1) ?: C.INDEX_UNSET
    }

    override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window =
        throw UnsupportedOperationException()

    override fun getPeriodCount() = count

    override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period =
        throw UnsupportedOperationException()

    override fun getIndexOfPeriod(uid: Any) = C.INDEX_UNSET

    override fun getUidOfPeriod(periodIndex: Int): Any = periodIndex
}
