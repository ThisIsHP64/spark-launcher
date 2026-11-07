package dev.gearturner.sparklauncher.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridOccupancyTest {

    @Test
    fun emptyGridReturnsTopLeft() {
        assertEquals(0 to 0, GridOccupancy(4, 5).findFree())
    }

    @Test
    fun findFreeSkipsOccupiedCellsInReadingOrder() {
        val grid = GridOccupancy(3, 3).apply {
            mark(0, 0)
            mark(1, 0)
        }
        assertEquals(2 to 0, grid.findFree())
        grid.mark(2, 0)
        assertEquals(0 to 1, grid.findFree())
    }

    @Test
    fun spannedItemsBlockEveryCoveredCell() {
        val grid = GridOccupancy(4, 4).apply { mark(1, 1, 2, 2) }
        assertFalse(grid.isFree(2, 2))
        assertTrue(grid.isFree(3, 3))
        assertFalse(grid.isFree(0, 0, 2, 2))
        assertEquals(0 to 0, grid.findFree(2, 1))
        // a centered 2x2 block leaves no 2x2 hole anywhere in a 4x4 grid
        assertNull(grid.findFree(2, 2))
    }

    @Test
    fun spanLargerThanGridNeverFits() {
        assertNull(GridOccupancy(4, 4).findFree(5, 1))
        assertFalse(GridOccupancy(4, 4).isFree(3, 0, 2, 1))
    }

    @Test
    fun markingOutsideTheGridIsIgnored() {
        val grid = GridOccupancy(2, 2).apply { mark(1, 1, 3, 3) }
        assertFalse(grid.isFree(1, 1))
        assertTrue(grid.isFree(0, 0))
    }

    @Test
    fun firstFreePlacementFillsExistingPagesBeforeAddingOne() {
        val full = (0 until 2).flatMap { y -> (0 until 2).map { x -> CellPlacement(0, x, y) } }
        val gap = listOf(CellPlacement(1, 0, 0))
        assertEquals(CellPlacement(1, 1, 0), findFirstFreePlacement(2, 2, full + gap))
        val allFull = full + (0 until 2).flatMap { y -> (0 until 2).map { x -> CellPlacement(1, x, y) } }
        assertEquals(CellPlacement(2, 0, 0), findFirstFreePlacement(2, 2, allFull))
    }

    @Test
    fun firstFreePlacementOnEmptyWorkspaceIsPageZero() {
        assertEquals(CellPlacement(0, 0, 0), findFirstFreePlacement(4, 5, emptyList()))
    }

    @Test
    fun sanitizedClampsOutOfRangeSettings() {
        val wild = LauncherSettings(homeColumns = 40, homeRows = 1, iconScale = 9f, iconCornerRadiusPercent = -3)
        val clean = wild.sanitized()
        assertEquals(8, clean.homeColumns)
        assertEquals(3, clean.homeRows)
        assertEquals(1.3f, clean.iconScale)
        assertEquals(0, clean.iconCornerRadiusPercent)
    }
}
