package dev.gearturner.sparklauncher.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetsTest {

    private val provider = AppKey("com.example.weather", "com.example.weather.Widget", 0)

    private fun widget(id: Long, placement: CellPlacement, overlap: Boolean = false) = WorkspaceItem(
        id, ItemType.WIDGET, Container.Desktop, placement, provider,
        appWidgetId = id.toInt(), widgetOptions = WidgetOptions(allowOverlap = overlap),
    )

    private fun app(id: Long, placement: CellPlacement) =
        WorkspaceItem(id, ItemType.APP, Container.Desktop, placement, AppKey("com.example.a$id", "Main", 0))

    @Test
    fun defaultSpanPrefersTargetCellsThenMinimumSize() {
        val targeted = WidgetSizeSpec(minWidthDp = 40f, minHeightDp = 40f, targetCellWidth = 4, targetCellHeight = 2)
        assertEquals(Span(4, 2), defaultSpan(targeted, 90f, 110f, columns = 5, rows = 6))
        val sized = WidgetSizeSpec(minWidthDp = 180f, minHeightDp = 130f)
        assertEquals(Span(2, 2), defaultSpan(sized, 90f, 110f, columns = 5, rows = 6))
    }

    @Test
    fun defaultSpanNeverExceedsTheGrid() {
        val huge = WidgetSizeSpec(minWidthDp = 2000f, minHeightDp = 2000f)
        assertEquals(Span(4, 5), defaultSpan(huge, 90f, 110f, columns = 4, rows = 5))
    }

    @Test
    fun resizeLimitsFollowTheProviderUnlessFree() {
        val spec = WidgetSizeSpec(minWidthDp = 180f, minHeightDp = 100f, minResizeWidthDp = 90f, maxResizeWidthDp = 270f)
        val (min, max) = resizeLimits(spec, Span(2, 1), 90f, 110f, 5, 6, freeResize = false)
        assertEquals(Span(1, 1), min)
        assertEquals(Span(3, 6), max)
        val (freeMin, freeMax) = resizeLimits(spec, Span(2, 1), 90f, 110f, 5, 6, freeResize = true)
        assertEquals(Span(1, 1), freeMin)
        assertEquals(Span(5, 6), freeMax)
    }

    @Test
    fun fixedAxesCannotResize() {
        val spec = WidgetSizeSpec(minWidthDp = 90f, minHeightDp = 90f, resizableVertically = false)
        val (min, max) = resizeLimits(spec, Span(2, 2), 90f, 90f, 5, 6, freeResize = false)
        assertEquals(2, min.y)
        assertEquals(2, max.y)
    }

    @Test
    fun resizeIsBlockedByNeighboursButNotForOverlapWidgets() {
        val w = widget(1, CellPlacement(0, 0, 0, 2, 2))
        val neighbour = app(2, CellPlacement(0, 2, 0))
        assertFalse(canResize(listOf(w, neighbour), 1, CellPlacement(0, 0, 0, 3, 2), 5, 6))
        assertTrue(canResize(listOf(w, neighbour), 1, CellPlacement(0, 0, 0, 2, 3), 5, 6))
        val floating = widget(1, CellPlacement(0, 0, 0, 2, 2), overlap = true)
        assertTrue(canResize(listOf(floating, neighbour), 1, CellPlacement(0, 0, 0, 3, 2), 5, 6))
        assertFalse(canResize(listOf(w), 1, CellPlacement(0, 4, 0, 2, 2), 5, 6))
    }

    @Test
    fun newWidgetsNeedTheirWholeFootprintFree() {
        val items = listOf(app(1, CellPlacement(0, 1, 1)))
        val dragged = Dragged.NewWidget(provider, 42, Span(2, 2))
        assertEquals(DropPlan.Reject, planDrop(items, dragged, DropTarget.Desktop(0, 0, 0), 4, 5, 4))
        assertEquals(
            DropPlan.Place(Container.Desktop, CellPlacement(0, 2, 0, 2, 2)),
            planDrop(items, dragged, DropTarget.Desktop(0, 2, 0), 4, 5, 4),
        )
        assertEquals(DropPlan.Reject, planDrop(items, dragged, DropTarget.Dock(0), 4, 5, 4))
    }

    @Test
    fun appsCanLandUnderAnOverlapWidgetAndItCanLandOnApps() {
        val floating = widget(1, CellPlacement(0, 0, 0, 2, 2), overlap = true)
        val a = app(2, CellPlacement(0, 3, 3))
        assertEquals(
            DropPlan.Place(Container.Desktop, CellPlacement(0, 0, 0)),
            planDrop(listOf(floating, a), Dragged.Existing(a), DropTarget.Desktop(0, 0, 0), 4, 5, 4),
        )
        val b = app(3, CellPlacement(0, 1, 1))
        assertEquals(
            DropPlan.Place(Container.Desktop, CellPlacement(0, 1, 1, 2, 2)),
            planDrop(listOf(floating, b), Dragged.Existing(floating), DropTarget.Desktop(0, 1, 1), 4, 5, 4),
        )
    }

    @Test
    fun widgetsNeverBecomeFolders() {
        val w = widget(1, CellPlacement(0, 0, 0))
        val a = app(2, CellPlacement(0, 1, 0))
        assertEquals(DropPlan.Reject, planDrop(listOf(w, a), Dragged.Existing(w), DropTarget.Desktop(0, 1, 0), 4, 5, 4))
        assertEquals(DropPlan.Reject, planDrop(listOf(w, a), Dragged.Existing(a), DropTarget.Desktop(0, 0, 0), 4, 5, 4))
    }

    @Test
    fun optionsAreClampedToSupportedRanges() {
        val clean = WidgetOptions(cornerRadiusDp = 400, opacityPercent = 2).sanitized()
        assertEquals(WidgetOptions.MAX_CORNER_RADIUS_DP, clean.cornerRadiusDp)
        assertEquals(WidgetOptions.MIN_OPACITY_PERCENT, clean.opacityPercent)
    }
}
