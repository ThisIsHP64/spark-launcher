package dev.gearturner.sparklauncher.core.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.unit.dp
import dev.gearturner.sparklauncher.core.model.SwipeDirection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates

/** receives a drag that began with a long press on an icon */
interface IconDragHandler {
    fun onDragStart(fingerInRoot: Offset, iconBoundsInRoot: Rect)
    fun onDragMove(fingerInRoot: Offset)
    fun onDragEnd()
    fun onDragCancel()
}

/**
 * tap, long press, and long press then drag in one detector, because the stock modifiers each
 * claim the long press and would fire a tap after a held finger lifts. a long press opens the
 * menu; moving past the touch slop afterwards turns it into a drag, like the pixel launcher.
 *
 * an icon with a [swipeUp] or [swipeDown] action also reacts to a vertical swipe in that direction.
 * only those directions are taken: the icon tells the home swipe detector through
 * [LocalIconSwipeClaim] which ones it owns, and every other swipe keeps its normal meaning. an icon
 * with no swipe action runs the original detector unchanged.
 *
 * the detector is keyed on nothing and reads the callbacks through updated state: callers rebuild
 * their lambdas whenever home state emits, and restarting the detector mid drag would orphan the
 * drag session with nobody left to move or end it.
 */
@Composable
fun Modifier.iconGestures(
    coordinates: () -> LayoutCoordinates?,
    iconBounds: () -> Rect?,
    haptics: HapticFeedback,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    dragHandler: IconDragHandler?,
    swipeUp: (() -> Unit)? = null,
    swipeDown: (() -> Unit)? = null,
): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentDragHandler by rememberUpdatedState(dragHandler)
    val currentSwipeUp by rememberUpdatedState(swipeUp)
    val currentSwipeDown by rememberUpdatedState(swipeDown)
    val claim by rememberUpdatedState(LocalIconSwipeClaim.current)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            // claiming the press stops tap detectors behind the icon, such as the empty space long
            // press; scrolling still works because pagers and the drawer swipe ignore this claim
            down.consume()
            val canSwipeUp = currentSwipeUp != null
            val canSwipeDown = currentSwipeDown != null
            val hasSwipes = canSwipeUp || canSwipeDown
            val activeClaim = claim?.takeIf { hasSwipes }
            activeClaim?.set(canSwipeUp, canSwipeDown)
            try {
                var up: PointerInputChange? = null
                var swipe: SwipeDirection? = null
                val released = try {
                    withTimeout(viewConfiguration.longPressTimeoutMillis) {
                        if (!hasSwipes) {
                            up = waitForUpOrCancellation()
                        } else {
                            when (val press = awaitUpOrSwipe(down, canSwipeUp, canSwipeDown)) {
                                is Press.Up -> up = press.change
                                is Press.Swipe -> swipe = press.direction
                                Press.Cancelled -> Unit
                            }
                        }
                    }
                    true
                } catch (e: PointerEventTimeoutCancellationException) {
                    false
                }
                swipe?.let { direction ->
                    val action = if (direction == SwipeDirection.UP) currentSwipeUp else currentSwipeDown
                    trackSwipe(down, direction, SWIPE_TRIGGER.toPx()) {
                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        action?.invoke()
                    }
                    return@awaitEachGesture
                }
                if (released) {
                    // a scroll in a parent consumes the gesture, which cancels the tap instead of launching
                    up?.let {
                        it.consume()
                        currentOnClick()
                    }
                    return@awaitEachGesture
                }

                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnLongClick()
                val node = coordinates() ?: return@awaitEachGesture
                var dragging = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            if (dragging) {
                                dragging = false
                                currentDragHandler?.onDragEnd()
                            }
                            break
                        }
                        if (!node.isAttached) break
                        val fingerInRoot = node.localToRoot(change.position)
                        val handler = currentDragHandler
                        if (!dragging && handler != null && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            val bounds = iconBounds() ?: break
                            dragging = true
                            handler.onDragStart(fingerInRoot, bounds)
                        }
                        if (dragging) {
                            change.consume()
                            currentDragHandler?.onDragMove(fingerInRoot)
                        }
                    }
                } finally {
                    // cancellation or a detached tile must not leave the session open with no finger driving it
                    if (dragging) currentDragHandler?.onDragCancel()
                }
            } finally {
                // cleared on every exit, including cancellation, so a claim never outlives its touch
                activeClaim?.clear()
            }
        }
    }
}

