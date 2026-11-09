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
import dev.gearturner.sparklauncher.core.model.WidgetOptions
import dev.gearturner.sparklauncher.core.model.addMissingApps
import dev.gearturner.sparklauncher.core.model.reflowLayout
import dev.gearturner.sparklauncher.core.model.applyDrop
import dev.gearturner.sparklauncher.core.model.canResize
import dev.gearturner.sparklauncher.core.model.diffLayout
import dev.gearturner.sparklauncher.core.model.moveWithinFolder
import dev.gearturner.sparklauncher.core.model.insertIntoFolder
import dev.gearturner.sparklauncher.core.model.normalizeLayout
import dev.gearturner.sparklauncher.core.model.planDrop
import dev.gearturner.sparklauncher.core.model.ItemType
import dev.gearturner.sparklauncher.core.model.ShortcutKey
import dev.gearturner.sparklauncher.core.model.WorkspaceItem
import dev.gearturner.sparklauncher.core.model.findFirstFreePlacement
import dev.gearturner.sparklauncher.core.model.SUBGRID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val active: ActiveLayout,
) {
    private val dao: WorkspaceDao = database.workspaceDao()

    private val releasedWidgets = MutableSharedFlow<List<Int>>(extraBufferCapacity = 16)

    /** system widget ids whose home items were deleted; the widget host frees them so none leak */
    val releasedWidgetIds: SharedFlow<List<Int>> = releasedWidgets.asSharedFlow()

    // serializes read-then-write placement so two quick adds cannot claim the same cell
    private val placementLock = Mutex()

    /** the active mode's layout, switching live when the user changes home mode */
    @OptIn(ExperimentalCoroutinesApi::class)
    val items: Flow<List<WorkspaceItem>> = active.layout.flatMapLatest { layout ->
        dao.observe(layout.key).map { rows -> rows.mapNotNull { it.toModelOrNull() } }
    }

    /** shortcuts placed in any mode's layout, which is every one the system must keep pinned for home */
    val placedShortcutKeys: Flow<Set<ShortcutKey>> = dao.observeShortcutRows()
        .map { rows -> rows.mapNotNull { it.toModelOrNull()?.shortcutKey }.toSet() }

    suspend fun isEmpty(): Boolean = dao.count(active.current().key) == 0

    suspend fun addAppToDesktop(app: AppKey, columns: Int, rows: Int): AddResult = placementLock.withLock {
        val layout = active.current()
        val existing = loadAll(layout)
        if (existing.any { it.app == app && it.container != Container.Dock && it.type == ItemType.APP }) {
            return@withLock AddResult.AlreadyOnHome
        }
        val desktop = existing.filter { it.container == Container.Desktop }.map { it.placement }
        val placement = findFirstFreePlacement(columns, rows, desktop, SUBGRID, SUBGRID, SUBGRID) ?: return@withLock AddResult.NoSpace
        val item = WorkspaceItem(0, ItemType.APP, Container.Desktop, placement, app)
        val id = dao.insert(item.toEntity(layout.key))
        AddResult.Added(item.copy(id = id))
    }

    /** writes the starter layout in one transaction-like batch; callers check isEmpty first */
    suspend fun seedDock(apps: List<AppKey>) = placementLock.withLock {
        val layout = active.current().key
        database.withTransaction {
            apps.forEachIndexed { slot, app ->
                dao.insert(WorkspaceItem(0, ItemType.APP, Container.Dock, CellPlacement(0, slot, 0), app).toEntity(layout))
            }
        }
    }

    /**
     * removes an item, along with a folder's contents, then tidies the layout. returns everything
     * deleted so callers can release what those items held, such as pinned shortcuts.
     */
    suspend fun remove(itemId: Long): List<WorkspaceItem> = placementLock.withLock {
        database.withTransaction {
            val layout = active.current()
            val before = loadAll(layout)
            if (before.none { it.id == itemId }) return@withTransaction emptyList()
            val after = normalizeLayout(before.filter { it.id != itemId })
            writeDiff(before, after, layout)
        }
    }

    /** an uninstalled app's icons would otherwise block their cells forever while staying invisible */
    suspend fun removePackage(packageName: String, userSerial: Long): List<WorkspaceItem> = placementLock.withLock {
        database.withTransaction {
            // an uninstalled app is gone from every mode's layout, not just the visible one
            LayoutId.entries.flatMap { layout ->
                val before = loadAll(layout)
                val after = normalizeLayout(
                    before.filterNot { item -> item.app.let { it != null && it.packageName == packageName && it.userSerial == userSerial } },
                )
                writeDiff(before, after, layout)
            }
        }
    }

    /** removes a shortcut from every mode's layout; a folder left empty collapses like after any removal */
    suspend fun removeShortcut(key: ShortcutKey): List<WorkspaceItem> = placementLock.withLock {
        database.withTransaction {
            LayoutId.entries.flatMap { layout ->
                val before = loadAll(layout)
                if (before.none { it.shortcutKey == key }) return@flatMap emptyList()
                writeDiff(before, normalizeLayout(before.filterNot { it.shortcutKey == key }), layout)
            }
        }
    }

    /**
     * applies a drag and drop in one transaction, so a crash midway can never leave a folder
     * half built. the dragged item is re-read first in case it changed while the finger was down.
     */
    suspend fun drop(dragged: Dragged, target: DropTarget, columns: Int, rows: Int, dockSlots: Int): DropPlan =
        placementLock.withLock {
            database.withTransaction {
                val layout = active.current()
                val before = loadAll(layout)
                val current = when (dragged) {
                    is Dragged.Existing -> Dragged.Existing(
                        before.firstOrNull { it.id == dragged.item.id } ?: return@withTransaction DropPlan.Reject,
                    )
                    is Dragged.NewApp, is Dragged.NewWidget -> dragged
                }
                val plan = planDrop(before, current, target, columns, rows, dockSlots, SUBGRID)
                if (plan == DropPlan.NoChange || plan == DropPlan.Reject) return@withTransaction plan
                var working = before
                var folderId = 0L
                if (plan is DropPlan.CreateFolder) {
                    val folder = WorkspaceItem(0, ItemType.FOLDER, plan.occupant.container, plan.occupant.placement, app = null)
                    folderId = dao.insert(folder.toEntity(layout.key))
                    working = before + folder.copy(id = folderId)
                }
                writeDiff(working, normalizeLayout(applyDrop(working, current, plan, folderId)), layout)
                plan
            }
        }

    /**
     * drops an app straight into an open folder at [rank]. a new app from the drawer is inserted as
     * a row first, inside the same transaction, so it has an id to be ranked by.
     */
    suspend fun dropIntoFolder(dragged: Dragged, folderId: Long, rank: Int): Boolean = placementLock.withLock {
        database.withTransaction {
            val layout = active.current()
            var before = loadAll(layout)
            if (before.none { it.id == folderId && it.type == ItemType.FOLDER }) return@withTransaction false
            val itemId = when (dragged) {
                is Dragged.Existing -> before.firstOrNull { it.id == dragged.item.id }?.id ?: return@withTransaction false
                is Dragged.NewApp -> {
                    val end = before.count { it.container == Container.Folder(folderId) }
                    val row = WorkspaceItem(0, ItemType.APP, Container.Folder(folderId), CellPlacement(0, end, 0), dragged.key)
                    val id = dao.insert(row.toEntity(layout.key))
                    before = before + row.copy(id = id)
                    id
                }
                is Dragged.NewWidget -> return@withTransaction false
            }
            val after = insertIntoFolder(before, itemId, folderId, rank)
            if (after.firstOrNull { it.id == itemId }?.container != Container.Folder(folderId)) return@withTransaction false
            writeDiff(before, normalizeLayout(after), layout)
            true
        }
    }

    /** moves an app to a new position inside the folder it is already in */
    suspend fun reorderInFolder(itemId: Long, toRank: Int) = placementLock.withLock {
        database.withTransaction {
            val layout = active.current()
            val before = loadAll(layout)
            writeDiff(before, moveWithinFolder(before, itemId, toRank), layout)
        }
    }

    /** answers whether a drop would be accepted, without writing, so a widget is only bound when it fits */
    suspend fun previewDrop(dragged: Dragged, target: DropTarget, columns: Int, rows: Int, dockSlots: Int): DropPlan =
        placementLock.withLock { planDrop(loadAll(active.current()), dragged, target, columns, rows, dockSlots, SUBGRID) }

    /** returns false when the new size would leave the grid or cover another item; [placement] is in half cells */
    suspend fun resize(itemId: Long, placement: CellPlacement, columns: Int, rows: Int): Boolean = placementLock.withLock {
        database.withTransaction {
            val layout = active.current()
            val before = loadAll(layout)
            if (!canResize(before, itemId, placement, columns * SUBGRID, rows * SUBGRID)) return@withTransaction false
            val after = before.map { if (it.id == itemId) it.copy(placement = placement) else it }
            writeDiff(before, after, layout)
            true
        }
    }

    suspend fun updateWidgetOptions(itemId: Long, options: WidgetOptions) = placementLock.withLock {
        database.withTransaction {
            val layout = active.current()
            val before = loadAll(layout)
            val after = before.map { if (it.id == itemId) it.copy(widgetOptions = options.sanitized()) else it }
            writeDiff(before, after, layout)
        }
    }

    /** the first spot a block fits on any page, adding a page when all are full; spans are in half cells */
    suspend fun firstFreeDesktopSpot(spanX: Int, spanY: Int, columns: Int, rows: Int): CellPlacement? =
        placementLock.withLock {
            // overlap widgets float above the grid, so they never block a spot
            val taken = loadAll(active.current()).filter { it.container == Container.Desktop && !it.overlaps }.map { it.placement }
            findFirstFreePlacement(columns, rows, taken, spanX, spanY, SUBGRID)
        }

    /** every widget id the layout still uses, for freeing ids orphaned by a crash mid placement */
    suspend fun widgetIds(): Set<Int> = loadEverywhere().mapNotNull { it.appWidgetId }.toSet()

    /**
     * fits every layout into a new grid; called whenever grid or dock size changes so nothing
     * ends up outside the visible cells where it could never be seen or moved again
     */
    suspend fun reflow(columns: Int, rows: Int, dockSlots: Int) = placementLock.withLock {
        database.withTransaction {
            LayoutId.entries.forEach { layout ->
                val before = loadAll(layout)
                writeDiff(before, normalizeLayout(reflowLayout(before, columns, rows, dockSlots, SUBGRID)), layout)
            }
        }
    }

    /**
     * keeps the home only layout holding every app. the first time it is used it starts as a
     * copy of the drawer mode layout, so the user's arrangement carries over; after that each
     * mode's layout is edited on its own.
     */
    suspend fun syncHomeOnly(apps: List<AppKey>, columns: Int, rows: Int) = placementLock.withLock {
        database.withTransaction {
            if (dao.count(LayoutId.HOME.key) == 0) copyLayout(LayoutId.DRAWER, LayoutId.HOME)
            val before = loadAll(LayoutId.HOME)
            writeDiff(before, normalizeLayout(addMissingApps(before, apps, columns, rows, SUBGRID)), LayoutId.HOME)
        }
    }

    /** folders get new ids in the copy, so their contents are pointed at the copied folder */
    private suspend fun copyLayout(from: LayoutId, to: LayoutId) {
        val source = loadAll(from)
        val folderIds = HashMap<Long, Long>()
        source.filter { it.type == ItemType.FOLDER }.forEach { folder ->
            folderIds[folder.id] = dao.insert(folder.copy(id = 0).toEntity(to.key))
        }
        source.filter { it.type != ItemType.FOLDER }.forEach { item ->
            val container = item.container
            val copied = if (container is Container.Folder) {
                val newFolder = folderIds[container.folderId] ?: return@forEach
                item.copy(id = 0, container = Container.Folder(newFolder))
            } else {
                item.copy(id = 0)
            }
            dao.insert(copied.toEntity(to.key))
        }
    }

    private suspend fun loadAll(layout: LayoutId): List<WorkspaceItem> = dao.getAll(layout.key).mapNotNull { it.toModelOrNull() }

    private suspend fun loadEverywhere(): List<WorkspaceItem> = dao.getEverywhere().mapNotNull { it.toModelOrNull() }

    /** returns the deleted items */
    private suspend fun writeDiff(before: List<WorkspaceItem>, after: List<WorkspaceItem>, layout: LayoutId): List<WorkspaceItem> {
        val diff = diffLayout(before, after)
        diff.inserts.forEach { dao.insert(it.toEntity(layout.key)) }
        diff.updates.forEach { dao.update(it.toEntity(layout.key)) }
        diff.deletes.forEach { dao.delete(it) }
        val deleted = diff.deletes.toSet()
        val removed = before.filter { it.id in deleted }
        val removedWidgets = removed.mapNotNull { it.appWidgetId }
        if (removedWidgets.isNotEmpty()) {
            // a widget copied into the other mode's layout is still showing there, so its id is kept
            val stillUsed = loadEverywhere().mapNotNull { it.appWidgetId }.toSet()
            removedWidgets.filter { it !in stillUsed }.takeIf { it.isNotEmpty() }?.let { releasedWidgets.tryEmit(it) }
        }
        return removed
    }

    /** a blank title clears the rename so the app's name shows again */
    suspend fun rename(itemId: Long, title: String?) = placementLock.withLock {
        val row = dao.getById(itemId) ?: return@withLock
        dao.update(row.copy(customTitle = title?.trim()?.take(MAX_TITLE_LENGTH)?.ifEmpty { null }))
    }

    /** the label is stored as the title so the shortcut stays readable while its app is unavailable */
    suspend fun addShortcutToDesktop(shortcut: ShortcutKey, owner: AppKey, label: String?, columns: Int, rows: Int): AddResult =
        placementLock.withLock {
            val layout = active.current()
            val existing = loadAll(layout)
            if (existing.any { it.shortcutKey == shortcut }) return@withLock AddResult.AlreadyOnHome
            val desktop = existing.filter { it.container == Container.Desktop }.map { it.placement }
            val placement = findFirstFreePlacement(columns, rows, desktop, SUBGRID, SUBGRID, SUBGRID) ?: return@withLock AddResult.NoSpace
            val item = WorkspaceItem(
                id = 0,
                type = ItemType.SHORTCUT,
                container = Container.Desktop,
                placement = placement,
                app = owner,
                customTitle = label?.take(MAX_TITLE_LENGTH),
                shortcutId = shortcut.shortcutId,
            )
            AddResult.Added(item.copy(id = dao.insert(item.toEntity(layout.key))))
        }

    /** every shortcut id still placed for a package, which is the set the system should keep pinned */
    suspend fun pinnedShortcutIds(packageName: String, userSerial: Long): Set<String> =
        // shortcuts placed in either mode's layout must stay pinned
        loadEverywhere().mapNotNull { it.shortcutKey }
            .filter { it.packageName == packageName && it.userSerial == userSerial }
            .map { it.shortcutId }
            .toSet()

    companion object {
        const val MAX_TITLE_LENGTH = 48
    }
}
