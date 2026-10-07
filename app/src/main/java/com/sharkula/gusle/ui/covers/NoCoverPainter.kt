package com.sharkula.gusle.ui.covers

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp

/**
 * Stands in for a song's cover while it loads, or when the song has none: fills its bounds (the
 * image clips them, e.g. to a circle) with [background], and centers a [noteSize] music [note] in
 * them.
 */
class NoCoverPainter(
    note: Painter,
    background: Brush,
    noteColor: Color,
    private val noteSize: Dp,
) : Painter() {
    /**
     * What it draws can change, e.g. with the playing song's colors. It's read while drawing, so
     * it's drawn again also where it's shown already: Coil keeps the painter it showed a failed
     * load with, rather than taking a new one.
     */
    var note by mutableStateOf(note)
    var background by mutableStateOf(background)
    var noteColor by mutableStateOf(noteColor)
    private val noteTint by derivedStateOf { ColorFilter.tint(this.noteColor) }

    override val intrinsicSize get() = Size.Unspecified

    override fun DrawScope.onDraw() {
        drawRect(background)
        val notePx = noteSize.toPx()
        translate((size.width - notePx) / 2, (size.height - notePx) / 2) {
            with(note) { draw(Size(notePx, notePx), colorFilter = noteTint) }
        }
    }
}
