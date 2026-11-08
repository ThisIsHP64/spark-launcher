package dev.gearturner.sparklauncher.core.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** a 1000 by 2000 window, a 400 by 300 menu, 10 pixel margins, and a 5 pixel gap */
class MenuPlacementTest {

    private val window = IntRect(0, 0, 1000, 2000)
    private val menu = IntSize(400, 300)

    private fun place(anchor: IntRect, content: IntSize = menu, rtl: Boolean = false) =
        menuPosition(anchor, window, content, marginPx = 10, gapPx = 5, rtl = rtl)

    private fun IntOffset.rect(content: IntSize = menu) = IntRect(x, y, x + content.width, y + content.height)

    @Test
    fun opensAboveTheItemLinedUpWithItsLeftEdge() {
        assertEquals(IntOffset(100, 695), place(IntRect(100, 1000, 300, 1250)))
    }

    @Test
    fun anItemOnTheRightEdgeGetsAMenuLinedUpWithItsRightEdgeThatDoesNotCoverIt() {
        val item = IntRect(760, 1000, 990, 1250)
        val position = place(item)
        assertEquals(IntOffset(590, 695), position)
        assertFalse(position.rect().overlaps(item))
    }

    @Test
    fun opensBesideAnItemOnTheRightWhenThereIsNoRoomAbove() {
        // the menu sits to the item's left, level with its top, without covering it
        val item = IntRect(760, 100, 990, 350)
        val position = place(item)
        assertEquals(IntOffset(355, 100), position)
        assertFalse(position.rect().overlaps(item))
    }

    @Test
    fun opensBesideAnItemOnTheLeftOnItsRightSide() {
        assertEquals(IntOffset(305, 100), place(IntRect(10, 100, 300, 350)))
    }

    @Test
    fun opensBelowWhenThereIsNoRoomAboveOrBeside() {
        val wide = IntSize(600, 300)
        assertEquals(IntOffset(300, 255), place(IntRect(300, 0, 500, 250), wide))
    }

    @Test
    fun aMenuTooTallForTheRoomAboveOpensBesideAndStaysOnScreen() {
        val tall = IntSize(400, 1500)
        val item = IntRect(100, 1000, 300, 1200)
        val position = place(item, tall)
        assertEquals(IntOffset(305, 490), position)
        assertFalse(position.rect(tall).overlaps(item))
    }

    @Test
    fun aMenuWiderThanTheRoomLeftIsSlidOnScreen() {
        val wide = IntSize(900, 300)
        assertEquals(IntOffset(90, 695), place(IntRect(500, 1000, 700, 1250), wide))
    }

    @Test
    fun theStatusAndNavigationBarsAreKeptClear() {
        // a frame from 150 to 1850, as on a phone drawn edge to edge
        val frame = IntRect(0, 150, 1000, 1850)
        val nearTop = menuPosition(IntRect(100, 400, 300, 650), frame, IntSize(900, 300), marginPx = 10, gapPx = 5)
        assertEquals(655, nearTop.y)
        val nearBottom = menuPosition(IntRect(100, 1500, 300, 1750), frame, menu, marginPx = 10, gapPx = 5)
        assertEquals(1195, nearBottom.y)
    }

    @Test
    fun rightToLeftLinesUpWithTheRightEdgeFirst() {
        assertEquals(IntOffset(200, 695), place(IntRect(300, 1000, 600, 1250), rtl = true))
    }
}
