package dev.gearturner.sparklauncher.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutReflowTest {

    private fun key(n: Int) = AppKey("com.example.a$n", "Main", 0)
    private fun app(id: Long, placement: CellPlacement, container: Container = Container.Desktop) =
        WorkspaceItem(id, ItemType.APP, container, placement, key(id.toInt()))
    private fun widget(id: Long, placement: CellPlacement) =
        WorkspaceItem(id, ItemType.WIDGET, Container.Desktop, placement, key(id.toInt()), appWidgetId = id.toInt())

    private fun noOverlaps(items: List<WorkspaceItem>, columns: Int, rows: Int) {
        items.filter { it.container == Container.Desktop }.groupBy { it.placement.page }.forEach { (_, onPage) ->
            val grid = GridOccupancy(columns, rows)
            onPage.forEach { item ->
                val p = item.placement
                assertTrue("$item overlaps or leaves the grid", grid.isFree(p.cellX, p.cellY, p.spanX, p.spanY))
                grid.mark(p.cellX, p.cellY, p.spanX, p.spanY)
            }
        }
    }

    @Test
    fun itemsThatStillFitStayExactlyWhereTheyAre() {
        val items = listOf(app(1, CellPlacement(0, 0, 0)), app(2, CellPlacement(1, 2, 3)))
        assertEquals(items.toSet(), reflowLayout(items, 4, 5, 4).toSet())
    }

    @Test
    fun shrinkingTheGridMovesOverflowToFreeSpotsWithoutLosingAnything() {
        val items = (0 until 25).map { i -> app(i.toLong() + 1, CellPlacement(0, i % 5, i / 5)) }
        val after = reflowLayout(items, 4, 4, 4)
        assertEquals(25, after.size)
        noOverlaps(after, 4, 4)
        assertEquals(setOf(0, 1), after.map { it.placement.page }.toSet())
    }

    @Test
    fun displacedItemsPreferTheirOwnPageAndKeepReadingOrder() {
        val items = listOf(
            app(1, CellPlacement(0, 0, 0)),
            app(2, CellPlacement(0, 4, 0)),
            app(3, CellPlacement(0, 4, 1)),
        )
        val after = reflowLayout(items, 4, 5, 4).associateBy { it.id }
        assertEquals(CellPlacement(0, 1, 0), after.getValue(2).placement)
        assertEquals(CellPlacement(0, 2, 0), after.getValue(3).placement)
    }

    @Test
    fun widgetsShrinkToTheNewGrid() {
        val after = reflowLayout(listOf(widget(1, CellPlacement(0, 0, 0, 5, 2))), 4, 5, 4).single()
        assertEquals(CellPlacement(0, 0, 0, 4, 2), after.placement)
    }

    @Test
    fun dockItemsPastTheNewSlotCountMoveToHome() {
        val items = (0 until 5).map { app(it.toLong() + 1, CellPlacement(0, it, 0), Container.Dock) }
        val after = reflowLayout(items, 4, 5, 3)
        assertEquals(3, after.count { it.container == Container.Dock })
        assertEquals(2, after.count { it.container == Container.Desktop })
        noOverlaps(after, 4, 5)
    }

    @Test
    fun hidingTheDockMovesEverythingOutOfIt() {
        val items = (0 until 4).map { app(it.toLong() + 1, CellPlacement(0, it, 0), Container.Dock) }
        assertTrue(reflowLayout(items, 4, 5, 0).all { it.container == Container.Desktop })
    }

    @Test
    fun folderContentsAreUntouched() {
        val child = app(9, CellPlacement(0, 3, 0), Container.Folder(5))
        assertTrue(child in reflowLayout(listOf(child), 3, 3, 0))
    }

    @Test
    fun missingAppsFillFreeSpotsInOrderAndSkipPlacedOnes() {
        val items = listOf(app(1, CellPlacement(0, 0, 0)), app(2, CellPlacement(0, 1, 0), Container.Folder(7)))
        val after = addMissingApps(items, listOf(key(1), key(2), key(3), key(4)), 2, 2)
        val added = after.filter { it.id == 0L }
        assertEquals(listOf(key(3), key(4)), added.map { it.app })
        assertEquals(listOf(CellPlacement(0, 1, 0), CellPlacement(0, 0, 1)), added.map { it.placement })
    }

    @Test
    fun missingAppsSpillOntoNewPages() {
        val after = addMissingApps(emptyList(), (1..5).map(::key), 2, 2)
        assertEquals(listOf(0, 0, 0, 0, 1), after.map { it.placement.page })
    }

    @Test
    fun normalizingKeepsEveryUnsavedAppAddedInOneBatch() {
        val existing = listOf(app(1, CellPlacement(0, 0, 0)))
        val apps = (1..30).map { key(it) }
        val after = normalizeLayout(addMissingApps(existing, apps, 4, 5))
        assertEquals(30, after.size)
        assertEquals(apps.toSet(), after.mapNotNull { it.app }.toSet())
        noOverlaps(after, 4, 5)
    }
}
