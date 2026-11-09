package dev.gearturner.sparklauncher.feature.workspace

import androidx.compose.ui.geometry.Offset
import dev.gearturner.sparklauncher.core.ui.IconSwipeClaim
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconOwnsSwipeTest {

    private val slop = 8f
    private val up = Offset(0f, -60f)
    private val down = Offset(0f, 60f)

    private fun claim(up: Boolean, down: Boolean) = IconSwipeClaim().also { it.set(up, down) }

    @Test
    fun noClaimNeverOwnsASwipe() {
        assertFalse(iconOwnsSwipe(up, slop, 1, null))
        assertFalse(iconOwnsSwipe(down, slop, 1, null))
    }

    @Test
    fun emptyClaimLeavesEveryDefaultSwipeToHome() {
        val empty = IconSwipeClaim()
        assertFalse(iconOwnsSwipe(up, slop, 1, empty))
        assertFalse(iconOwnsSwipe(down, slop, 1, empty))
    }

    @Test
    fun claimOnlyCoversItsOwnDirection() {
        val upOnly = claim(up = true, down = false)
        assertTrue(iconOwnsSwipe(up, slop, 1, upOnly))
        assertFalse(iconOwnsSwipe(down, slop, 1, upOnly))
        val downOnly = claim(up = false, down = true)
        assertFalse(iconOwnsSwipe(up, slop, 1, downOnly))
        assertTrue(iconOwnsSwipe(down, slop, 1, downOnly))
    }

    @Test
    fun sidewaysSwipeIsNeverOwned() {
        assertFalse(iconOwnsSwipe(Offset(80f, -10f), slop, 1, claim(up = true, down = true)))
    }

    @Test
    fun swipeInsideTheSlopIsNeverOwned() {
        assertFalse(iconOwnsSwipe(Offset(0f, -4f), slop, 1, claim(up = true, down = true)))
    }

    @Test
    fun twoFingerSwipesAreNeverOwned() {
        assertFalse(iconOwnsSwipe(up, slop, 2, claim(up = true, down = true)))
    }
}
