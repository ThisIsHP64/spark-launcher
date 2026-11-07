package dev.gearturner.sparklauncher.core.model

/**
 * tracks which cells of a single page are taken so items never overlap.
 * kept free of android types so placement rules can be unit tested on the jvm.
 */
class GridOccupancy(val columns: Int, val rows: Int) {

    private val cells = Array(rows) { BooleanArray(columns) }

    init {
        require(columns > 0 && rows > 0) { "grid must have at least one row and column" }
    }

    fun fits(x: Int, y: Int, spanX: Int, spanY: Int): Boolean =
        x >= 0 && y >= 0 && spanX >= 1 && spanY >= 1 && x + spanX <= columns && y + spanY <= rows

    fun isFree(x: Int, y: Int, spanX: Int = 1, spanY: Int = 1): Boolean {
        if (!fits(x, y, spanX, spanY)) return false
        for (row in y until y + spanY) {
            for (col in x until x + spanX) {
                if (cells[row][col]) return false
            }
        }
        return true
    }

    /** cells outside the grid are ignored so stale rows from a larger grid cannot crash the layout */
    fun mark(x: Int, y: Int, spanX: Int = 1, spanY: Int = 1, occupied: Boolean = true) {
        for (row in y until minOf(y + spanY, rows)) {
            for (col in x until minOf(x + spanX, columns)) {
                if (row >= 0 && col >= 0) cells[row][col] = occupied
            }
        }
    }

    /** scans row by row, left to right, matching how people read a home screen */
    fun findFree(spanX: Int = 1, spanY: Int = 1): Pair<Int, Int>? {
        for (y in 0..rows - spanY) {
            for (x in 0..columns - spanX) {
                if (isFree(x, y, spanX, spanY)) return x to y
            }
        }
        return null
    }

    companion object {
        fun of(columns: Int, rows: Int, placements: Iterable<CellPlacement>): GridOccupancy =
            GridOccupancy(columns, rows).apply {
                placements.forEach { mark(it.cellX, it.cellY, it.spanX, it.spanY) }
            }
    }
}

/**
 * picks the first free cell across existing pages, appending a new page when all are full.
 * returns null only if the requested span cannot fit on an empty page.
 */
fun findFirstFreePlacement(
    columns: Int,
    rows: Int,
    desktopPlacements: List<CellPlacement>,
    spanX: Int = 1,
    spanY: Int = 1,
): CellPlacement? {
    if (spanX > columns || spanY > rows) return null
    val byPage = desktopPlacements.groupBy { it.page }
    val pageCount = (desktopPlacements.maxOfOrNull { it.page } ?: -1) + 1
    for (page in 0 until pageCount) {
        val grid = GridOccupancy.of(columns, rows, byPage[page].orEmpty())
        grid.findFree(spanX, spanY)?.let { (x, y) -> return CellPlacement(page, x, y, spanX, spanY) }
    }
    return CellPlacement(pageCount, 0, 0, spanX, spanY)
}
