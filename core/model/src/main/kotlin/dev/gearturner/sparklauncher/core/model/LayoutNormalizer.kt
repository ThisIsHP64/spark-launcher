package dev.gearturner.sparklauncher.core.model

/**
 * tidies a layout after any edit so it never holds states the user cannot see or fix:
 * folders with one app become that app, empty folders vanish, folder order has no gaps,
 * and empty home pages between other pages are removed.
 */
fun normalizeLayout(items: List<WorkspaceItem>): List<WorkspaceItem> {
    // unsaved items all share id 0, so they get distinct negative keys here or all but one would be lost
    var nextUnsaved = -1L
    val byId = LinkedHashMap<Long, WorkspaceItem>()
    items.forEach { item -> byId[if (item.id == 0L) nextUnsaved-- else item.id] = item }

    val folders = items.filter { it.type == ItemType.FOLDER && it.id != 0L }
    for (folder in folders) {
        val children = byId.entries
            .filter { it.value.container == Container.Folder(folder.id) }
            .sortedWith(compareBy({ it.value.placement.cellX }, { it.key }))
        when (children.size) {
            0 -> byId.remove(folder.id)
            1 -> {
                // the last app takes over the folder's spot so the home screen does not shift
                byId.remove(folder.id)
                val (key, only) = children.single()
                byId[key] = only.copy(container = folder.container, placement = folder.placement)
            }
            else -> children.forEachIndexed { rank, (key, child) ->
                if (child.placement.cellX != rank || child.placement.page != 0 || child.placement.cellY != 0) {
                    byId[key] = child.copy(placement = CellPlacement(0, rank, 0))
                }
            }
        }
    }
    // children of a folder that no longer exists would be invisible forever, so they are dropped
    val liveFolderIds = byId.values.filter { it.type == ItemType.FOLDER }.map { it.id }.toSet()
    byId.entries.removeAll { val c = it.value.container; c is Container.Folder && c.folderId !in liveFolderIds }

    val usedPages = byId.values.filter { it.container == Container.Desktop }.map { it.placement.page }.distinct().sorted()
    val pageMap = usedPages.withIndex().associate { (index, page) -> page to index }
    for ((key, item) in byId.entries.toList()) {
        if (item.container != Container.Desktop) continue
        val newPage = pageMap.getValue(item.placement.page)
        if (newPage != item.placement.page) byId[key] = item.copy(placement = item.placement.copy(page = newPage))
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
