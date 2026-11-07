package dev.gearturner.sparklauncher.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.gearturner.sparklauncher.core.data.db.SparkDatabase
import dev.gearturner.sparklauncher.core.data.db.WorkspaceItemEntity
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.CellPlacement
import dev.gearturner.sparklauncher.core.model.Container
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
        repository = WorkspaceRepository(db.workspaceDao())
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
    fun removeDeletesTheRow() = runTest {
        val added = repository.addAppToDesktop(app("gone"), 4, 5) as AddResult.Added
        repository.remove(added.item.id)
        assertTrue(repository.isEmpty())
    }
}
