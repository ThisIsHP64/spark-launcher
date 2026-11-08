package dev.gearturner.sparklauncher.feature.workspace

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil
import kotlin.math.floor

/** pages hold at most a four by four grid, like the pixel launcher's folders */
internal const val FOLDER_MAX_COLUMNS = 4
internal const val FOLDER_MAX_ROWS = 4
internal const val FOLDER_PAGE_SIZE = FOLDER_MAX_COLUMNS * FOLDER_MAX_ROWS

/** small folders stay compact: two columns up to four apps, three up to nine, then four */
internal fun folderColumns(count: Int): Int = when {
    count <= 4 -> 2
    count <= 9 -> 3
    else -> FOLDER_MAX_COLUMNS
}

/** rows on a page; every page of a multi page folder has the full height so paging does not resize it */
internal fun folderRows(count: Int): Int {
    val columns = folderColumns(count)
    return ceil(count.coerceIn(1, FOLDER_PAGE_SIZE) / columns.toFloat()).toInt().coerceIn(1, FOLDER_MAX_ROWS)
}

internal fun folderPageCount(count: Int): Int = ceil(count.coerceAtLeast(1) / FOLDER_PAGE_SIZE.toFloat()).toInt()

/** where one rank sits: its page and its cell on that page */
internal data class FolderSlot(val page: Int, val column: Int, val row: Int)

internal fun slotOf(rank: Int, columns: Int): FolderSlot {
    val page = rank / FOLDER_PAGE_SIZE
    val onPage = rank % FOLDER_PAGE_SIZE
    return FolderSlot(page, onPage % columns, onPage / columns)
}

/**
 * the rank under [local], a point relative to the top left of the visible page's grid. points past
 * the last app land on the last rank, so dropping in the empty tail moves an app to the end.
 */
internal fun rankAt(local: Offset, cellWidth: Float, cellHeight: Float, columns: Int, rows: Int, page: Int, count: Int): Int {
    if (count <= 0 || cellWidth <= 0f || cellHeight <= 0f) return 0
    val column = floor(local.x / cellWidth).toInt().coerceIn(0, columns - 1)
    val row = floor(local.y / cellHeight).toInt().coerceIn(0, rows - 1)
    return (page * FOLDER_PAGE_SIZE + row * columns + column).coerceIn(0, count - 1)
}

/** the order to draw while an app hovers at [hoverRank]; everything else closes the gap around it */
internal fun <T> displayOrder(children: List<T>, idOf: (T) -> Long, draggedId: Long?, hoverRank: Int?): List<T> {
    if (draggedId == null || hoverRank == null) return children
    val dragged = children.firstOrNull { idOf(it) == draggedId } ?: return children
    val rest = children.filter { idOf(it) != draggedId }.toMutableList()
    rest.add(hoverRank.coerceIn(0, rest.size), dragged)
    return rest
}

/** where an app already in the folder is drawn while a new one hovers at [gap]: everything from the gap on moves up one */
internal fun rankWithGap(index: Int, gap: Int?): Int = if (gap != null && index >= gap) index + 1 else index

/**
 * the open folder's live layout in root coordinates, written on every layout pass and read by
 * drag math, which runs outside composition.
 */
@Stable
internal class FolderGeometry {
    var panelBounds: Rect? = null
    var gridBounds: Rect? = null
    var cellWidth: Float = 0f
    var cellHeight: Float = 0f
    var columns: Int = 1
    var rows: Int = 1
    var page: Int = 0
    var count: Int = 0

    fun rankAt(finger: Offset): Int? {
        val grid = gridBounds ?: return null
        return rankAt(finger - grid.topLeft, cellWidth, cellHeight, columns, rows, page, count)
    }

    /** a little slack around the panel so a drag along its edge does not drop out by accident */
    fun contains(finger: Offset, slack: Float): Boolean = panelBounds?.inflate(slack)?.contains(finger) == true

    /**
     * -1 or 1 while the finger is beside the grid, in the panel's margin or the slack around it,
     * which flips pages; 0 over the grid itself, so the edge columns stay ordinary drop spots
     */
    fun edgeDirection(finger: Offset, slack: Float): Int {
        val grid = gridBounds ?: return 0
        if (!contains(finger, slack)) return 0
        return when {
            finger.x < grid.left -> -1
            finger.x > grid.right -> 1
            else -> 0
        }
    }}
