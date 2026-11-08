package dev.gearturner.sparklauncher.core.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
 */
fun Modifier.iconGestures(
    coordinates: () -> LayoutCoordinates?,
    iconBounds: () -> Rect?,
    haptics: HapticFeedback,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    dragHandler: IconDragHandler?,
): Modifier = pointerInput(onClick, onLongClick, dragHandler) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        // claiming the press stops tap detectors behind the icon, such as the empty space long
        // press; scrolling still works because pagers and the drawer swipe ignore this claim
        down.consume()
        var up: PointerInputChange? = null
        val released = try {
            withTimeout(viewConfiguration.longPressTimeoutMillis) {
                up = waitForUpOrCancellation()
            }
            true
        } catch (e: PointerEventTimeoutCancellationException) {
            false
        }
        if (released) {
            // a scroll in a parent consumes the gesture, which cancels the tap instead of launching
            up?.let {
                it.consume()
                onClick()
            }
            return@awaitEachGesture
        }

        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onLongClick()
        val node = coordinates() ?: return@awaitEachGesture
        var dragging = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                change.consume()
                if (dragging) dragHandler?.onDragEnd()
                break
            }
            if (!node.isAttached) {
                if (dragging) dragHandler?.onDragCancel()
                break
            }
            val fingerInRoot = node.localToRoot(change.position)
            if (!dragging && dragHandler != null && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                val bounds = iconBounds() ?: break
                dragging = true
                dragHandler.onDragStart(fingerInRoot, bounds)
            }
            if (dragging) {
                change.consume()
                dragHandler?.onDragMove(fingerInRoot)
            }
        }
    }
}

/**
 * long press and drag for hosted widgets. widgets are android views that take the touch stream
 * for their own buttons, so this watches in the initial pass, before the view sees anything,
 * and only claims the gesture once a long press is certain. from then on every event is consumed,
 * which hands the widget a cancel instead of a click. taps and scrolls never reach that point.
 */
fun Modifier.widgetGestures(
    coordinates: () -> LayoutCoordinates?,
    bounds: () -> Rect?,
    haptics: HapticFeedback,
    onLongClick: () -> Unit,
    dragHandler: IconDragHandler?,
): Modifier = pointerInput(onLongClick, dragHandler) {
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
        onLongClick()
        val node = coordinates() ?: return@awaitEachGesture
        var dragging = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (dragging) dragHandler?.onDragEnd()
                break
            }
            if (!node.isAttached) {
                if (dragging) dragHandler?.onDragCancel()
                break
            }
            val fingerInRoot = node.localToRoot(change.position)
            if (!dragging && dragHandler != null && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                val itemBounds = bounds() ?: break
                dragging = true
                dragHandler.onDragStart(fingerInRoot, itemBounds)
            }
            if (dragging) dragHandler?.onDragMove(fingerInRoot)
        }
    }
}
