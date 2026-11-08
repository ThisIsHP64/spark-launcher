package dev.gearturner.sparklauncher.feature.workspace

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.gearturner.sparklauncher.core.model.Gesture
import dev.gearturner.sparklauncher.core.ui.DrawerController
import kotlin.math.abs

/**
 * vertical swipes anywhere on home. this runs after the pager and tiles have seen each event, so a
 * page flip, a widget's own scrolling or an icon drag always wins and this gesture backs off.
 *
 * a one finger swipe up drags the drawer with the finger when that is its action, as on the pixel.
 * every other swipe fires once it has travelled far enough, so the shade opens while the finger
 * is still moving instead of after it lifts.
 */
@Composable
internal fun Modifier.homeSwipes(
    drawer: DrawerController,
    enabled: Boolean,
    swipeUpDragsDrawer: Boolean,
    onGesture: (Gesture) -> Unit,
): Modifier {
    val trigger = with(LocalDensity.current) { TRIGGER_DISTANCE.toPx() }
    val dragsDrawer by rememberUpdatedState(swipeUpDragsDrawer)
    val fire by rememberUpdatedState(onGesture)
    if (!enabled) return this
    return pointerInput(drawer, trigger) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val starts = HashMap<PointerId, Offset>().apply { put(down.id, down.position) }
            val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var mode = Mode.Undecided
            var twoFingers = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) {
                    if (mode == Mode.Drawer) drawer.settle(-velocity.calculateVelocity().y)
                    return@awaitEachGesture
                }
                event.changes.forEach { if (it.pressed && it.id !in starts) starts[it.id] = it.position }
                // a second finger only counts as a two finger swipe if it lands before anything was decided
                if (pressed.size >= 2 && mode == Mode.Undecided) twoFingers = true
                if (mode == Mode.Undecided && event.changes.any { it.isConsumed }) {
                    mode = Mode.Ignored
                }
                val primary = event.changes.firstOrNull { it.id == down.id }
                when (mode) {
                    Mode.Ignored, Mode.Fired -> Unit
                    Mode.Drawer -> {
                        if (primary != null && primary.pressed) {
                            velocity.addPosition(primary.uptimeMillis, primary.position)
                            drawer.dragBy(-primary.positionChange().y)
                            primary.consume()
                        }
                    }
                    Mode.Undecided -> {
                        val travel = averageTravel(pressed.map { it.position - (starts[it.id] ?: it.position) })
                        if (abs(travel.x) > viewConfiguration.touchSlop && abs(travel.x) > abs(travel.y)) {
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
