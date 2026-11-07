package dev.gearturner.sparklauncher.core.data

import dev.gearturner.sparklauncher.core.data.db.WorkspaceDao
import dev.gearturner.sparklauncher.core.data.db.toEntity
import dev.gearturner.sparklauncher.core.data.db.toModelOrNull
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.CellPlacement
import dev.gearturner.sparklauncher.core.model.Container
import dev.gearturner.sparklauncher.core.model.ItemType
import dev.gearturner.sparklauncher.core.model.WorkspaceItem
import dev.gearturner.sparklauncher.core.model.findFirstFreePlacement
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AddResult {
    data class Added(val item: WorkspaceItem) : AddResult
    data object AlreadyOnHome : AddResult
    data object NoSpace : AddResult
}

@Singleton
class WorkspaceRepository @Inject constructor(
    private val dao: WorkspaceDao,
) {
    // serializes read-then-write placement so two quick adds cannot claim the same cell
    private val placementLock = Mutex()

    val items: Flow<List<WorkspaceItem>> = dao.observeAll().map { rows -> rows.mapNotNull { it.toModelOrNull() } }

    suspend fun isEmpty(): Boolean = dao.count() == 0

    suspend fun addAppToDesktop(app: AppKey, columns: Int, rows: Int): AddResult = placementLock.withLock {
        val existing = dao.getAll().mapNotNull { it.toModelOrNull() }
        if (existing.any { it.app == app && it.container != Container.Dock && it.type == ItemType.APP }) {
            return@withLock AddResult.AlreadyOnHome
        }
        val desktop = existing.filter { it.container == Container.Desktop }.map { it.placement }
        val placement = findFirstFreePlacement(columns, rows, desktop) ?: return@withLock AddResult.NoSpace
        val item = WorkspaceItem(0, ItemType.APP, Container.Desktop, placement, app)
        val id = dao.insert(item.toEntity())
        AddResult.Added(item.copy(id = id))
    }

    /** writes the starter layout in one transaction-like batch; callers check isEmpty first */
    suspend fun seedDock(apps: List<AppKey>) = placementLock.withLock {
        val rows = apps.mapIndexed { slot, app ->
            WorkspaceItem(0, ItemType.APP, Container.Dock, CellPlacement(0, slot, 0), app).toEntity()
        }
        dao.insertAll(rows)
    }

    suspend fun remove(itemId: Long) = dao.delete(itemId)
}
