package dev.gearturner.sparklauncher.core.model

import kotlin.math.ceil
import kotlinx.serialization.Serializable

/** the per widget choices nova users expect; stored as json so new options need no migration */
@Serializable
data class WidgetOptions(
    /** adds the standard inset around the widget; off lets it fill its cells edge to edge */
    val padding: Boolean = true,
    /** extra rounding clipped over the widget, in dp; 0 leaves the widget's own shape alone */
    val cornerRadiusDp: Int = 0,
    /** fades the whole widget, from 30 to 100 percent */
    val opacityPercent: Int = 100,
    val allowOverlap: Boolean = false,
    /** lets the user resize past the limits the provider declares, like nova's resize any widget */
    val ignoreSizeLimits: Boolean = false,
) {
    fun sanitized(): WidgetOptions = copy(
        cornerRadiusDp = cornerRadiusDp.coerceIn(0, MAX_CORNER_RADIUS_DP),
        opacityPercent = opacityPercent.coerceIn(MIN_OPACITY_PERCENT, 100),
    )

    companion object {
        const val MAX_CORNER_RADIUS_DP = 40
        const val MIN_OPACITY_PERCENT = 30
    }
}

/**
 * the sizing facts a provider declares, already converted to dp. zero means "not declared" for
 * the optional fields, matching how the platform reports them.
 */
data class WidgetSizeSpec(
    val minWidthDp: Float,
    val minHeightDp: Float,
    val minResizeWidthDp: Float = 0f,
    val minResizeHeightDp: Float = 0f,
    val maxResizeWidthDp: Float = 0f,
    val maxResizeHeightDp: Float = 0f,
    val targetCellWidth: Int = 0,
    val targetCellHeight: Int = 0,
    val resizableHorizontally: Boolean = true,
    val resizableVertically: Boolean = true,
)

data class Span(val x: Int, val y: Int)

/** how many cells a dp length needs; a sliver of tolerance stops 179.9dp in 90dp cells becoming 3 */
private fun cellsFor(lengthDp: Float, cellDp: Float): Int =
    if (lengthDp <= 0f || cellDp <= 0f) 1 else ceil(lengthDp / cellDp - SPAN_TOLERANCE).toInt().coerceAtLeast(1)

/** the size a widget is placed at, preferring the provider's own target cells when it gives them */
fun defaultSpan(spec: WidgetSizeSpec, cellWidthDp: Float, cellHeightDp: Float, columns: Int, rows: Int): Span {
    val x = if (spec.targetCellWidth > 0) spec.targetCellWidth else cellsFor(spec.minWidthDp, cellWidthDp)
    val y = if (spec.targetCellHeight > 0) spec.targetCellHeight else cellsFor(spec.minHeightDp, cellHeightDp)
    return Span(x.coerceIn(1, columns), y.coerceIn(1, rows))
}

/**
 * the allowed resize range. with [freeResize] the user may shrink below what the provider asks
 * for and grow past its maximum, which is nova's "resize any widget" behavior.
 */
fun resizeLimits(
    spec: WidgetSizeSpec,
    current: Span,
    cellWidthDp: Float,
    cellHeightDp: Float,
    columns: Int,
    rows: Int,
    freeResize: Boolean,
): Pair<Span, Span> {
    if (freeResize) return Span(1, 1) to Span(columns, rows)
    val minX = if (!spec.resizableHorizontally) current.x else cellsFor(spec.minResizeWidthDp.takeIf { it > 0f } ?: spec.minWidthDp, cellWidthDp)
    val minY = if (!spec.resizableVertically) current.y else cellsFor(spec.minResizeHeightDp.takeIf { it > 0f } ?: spec.minHeightDp, cellHeightDp)
    val maxX = when {
        !spec.resizableHorizontally -> current.x
        spec.maxResizeWidthDp > 0f -> cellsFor(spec.maxResizeWidthDp, cellWidthDp)
        else -> columns
    }
    val maxY = when {
        !spec.resizableVertically -> current.y
        spec.maxResizeHeightDp > 0f -> cellsFor(spec.maxResizeHeightDp, cellHeightDp)
        else -> rows
    }
    val min = Span(minX.coerceIn(1, columns), minY.coerceIn(1, rows))
    val max = Span(maxX.coerceIn(min.x, columns), maxY.coerceIn(min.y, rows))
    return min to max
}

/** checks a resize against the grid edges and every other item, honoring overlap on either side */
fun canResize(items: List<WorkspaceItem>, itemId: Long, placement: CellPlacement, columns: Int, rows: Int): Boolean {
    val item = items.firstOrNull { it.id == itemId } ?: return false
    if (item.container != Container.Desktop) return false
    if (placement.cellX + placement.spanX > columns || placement.cellY + placement.spanY > rows) return false
    if (item.overlaps) return true
    return items.none { other ->
        other.id != itemId && other.container == Container.Desktop && !other.overlaps &&
            other.placement.page == placement.page && other.placement.intersects(placement)
    }
}

internal fun CellPlacement.intersects(other: CellPlacement): Boolean =
    cellX < other.cellX + other.spanX && other.cellX < cellX + spanX &&
        cellY < other.cellY + other.spanY && other.cellY < cellY + spanY

private const val SPAN_TOLERANCE = 0.05f
