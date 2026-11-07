package dev.gearturner.sparklauncher.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import dev.gearturner.sparklauncher.core.model.AppKey
import kotlin.math.roundToInt

/** where a drag started, which decides what dropping it can mean */
enum class DragOrigin { Home, Folder, Drawer }

@Immutable
sealed interface DragPayload {
    /** an item already on home, in the dock or in a folder */
    data class Existing(val itemId: Long, val app: AppKey?, val canUninstall: Boolean) : DragPayload

    /** an app pulled out of the drawer, which becomes a new home item */
    data class NewApp(val key: AppKey, val canUninstall: Boolean) : DragPayload
}

@Immutable
data class DragSession(
    val payload: DragPayload,
    val origin: DragOrigin,
    val icon: ImageBitmap?,
    val iconSizePx: Float,
    /** where in the icon the finger grabbed it, so the icon does not jump under the finger */
    val grabOffset: Offset,
    val fingerInRoot: Offset,
)

/** decides what a released drag does; the home screen installs one while it is showing */
fun interface DropHandler {
    /** [onSettled] must run once the drop is written or refused, so the source icon can reappear */
    fun onDrop(session: DragSession, onSettled: () -> Unit)
}

/**
 * the one drag in progress, shared by home, folders and the drawer so a drag can start on one
 * surface and end on another.
 */
@Stable
class DragController {

    var session by mutableStateOf<DragSession?>(null)
        private set

    var dropHandler: DropHandler? = null

    val isDragging: Boolean get() = session != null

    fun start(session: DragSession) {
        this.session = session
    }

    fun move(fingerInRoot: Offset) {
        session = session?.copy(fingerInRoot = fingerInRoot)
    }

    fun drop(onSettled: () -> Unit) {
        val ended = session ?: return onSettled()
        session = null
        val handler = dropHandler
        if (handler == null) onSettled() else handler.onDrop(ended, onSettled)
    }

    fun cancel() {
        session = null
    }
}

val LocalDragController = staticCompositionLocalOf<DragController?> { null }

/** draws the dragged icon above everything, slightly enlarged the way the pixel launcher lifts it */
@Composable
fun DragLayer(controller: DragController, modifier: Modifier = Modifier) {
    val session = controller.session ?: return
    val sizeDp = with(LocalDensity.current) { session.iconSizePx.toDp() }
    Box(modifier = modifier.fillMaxSize()) {
        val lifted = Modifier
            .offset {
                val topLeft = session.fingerInRoot - session.grabOffset
                IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt())
            }
            .size(sizeDp)
            .graphicsLayer {
                scaleX = LIFT_SCALE
                scaleY = LIFT_SCALE
                shadowElevation = 12f
            }
        val icon = session.icon
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = lifted)
        } else {
            // folders have no single bitmap, so a soft plate stands in for them while they move
            Box(
                modifier = lifted.background(
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                    RoundedCornerShape(percent = 30),
                ),
            )
        }
    }
}

private const val LIFT_SCALE = 1.1f
