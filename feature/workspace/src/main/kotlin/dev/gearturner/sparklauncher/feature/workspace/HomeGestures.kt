package dev.gearturner.sparklauncher.feature.workspace

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.gearturner.sparklauncher.core.model.Gesture
import dev.gearturner.sparklauncher.core.ui.DrawerController
import kotlin.math.abs

/**
 * vertical swipes anywhere on home. this runs after the pager and tiles have seen each event, so a
 * page flip, a widget's own scrolling, or an icon drag always wins and this gesture backs off.
 *
 * a one finger swipe up drags the drawer with the finger when that is its action, as on the pixel.
 * every other swipe fires once it has travelled far enough, so the shade opens while the finger
 * is still moving instead of after it lifts.
 */
@Composable
internal fun Modifier.homeSwipes(
    drawer: DrawerController,
    enabled: () -> Boolean,
    swipeUpDragsDrawer: Boolean,
    startsOnWidget: (Offset) -> Boolean,
    onGesture: (Gesture) -> Unit,
): Modifier {
    val density = LocalDensity.current
    val trigger = with(density) { TRIGGER_DISTANCE.toPx() }
    // the system's own bottom gesture band, where a swipe belongs to home and recents, not to spark
    val systemGestureBottom = WindowInsets.systemGestures.getBottom(density).toFloat()
    val dragsDrawer by rememberUpdatedState(swipeUpDragsDrawer)
    val fire by rememberUpdatedState(onGesture)
    val isEnabled by rememberUpdatedState(enabled)
    val onWidget by rememberUpdatedState(startsOnWidget)
    // the detector always stays attached and checks [isEnabled] instead: removing a pointer input
    // node mid gesture makes compose cancel the touch for everything under it, which would end the
    // very icon drag that turned this detector off. it is a function read on every event, because a
    // drag that starts on the same event that crosses touch slop only shows up in snapshot state,
    // never in a value that waits for the next recomposition
    return pointerInput(drawer, trigger, systemGestureBottom) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!isEnabled()) return@awaitEachGesture
            if (down.position.y > size.height - systemGestureBottom) return@awaitEachGesture
            val starts = HashMap<PointerId, Offset>().apply { put(down.id, down.position) }
            val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var mode = Mode.Undecided
            var twoFingers = false
            // a widget may scroll its own list, so swipes that start on one are never claimed early
            val mayClaim = !onWidget(down.position)
            var claimed = false
            while (true) {
                // the pager grabs a swipe as soon as it leans sideways past the touch slop. home looks
                // first, before the pager and tiles, and claims a swipe that is mostly vertical so
                // diagonal swipes still count. after a claim it keeps the moves to itself; lifts are left
                // alone so a cancel can still be told apart from a real release below
                val early = awaitPointerEvent(PointerEventPass.Initial)
                if (mayClaim && !claimed && mode == Mode.Undecided && isEnabled()) {
                    val moved = early.changes.filter { it.pressed }
                    moved.forEach { if (it.id !in starts) starts[it.id] = it.position }
                    val travel = averageTravel(moved.map { it.position - (starts[it.id] ?: it.position) })
                    if (travel.getDistance() > viewConfiguration.touchSlop) {
                        if (abs(travel.x) <= abs(travel.y) * MAX_SIDEWAYS_RATIO) claimed = true else mode = Mode.Ignored
                    }
                }
                if (claimed && mode != Mode.Ignored) early.changes.forEach { if (it.pressed) it.consume() }
                val event = awaitPointerEvent(PointerEventPass.Main)
                // read after the tiles below have handled this event, since one may have just started an icon drag
                val active = isEnabled()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) {
                    if (mode == Mode.Drawer) {
                        // a real lift arrives unconsumed; a cancel, such as the system taking the swipe
                        // for recents, arrives already consumed and must not fling the drawer open
                        val cancelled = event.changes.all { it.isConsumed }
                        if (cancelled) drawer.close() else drawer.settle(-velocity.calculateVelocity().y)
                    }
                    return@awaitEachGesture
                }
                event.changes.forEach { if (it.pressed && it.id !in starts) starts[it.id] = it.position }
                // a second finger only counts as a two finger swipe if it lands before anything was decided
                if (pressed.size >= 2 && mode == Mode.Undecided) twoFingers = true
                if (mode == Mode.Undecided && !claimed && event.changes.any { it.isConsumed }) {
                    mode = Mode.Ignored
                }
                val primary = event.changes.firstOrNull { it.id == down.id }
                if (!active && mode != Mode.Drawer) mode = Mode.Ignored
                when (mode) {
                    Mode.Ignored, Mode.Fired -> Unit
                    Mode.Drawer -> {
                        if (primary != null && primary.pressed) {
                            velocity.addPosition(primary.uptimeMillis, primary.position)
                            // the move may already be consumed by this detector's own early claim
                            drawer.dragBy(-primary.positionChangeIgnoreConsumed().y)
                            primary.consume()
                        }
                    }
                    Mode.Undecided -> {
                        val travel = averageTravel(pressed.map { it.position - (starts[it.id] ?: it.position) })
                        if (!claimed && abs(travel.x) > viewConfiguration.touchSlop && abs(travel.x) > abs(travel.y) * MAX_SIDEWAYS_RATIO) {
                            // sideways is the pager's job
                            mode = Mode.Ignored
                        } else if (!twoFingers && dragsDrawer && travel.y < -viewConfiguration.touchSlop) {
                            mode = Mode.Drawer
                            primary?.let {
                                velocity.addPosition(it.uptimeMillis, it.position)
                                it.consume()
                            }
                        } else if (abs(travel.y) > trigger) {
                            val up = travel.y < 0
                            fire(
                                when {
                                    twoFingers && up -> Gesture.TWO_FINGER_SWIPE_UP
                                    twoFingers -> Gesture.TWO_FINGER_SWIPE_DOWN
                                    up -> Gesture.SWIPE_UP
                                    else -> Gesture.SWIPE_DOWN
                                },
                            )
                            pressed.forEach { it.consume() }
                            mode = Mode.Fired
                        }
                    }
                }
            }
        }
    }
}

private enum class Mode { Undecided, Drawer, Fired, Ignored }

private fun averageTravel(moves: List<Offset>): Offset =
    if (moves.isEmpty()) Offset.Zero else moves.reduce { a, b -> a + b } / moves.size.toFloat()

// long enough that a sloppy tap never opens the shade, short enough to feel immediate
private val TRIGGER_DISTANCE = 48.dp

// tan(55 degrees): a swipe counts as vertical up to 55 degrees off straight up or down, leaving page
// flips the 35 degrees either side of horizontal
private const val MAX_SIDEWAYS_RATIO = 1.43f
