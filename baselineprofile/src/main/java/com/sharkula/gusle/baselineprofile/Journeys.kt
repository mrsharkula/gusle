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

// In Albums, Morning Songs comes after the two Greatest Hits and the three Gusle test songs (one per
// artist). Zither Interlude (M4A, wide cover) is its third song in track order, the first on its
// second disc.
private const val AlbumName = "Morning Songs"
private const val AlbumsSong = "Zither Interlude"

// Now playing's heart, and the button behind a swiped row, as screen readers name them.
private const val AddFavorite = "Add to favorites"
private const val RemoveFavorite = "Remove from favorites"

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
 * Every screen, from Home, as a person uses them: All songs scrolled, a song's actions opened by
 * holding its row, and a song played, made a favorite with Now playing's heart, seeking in it, the
 * next songs, the Queue scrolled and a song played from it, back to Home with its playing song, an
 * artist's song played from Artists and an album's from Albums, the favorite played from Favorites
 * and removed there with a swipe, Settings with About, Stop playing, another theme, another jump
 * step and another crown step. It ends with nothing playing and no favorites, so no music plays on
 * between runs, in walnut, with jumps of 10 s and crown steps of 4 s.
 */
fun MacrobenchmarkScope.useEveryScreen() {
    tap("All songs")
    waitFor("Shuffle all")
    // Down to the end of the list, a quick turn of the crown back up, and on to the song.
    repeat(3) { drag(down = true) }
    repeat(3) { fling(down = true) }
    turnCrown(-6)
    useSongActions(NextSongs.last())
    scrollToAndTap(FirstSong, down = false)

    // Now playing: the wavy ring, the cover and its colors, the volume arc, the heart.
    waitFor("Pause")
    waitFor(FirstSong)
    Thread.sleep(4_000)
    changeVolume()
    // A run that stopped half way may have left it a favorite.
    if (find(RemoveFavorite) != null) tap(RemoveFavorite)
    tap(AddFavorite)
    waitFor(RemoveFavorite)
    seek()
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

    // Albums, with the playing song's album marked, and an album's songs, one of them played; back
    // the same way.
    scrollToAndTap("Albums", down = true)
    waitFor("Greatest Hits")
    scrollToAndTap(AlbumName, down = true)
    waitFor("Shuffle all")
    scrollToAndTap(AlbumsSong, down = true)
    waitFor("Pause")
    waitFor(AlbumsSong)
    Thread.sleep(2_500)
    pressBack()
    waitUntilGone("Next song")
    waitUntilStill(AlbumsSong)
    Thread.sleep(1_000)
    pressBack()
    waitUntilGone(AlbumsSong)
    waitUntilStill(AlbumName)
    pressBack()
    waitUntilGone("Gusle test songs")
    waitUntilStill("All songs")

    useFavorites()

    // Settings is the edge button at the end of Home.
    repeat(2) { drag(down = true) }
    tap("Settings")
    // Below Stop playing, Theme, Jump by, Crown step and Rescan music: at the end of the list, which
    // can't bring it up to the middle of the screen (so not scrollToAndTap). Dragged there once the
    // screen has slid in, with Jump by its last row in sight: a drag while it slid in once didn't
    // scroll it.
    waitUntilStill("Jump by")
    repeat(2) { drag(down = true) }
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
    useJumpSteps()
    useCrownSteps()
    Thread.sleep(1_000)
}

/**
 * From Home, with the first song of all its only favorite: opens Favorites from Home's row near its
 * end, plays the song there, sees the Queue named after Favorites, and back in the list, swipes the
 * song's row open and removes it, which leaves the empty screen. Back to Home from there.
 */
