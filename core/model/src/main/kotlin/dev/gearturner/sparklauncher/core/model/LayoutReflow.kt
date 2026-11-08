package dev.gearturner.sparklauncher.core.model

/**
 * fits an existing layout into a new grid without losing anything. items that still fit where
 * they are stay put; the rest move, in reading order, to the first free spot on their own page,
 * then later pages, then new pages at the end. widgets wider or taller than the new grid shrink
 * to fit, and dock items past the new slot count move onto the home pages.
 */
fun reflowLayout(items: List<WorkspaceItem>, columns: Int, rows: Int, dockSlots: Int): List<WorkspaceItem> {
    require(columns > 0 && rows > 0) { "grid must have at least one row and column" }
    val result = items.filter { it.container !is Container.Desktop && it.container !is Container.Dock }.toMutableList()
    val displaced = ArrayList<WorkspaceItem>()

    val dock = items.filter { it.container == Container.Dock }.sortedBy { it.placement.cellX }
    dock.forEach { if (it.placement.cellX < dockSlots) result += it else displaced += it }

    val desktop = items.filter { it.container == Container.Desktop }
        .sortedWith(compareBy({ it.placement.page }, { it.placement.cellY }, { it.placement.cellX }))
    val grids = HashMap<Int, GridOccupancy>()
    fun grid(page: Int) = grids.getOrPut(page) { GridOccupancy(columns, rows) }

    for (item in desktop) {
        val p = item.placement
        val clamped = p.copy(spanX = p.spanX.coerceAtMost(columns), spanY = p.spanY.coerceAtMost(rows))
        when {
            item.overlaps -> {
                // floating widgets keep their page and slide inward just enough to stay on screen
                result += item.copy(
                    placement = clamped.copy(
                        cellX = clamped.cellX.coerceAtMost(columns - clamped.spanX),
                        cellY = clamped.cellY.coerceAtMost(rows - clamped.spanY),
                    ),
                )
            }
            grid(p.page).isFree(clamped.cellX, clamped.cellY, clamped.spanX, clamped.spanY) -> {
                grid(p.page).mark(clamped.cellX, clamped.cellY, clamped.spanX, clamped.spanY)
                result += item.copy(placement = clamped)
            }
            else -> displaced += item.copy(placement = clamped)
        }
    }

    val lastPage = (desktop.maxOfOrNull { it.placement.page } ?: 0)
    for (item in displaced) {
        val span = if (item.container == Container.Dock) CellPlacement(0, 0, 0) else item.placement
        val startPage = if (item.container == Container.Dock) 0 else item.placement.page
        var page = startPage
        var spot: Pair<Int, Int>? = null
        while (spot == null) {
            spot = grid(page).findFree(span.spanX, span.spanY)
            if (spot == null) page++
            // a span that cannot fit even on an empty page was already clamped, so this always ends
            check(page <= lastPage + displaced.size + 1) { "reflow could not place an item" }
        }
        val (x, y) = spot
        grid(page).mark(x, y, span.spanX, span.spanY)
        result += item.copy(container = Container.Desktop, placement = CellPlacement(page, x, y, span.spanX, span.spanY))
    }
    return result
}

/**
 * home only mode keeps every app on the home pages. this adds the given apps that are not placed
 * anywhere yet, in the order given, to the first free spots after everything already there.
 */
fun addMissingApps(items: List<WorkspaceItem>, apps: List<AppKey>, columns: Int, rows: Int): List<WorkspaceItem> {
    val placed = items.filter { it.type == ItemType.APP }.mapNotNull { it.app }.toSet()
    val missing = apps.filter { it !in placed }.distinct()
    if (missing.isEmpty()) return items
    val desktop = items.filter { it.container == Container.Desktop && !it.overlaps }
    val grids = HashMap<Int, GridOccupancy>()
    desktop.forEach { item ->
        val p = item.placement
        grids.getOrPut(p.page) { GridOccupancy(columns, rows) }.mark(p.cellX, p.cellY, p.spanX, p.spanY)
    }
    var page = 0
    val added = missing.map { key ->
        var spot = grids.getOrPut(page) { GridOccupancy(columns, rows) }.findFree()
        while (spot == null) {
            page++
            spot = grids.getOrPut(page) { GridOccupancy(columns, rows) }.findFree()
        }
        val (x, y) = spot
        grids.getValue(page).mark(x, y)
        WorkspaceItem(0, ItemType.APP, Container.Desktop, CellPlacement(page, x, y), key)
    }
    return items + added
}
