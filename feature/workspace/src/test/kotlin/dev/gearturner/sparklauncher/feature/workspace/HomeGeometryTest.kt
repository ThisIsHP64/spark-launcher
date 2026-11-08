package dev.gearturner.sparklauncher.feature.workspace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.CellPlacement
import dev.gearturner.sparklauncher.core.model.DropTarget
import dev.gearturner.sparklauncher.core.ui.DragPayload
import org.junit.Assert.assertEquals
import org.junit.Test

/** a 4 by 5 grid of 100 pixel cells with no padding, so one half cell is 50 pixels */
class HomeGeometryTest {

    private fun geometry(subgrid: Boolean) = HomeGeometry().apply {
        pagerBounds = Rect(0f, 0f, 400f, 500f)
        columns = 4
        rows = 5
        this.subgrid = subgrid
    }

    private val app = DragPayload.NewApp(AppKey("com.example", "Main", 0), canUninstall = false)
    private fun widget(spanX: Int, spanY: Int) = DragPayload.Existing(1, null, false, spanX, spanY, isWidget = true)

    private fun target(g: HomeGeometry, finger: Offset, payload: DragPayload = app, topLeft: Offset = finger) =
        (g.resolve(finger, topLeft, 0, payload) as DropZone.Cell).target

    @Test
    fun withoutSubgridAnIconLandsInTheWholeCellUnderTheFinger() {
        assertEquals(DropTarget.Desktop(0, 2, 0), target(geometry(false), Offset(190f, 40f)))
    }

    @Test
    fun withSubgridAnIconCentersOnTheNearestHalfCell() {
        // a finger on the line between two cells puts the icon exactly between them
        assertEquals(DropTarget.Desktop(0, 1, 0), target(geometry(true), Offset(100f, 50f)))
        assertEquals(DropTarget.Desktop(0, 6, 8), target(geometry(true), Offset(399f, 499f)))
    }

    private fun withIconAt32(subgrid: Boolean) = geometry(subgrid).apply {
        iconAt = { _, x, y -> if (x in 3..4 && y in 2..3) CellPlacement(0, 3, 2, 2, 2) else null }
    }

    @Test
    fun anIconOverTheMiddleOfAnotherTargetsItsExactSpot() {
        assertEquals(DropTarget.Desktop(0, 3, 2), target(withIconAt32(true), Offset(200f, 150f)))
    }

    @Test
    fun theEdgeOfAnIconLeavesTheHalfSpotBesideItFree() {
        // a finger on the icon's right quarter centers the dragged icon half a cell to its right
        assertEquals(DropTarget.Desktop(0, 4, 2), target(withIconAt32(true), Offset(240f, 150f)))
    }

    @Test
    fun withoutSubgridTheWholeIconMerges() {
        assertEquals(DropTarget.Desktop(0, 3, 2), target(withIconAt32(false), Offset(240f, 120f)))
    }

    @Test
    fun widgetsSnapByWholeOrHalfCells() {
        val finger = Offset(200f, 200f)
        val topLeft = Offset(130f, 20f)
        assertEquals(DropTarget.Desktop(0, 2, 0), target(geometry(false), finger, widget(4, 2), topLeft))
        assertEquals(DropTarget.Desktop(0, 3, 0), target(geometry(true), finger, widget(4, 2), topLeft))
    }

    @Test
    fun aWidgetCannotBeSnappedOffTheGrid() {
        assertEquals(DropTarget.Desktop(0, 4, 0), target(geometry(false), Offset(390f, 10f), widget(3, 2), Offset(390f, 10f)))
        assertEquals(DropTarget.Desktop(0, 5, 0), target(geometry(true), Offset(390f, 10f), widget(3, 2), Offset(390f, 10f)))
    }

    @Test
    fun placementsAreDrawnInHalfCells() {
        assertEquals(Rect(50f, 100f, 150f, 200f), geometry(true).rectOf(CellPlacement(0, 1, 2, 2, 2)))
    }
}
