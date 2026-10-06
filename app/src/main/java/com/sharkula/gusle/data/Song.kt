package com.sharkula.gusle.data

import android.net.Uri

/** A song on the watch, as MediaStore knows it. */
data class Song(
    val id: Long,
    /** Plays the song; its cover is loaded from it too. */
    val uri: Uri,
    /** The title tag, or the file name without its extension. */
    val title: String,
    /** Null when the song has no artist tag. */
    val artist: String?,
    /** 0 when MediaStore doesn't know it. */
    val durationMs: Long,
    /** When the file last changed, in seconds: its cover is re-read after that. */
    val fileModified: Long,
)
