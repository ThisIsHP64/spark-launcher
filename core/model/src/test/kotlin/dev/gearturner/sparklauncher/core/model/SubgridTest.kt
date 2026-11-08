package dev.gearturner.sparklauncher.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** placement rules on the half cell grid that home stores, with a 4 by 5 cell grid */
class SubgridTest {

    private fun key(name: String) = AppKey("com.example.$name", "Main", 0)
    private fun app(id: Long, x: Int, y: Int, container: Container = Container.Desktop) =
        WorkspaceItem(id, ItemType.APP, container, CellPlacement(0, x, y, if (container == Container.Desktop) SUBGRID else 1, if (container == Container.Desktop) SUBGRID else 1), key("a$id"))
    private fun widget(id: Long, placement: CellPlacement) =
        WorkspaceItem(id, ItemType.WIDGET, Container.Desktop, placement, key("w$id"), appWidgetId = id.toInt())

    private fun plan(items: List<WorkspaceItem>, dragged: Dragged, target: DropTarget) =
        planDrop(items, dragged, target, columns = 4, rows = 5, dockSlots = 4, unit = SUBGRID)

    @Test
    fun anIconCanSitBetweenTwoCells() {
        val a = app(1, 0, 0)
        val result = plan(listOf(a), Dragged.Existing(a), DropTarget.Desktop(0, 3, 0))
        assertEquals(DropPlan.Place(Container.Desktop, CellPlacement(0, 3, 0, 2, 2)), result)
    }

    @Test
    fun anIconCannotHangOffTheLastHalfCell() {
        val a = app(1, 0, 0)
        assertEquals(DropPlan.Reject, plan(listOf(a), Dragged.Existing(a), DropTarget.Desktop(0, 7, 0)))
        assertEquals(DropPlan.Place(Container.Desktop, CellPlacement(0, 6, 8, 2, 2)), plan(listOf(a), Dragged.Existing(a), DropTarget.Desktop(0, 6, 8)))
    }

    @Test
    fun anIconExactlyOnAnotherMakesAFolder() {
        val a = app(1, 0, 0)
        val b = app(2, 3, 2)
        assertEquals(DropPlan.CreateFolder(b), plan(listOf(a, b), Dragged.Existing(a), DropTarget.Desktop(0, 3, 2)))
    }

    @Test
    fun anIconHalfOverAnotherIsRefused() {
        val a = app(1, 0, 0)
        val b = app(2, 2, 0)
        assertEquals(DropPlan.Reject, plan(listOf(a, b), Dragged.Existing(a), DropTarget.Desktop(0, 3, 0)))
    }

    @Test
    fun drawerAndDockIconsTakeAFullCellOnHome() {
        val docked = app(1, 2, 0, Container.Dock)
        assertEquals(DropPlan.Place(Container.Desktop, CellPlacement(0, 1, 1, 2, 2)), plan(listOf(docked), Dragged.Existing(docked), DropTarget.Desktop(0, 1, 1)))
        assertEquals(DropPlan.Place(Container.Desktop, CellPlacement(0, 5, 0, 2, 2)), plan(emptyList(), Dragged.NewApp(key("n")), DropTarget.Desktop(0, 5, 0)))
    }

    @Test
    fun homeIconsGoToTheDockButWidgetsDoNot() {
        val a = app(1, 3, 3)
        val w = widget(2, CellPlacement(0, 0, 0, 2, 2))
        assertEquals(DropPlan.Place(Container.Dock, CellPlacement(0, 1, 0)), plan(listOf(a, w), Dragged.Existing(a), DropTarget.Dock(1)))
        assertEquals(DropPlan.Reject, plan(listOf(a, w), Dragged.Existing(w), DropTarget.Dock(1)))
    }

    @Test
    fun aWidgetMovesByHalfCells() {
        val w = widget(1, CellPlacement(0, 0, 0, 5, 4))
        assertEquals(DropPlan.Place(Container.Desktop, CellPlacement(0, 1, 3, 5, 4)), plan(listOf(w), Dragged.Existing(w), DropTarget.Desktop(0, 1, 3)))
        assertEquals(DropPlan.Reject, plan(listOf(w), Dragged.Existing(w), DropTarget.Desktop(0, 4, 0)))
    }

    @Test
    fun automaticPlacementStaysOnWholeCells() {
        // an icon half a cell in leaves the first whole cell blocked, so the next app goes to the second
        val taken = listOf(CellPlacement(0, 1, 0, 2, 2))
        assertEquals(CellPlacement(0, 4, 0, 2, 2), findFirstFreePlacement(4, 5, taken, SUBGRID, SUBGRID, SUBGRID))
    }

    @Test
    fun automaticPlacementFallsBackToHalfCellsWhenOnlyThoseAreFree() {
        val grid = GridOccupancy(4, 2)
        grid.mark(0, 0, 1, 2)
        grid.mark(3, 0, 1, 2)
        assertEquals(1 to 0, grid.findFree(2, 2, SUBGRID))
    }

    @Test
    fun missingAppsAreAddedAsFullCells() {
        val added = addMissingApps(emptyList(), listOf(key("x"), key("y")), 4, 5, SUBGRID)
        assertEquals(listOf(CellPlacement(0, 0, 0, 2, 2), CellPlacement(0, 2, 0, 2, 2)), added.map { it.placement })
    }

    @Test
    fun reflowKeepsHalfOffsetsThatStillFitAndMovesTheRest() {
        val half = app(1, 3, 1)
        val far = app(2, 6, 0)
        val result = reflowLayout(listOf(half, far), 3, 5, dockSlots = 4, unit = SUBGRID)
        assertEquals(CellPlacement(0, 3, 1, 2, 2), result.first { it.id == 1L }.placement)
        val moved = result.first { it.id == 2L }.placement
        assertTrue(moved.cellX + moved.spanX <= 6)
        assertTrue(!moved.intersects(result.first { it.id == 1L }.placement))
    }

    @Test
    fun dockOverflowLandsOnHomeAsAFullCell() {
        val docked = app(1, 3, 0, Container.Dock)
        val result = reflowLayout(listOf(docked), 4, 5, dockSlots = 2, unit = SUBGRID)
        assertEquals(CellPlacement(0, 0, 0, 2, 2), result.single().placement)
    }
}
