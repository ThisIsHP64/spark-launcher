package dev.gearturner.sparklauncher.core.model

/** where the user let go of a dragged item */
sealed interface DropTarget {
    data class Desktop(val page: Int, val cellX: Int, val cellY: Int) : DropTarget
    data class Dock(val slot: Int) : DropTarget
}

/** what is being dragged: something already placed, or an app pulled out of the drawer */
sealed interface Dragged {
    data class Existing(val item: WorkspaceItem) : Dragged
    data class NewApp(val key: AppKey) : Dragged

    /** a widget already bound to [appWidgetId], waiting for a home spot */
    data class NewWidget(val provider: AppKey, val appWidgetId: Int, val span: Span) : Dragged
}

sealed interface DropPlan {
    /** move the existing item, or insert the new one, at this spot */
    data class Place(val container: Container, val placement: CellPlacement) : DropPlan

    /** turn [occupant] into a folder holding it and the dragged item */
    data class CreateFolder(val occupant: WorkspaceItem) : DropPlan

    data class AddToFolder(val folder: WorkspaceItem) : DropPlan

    /** dropped back where it started, so nothing needs writing */
    data object NoChange : DropPlan

    /** the target cannot take the item; the ui snaps it back */
    data object Reject : DropPlan
}

/**
 * decides what a drop means, following the pixel launcher's rules: empty cells take the item,
 * app on app makes a folder, anything droppable on a folder joins it, and everything else is
 * refused rather than shoving other icons around. [columns] and [rows] are whole cells, while home
 * targets, placements and widget spans are in units of 1 / [unit] of a cell. an icon only merges
 * when it lands exactly on the other item's spot, so one dropped half over another is refused.
 */
fun planDrop(
    items: List<WorkspaceItem>,
    dragged: Dragged,
    target: DropTarget,
    columns: Int,
    rows: Int,
    dockSlots: Int,
    unit: Int = 1,
): DropPlan {
    val draggedItem = (dragged as? Dragged.Existing)?.item
    val newWidget = dragged as? Dragged.NewWidget
    // only a single icon can merge into a folder or sit in the dock; folders and widgets do not nest
    val draggedIsIcon = when (dragged) {
        is Dragged.Existing -> dragged.item.type == ItemType.APP || dragged.item.type == ItemType.SHORTCUT
        is Dragged.NewApp -> true
        is Dragged.NewWidget -> false
    }
    val draggedIsTile = draggedIsIcon || draggedItem?.type == ItemType.FOLDER
    // icons and folders always take one cell on home, whatever span they had in the dock or a folder
    val spanX = if (draggedIsTile) unit else draggedItem?.placement?.spanX ?: newWidget?.span?.x ?: unit
    val spanY = if (draggedIsTile) unit else draggedItem?.placement?.spanY ?: newWidget?.span?.y ?: unit
    val (container, placement) = when (target) {
        is DropTarget.Desktop -> {
            if (target.cellX < 0 || target.cellY < 0 || target.cellX + spanX > columns * unit || target.cellY + spanY > rows * unit) {
                return DropPlan.Reject
            }
            Container.Desktop to CellPlacement(target.page.coerceAtLeast(0), target.cellX, target.cellY, spanX, spanY)
        }
        is DropTarget.Dock -> {
            // the dock is a single row, so anything wider than one slot cannot live there
            if (target.slot !in 0 until dockSlots || !draggedIsTile) return DropPlan.Reject
            Container.Dock to CellPlacement(0, target.slot, 0)
        }
    }

    val others = items.filter { it.id != draggedItem?.id }
    // an overlap widget ignores everything under it, and nothing is blocked by one
    val occupants = if (draggedItem?.overlaps == true) {
        emptyList()
    } else {
        others.filter { it.container == container && !it.overlaps && it.covers(placement) }
    }
    if (occupants.isEmpty()) {
        if (draggedItem != null && draggedItem.container == container && draggedItem.placement == placement) {
            return DropPlan.NoChange
        }
        return DropPlan.Place(container, placement)
    }

    val occupant = occupants.singleOrNull() ?: return DropPlan.Reject
    if (!draggedIsIcon) return DropPlan.Reject
    if (occupant.placement.cellX != placement.cellX || occupant.placement.cellY != placement.cellY) return DropPlan.Reject
    return when (occupant.type) {
        ItemType.FOLDER -> {
            if (draggedItem?.container == Container.Folder(occupant.id)) DropPlan.NoChange else DropPlan.AddToFolder(occupant)
        }
        ItemType.APP, ItemType.SHORTCUT -> DropPlan.CreateFolder(occupant)
        ItemType.WIDGET -> DropPlan.Reject
    }
}

