package com.sharkula.gusle.ui.songs

import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionTitleTest {
    @Test
    fun keepsATitleThatFits() {
        assertEquals("Bismarck", questionTitle("Bismarck"))
        val forty = "a".repeat(40)
        assertEquals(forty, questionTitle(forty))
    }

    @Test
    fun cutsALongTitleWithAnEllipsis() {
        assertEquals(
            "Chamillionaire - Hip Hop Police (Main)…",
            questionTitle("Chamillionaire - Hip Hop Police (Main) - Official Video"),
        )
        // No space before the ellipsis.
        assertEquals("The Last Stand (Live at Sabaton Open…", questionTitle("The Last Stand (Live at Sabaton Open    Air) 2024"))
    }

    @Test
    fun neverCutsACharacterInHalf() {
        val a38 = "a".repeat(38)
        // An emoji is two chars; the cut would fall between them.
        assertEquals("$a38…", questionTitle(a38 + "\uD83C\uDFB5" + "and more"))
        // A family is one emoji of five code points joined by ZWJs; a flag is two regional letters.
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        assertEquals("${"a".repeat(36)}…", questionTitle("a".repeat(36) + family + " song"))
        val flag = "\uD83C\uDDF7\uD83C\uDDF8"
        assertEquals("${"a".repeat(37)}…", questionTitle("a".repeat(37) + flag + " song"))
        // "e" and a combining acute accent, as one letter.
        assertEquals("$a38…", questionTitle(a38 + "e\u0301" + "tude"))
    }
}
