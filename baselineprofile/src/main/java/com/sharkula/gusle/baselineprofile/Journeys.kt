package com.sharkula.gusle.baselineprofile

import android.graphics.Point
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.boundsInScreen
import androidx.test.uiautomator.children
import kotlin.math.abs

/** The app ID of the Gusle build under test, which this module's build file passes in. */
val targetAppId: String = requireNotNull(InstrumentationRegistry.getArguments().getString("targetAppId")) {
    "targetAppId wasn't passed as an instrumentation runner argument"
}

// Songs from scripts/push-test-songs.sh --many. A–Z, Mountain Song (Opus, wide cover) is followed
// by No Cover Here (Opus) and Northern Harbor (M4A, cover). The long title (M4A) is the first song
// of all, and scrolls on Now playing.
private const val FirstSong = "Mountain Song"
private val NextSongs = listOf("No Cover Here", "Northern Harbor")
private const val LongTitle = "A Very Long Song Title That Runs Well Past the Edge of the Round Screen"

// In Artists, Test Trio comes third, after Test Choir and Test Ensemble. Old Meadow (Opus, cover) is
// in the middle of their songs, A–Z.
private const val Artist = "Test Trio"
private const val ArtistsSong = "Old Meadow"

/** How long to wait for something to come on the screen. */
private const val WaitMs = 10_000L

/** How often to look again meanwhile. */
private const val PollMs = 100L

/** Enough to go through a whole list of the test songs one way, a little at a time. */
private const val MaxDrags = 40

/** Lets Gusle read the songs, so it opens on Home rather than on the permission screen. */
fun MacrobenchmarkScope.allowMusicAccess() {
    device.executeShellCommand("pm grant $packageName android.permission.READ_MEDIA_AUDIO")
}

/** Opens Gusle from the watch face, as from the launcher, and waits for Home's songs. */
fun MacrobenchmarkScope.openHome() {
    pressHome()
    startActivityAndWait()
    waitFor("All songs")
}

/**
 * Plays the first song of all and pauses it, so Gusle opens with it on top of Home, as it does for
 * someone coming back to it; freshly installed, it has none. Run before collecting a profile or
 * measuring, it's in neither.
 */
fun MacrobenchmarkScope.rememberFirstSong() {
    allowMusicAccess()
    openHome()
    // Home shows the song it came back to on top, paused; a test before may have left one.
    if (find("Paused") == null) {
        tap("All songs")
        tapFirstSong()
        tap("Pause")
        // Saved as it pauses.
        waitFor("Play")
        Thread.sleep(1_000)
    }
    pressHome()
}

/**
 * From Home, with no song remembered (e.g. its data cleared), plays the first song of all in All
 * songs, which opens Now playing on it: the screens someone listening has open, Home → All songs →
 * Now playing. A remembered song would open paused instead.
 */
fun MacrobenchmarkScope.playFirstSong() {
    tap("All songs")
    tapFirstSong()
    waitFor("Pause")
}

/** Taps the first song in All songs. */
private fun MacrobenchmarkScope.tapFirstSong() {
    waitFor("Shuffle all")
    // Its title comes right after Shuffle all.
    val shown = onScreen()
    tap(shown[shown.indexOfFirst { (label, _) -> label == "Shuffle all" } + 1].first)
}

/**
 * Every screen, from Home, as a person uses them: All songs scrolled and a song played, the next
 * songs, the Queue scrolled and a song played from it, back to Home with its playing song, an
 * artist's song played from Artists, Settings with About, Stop playing, and another theme. It ends
 * with nothing playing, so no music plays on between runs, and in walnut.
 */
