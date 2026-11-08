package dev.gearturner.sparklauncher.feature.workspace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderLayoutTest {

    @Test
    fun columnsGrowWithTheFolder() {
        assertEquals(listOf(2, 2, 3, 3, 4, 4), listOf(1, 4, 5, 9, 10, 17).map(::folderColumns))
    }

    @Test
    fun rowsFitTheAppsAndCapAtFour() {
        assertEquals(1, folderRows(2))
        assertEquals(2, folderRows(4))
        assertEquals(3, folderRows(9))
        assertEquals(3, folderRows(12))
        assertEquals(4, folderRows(16))
        assertEquals(4, folderRows(40))
    }

    @Test
    fun pagesStartAfterSixteenApps() {
        assertEquals(1, folderPageCount(0))
        assertEquals(1, folderPageCount(16))
        assertEquals(2, folderPageCount(17))
        assertEquals(FolderSlot(1, 0, 0), slotOf(16, 4))
        assertEquals(FolderSlot(0, 2, 1), slotOf(5, 3))
    }

    @Test
    fun rankAtReadsTheCellUnderTheFinger() {
        // a 3 by 3 folder with 100 by 120 cells
        assertEquals(0, rankAt(Offset(10f, 10f), 100f, 120f, 3, 3, 0, 9))
        assertEquals(4, rankAt(Offset(150f, 130f), 100f, 120f, 3, 3, 0, 9))
        assertEquals(8, rankAt(Offset(299f, 359f), 100f, 120f, 3, 3, 0, 9))
    }

    @Test
    fun rankAtClampsToTheEdgesAndTheLastApp() {
        assertEquals(0, rankAt(Offset(-40f, -40f), 100f, 120f, 3, 3, 0, 9))
        assertEquals(8, rankAt(Offset(900f, 900f), 100f, 120f, 3, 3, 0, 9))
        // the empty tail of a partly filled row lands on the last app
        assertEquals(6, rankAt(Offset(250f, 250f), 100f, 120f, 3, 3, 0, 7))
        // on the second page ranks continue from sixteen
        assertEquals(17, rankAt(Offset(150f, 10f), 100f, 120f, 4, 4, 1, 20))
    }

    @Test
    fun displayOrderMovesTheDraggedAppForwardAndBack() {
        val ids = listOf(1L, 2L, 3L, 4L)
        assertEquals(listOf(2L, 3L, 1L, 4L), displayOrder(ids, { it }, 1L, 2))
        assertEquals(listOf(4L, 1L, 2L, 3L), displayOrder(ids, { it }, 4L, 0))
        assertEquals(ids, displayOrder(ids, { it }, null, 2))
        assertEquals(ids, displayOrder(ids, { it }, 9L, 0))
    }

    @Test
    fun anIncomingAppOpensAGapAtItsSpot() {
        assertEquals(listOf(0, 2, 3), listOf(0, 1, 2).map { rankWithGap(it, 1) })
        assertEquals(listOf(0, 1, 2), listOf(0, 1, 2).map { rankWithGap(it, 3) })
        assertEquals(listOf(0, 1, 2), listOf(0, 1, 2).map { rankWithGap(it, null) })
    }

    @Test
    fun pagesFlipOnlyBesideTheGridNotOverItsEdgeColumns() {
        val geometry = FolderGeometry().apply {
            panelBounds = Rect(0f, 0f, 440f, 600f)
            gridBounds = Rect(20f, 80f, 420f, 560f)
        }
        // over the first and last columns of the grid
        assertEquals(0, geometry.edgeDirection(Offset(25f, 200f), 24f))
        assertEquals(0, geometry.edgeDirection(Offset(415f, 200f), 24f))
        // in the panel margin and the slack just outside it
        assertEquals(-1, geometry.edgeDirection(Offset(10f, 200f), 24f))
        assertEquals(1, geometry.edgeDirection(Offset(455f, 200f), 24f))
        // past the slack the app has left the folder, so nothing flips
        assertEquals(0, geometry.edgeDirection(Offset(-60f, 200f), 24f))
    }
}
