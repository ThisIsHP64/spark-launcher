package dev.gearturner.sparklauncher.core.ui

import androidx.compose.ui.geometry.Offset
import dev.gearturner.sparklauncher.core.model.SwipeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconSwipesTest {

    private val slop = 8f

    @Test
    fun straightUpAndDownAreClassified() {
        assertEquals(SwipeDirection.UP, swipeDirection(Offset(0f, -30f), slop))
        assertEquals(SwipeDirection.DOWN, swipeDirection(Offset(0f, 30f), slop))
    }

    @Test
    fun noDirectionWhileInsideTheTouchSlop() {
        assertNull(swipeDirection(Offset(0f, -8f), slop))
        assertNull(swipeDirection(Offset(3f, 4f), slop))
        assertNull(swipeDirection(Offset.Zero, slop))
    }

    @Test
    fun sidewaysSwipesBelongToThePager() {
        assertNull(swipeDirection(Offset(40f, -5f), slop))
        assertNull(swipeDirection(Offset(-40f, 10f), slop))
    }

    @Test
    fun diagonalSwipesCountUpToFiftyFiveDegreesOffVertical() {
        // 45 degrees off vertical is still a vertical swipe, like on the home screen
        assertEquals(SwipeDirection.UP, swipeDirection(Offset(20f, -20f), slop))
        assertEquals(SwipeDirection.DOWN, swipeDirection(Offset(-25f, 20f), slop))
        // 60 degrees off vertical leans sideways
        assertNull(swipeDirection(Offset(35f, -20f), slop))
    }

    @Test
    fun emptyClaimOwnsNothing() {
        val claim = IconSwipeClaim()
        assertFalse(claim.owns(SwipeDirection.UP))
        assertFalse(claim.owns(SwipeDirection.DOWN))
    }

    @Test
    fun claimCoversOnlyTheDirectionsSet() {
        val claim = IconSwipeClaim()
        claim.set(up = true, down = false)
        assertTrue(claim.owns(SwipeDirection.UP))
        assertFalse(claim.owns(SwipeDirection.DOWN))
    }

    @Test
    fun clearedClaimOwnsNothingAgain() {
        val claim = IconSwipeClaim()
        claim.set(up = true, down = true)
        claim.clear()
        assertFalse(claim.owns(SwipeDirection.UP))
        assertFalse(claim.owns(SwipeDirection.DOWN))
    }
}