private fun WorkspaceItem.covers(other: CellPlacement): Boolean {
    if (container == Container.Desktop && placement.page != other.page) return false
    return placement.intersects(other)
}

/**
 * applies a plan to the layout and returns the full new layout. [newFolderId] must be the id of a
 * folder row already inserted for a [DropPlan.CreateFolder] plan, since folder children point at it.
 * new items carry id 0, which callers insert.
 */
fun applyDrop(
    items: List<WorkspaceItem>,
    dragged: Dragged,
    plan: DropPlan,
    newFolderId: Long = 0,
): List<WorkspaceItem> {
    fun draggedAs(container: Container, placement: CellPlacement): WorkspaceItem = when (dragged) {
        is Dragged.Existing -> dragged.item.copy(container = container, placement = placement)
        is Dragged.NewApp -> WorkspaceItem(0, ItemType.APP, container, placement, dragged.key)
        is Dragged.NewWidget -> WorkspaceItem(
            id = 0,
            type = ItemType.WIDGET,
            container = container,
            placement = placement,
            app = dragged.provider,
            appWidgetId = dragged.appWidgetId,
        )
    }
    val draggedId = (dragged as? Dragged.Existing)?.item?.id
    val rest = items.filter { it.id != draggedId }
    return when (plan) {
        DropPlan.NoChange, DropPlan.Reject -> items
        is DropPlan.Place -> rest + draggedAs(plan.container, plan.placement)
        is DropPlan.AddToFolder -> {
            val rank = rest.count { it.container == Container.Folder(plan.folder.id) }
            rest + draggedAs(Container.Folder(plan.folder.id), CellPlacement(0, rank, 0))
        }
        is DropPlan.CreateFolder -> {
            require(newFolderId > 0) { "a folder row must be inserted before its children are moved into it" }
            val folder = WorkspaceItem(newFolderId, ItemType.FOLDER, plan.occupant.container, plan.occupant.placement, app = null)
            val inside = Container.Folder(newFolderId)
            rest.filter { it.id != plan.occupant.id && it.id != newFolderId } +
                folder +
                plan.occupant.copy(container = inside, placement = CellPlacement(0, 0, 0)) +
                draggedAs(inside, CellPlacement(0, 1, 0))
        }
    }
}

/**
 * moves one app to [toRank] within its own folder and renumbers the folder from zero, so ranks
 * never gap or collide. anything not inside a folder is returned unchanged.
 */
fun moveWithinFolder(items: List<WorkspaceItem>, itemId: Long, toRank: Int): List<WorkspaceItem> {
    val moving = items.firstOrNull { it.id == itemId } ?: return items
    val folder = moving.container as? Container.Folder ?: return items
    val siblings = items.filter { it.container == folder }.sortedWith(compareBy({ it.placement.cellX }, { it.id }))
    val reordered = siblings.filter { it.id != itemId }.toMutableList()
    reordered.add(toRank.coerceIn(0, reordered.size), moving)
    val ranks = reordered.withIndex().associate { (rank, item) -> item.id to rank }
    return items.map { item ->
        val rank = ranks[item.id] ?: return@map item
        if (item.placement.cellX == rank && item.placement.page == 0 && item.placement.cellY == 0) item
        else item.copy(placement = CellPlacement(0, rank, 0))
    }
}

/**
 * puts an app or shortcut into [folderId] at [rank], from wherever it was. folders and widgets
 * never go inside a folder, so for those, or for a missing folder, the layout is returned unchanged.
 */
fun insertIntoFolder(items: List<WorkspaceItem>, itemId: Long, folderId: Long, rank: Int): List<WorkspaceItem> {
    val item = items.firstOrNull { it.id == itemId } ?: return items
    if (item.type != ItemType.APP && item.type != ItemType.SHORTCUT) return items
    if (items.none { it.id == folderId && it.type == ItemType.FOLDER }) return items
    val inside = Container.Folder(folderId)
    // joins at the end first, then moves to the requested spot, so the ranks stay contiguous
    val end = items.count { it.container == inside && it.id != itemId }
    val moved = items.map { if (it.id == itemId) it.copy(container = inside, placement = CellPlacement(0, end, 0)) else it }
    return moveWithinFolder(moved, itemId, rank)
}
