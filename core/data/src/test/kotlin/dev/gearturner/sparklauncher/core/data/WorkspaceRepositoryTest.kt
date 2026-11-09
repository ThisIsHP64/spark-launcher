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
import dev.gearturner.sparklauncher.core.model.WorkspaceItem
import dev.gearturner.sparklauncher.core.model.ShortcutKey
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
    private val mode = MutableStateFlow(LayoutId.DRAWER)

    /** the mode switch, without a settings store behind it */
    private val activeLayout = object : ActiveLayout {
        override val layout = mode
        override suspend fun current() = mode.value
    }

    private fun app(name: String) = AppKey("com.example.$name", "com.example.$name.Main", 0)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SparkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WorkspaceRepository(db, activeLayout)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun addsAppsInReadingOrderAndSpillsToNewPage() = runTest {
        repeat(5) { repository.addAppToDesktop(app("a$it"), columns = 2, rows = 2) }
        val placements = repository.items.first().map { it.placement }
        assertEquals(
            listOf(
                CellPlacement(0, 0, 0, 2, 2), CellPlacement(0, 2, 0, 2, 2),
                CellPlacement(0, 0, 2, 2, 2), CellPlacement(0, 2, 2, 2, 2),
                CellPlacement(1, 0, 0, 2, 2),
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
    fun removingAShortcutClearsItFromEveryLayoutAndLeavesOthers() = runTest {
        val owner = app("maps")
        val home = ShortcutKey(owner.packageName, "home", 0)
        val work = ShortcutKey(owner.packageName, "work", 0)
        repository.addShortcutToDesktop(home, owner, "Home", 4, 5)
        repository.addShortcutToDesktop(work, owner, "Work", 4, 5)
        mode.value = LayoutId.HOME
        repository.addShortcutToDesktop(home, owner, "Home", 4, 5)
        assertEquals(setOf(home, work), repository.placedShortcutKeys.first())

        val removed = repository.removeShortcut(home)

        assertEquals(2, removed.size)
        assertEquals(setOf(work), repository.placedShortcutKeys.first())
        assertEquals(setOf("work"), repository.pinnedShortcutIds(owner.packageName, 0))
        assertTrue(repository.items.first().isEmpty())
        mode.value = LayoutId.DRAWER
        assertEquals(listOf(work), repository.items.first().mapNotNull { it.shortcutKey })
    }

    @Test
    fun removingAShortcutThatIsNotPlacedChangesNothing() = runTest {
        val owner = app("maps")
        repository.addShortcutToDesktop(ShortcutKey(owner.packageName, "home", 0), owner, "Home", 4, 5)
        assertTrue(repository.removeShortcut(ShortcutKey(owner.packageName, "other", 0)).isEmpty())
        assertEquals(1, repository.items.first().size)
    }

    @Test
    fun droppingAppOnAppBuildsAFolderAndDraggingOneOutDissolvesIt() = runTest {
        val a = (repository.addAppToDesktop(app("a"), 4, 5) as AddResult.Added).item
        val b = (repository.addAppToDesktop(app("b"), 4, 5) as AddResult.Added).item
        val plan = repository.drop(Dragged.Existing(a), DropTarget.Desktop(0, 2, 0), 4, 5, 4)
        assertTrue(plan is DropPlan.CreateFolder)
        val withFolder = repository.items.first()
        val folder = withFolder.single { it.type == ItemType.FOLDER }
        assertEquals(CellPlacement(0, 2, 0, 2, 2), folder.placement)
        assertEquals(2, withFolder.count { it.container == Container.Folder(folder.id) })

        val movedA = withFolder.single { it.id == a.id }
        repository.drop(Dragged.Existing(movedA), DropTarget.Desktop(0, 6, 8), 4, 5, 4)
        val after = repository.items.first().associateBy { it.id }
        assertEquals(2, after.size)
        assertEquals(CellPlacement(0, 2, 0, 2, 2), after.getValue(b.id).placement)
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
        repository.drop(Dragged.Existing(a), DropTarget.Desktop(0, 2, 0), 4, 5, 4)
        repository.removePackage("com.example.a", 0)
        val left = repository.items.first().single()
        assertEquals(app("b"), left.app)
        assertEquals(Container.Desktop, left.container)
    }

    @Test
    fun aHalfCellResizeIsStoredAsIs() = runTest {
        val provider = AppKey("com.example.clock", "com.example.clock.Widget", 0)
        repository.drop(Dragged.NewWidget(provider, 12, Span(4, 2)), DropTarget.Desktop(0, 1, 0), 4, 5, 4)
        val widget = repository.items.first().single()
        assertEquals(CellPlacement(0, 1, 0, 4, 2), widget.placement)
        assertTrue(repository.resize(widget.id, CellPlacement(0, 1, 0, 5, 3), 4, 5))
        assertEquals(CellPlacement(0, 1, 0, 5, 3), repository.items.first().single().placement)
    }

    @Test
    fun widgetsArePlacedResizedAndReleaseTheirIdWhenRemoved() = runTest {
        val provider = AppKey("com.example.clock", "com.example.clock.Widget", 0)
        val plan = repository.drop(Dragged.NewWidget(provider, 77, Span(4, 4)), DropTarget.Desktop(0, 0, 0), 4, 5, 4)
        assertTrue(plan is DropPlan.Place)
        val widget = repository.items.first().single()
        assertEquals(77, widget.appWidgetId)
        assertEquals(CellPlacement(0, 0, 0, 4, 4), widget.placement)

        assertTrue(repository.resize(widget.id, CellPlacement(0, 0, 0, 8, 4), 4, 5))
        assertFalse(repository.resize(widget.id, CellPlacement(0, 0, 0, 9, 4), 4, 5))

        repository.updateWidgetOptions(widget.id, WidgetOptions(padding = false, opacityPercent = 1))
        val stored = repository.items.first().single().widgetOptions
        assertFalse(stored.padding)
        assertEquals(WidgetOptions.MIN_OPACITY_PERCENT, stored.opacityPercent)

        val released = async { repository.releasedWidgetIds.first() }
        yield()
        repository.remove(widget.id)
        assertEquals(listOf(77), released.await())
    }

    @Test
    fun homeOnlyStartsAsACopyAndThenEachModeKeepsItsOwnLayout() = runTest {
        val a = (repository.addAppToDesktop(app("a"), 4, 5) as AddResult.Added).item
        repository.addAppToDesktop(app("b"), 4, 5)
        repository.drop(Dragged.Existing(a), DropTarget.Desktop(0, 2, 0), 4, 5, 4)

        repository.syncHomeOnly(listOf(app("a"), app("b"), app("c")), 4, 5)
        mode.value = LayoutId.HOME
        val home = repository.items.first()
        // the folder came along with both apps inside, and the missing app was added
        val folder = home.single { it.type == ItemType.FOLDER }
        assertEquals(2, home.count { it.container == Container.Folder(folder.id) })
        assertTrue(home.any { it.app == app("c") && it.container == Container.Desktop })

        repository.remove(home.single { it.app == app("c") }.id)
        mode.value = LayoutId.DRAWER
        val drawer = repository.items.first()
        assertEquals(3, drawer.size)
        assertTrue(drawer.none { it.app == app("c") })
    }

    @Test
    fun aWidgetSharedByBothLayoutsKeepsItsIdUntilBothRemoveIt() = runTest {
        val provider = AppKey("com.example.clock", "com.example.clock.Widget", 0)
        repository.drop(Dragged.NewWidget(provider, 55, Span(2, 1)), DropTarget.Desktop(0, 0, 0), 4, 5, 4)
        repository.syncHomeOnly(emptyList(), 4, 5)

        val released = mutableListOf<Int>()
        val collector = backgroundScope.launch { repository.releasedWidgetIds.collect { released += it } }
        yield()
        repository.remove(repository.items.first().single().id)
        yield()
        assertTrue(released.isEmpty())

        mode.value = LayoutId.HOME
        repository.remove(repository.items.first().single().id)
        yield()
        assertEquals(listOf(55), released)
        collector.cancel()
    }

    @Test
    fun reflowFitsEveryLayoutIntoASmallerGrid() = runTest {
        repeat(6) { repository.addAppToDesktop(app("r$it"), 3, 2) }
        repository.reflow(columns = 2, rows = 2, dockSlots = 4)
        val placements = repository.items.first().map { it.placement }
        assertTrue(placements.all { it.cellX + it.spanX <= 4 && it.cellY + it.spanY <= 4 })
        assertEquals(6, placements.distinct().size)
    }

    @Test
    fun reorderingInsideAFolderPersistsOnlyInTheActiveLayout() = runTest {
        val a = (repository.addAppToDesktop(app("a"), 4, 5) as AddResult.Added).item
        repository.addAppToDesktop(app("b"), 4, 5)
        repository.drop(Dragged.Existing(a), DropTarget.Desktop(0, 2, 0), 4, 5, 4)
        val c = (repository.addAppToDesktop(app("c"), 4, 5) as AddResult.Added).item
        val folder = repository.items.first().single { it.type == ItemType.FOLDER }
        repository.drop(Dragged.Existing(c), DropTarget.Desktop(0, 2, 0), 4, 5, 4)

        fun names(items: List<WorkspaceItem>) =
            items.filter { it.container == Container.Folder(folder.id) }.sortedBy { it.placement.cellX }.map { it.app!!.packageName }
        assertEquals(listOf("com.example.b", "com.example.a", "com.example.c"), names(repository.items.first()))

        repository.reorderInFolder(c.id, 0)
        assertEquals(listOf("com.example.c", "com.example.b", "com.example.a"), names(repository.items.first()))

        // the other mode's layout has no such folder and must stay empty
        mode.value = LayoutId.HOME
        assertTrue(repository.items.first().isEmpty())
    }

    @Test
    fun droppingIntoAFolderPlacesExistingAndNewAppsAtTheirSpot() = runTest {
        val a = (repository.addAppToDesktop(app("a"), 4, 5) as AddResult.Added).item
        repository.addAppToDesktop(app("b"), 4, 5)
        repository.drop(Dragged.Existing(a), DropTarget.Desktop(0, 2, 0), 4, 5, 4)
        val folder = repository.items.first().single { it.type == ItemType.FOLDER }
        val c = (repository.addAppToDesktop(app("c"), 4, 5) as AddResult.Added).item

        assertTrue(repository.dropIntoFolder(Dragged.Existing(c), folder.id, 0))
        assertTrue(repository.dropIntoFolder(Dragged.NewApp(app("d")), folder.id, 2))

        val inside = repository.items.first().filter { it.container == Container.Folder(folder.id) }.sortedBy { it.placement.cellX }
        assertEquals(listOf("c", "b", "d", "a").map { "com.example.$it" }, inside.map { it.app!!.packageName })
        assertEquals(listOf(0, 1, 2, 3), inside.map { it.placement.cellX })
    }

    @Test
    fun droppingIntoAMissingFolderIsRefused() = runTest {
        val a = (repository.addAppToDesktop(app("a"), 4, 5) as AddResult.Added).item
        assertFalse(repository.dropIntoFolder(Dragged.Existing(a), 999, 0))
    }
}
