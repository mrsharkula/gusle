package com.sharkula.gusle.ui.covers

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
    private val note: Painter,
    private val background: Brush,
    noteColor: Color,
    private val noteSize: Dp,
) : Painter() {
    private val noteTint = ColorFilter.tint(noteColor)

    override val intrinsicSize get() = Size.Unspecified

    override fun DrawScope.onDraw() {
        drawRect(background)
        val notePx = noteSize.toPx()
        translate((size.width - notePx) / 2, (size.height - notePx) / 2) {
            with(note) { draw(Size(notePx, notePx), colorFilter = noteTint) }
        }
    }
}