fun MacrobenchmarkScope.useEveryScreen() {
    tap("All songs")
    waitFor("Shuffle all")
    // Down to the end of the list, a quick turn of the crown back up, and on to the song.
    repeat(3) { drag(down = true) }
    repeat(3) { fling(down = true) }
    turnCrown(-6)
    scrollToAndTap(FirstSong, down = false)

    // Now playing: the wavy ring, the cover and its colors, the volume arc.
    waitFor("Pause")
    waitFor(FirstSong)
    Thread.sleep(4_000)
    changeVolume()
    // Song changes: the next cover, its colors, the transitions.
    for (song in NextSongs) {
        tap("Next song")
        waitFor(song)
        Thread.sleep(2_500)
    }

    swipeLeft()
    // The Queue opens on the playing song, with the one played before it above.
    waitFor(NextSongs.first())
    repeat(2) { drag(down = true) }
    turnCrown(3)
    // Up past the songs played, dimmed, to the first.
    scrollToAndTap(LongTitle, down = false)
    Thread.sleep(2_000)
    pressBack()
    // Now playing again, with the long title scrolling.
    waitFor("Pause")
    Thread.sleep(3_000)

    pressBack()
    // All songs, still scrolled to where the song was tapped.
    waitFor(FirstSong)
    pressBack()
    // Home, with the playing song on top and its equalizer.
    waitFor(LongTitle)
    Thread.sleep(2_000)

    // Artists, with the playing song's artist marked, and an artist's songs, one of them played.
    scrollToAndTap("Artists", down = true)
    waitFor("Test Choir")
    scrollToAndTap(Artist, down = true)
    waitFor("Shuffle all")
    scrollToAndTap(ArtistsSong, down = true)
    waitFor("Pause")
    waitFor(ArtistsSong)
    Thread.sleep(2_500)
    // After each back, it waits for the screen that closes to be gone, by something only it shows,
    // and for the one below to be still: the artist's songs, with the one playing marked, then
    // Artists, then Home.
    pressBack()
    waitUntilGone("Next song")
    waitUntilStill(ArtistsSong)
    Thread.sleep(1_000)
    pressBack()
    waitUntilGone(ArtistsSong)
    waitUntilStill(Artist)
    pressBack()
    waitUntilGone("Test Trio feat. Test Choir")
    waitUntilStill("All songs")

    // Settings is the edge button at the end of Home.
    repeat(2) { drag(down = true) }
    tap("Settings")
    // Below Stop playing, Theme and Rescan music: at the end of the list, which can't bring it up to
    // the middle of the screen. Dragged there once the screen has slid in: a drag while it slid in
    // once didn't scroll it.
    waitUntilStill("Rescan music")
    drag(down = true)
    tap("About")
    waitFor("Plays the music stored on your watch.")
    pressBack()
    waitFor("Rescan music")

    // Paused and stopped, so a theme shows: the song's cover would color the screens.
    device.executeShellCommand("cmd media_session dispatch pause")
    scrollToAndTap("Stop playing", down = false)
    // Gusle closes as the song goes; it opens again on Home, in the theme.
    openHome()
    useThemes()
    Thread.sleep(1_000)
}

/**
 * From Home with nothing playing, chooses another theme in Settings → Theme, which recolors every
 * screen at once, and goes back through them in it; then chooses walnut again, so the next run
 * starts the same.
 */
private fun MacrobenchmarkScope.useThemes() {
    for (theme in listOf("Plum", "Walnut")) {
        repeat(2) { drag(down = true) }
        tap("Settings")
        tap("Theme")
        // Plum is further down the list, walnut at its top.
        scrollToAndTap(theme, down = true)
        // The Theme screen stays open, in the new colors.
        Thread.sleep(1_000)
        pressBack()
        // Settings, naming it.
        waitFor("Rescan music")
        waitFor(theme)
        pressBack()
        // Home, still at its end.
        waitFor("Playlists")
        Thread.sleep(1_000)
    }
}

// UI Automator gets no event when Gusle's lists scroll: its own scrolling waits for one that never
// comes, and the nodes it keeps go out of date, so its lookups miss rows and screens that came in
// since. The journeys read the screen afresh, and swipe straight up or down its middle. Gusle's own
// texts are in English on the emulator.

/**
 * Where a text, a description (e.g. an icon button's) or a state (e.g. the player's song's row's) is
 * on the screen now; null if it isn't.
 */
private fun MacrobenchmarkScope.find(label: String): Rect? {
    fun search(node: AccessibilityNodeInfo): Rect? {
        // False for a node that's gone, e.g. a row scrolled away.
        if (!node.refresh()) return null
        if (listOf(node.text, node.contentDescription, node.stateDescription).any { it?.toString() == label }) {
            return node.boundsInScreen()
        }
        return (0 until node.childCount).asSequence().mapNotNull(node::getChild).firstNotNullOfOrNull(::search)
    }
    return windowRoots().firstNotNullOfOrNull(::search)
}

/** All the texts and descriptions on the screen now, each with where it is. */
private fun MacrobenchmarkScope.onScreen(): List<Pair<String, Rect>> {
    val shown = mutableListOf<Pair<String, Rect>>()
    fun read(node: AccessibilityNodeInfo) {
        if (!node.refresh()) return
        node.text?.let { shown += it.toString() to node.boundsInScreen() }
        node.contentDescription?.let { shown += it.toString() to node.boundsInScreen() }
        node.children().forEach(::read)
    }
    windowRoots().forEach(::read)
    return shown
}

/** Waits for [label] to be on the screen, and returns where it is. */
private fun MacrobenchmarkScope.waitFor(label: String): Rect {
    val until = SystemClock.uptimeMillis() + WaitMs
    while (true) {
        find(label)?.let { return it }
        check(SystemClock.uptimeMillis() < until) {
            "\"$label\" isn't on the screen, which shows ${onScreen().map { (text, _) -> text }}"
        }
        Thread.sleep(PollMs)
    }
}

