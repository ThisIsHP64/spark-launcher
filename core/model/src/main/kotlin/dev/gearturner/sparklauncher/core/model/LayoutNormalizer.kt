package dev.gearturner.sparklauncher.core.model

/**
 * tidies a layout after any edit so it never holds states the user cannot see or fix:
 * folders with one app become that app, empty folders vanish, folder order has no gaps,
 * and empty home pages between other pages are removed.
 */
fun normalizeLayout(items: List<WorkspaceItem>): List<WorkspaceItem> {
    val byId = items.associateBy { it.id }.toMutableMap()

    val folders = items.filter { it.type == ItemType.FOLDER }
    for (folder in folders) {
        val children = byId.values
            .filter { it.container == Container.Folder(folder.id) }
            .sortedWith(compareBy({ it.placement.cellX }, { it.id }))
        when (children.size) {
            0 -> byId.remove(folder.id)
            1 -> {
                // the last app takes over the folder's spot so the home screen does not shift
                byId.remove(folder.id)
                val only = children.single()
                byId[only.id] = only.copy(container = folder.container, placement = folder.placement)
            }
            else -> children.forEachIndexed { rank, child ->
                if (child.placement.cellX != rank || child.placement.page != 0 || child.placement.cellY != 0) {
                    byId[child.id] = child.copy(placement = CellPlacement(0, rank, 0))
                }
            }
        }
    }
    // children of a folder that no longer exists would be invisible forever, so they are dropped
    val liveFolderIds = byId.values.filter { it.type == ItemType.FOLDER }.map { it.id }.toSet()
    byId.values.filter { val c = it.container; c is Container.Folder && c.folderId !in liveFolderIds }
        .forEach { byId.remove(it.id) }

    val usedPages = byId.values.filter { it.container == Container.Desktop }.map { it.placement.page }.distinct().sorted()
    val pageMap = usedPages.withIndex().associate { (index, page) -> page to index }
    for (item in byId.values.toList()) {
        if (item.container != Container.Desktop) continue
        val newPage = pageMap.getValue(item.placement.page)
        if (newPage != item.placement.page) byId[item.id] = item.copy(placement = item.placement.copy(page = newPage))
    }
    return byId.values.toList()
}

/** the minimal set of writes that turns [before] into [after], matching items by id */
data class LayoutDiff(
    val inserts: List<WorkspaceItem>,
    val updates: List<WorkspaceItem>,
    val deletes: List<Long>,
)

fun diffLayout(before: List<WorkspaceItem>, after: List<WorkspaceItem>): LayoutDiff {
    val old = before.associateBy { it.id }
    val newIds = after.map { it.id }.toSet()
    return LayoutDiff(
        inserts = after.filter { it.id == 0L },
        updates = after.filter { it.id != 0L && old[it.id] != null && old[it.id] != it },
        deletes = before.map { it.id }.filter { it !in newIds },
    )
}
