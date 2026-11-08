package dev.gearturner.sparklauncher.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.gearturner.sparklauncher.core.data.db.SparkDatabase
import dev.gearturner.sparklauncher.core.data.db.WorkspaceItemEntity
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.CellPlacement
import dev.gearturner.sparklauncher.core.model.Container
import dev.gearturner.sparklauncher.core.model.DropPlan
import dev.gearturner.sparklauncher.core.model.DropTarget
import dev.gearturner.sparklauncher.core.model.Dragged
import dev.gearturner.sparklauncher.core.model.ItemType
import dev.gearturner.sparklauncher.core.model.Span
import dev.gearturner.sparklauncher.core.model.WidgetOptions
import dev.gearturner.sparklauncher.core.model.ShortcutKey
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkspaceRepositoryTest {

    private lateinit var db: SparkDatabase
    private lateinit var repository: WorkspaceRepository

    private fun app(name: String) = AppKey("com.example.$name", "com.example.$name.Main", 0)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SparkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WorkspaceRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun addsAppsInReadingOrderAndSpillsToNewPage() = runTest {
        repeat(5) { repository.addAppToDesktop(app("a$it"), columns = 2, rows = 2) }
        val placements = repository.items.first().map { it.placement }
        assertEquals(
            listOf(
                CellPlacement(0, 0, 0), CellPlacement(0, 1, 0),
                CellPlacement(0, 0, 1), CellPlacement(0, 1, 1),
                CellPlacement(1, 0, 0),
            ),
            placements,
        )
    }

    @Test
    fun addingSameAppTwiceIsRejected() = runTest {
        repository.addAppToDesktop(app("a"), 4, 5)
        assertEquals(AddResult.AlreadyOnHome, repository.addAppToDesktop(app("a"), 4, 5))
    }

    @Test
    fun dockSeedUsesSequentialSlots() = runTest {
        repository.seedDock(listOf(app("phone"), app("sms"), app("web")))
        val dock = repository.items.first().filter { it.container == Container.Dock }
        assertEquals(listOf(0, 1, 2), dock.map { it.placement.cellX })
    }

    @Test
    fun corruptRowsAreSkippedInsteadOfCrashing() = runTest {
        db.workspaceDao().insert(
            WorkspaceItemEntity(type = "NOT_A_TYPE", container = "desktop", page = 0, cellX = 0, cellY = 0),
        )
        db.workspaceDao().insert(
            WorkspaceItemEntity(type = "APP", container = "desktop", page = 0, cellX = -4, cellY = 0),
        )
        repository.addAppToDesktop(app("ok"), 4, 5)
        val items = repository.items.first()
        assertEquals(1, items.size)
        assertTrue(items.single().app == app("ok"))
    }

    @Test
    fun removeDeletesTheRowAndReturnsIt() = runTest {
        val added = repository.addAppToDesktop(app("gone"), 4, 5) as AddResult.Added
        assertEquals(listOf(added.item.id), repository.remove(added.item.id).map { it.id })
        assertTrue(repository.isEmpty())
    }

    @Test
    fun renameTrimsAndBlankClears() = runTest {
        val added = repository.addAppToDesktop(app("a"), 4, 5) as AddResult.Added
        repository.rename(added.item.id, "  Notes ")
        assertEquals("Notes", repository.items.first().single().customTitle)
        repository.rename(added.item.id, " ")
        assertEquals(null, repository.items.first().single().customTitle)
    }

    @Test
    fun shortcutsAreTrackedAsThePinnedSetForTheirPackage() = runTest {
        val owner = app("maps")
        val home = ShortcutKey(owner.packageName, "home", 0)
        val work = ShortcutKey(owner.packageName, "work", 0)
        repository.addShortcutToDesktop(home, owner, "Home", 4, 5)
        repository.addShortcutToDesktop(work, owner, "Work", 4, 5)
        assertEquals(AddResult.AlreadyOnHome, repository.addShortcutToDesktop(home, owner, "Home", 4, 5))
        assertEquals(setOf("home", "work"), repository.pinnedShortcutIds(owner.packageName, 0))
        val shortcutItem = repository.items.first().first { it.shortcutKey == home }
        repository.remove(shortcutItem.id)
        assertEquals(setOf("work"), repository.pinnedShortcutIds(owner.packageName, 0))
    }

    @Test
    fun droppingAppOnAppBuildsAFolderAndDraggingOneOutDissolvesIt() = runTest {
        val a = (repository.addAppToDesktop(app("a"), 4, 5) as AddResult.Added).item
        val b = (repository.addAppToDesktop(app("b"), 4, 5) as AddResult.Added).item
        val plan = repository.drop(Dragged.Existing(a), DropTarget.Desktop(0, 1, 0), 4, 5, 4)
        assertTrue(plan is DropPlan.CreateFolder)
        val withFolder = repository.items.first()
        val folder = withFolder.single { it.type == ItemType.FOLDER }
        assertEquals(CellPlacement(0, 1, 0), folder.placement)
        assertEquals(2, withFolder.count { it.container == Container.Folder(folder.id) })

        val movedA = withFolder.single { it.id == a.id }
        repository.drop(Dragged.Existing(movedA), DropTarget.Desktop(0, 3, 4), 4, 5, 4)
        val after = repository.items.first().associateBy { it.id }
        assertEquals(2, after.size)
        assertEquals(CellPlacement(0, 1, 0), after.getValue(b.id).placement)
        assertEquals(Container.Desktop, after.getValue(b.id).container)
    }

    @Test
    fun drawerAppsCanBeDroppedOntoTheDock() = runTest {
        repository.drop(Dragged.NewApp(app("n")), DropTarget.Dock(2), 4, 5, 4)
        val item = repository.items.first().single()
        assertEquals(Container.Dock, item.container)
        assertEquals(2, item.placement.cellX)
    }

    @Test
    fun removingAPackageClearsItsIconsAndTidiesFolders() = runTest {
        val a = (repository.addAppToDesktop(app("a"), 4, 5) as AddResult.Added).item
        repository.addAppToDesktop(app("b"), 4, 5)
        repository.drop(Dragged.Existing(a), DropTarget.Desktop(0, 1, 0), 4, 5, 4)
        repository.removePackage("com.example.a", 0)
        val left = repository.items.first().single()
        assertEquals(app("b"), left.app)
        assertEquals(Container.Desktop, left.container)
    }

    @Test
    fun widgetsArePlacedResizedAndReleaseTheirIdWhenRemoved() = runTest {
        val provider = AppKey("com.example.clock", "com.example.clock.Widget", 0)
        val plan = repository.drop(Dragged.NewWidget(provider, 77, Span(2, 2)), DropTarget.Desktop(0, 0, 0), 4, 5, 4)
        assertTrue(plan is DropPlan.Place)
        val widget = repository.items.first().single()
        assertEquals(77, widget.appWidgetId)
        assertEquals(CellPlacement(0, 0, 0, 2, 2), widget.placement)

        assertTrue(repository.resize(widget.id, CellPlacement(0, 0, 0, 4, 2), 4, 5))
        assertFalse(repository.resize(widget.id, CellPlacement(0, 0, 0, 5, 2), 4, 5))

        repository.updateWidgetOptions(widget.id, WidgetOptions(padding = false, opacityPercent = 1))
        val stored = repository.items.first().single().widgetOptions
        assertFalse(stored.padding)
        assertEquals(WidgetOptions.MIN_OPACITY_PERCENT, stored.opacityPercent)

        val released = async { repository.releasedWidgetIds.first() }
        yield()
        repository.remove(widget.id)
        assertEquals(listOf(77), released.await())
    }
}
