package dev.gearturner.sparklauncher.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import dev.gearturner.sparklauncher.core.model.AppKey
import kotlin.math.roundToInt

/** where a drag started, which decides what dropping it can mean */
enum class DragOrigin { Home, Folder, Drawer, WidgetPicker }

@Immutable
sealed interface DragPayload {
    /** an item already on home, in the dock, or in a folder; widgets carry their size in half cells */
    data class Existing(
        val itemId: Long,
        val app: AppKey?,
        val canUninstall: Boolean,
        val spanX: Int = 1,
        val spanY: Int = 1,
        val isWidget: Boolean = false,
    ) : DragPayload

    /** an app pulled out of the drawer, which becomes a new home item */
    data class NewApp(val key: AppKey, val canUninstall: Boolean) : DragPayload

    /** a widget pulled out of the picker; it is bound to the system only once it lands somewhere valid */
    data class NewWidget(val provider: AppKey, val spanX: Int, val spanY: Int) : DragPayload
}

val DragPayload.spanX: Int
    get() = when (this) {
        is DragPayload.Existing -> spanX
        is DragPayload.NewApp -> 1
        is DragPayload.NewWidget -> spanX
    }

val DragPayload.spanY: Int
    get() = when (this) {
        is DragPayload.Existing -> spanY
        is DragPayload.NewApp -> 1
        is DragPayload.NewWidget -> spanY
    }

@Immutable
data class DragSession(
    val payload: DragPayload,
    val origin: DragOrigin,
    val icon: ImageBitmap?,
    /** the size of the lifted copy; widgets keep their full size so the user sees what will fit */
    val sizePx: Size,
    /** where in the icon the finger grabbed it, so the icon does not jump under the finger */
    val grabOffset: Offset,
    val fingerInRoot: Offset,
    /** drawn instead of [icon] for items with no single bitmap, such as a folder's app preview */
    val preview: (@Composable () -> Unit)? = null,
    /** the name under an app or folder, carried along so the whole tile moves together */
    val label: String? = null,
)

/** decides what a released drag does; the home screen installs one while it is showing */
fun interface DropHandler {
    /** [onSettled] must run once the drop is written or refused, so the source icon can reappear */
    fun onDrop(session: DragSession, onSettled: () -> Unit)
}

/**
 * the one drag in progress, shared by home, folders, and the drawer so a drag can start on one
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

/**
 * draws the dragged icon above everything, slightly enlarged the way the pixel launcher lifts it.
 * [showLabels] follows the home label setting, since the item is always dropped onto home.
 */
@Composable
fun DragLayer(controller: DragController, showLabels: Boolean, modifier: Modifier = Modifier) {
    val session = controller.session ?: return
    val widthDp = with(LocalDensity.current) { session.sizePx.width.toDp() }
    val heightDp = with(LocalDensity.current) { session.sizePx.height.toDp() }
    Box(modifier = modifier.fillMaxSize()) {
        val lifted = Modifier
            .offset {
                val topLeft = session.fingerInRoot - session.grabOffset
                IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt())
            }
            .size(widthDp, heightDp)
            .graphicsLayer {
                scaleX = LIFT_SCALE
                scaleY = LIFT_SCALE
                shadowElevation = 12f
            }
        val icon = session.icon
        val preview = session.preview
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = lifted)
        } else if (preview != null) {
            Box(modifier = lifted) { preview() }
        } else {
            // widgets have no single bitmap, so a soft plate stands in while they move
            Box(
                modifier = lifted.background(
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                    RoundedCornerShape(24.dp),
                ),
            )
        }
        val label = session.label
        if (showLabels && label != null) {
            val gap = with(LocalDensity.current) { LABEL_GAP.toPx() }
            val labelWidthPx = with(LocalDensity.current) { LABEL_WIDTH.toPx() }
            Box(
                modifier = Modifier
                    .offset {
                        // sits under the lifted icon, which grows around its own center
                        val topLeft = session.fingerInRoot - session.grabOffset
                        val iconBottom = topLeft.y + session.sizePx.height * (1f + LIFT_SCALE) / 2f
                        val left = topLeft.x + session.sizePx.width / 2f - labelWidthPx / 2f
                        IntOffset(left.roundToInt(), (iconBottom + gap).roundToInt())
                    }
                    .width(LABEL_WIDTH),
                contentAlignment = Alignment.TopCenter,
            ) {
                IconLabel(label, LabelStyle.OnWallpaper)
            }
        }
    }
}

private val LABEL_GAP = 6.dp
private val LABEL_WIDTH = 96.dp

private const val LIFT_SCALE = 1.1f
