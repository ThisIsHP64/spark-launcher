package dev.gearturner.sparklauncher.core.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * shared open progress of the app drawer, from 0 closed to 1 open.
 * home and drawer both drive it, so a swipe can start on one surface and settle on the other.
 * drags write the value synchronously so fast flicks never race a queued animation.
 */
@Stable
class DrawerController internal constructor(private val scope: CoroutineScope) {

    private var progress by mutableFloatStateOf(0f)
    private var settleJob: Job? = null

    /** the screen height in pixels; finger travel is divided by it to get progress */
    var travelPx: Float = 1f
        set(value) {
            field = value.coerceAtLeast(1f)
        }

    /** set when the drawer is opened to search, so the field takes focus and raises the keyboard */
    var searchRequested: Boolean by mutableStateOf(false)
        private set

    val value: Float get() = progress
    val isOpen: Boolean get() = progress > 0.5f
    val isVisible: Boolean get() = progress > 0f

    /** positive delta means the finger moved up, which opens the drawer */
    fun dragBy(deltaPx: Float) {
        settleJob?.cancel()
        progress = (progress + deltaPx / travelPx).coerceIn(0f, 1f)
    }

    /** velocity is in pixels per second, positive when flinging upward */
    fun settle(velocityPx: Float) {
        val target = when {
            velocityPx > FLING_THRESHOLD -> 1f
            velocityPx < -FLING_THRESHOLD -> 0f
            else -> if (progress > 0.5f) 1f else 0f
        }
        animateTo(target, velocityPx / travelPx)
    }

    fun open() = animateTo(1f, 0f)

    fun openSearch() {
        searchRequested = true
        open()
    }

    fun consumeSearchRequest() {
        searchRequested = false
    }

    fun close() = animateTo(0f, 0f)

    /** jumps shut with no animation, for when the launcher is hidden and nobody would see it move */
    fun snapClosed() {
        settleJob?.cancel()
        progress = 0f
    }

    private fun animateTo(target: Float, initialVelocity: Float) {
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(
                initialValue = progress,
                targetValue = target,
                initialVelocity = initialVelocity,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f),
            ) { value, _ -> progress = value.coerceIn(0f, 1f) }
        }
    }

    private companion object {
        // matches the fling speed aosp uses to commit to opening all apps
        const val FLING_THRESHOLD = 800f
    }
}

/** lets menus inside the drawer close it, for example before opening the widget picker */
val LocalDrawer = staticCompositionLocalOf<DrawerController?> { null }

@Composable
fun rememberDrawerController(): DrawerController {
    val scope = rememberCoroutineScope()
    return remember(scope) { DrawerController(scope) }
}