private fun MacrobenchmarkScope.useFavorites() {
    scrollToAndTap("Favorites", down = true)
    waitFor("Shuffle all")
    scrollToAndTap(FirstSong, down = true)
    waitFor("Pause")
    waitFor(FirstSong)
    Thread.sleep(2_500)
    swipeLeft()
    // The Queue, under the name of where its songs came from.
    waitUntilStill("End of list")
    waitFor("Favorites")
    Thread.sleep(1_000)
    pressBack()
    waitUntilGone("End of list")
    waitFor("Pause")
    pressBack()
    waitUntilGone("Next song")
    waitUntilStill(FirstSong)
    // Open, then the button behind it.
    swipeRowLeft(FirstSong)
    tap(RemoveFavorite)
    waitFor("No favorites yet")
    Thread.sleep(1_000)
    pressBack()
    waitUntilGone("No favorites yet")
    // Home, scrolled to its row.
    waitUntilStill("Favorites")
}

/**
 * In a list of songs, holds [title]'s row, which opens the song's actions over its cover: adds it to
 * the favorites there and removes it again, so it isn't one after, and goes back to the list.
 */
private fun MacrobenchmarkScope.useSongActions(title: String) {
    hold(scrollTo(title, down = false))
    // Its actions, once they've slid in. Not Now playing, which a tap would have opened: its heart
    // says the same.
    val heart = waitForAny(AddFavorite, RemoveFavorite)
    waitUntilStill(heart)
    check(find("Next song") == null) { "Holding \"$title\" opened Now playing rather than its actions" }
    // A run that stopped half way may have left it a favorite.
    if (heart == RemoveFavorite) {
        tap(RemoveFavorite)
        waitFor(AddFavorite)
    }
    // The cover comes up once it's read.
    tap(AddFavorite)
    waitFor(RemoveFavorite)
    Thread.sleep(1_000)
    tap(RemoveFavorite)
    waitFor(AddFavorite)
    Thread.sleep(500)
    pressBack()
    waitUntilGone(AddFavorite)
    waitUntilStill(title)
}

/**
 * From Now playing, opens the seek screen by the time under the title, turns the crown on through
 * the song and back, which jumps each time it stops, jumps on with the + button, and goes back.
 */
private fun MacrobenchmarkScope.seek() {
    // Its label has the time in it, e.g. "4 seconds, seek".
    tap(checkNotNull(find { it.endsWith(", seek") }) { "No time under the title on ${onScreen().map { it.first }}" })
    // "Forward 10 seconds", unless a run that stopped half way left another step chosen.
    val forward = waitForAny("Forward 10 seconds", "Forward 5 seconds", "Forward 15 seconds")
    waitUntilStill(forward)
    // Once the hint has faded.
    Thread.sleep(3_000)
    turnCrown(4)
    turnCrown(-2)
    tap(forward)
    Thread.sleep(1_000)
    pressBack()
    waitUntilGone(forward)
    waitFor("Pause")
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
        // Settings, naming it: Jump by is a row of Settings the chooser doesn't name (Rescan music
        // is the fourth row now, below the screen).
        waitFor("Jump by")
        waitFor(theme)
        pressBack()
        // Home, still at its end.
        waitFor("Favorites")
        Thread.sleep(1_000)
    }
}

/**
 * From Home with nothing playing, chooses another step in Settings → Jump by, which the row then
 * names, and goes back through them; then 10 seconds again, so the next run starts the same.
 */
private fun MacrobenchmarkScope.useJumpSteps() {
    // Each step, and one the chooser names that Settings then doesn't.
    for ((step, other) in listOf("15 seconds" to "10 seconds", "10 seconds" to "15 seconds")) {
        repeat(2) { drag(down = true) }
        tap("Settings")
        tap("Jump by")
        scrollToAndTap(step, down = true)
        // The screen stays open, the step selected.
        Thread.sleep(1_000)
        pressBack()
        // Settings, naming it: once the chooser, which names every step, has slid out, and Crown
        // step, a row the chooser doesn't name, is there.
        waitUntilGone(other)
        waitFor("Crown step")
        waitFor(step)
        pressBack()
        // Home, still at its end.
        waitFor("Favorites")
        Thread.sleep(1_000)
    }
}

