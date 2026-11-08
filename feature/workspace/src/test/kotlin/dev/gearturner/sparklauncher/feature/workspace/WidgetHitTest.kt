package dev.gearturner.sparklauncher.feature.workspace

import androidx.compose.ui.geometry.Offset
import dev.gearturner.sparklauncher.core.model.CellPlacement
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** half cells of 50 by 60 pixels, with one widget covering half cells x 2..5 and y 4..7 */
class WidgetHitTest {

    private val widget = CellPlacement(page = 0, cellX = 2, cellY = 4, spanX = 4, spanY = 3)

    private fun hit(x: Float, y: Float, vararg placements: CellPlacement) =
        isOnWidget(Offset(x, y), placements.toList(), cellWidthPx = 50f, cellHeightPx = 60f)

    @Test
    fun pointInsideTheWidgetCounts() {
        assertTrue(hit(150f, 300f, widget))
    }

    @Test
    fun nearEdgesAreInsideButFarEdgesAreNot() {
        assertTrue(hit(100f, 240f, widget))
        assertFalse(hit(300f, 300f, widget))
        assertFalse(hit(150f, 420f, widget))
    }

    @Test
    fun pointOutsideTheWidgetDoesNotCount() {
        assertFalse(hit(99f, 300f, widget))
        assertFalse(hit(150f, 239f, widget))
    }

    @Test
    fun noWidgetsMeansNoHit() {
        assertFalse(hit(150f, 300f))
    }

    @Test
    fun anyOfSeveralWidgetsCounts() {
        val other = CellPlacement(page = 0, cellX = 0, cellY = 0, spanX = 2, spanY = 2)
        assertTrue(hit(10f, 10f, widget, other))
        assertTrue(hit(150f, 300f, widget, other))
        assertFalse(hit(10f, 300f, widget, other))
    }
}
