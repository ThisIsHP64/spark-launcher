package dev.gearturner.sparklauncher.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import dev.gearturner.sparklauncher.core.model.SwipeDirection
import kotlin.math.abs

// tan(55 degrees): a swipe counts as vertical up to 55 degrees off straight up or down, leaving page
// flips the 35 degrees either side of horizontal. home swipes use the same ratio so an icon and the
// home screen always agree on what a vertical swipe is
const val MAX_SIDEWAYS_RATIO = 1.43f

/** the vertical direction of a swipe once it has moved past the touch slop, or null while undecided or sideways */
fun swipeDirection(travel: Offset, touchSlop: Float): SwipeDirection? {
    if (travel.getDistance() <= touchSlop) return null
    if (abs(travel.x) > abs(travel.y) * MAX_SIDEWAYS_RATIO) return null
    return if (travel.y < 0) SwipeDirection.UP else SwipeDirection.DOWN
}

/**
 * the swipe directions the icon under the current touch has an action for. the icon sets this when
 * a finger lands on it and clears it when the touch ends, and the home swipe detector reads it to
 * leave exactly those directions alone. it is empty whenever no icon with an action is pressed, so
 * the default swipes behave as if this did not exist.
 */
class IconSwipeClaim {
    private var up = false
    private var down = false

    fun owns(direction: SwipeDirection): Boolean = when (direction) {
        SwipeDirection.UP -> up
        SwipeDirection.DOWN -> down
    }

    fun set(up: Boolean, down: Boolean) {
        this.up = up
        this.down = down
    }

    fun clear() = set(up = false, down = false)
}

val LocalIconSwipeClaim = staticCompositionLocalOf<IconSwipeClaim?> { null }
