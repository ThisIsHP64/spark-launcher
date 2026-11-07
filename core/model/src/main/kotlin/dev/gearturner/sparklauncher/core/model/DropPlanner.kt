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
 * refused rather than shoving other icons around.
 */
fun planDrop(
    items: List<WorkspaceItem>,
    dragged: Dragged,
    target: DropTarget,
    columns: Int,
    rows: Int,
    dockSlots: Int,
): DropPlan {
    val draggedItem = (dragged as? Dragged.Existing)?.item
    val spanX = draggedItem?.placement?.spanX ?: 1
    val spanY = draggedItem?.placement?.spanY ?: 1
    val (container, placement) = when (target) {
        is DropTarget.Desktop -> {
            if (target.cellX < 0 || target.cellY < 0 || target.cellX + spanX > columns || target.cellY + spanY > rows) {
                return DropPlan.Reject
            }
            Container.Desktop to CellPlacement(target.page.coerceAtLeast(0), target.cellX, target.cellY, spanX, spanY)
        }
        is DropTarget.Dock -> {
            // the dock is a single row, so anything wider than one slot cannot live there
            if (target.slot !in 0 until dockSlots || spanX != 1 || spanY != 1) return DropPlan.Reject
            Container.Dock to CellPlacement(0, target.slot, 0)
        }
    }

    val others = items.filter { it.id != draggedItem?.id }
    val occupants = others.filter { it.container == container && it.overlaps(placement) }
    if (occupants.isEmpty()) {
        if (draggedItem != null && draggedItem.container == container && draggedItem.placement == placement) {
            return DropPlan.NoChange
        }
        return DropPlan.Place(container, placement)
    }

    val occupant = occupants.singleOrNull() ?: return DropPlan.Reject
    // only a single icon can merge into a folder; folders and widgets do not nest
    val draggedIsIcon = draggedItem == null || draggedItem.type == ItemType.APP || draggedItem.type == ItemType.SHORTCUT
    if (!draggedIsIcon || spanX != 1 || spanY != 1) return DropPlan.Reject
    return when (occupant.type) {
        ItemType.FOLDER -> {
            if (draggedItem?.container == Container.Folder(occupant.id)) DropPlan.NoChange else DropPlan.AddToFolder(occupant)
        }
        ItemType.APP, ItemType.SHORTCUT -> DropPlan.CreateFolder(occupant)
        ItemType.WIDGET -> DropPlan.Reject
    }
}

private fun WorkspaceItem.overlaps(other: CellPlacement): Boolean {
    val p = placement
    if (container == Container.Desktop && p.page != other.page) return false
    return p.cellX < other.cellX + other.spanX && other.cellX < p.cellX + p.spanX &&
        p.cellY < other.cellY + other.spanY && other.cellY < p.cellY + p.spanY
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
