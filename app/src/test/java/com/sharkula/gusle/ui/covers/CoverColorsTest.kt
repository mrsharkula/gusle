package com.sharkula.gusle.ui.covers

import com.materialkolor.hct.Hct
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CoverColorsTest {
    @Test
    fun mainColorIsTheColorfulOne() {
        // Mostly near-black, with a red third: the red is what the cover is about.
        val pixels = cover { i -> if (i % 3 == 0) 0x93291C else 0x101010 }
        val main = mainColor(pixels)
        assertNotNull(main)
        assertEquals(Hct.fromInt(0xFF93291C.toInt()).hue, Hct.fromInt(main!!).hue, 5.0)
    }

    @Test
    fun blackAndWhiteHasNoMainColor() {
        assertNull(mainColor(cover { i -> if (i % 2 == 0) 0x000000 else 0xE0E0E0 }))
    }

    /** 10,000 pixels of [rgb], each a little off in brightness, as in a real picture. */
    private fun cover(rgb: (Int) -> Int): IntArray {
        val random = Random(1)
        return IntArray(10_000) { i ->
            val base = rgb(i)
            val shift = random.nextInt(-6, 7)
            val channels = intArrayOf(base shr 16 and 0xFF, base shr 8 and 0xFF, base and 0xFF)
                .map { (it + shift).coerceIn(0, 255) }
            (0xFF shl 24) or (channels[0] shl 16) or (channels[1] shl 8) or channels[2]
        }
    }
}