/**
 * From Home with nothing playing, chooses another step in Settings → Crown step, which the row
 * then names, and goes back through them; then 4 seconds again, so the next run starts the same.
 */
private fun MacrobenchmarkScope.useCrownSteps() {
    // Each step, and one the chooser names that Settings then doesn't.
    for ((step, other) in listOf("7 seconds" to "4 seconds", "4 seconds" to "7 seconds")) {
        repeat(2) { drag(down = true) }
        tap("Settings")
        // The third row, under Theme and Jump by: dragged up once the screen has slid in (a drag
        // while it slides in can be lost).
        waitUntilStill("Jump by")
        scrollToAndTap("Crown step", down = true)
        scrollToAndTap(step, down = true)
        // The screen stays open, the step selected.
        Thread.sleep(1_000)
        pressBack()
        // Settings, naming it: once the chooser, which names every step, has slid out, and Jump by,
        // a row the chooser doesn't name, is there.
        waitUntilGone(other)
        waitFor("Jump by")
        waitFor(step)
        pressBack()
        // Home, still at its end.
        waitFor("Favorites")
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
private fun MacrobenchmarkScope.find(label: String): Rect? = find { it == label }

/** Where the first text, description or state that [matches] is on the screen now; null if none is. */
private fun MacrobenchmarkScope.find(matches: (String) -> Boolean): Rect? {
    fun search(node: AccessibilityNodeInfo): Rect? {
        // False for a node that's gone, e.g. a row scrolled away.
        if (!node.refresh()) return null
        if (listOf(node.text, node.contentDescription, node.stateDescription).any { it != null && matches(it.toString()) }) {
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

/** Waits for one of [labels] to be on the screen, and returns it. */
private fun MacrobenchmarkScope.waitForAny(vararg labels: String): String {
    val until = SystemClock.uptimeMillis() + WaitMs
    while (true) {
        labels.firstOrNull { find(it) != null }?.let { return it }
        check(SystemClock.uptimeMillis() < until) {
            "None of ${labels.toList()} is on the screen, which shows ${onScreen().map { (text, _) -> text }}"
        }
        Thread.sleep(PollMs)
    }
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

/** Holds a finger still on [bounds] for 0.8 s, twice the watch's touch-and-hold delay. */
private fun MacrobenchmarkScope.hold(bounds: Rect) {
    // By the shell, which holds as long as it's told; UiDevice.swipe's steps take as long as the
    // device makes them, which can be shorter than a long press.
    val (x, y) = bounds.centerX() to bounds.centerY()
    device.executeShellCommand("input swipe $x $y $x $y 800")
}

/** Drags the list until the row with [title] is well on the screen ([scrollTo]), and taps it. */
private fun MacrobenchmarkScope.scrollToAndTap(title: String, down: Boolean) {
    tap(scrollTo(title, down))
}

/**
 * Drags the list until the row with [title] is well on the screen, away from the round edges where
 * rows shrink, and returns where it is: towards the list's end first if [down], else towards its
 * start, and the other way once it goes no further.
 */
private fun MacrobenchmarkScope.scrollTo(title: String, down: Boolean): Rect {
    val height = device.displayHeight
    val middle = height / 4..height * 3 / 4
    for (towardsEnd in listOf(down, !down)) {
        var shown: List<Pair<String, Rect>>? = null
        var drags = 0
        while (drags++ < MaxDrags) {
            val now = onScreen()
            val row = now.firstOrNull { (label, _) -> label == title }?.second
            if (row != null) {
                if (row.centerY() in middle) return row
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

/**
 * Swipes the row with [title] a quarter of the screen to the left and lets go, which opens it on the
 * button behind its end.
 */
private fun MacrobenchmarkScope.swipeRowLeft(title: String) {
    val row = waitUntilStill(title)
    val width = device.displayWidth
    device.swipe(width * 3 / 4, row.centerY(), width / 2, row.centerY(), 20)
    Thread.sleep(500)
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