/** Taps [label] once it's still: it moves while its screen slides in. */
private fun MacrobenchmarkScope.tap(label: String) {
    tap(waitUntilStill(label))
}

/** Waits for [label] to be on the screen and still (its screen has slid in); returns where it is. */
private fun MacrobenchmarkScope.waitUntilStill(label: String): Rect {
    val until = SystemClock.uptimeMillis() + WaitMs
    var last = waitFor(label)
    while (true) {
        Thread.sleep(PollMs)
        val now = waitFor(label)
        if (now == last) return now
        check(SystemClock.uptimeMillis() < until) { "\"$label\" doesn't stop moving" }
        last = now
    }
}

/** Waits for [label] to leave the screen, e.g. as its screen closes. */
private fun MacrobenchmarkScope.waitUntilGone(label: String) {
    val until = SystemClock.uptimeMillis() + WaitMs
    while (find(label) != null) {
        check(SystemClock.uptimeMillis() < until) { "\"$label\" stays on the screen" }
        Thread.sleep(PollMs)
    }
}

private fun MacrobenchmarkScope.tap(bounds: Rect) {
    device.click(bounds.centerX(), bounds.centerY())
}

/**
 * Drags the list until the row with [title] is well on the screen, away from the round edges where
 * rows shrink, and taps it: towards the list's end first if [down], else towards its start, and
 * the other way once it goes no further.
 */
private fun MacrobenchmarkScope.scrollToAndTap(title: String, down: Boolean) {
    val height = device.displayHeight
    val middle = height / 4..height * 3 / 4
    for (towardsEnd in listOf(down, !down)) {
        var shown: List<Pair<String, Rect>>? = null
        var drags = 0
        while (drags++ < MaxDrags) {
            val now = onScreen()
            val row = now.firstOrNull { (label, _) -> label == title }?.second
            if (row != null) {
                if (row.centerY() in middle) {
                    tap(row)
                    return
                }
                // In sight near an edge: a little towards it.
                drag(down = row.centerY() > middle.last, fraction = 0.2f)
                continue
            }
            // Nothing moved: the list goes no further this way.
            if (now == shown) break
            shown = now
            // Less than a screen at a time, so no row is skipped.
            drag(towardsEnd, fraction = 0.35f)
        }
    }
    error("\"$title\" isn't in the list")
}

/**
 * Drags the list a [fraction] of the screen towards its end ([down]) or its start, and holds still
 * before letting go, so it stops there.
 */
private fun MacrobenchmarkScope.drag(down: Boolean, fraction: Float = 0.6f) {
    val (from, to) = verticalSwipe(down, fraction)
    device.swipe(arrayOf(from, to, to), 20)
    Thread.sleep(300)
}

/** Flings the list towards its end ([down]) or its start, and waits until it stops. */
private fun MacrobenchmarkScope.fling(down: Boolean) {
    val (from, to) = verticalSwipe(down, 0.6f)
    device.swipe(from.x, from.y, to.x, to.y, 4)
    Thread.sleep(2_000)
}

private fun MacrobenchmarkScope.verticalSwipe(down: Boolean, fraction: Float): Pair<Point, Point> {
    val x = device.displayWidth / 2
    val middle = device.displayHeight / 2
    val half = (device.displayHeight * fraction / 2).toInt()
    // The finger moves up to go down the list.
    val (fromY, toY) = if (down) middle + half to middle - half else middle - half to middle + half
    return Point(x, fromY) to Point(x, toY)
}

/** Swipes left across the middle of the screen: Now playing's way to the Queue. */
private fun MacrobenchmarkScope.swipeLeft() {
    val (width, height) = device.displayWidth to device.displayHeight
    device.swipe(width * 3 / 4, height / 2, width / 4, height / 2, 20)
}

/** Turns the crown [notches]: positive goes down a list and turns Now playing's volume up. */
private fun MacrobenchmarkScope.turnCrown(notches: Int) {
    val scroll = if (notches > 0) -1 else 1
    repeat(abs(notches)) { device.executeShellCommand("input rotaryencoder scroll --axis SCROLL,$scroll") }
    Thread.sleep(1_000)
}

/** Turns the volume up with the crown and down again, then leaves it as it was. */
private fun MacrobenchmarkScope.changeVolume() {
    val music = 3 // AudioManager.STREAM_MUSIC
    fun volume(): String {
        val said = device.executeShellCommand("cmd audio get-stream-volume $music")
        return checkNotNull(Regex("-> (\\d+)").find(said)) { "Can't read the volume: $said" }.groupValues[1]
    }
    val before = volume()
    turnCrown(1)
    turnCrown(-1)
    device.executeShellCommand("cmd audio set-volume $music $before")
    check(volume() == before) { "The volume wasn't set back to $before" }
}
