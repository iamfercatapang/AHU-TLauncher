package com.ahu.tlauncher

import com.ahu.tlauncher.ui.home.BackgroundRemover
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BackgroundRemoverTest {
    private val white = 0xFFFFFFFF.toInt()
    private val dark = 0xFF202020.toInt()

    /** 7×7: white border area, dark ring (car outline), white interior (white car body). */
    private fun sample(): IntArray {
        val w = 7
        return IntArray(w * w) { i ->
            val x = i % w; val y = i / w
            if ((x == 1 || x == 5) && y in 1..5 || (y == 1 || y == 5) && x in 1..5) dark else white
        }
    }

    @Test fun removesOnlyBorderConnectedWhite() {
        val out = BackgroundRemover.process(sample(), 7, 7)
        assertEquals(0, out[0])                          // corner background -> transparent
        assertEquals(dark, out[1 * 7 + 1])               // outline kept
        assertEquals(white, out[3 * 7 + 3])              // white body inside outline kept
    }

    @Test fun alreadyTransparentImageUntouched() {
        val px = sample().also { it[0] = 0 }
        assertSame(px, BackgroundRemover.process(px, 7, 7))
    }

    @Test fun colouredBackgroundUntouched() {
        val px = IntArray(9) { 0xFF3366CC.toInt() }
        assertSame(px, BackgroundRemover.process(px, 3, 3))
    }
}
