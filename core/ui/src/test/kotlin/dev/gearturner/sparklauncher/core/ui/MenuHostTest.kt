package dev.gearturner.sparklauncher.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuHostTest {

    private val host = MenuHost()

    /** a menu whose owner closes it when asked, the way every caller of ItemMenu does */
    private fun ownedMenu(dismissals: IntArray = IntArray(1)): HostedMenu = host.newMenu().apply {
        onDismiss = {
            dismissals[0]++
            host.hide(this)
        }
    }

    @Test
    fun showingAMenuMakesItTheOpenOne() {
        val menu = ownedMenu()
        host.show(menu)
        assertSame(menu, host.current)
        assertTrue(menu.expanded)
        assertTrue(host.isOpen)
    }

    @Test
    fun aClosingMenuStaysDrawnUntilItsAnimationFinishes() {
        val menu = ownedMenu()
        host.show(menu)
        host.hide(menu)
        assertSame(menu, host.current)
        assertFalse(host.isOpen)
        host.finished(menu)
        assertNull(host.current)
    }

    @Test
    fun finishingIsIgnoredForAMenuThatWasReopened() {
        val menu = ownedMenu()
        host.show(menu)
        host.hide(menu)
        host.show(menu)
        host.finished(menu)
        assertSame(menu, host.current)
        assertTrue(host.isOpen)
    }

    @Test
    fun openingASecondMenuClosesTheFirstThroughItsOwner() {
        val firstDismissals = IntArray(1)
        val first = ownedMenu(firstDismissals)
        val second = ownedMenu()
        host.show(first)
        host.show(second)
        assertEquals(1, firstDismissals[0])
        assertFalse(first.expanded)
        assertSame(second, host.current)
    }

    @Test
    fun dismissAsksTheOwnerOnlyWhileTheMenuIsOpen() {
        val dismissals = IntArray(1)
        val menu = ownedMenu(dismissals)
        host.dismiss()
        assertEquals(0, dismissals[0])
        host.show(menu)
        host.dismiss()
        assertEquals(1, dismissals[0])
        // already closing, so a second tap outside does not ask again
        host.dismiss()
        assertEquals(1, dismissals[0])
    }

    @Test
    fun removingDropsTheMenuAtOnce() {
        val menu = ownedMenu()
        host.show(menu)
        host.remove(menu)
        assertNull(host.current)
        assertFalse(menu.expanded)
    }

    @Test
    fun removingAMenuThatIsNotShownLeavesTheOpenOneAlone() {
        val open = ownedMenu()
        val other = ownedMenu()
        host.show(open)
        host.remove(other)
        assertSame(open, host.current)
        assertTrue(open.expanded)
    }
}
