package dev.gearturner.sparklauncher.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DropPlannerTest {

    private fun key(name: String) = AppKey("com.example.$name", "Main", 0)
    private fun app(id: Long, name: String, container: Container = Container.Desktop, placement: CellPlacement) =
        WorkspaceItem(id, ItemType.APP, container, placement, key(name))
    private fun folder(id: Long, placement: CellPlacement, container: Container = Container.Desktop) =
        WorkspaceItem(id, ItemType.FOLDER, container, placement, app = null)

    private fun plan(items: List<WorkspaceItem>, dragged: Dragged, target: DropTarget) =
        planDrop(items, dragged, target, columns = 4, rows = 5, dockSlots = 4)

    @Test
    fun emptyCellTakesTheItem() {
        val a = app(1, "a", placement = CellPlacement(0, 0, 0))
        val result = plan(listOf(a), Dragged.Existing(a), DropTarget.Desktop(0, 2, 3))
        assertEquals(DropPlan.Place(Container.Desktop, CellPlacement(0, 2, 3)), result)
    }

    @Test
    fun droppingInPlaceChangesNothing() {
        val a = app(1, "a", placement = CellPlacement(0, 1, 1))
        assertEquals(DropPlan.NoChange, plan(listOf(a), Dragged.Existing(a), DropTarget.Desktop(0, 1, 1)))
    }

    @Test
    fun appOnAppMakesAFolder() {
        val a = app(1, "a", placement = CellPlacement(0, 0, 0))
        val b = app(2, "b", placement = CellPlacement(0, 1, 0))
        assertEquals(DropPlan.CreateFolder(b), plan(listOf(a, b), Dragged.Existing(a), DropTarget.Desktop(0, 1, 0)))
    }

    @Test
    fun drawerAppOnFolderJoinsIt() {
        val f = folder(5, CellPlacement(0, 0, 0))
        val result = plan(listOf(f), Dragged.NewApp(key("n")), DropTarget.Desktop(0, 0, 0))
        assertEquals(DropPlan.AddToFolder(f), result)
    }

    @Test
    fun foldersDoNotNest() {
        val f = folder(5, CellPlacement(0, 0, 0))
        val g = folder(6, CellPlacement(0, 1, 0))
        assertEquals(DropPlan.Reject, plan(listOf(f, g), Dragged.Existing(f), DropTarget.Desktop(0, 1, 0)))
    }

    @Test
    fun outOfBoundsAndBadDockSlotsAreRejected() {
        val a = app(1, "a", placement = CellPlacement(0, 0, 0))
        assertEquals(DropPlan.Reject, plan(listOf(a), Dragged.Existing(a), DropTarget.Desktop(0, 4, 0)))
        assertEquals(DropPlan.Reject, plan(listOf(a), Dragged.Existing(a), DropTarget.Dock(4)))
    }

    @Test
    fun dockSlotsAreSeparateFromDesktopCells() {
        val docked = app(1, "a", Container.Dock, CellPlacement(0, 0, 0))
        val desk = app(2, "b", placement = CellPlacement(0, 0, 0))
        val result = plan(listOf(docked, desk), Dragged.Existing(desk), DropTarget.Dock(1))
        assertEquals(DropPlan.Place(Container.Dock, CellPlacement(0, 1, 0)), result)
    }

    @Test
    fun createFolderPutsBothAppsInsideAtTheOccupantsSpot() {
        val a = app(1, "a", placement = CellPlacement(0, 0, 0))
        val b = app(2, "b", placement = CellPlacement(0, 2, 2))
        val after = applyDrop(listOf(a, b), Dragged.Existing(a), DropPlan.CreateFolder(b), newFolderId = 9)
        val folder = after.single { it.id == 9L }
        assertEquals(CellPlacement(0, 2, 2), folder.placement)
        assertEquals(listOf(2L, 1L), after.filter { it.container == Container.Folder(9) }.sortedBy { it.placement.cellX }.map { it.id })
    }

    @Test
    fun normalizeTurnsOneAppFoldersBackIntoApps() {
        val f = folder(5, CellPlacement(0, 3, 4))
        val child = app(1, "a", Container.Folder(5), CellPlacement(0, 0, 0))
        val after = normalizeLayout(listOf(f, child))
        assertEquals(listOf(app(1, "a", placement = CellPlacement(0, 3, 4))), after)
    }

    @Test
    fun normalizeCompactsEmptyPagesAndFolderRanks() {
        val items = listOf(
            app(1, "a", placement = CellPlacement(0, 0, 0)),
            app(2, "b", placement = CellPlacement(2, 0, 0)),
            folder(5, CellPlacement(0, 1, 0)),
            app(3, "c", Container.Folder(5), CellPlacement(0, 4, 0)),
            app(4, "d", Container.Folder(5), CellPlacement(0, 7, 0)),
        )
        val after = normalizeLayout(items).associateBy { it.id }
        assertEquals(1, after.getValue(2).placement.page)
        assertEquals(0, after.getValue(3).placement.cellX)
        assertEquals(1, after.getValue(4).placement.cellX)
    }

    @Test
    fun orphanedFolderChildrenAreDropped() {
        val orphan = app(1, "a", Container.Folder(99), CellPlacement(0, 0, 0))
        assertTrue(normalizeLayout(listOf(orphan)).isEmpty())
    }

    @Test
    fun diffFindsInsertsUpdatesAndDeletes() {
        val a = app(1, "a", placement = CellPlacement(0, 0, 0))
        val b = app(2, "b", placement = CellPlacement(0, 1, 0))
        val moved = a.copy(placement = CellPlacement(0, 3, 3))
        val fresh = app(0, "n", placement = CellPlacement(0, 2, 2))
        val diff = diffLayout(listOf(a, b), listOf(moved, fresh))
        assertEquals(listOf(fresh), diff.inserts)
        assertEquals(listOf(moved), diff.updates)
        assertEquals(listOf(2L), diff.deletes)
    }
}
