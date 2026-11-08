package dev.gearturner.sparklauncher.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconAccentTest {

    private val gray = 0xFF808080.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val red = 0xFFE53935.toInt()
    private val blue = 0xFF1E88E5.toInt()
    private val clear = 0x00FF0000

    @Test
    fun theMostCommonVibrantHueWinsOverGrayAndWhite() {
        val pixels = IntArray(40) { gray } + IntArray(30) { white } + IntArray(10) { blue } + IntArray(4) { red }
        assertEquals(blue, accentOf(pixels))
    }

    @Test
    fun aGrayOrThemedIconHasNoAccent() {
        assertNull(accentOf(IntArray(50) { gray } + IntArray(50) { white }))
    }

    @Test
    fun transparentPixelsAreIgnored() {
        assertEquals(blue, accentOf(IntArray(90) { clear } + IntArray(3) { blue }))
    }
}
