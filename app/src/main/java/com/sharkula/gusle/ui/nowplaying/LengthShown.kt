package com.sharkula.gusle.ui.nowplaying

/**
 * What the small time at the bottom of the seek screen shows: the song's length ("4:47"), or how
 * much of it is left ("−1:34"). A tap on it shows the other, and the choice is kept
 * ([LengthShownChoice]), so the screen opens as it was left.
 */
enum class LengthShown(
    /** What the choice is saved as, so renaming or moving an entry doesn't change what was chosen. */
    val id: String,
) {
    Length("length"),
    TimeLeft("left");

    /** The other one: what a tap shows instead. */
    val other: LengthShown get() = if (this == Length) TimeLeft else Length

    companion object {
        /** Until a tap: the length, as the screen always showed. */
        val Default = Length

        /** The one saved as [id]; [Default] when there's none, or it's no longer one of them. */
        fun fromId(id: String?): LengthShown = entries.firstOrNull { it.id == id } ?: Default
    }
}
