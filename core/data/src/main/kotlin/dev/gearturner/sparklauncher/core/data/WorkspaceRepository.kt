package dev.gearturner.sparklauncher.core.data

import androidx.room.withTransaction
import dev.gearturner.sparklauncher.core.data.db.SparkDatabase
import dev.gearturner.sparklauncher.core.data.db.WorkspaceDao
import dev.gearturner.sparklauncher.core.data.db.toEntity
import dev.gearturner.sparklauncher.core.data.db.toModelOrNull
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.CellPlacement
import dev.gearturner.sparklauncher.core.model.Container
import dev.gearturner.sparklauncher.core.model.DropPlan
import dev.gearturner.sparklauncher.core.model.DropTarget
import dev.gearturner.sparklauncher.core.model.Dragged
import dev.gearturner.sparklauncher.core.model.applyDrop
import dev.gearturner.sparklauncher.core.model.diffLayout
import dev.gearturner.sparklauncher.core.model.normalizeLayout
import dev.gearturner.sparklauncher.core.model.planDrop
import dev.gearturner.sparklauncher.core.model.ItemType
import dev.gearturner.sparklauncher.core.model.ShortcutKey
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
    private val database: SparkDatabase,
) {
    private val dao: WorkspaceDao = database.workspaceDao()

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

    /**
     * removes an item, along with a folder's contents, then tidies the layout. returns everything
     * deleted so callers can release what those items held, such as pinned shortcuts.
     */
    suspend fun remove(itemId: Long): List<WorkspaceItem> = placementLock.withLock {
        database.withTransaction {
            val before = loadAll()
            if (before.none { it.id == itemId }) return@withTransaction emptyList()
            val after = normalizeLayout(before.filter { it.id != itemId })
            writeDiff(before, after)
        }
    }

    /** an uninstalled app's icons would otherwise block their cells forever while staying invisible */
    suspend fun removePackage(packageName: String, userSerial: Long): List<WorkspaceItem> = placementLock.withLock {
        database.withTransaction {
            val before = loadAll()
            val after = normalizeLayout(
                before.filterNot { item -> item.app.let { it != null && it.packageName == packageName && it.userSerial == userSerial } },
            )
            writeDiff(before, after)
        }
    }

    /**
     * applies a drag and drop in one transaction, so a crash midway can never leave a folder
     * half built. the dragged item is re-read first in case it changed while the finger was down.
     */
    suspend fun drop(dragged: Dragged, target: DropTarget, columns: Int, rows: Int, dockSlots: Int): DropPlan =
        placementLock.withLock {
            database.withTransaction {
                val before = loadAll()
                val current = when (dragged) {
                    is Dragged.Existing -> Dragged.Existing(
                        before.firstOrNull { it.id == dragged.item.id } ?: return@withTransaction DropPlan.Reject,
                    )
                    is Dragged.NewApp -> dragged
                }
                val plan = planDrop(before, current, target, columns, rows, dockSlots)
                if (plan == DropPlan.NoChange || plan == DropPlan.Reject) return@withTransaction plan
                var working = before
                var folderId = 0L
                if (plan is DropPlan.CreateFolder) {
                    val folder = WorkspaceItem(0, ItemType.FOLDER, plan.occupant.container, plan.occupant.placement, app = null)
                    folderId = dao.insert(folder.toEntity())
                    working = before + folder.copy(id = folderId)
                }
                writeDiff(working, normalizeLayout(applyDrop(working, current, plan, folderId)))
                plan
            }
        }

    private suspend fun loadAll(): List<WorkspaceItem> = dao.getAll().mapNotNull { it.toModelOrNull() }

    /** returns the deleted items */
    private suspend fun writeDiff(before: List<WorkspaceItem>, after: List<WorkspaceItem>): List<WorkspaceItem> {
        val diff = diffLayout(before, after)
        diff.inserts.forEach { dao.insert(it.toEntity()) }
        diff.updates.forEach { dao.update(it.toEntity()) }
        diff.deletes.forEach { dao.delete(it) }
        val deleted = diff.deletes.toSet()
        return before.filter { it.id in deleted }
    }

    /** a blank title clears the rename so the app's name shows again */
    suspend fun rename(itemId: Long, title: String?) = placementLock.withLock {
        val row = dao.getById(itemId) ?: return@withLock
        dao.update(row.copy(customTitle = title?.trim()?.take(MAX_TITLE_LENGTH)?.ifEmpty { null }))
    }

    /** the label is stored as the title so the shortcut stays readable while its app is unavailable */
    suspend fun addShortcutToDesktop(shortcut: ShortcutKey, owner: AppKey, label: String?, columns: Int, rows: Int): AddResult =
        placementLock.withLock {
            val existing = dao.getAll().mapNotNull { it.toModelOrNull() }
            if (existing.any { it.shortcutKey == shortcut }) return@withLock AddResult.AlreadyOnHome
            val desktop = existing.filter { it.container == Container.Desktop }.map { it.placement }
            val placement = findFirstFreePlacement(columns, rows, desktop) ?: return@withLock AddResult.NoSpace
            val item = WorkspaceItem(
                id = 0,
                type = ItemType.SHORTCUT,
                container = Container.Desktop,
                placement = placement,
                app = owner,
                customTitle = label?.take(MAX_TITLE_LENGTH),
                shortcutId = shortcut.shortcutId,
            )
            AddResult.Added(item.copy(id = dao.insert(item.toEntity())))
        }

    /** every shortcut id still placed for a package, which is the set the system should keep pinned */
    suspend fun pinnedShortcutIds(packageName: String, userSerial: Long): Set<String> =
        dao.getAll().mapNotNull { it.toModelOrNull()?.shortcutKey }
            .filter { it.packageName == packageName && it.userSerial == userSerial }
            .map { it.shortcutId }
            .toSet()

    companion object {
        const val MAX_TITLE_LENGTH = 48
    }
}