/** what a press that has not yet become a long press turned into */
private sealed interface Press {
    data class Up(val change: PointerInputChange) : Press
    data class Swipe(val direction: SwipeDirection) : Press
    data object Cancelled : Press
}

/**
 * the stock wait for a lift or cancellation, extended to stop early on a vertical swipe in a
 * direction that has an action. a parent that consumes the touch, such as the pager, still cancels
 * it, and a second finger leaves the gesture to the home screen's two finger swipes.
 */
private suspend fun AwaitPointerEventScope.awaitUpOrSwipe(
    down: PointerInputChange,
    canSwipeUp: Boolean,
    canSwipeDown: Boolean,
): Press {
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.count { it.pressed } > 1) return Press.Cancelled
        val change = event.changes.firstOrNull { it.id == down.id } ?: return Press.Cancelled
        if (change.isConsumed) return Press.Cancelled
        if (!change.pressed) return Press.Up(change)
        val direction = swipeDirection(change.position - down.position, viewConfiguration.touchSlop)
        if (direction == SwipeDirection.UP && canSwipeUp || direction == SwipeDirection.DOWN && canSwipeDown) {
            change.consume()
            return Press.Swipe(direction)
        }
        // parents handle the event after this node, so consumption is only visible in the final pass
        val final = awaitPointerEvent(PointerEventPass.Final)
        if (final.changes.any { it.isConsumed }) return Press.Cancelled
    }
}

/**
 * runs a committed swipe to its end: fires once the finger has travelled far enough in the swipe
 * direction, and swallows every event until the lift so nothing behind the icon reacts to it.
 */
private suspend fun AwaitPointerEventScope.trackSwipe(
    down: PointerInputChange,
    direction: SwipeDirection,
    triggerPx: Float,
    onTrigger: () -> Unit,
) {
    var fired = false
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: return
        change.consume()
        if (!change.pressed) return
        if (fired) continue
        val dy = change.position.y - down.position.y
        val travelled = if (direction == SwipeDirection.UP) -dy else dy
        if (travelled >= triggerPx) {
            fired = true
            onTrigger()
        }
    }
}

// long enough that a sloppy drag never launches anything, short enough to feel immediate
private val SWIPE_TRIGGER = 40.dp

/**
 * long press and drag for hosted widgets. widgets are android views that take the touch stream
 * for their own buttons, so this watches in the initial pass, before the view sees anything,
 * and only claims the gesture once a long press is certain. from then on every event is consumed,
 * which hands the widget a cancel instead of a click. taps and scrolls never reach that point.
 * callbacks are read through updated state for the same reason as in [iconGestures].
 */
@Composable
fun Modifier.widgetGestures(
    coordinates: () -> LayoutCoordinates?,
    bounds: () -> Rect?,
    haptics: HapticFeedback,
    onLongClick: () -> Unit,
    dragHandler: IconDragHandler?,
): Modifier {
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentDragHandler by rememberUpdatedState(dragHandler)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var interrupted = false
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (!interrupted) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    interrupted = change == null || !change.pressed || change.isConsumed ||
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                }
            }
            if (interrupted) return@awaitEachGesture

            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            currentOnLongClick()
            val node = coordinates() ?: return@awaitEachGesture
            var dragging = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (dragging) {
                            dragging = false
                            currentDragHandler?.onDragEnd()
                        }
                        break
                    }
                    if (!node.isAttached) break
                    val fingerInRoot = node.localToRoot(change.position)
                    val handler = currentDragHandler
                    if (!dragging && handler != null && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        val itemBounds = bounds() ?: break
                        dragging = true
                        handler.onDragStart(fingerInRoot, itemBounds)
                    }
                    if (dragging) currentDragHandler?.onDragMove(fingerInRoot)
                }
            } finally {
                if (dragging) currentDragHandler?.onDragCancel()
            }
        }
    }
}
