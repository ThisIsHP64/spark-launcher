package dev.gearturner.sparklauncher.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewGeometryTest {

    @Test
    fun iconShrinksWithTheSketchRatio() {
        // a 100 wide sketch of a 400 dp screen shows the 56 dp base icon at a quarter of its size
        assertEquals(14f, previewIconDiameter(1f, 100f, 400f), 0.001f)
    }

    @Test
    fun diameterFollowsTheScale() {
        val small = previewIconDiameter(0.7f, 100f, 400f)
        val large = previewIconDiameter(1.3f, 100f, 400f)
        assertEquals(small * 1.3f / 0.7f, large, 0.001f)
        assertTrue(large > small)
    }

    @Test
    fun zeroScreenWidthDoesNotDivideByZero() {
        assertTrue(previewIconDiameter(1f, 100f, 0f).isFinite())
    }
}
