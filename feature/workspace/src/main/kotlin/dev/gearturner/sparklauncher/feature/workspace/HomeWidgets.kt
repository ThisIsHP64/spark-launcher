package dev.gearturner.sparklauncher.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.gearturner.sparklauncher.core.model.CellPlacement
import dev.gearturner.sparklauncher.core.model.Span
import dev.gearturner.sparklauncher.core.model.WidgetOptions
import dev.gearturner.sparklauncher.core.model.resizeLimits
import dev.gearturner.sparklauncher.core.ui.DragOrigin
import dev.gearturner.sparklauncher.core.ui.DragPayload
import dev.gearturner.sparklauncher.core.ui.ItemMenu
import dev.gearturner.sparklauncher.core.ui.LocalDragController
import dev.gearturner.sparklauncher.core.ui.MenuAction
import dev.gearturner.sparklauncher.core.ui.MenuGroup
import dev.gearturner.sparklauncher.core.ui.rememberLaunchAnchor
import dev.gearturner.sparklauncher.feature.widgets.HostedWidget
import dev.gearturner.sparklauncher.feature.widgets.LocalWidgetBinder
import dev.gearturner.sparklauncher.feature.widgets.WidgetOptionsDialog
import kotlin.math.roundToInt

@Composable
internal fun WidgetTileView(
    widget: WidgetTile,
    viewModel: HomeViewModel,
    onResize: (WidgetTile) -> Unit,
    modifier: Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var editingOptions by remember { mutableStateOf(false) }
    // a live preview while the options dialog is open, so sliders show their effect immediately
    var previewOptions by remember { mutableStateOf<WidgetOptions?>(null) }
    val binder = LocalWidgetBinder.current
    val controller = LocalDragController.current
    val anchor = rememberLaunchAnchor()
    val payload = DragPayload.Existing(
        itemId = widget.itemId,
        app = null,
        canUninstall = false,
        spanX = widget.placement.spanX,
        spanY = widget.placement.spanY,
    )
    val dragHandler = remember(controller, payload) {
        controller?.let { itemDragHandler(it, anchor, payload, DragOrigin.Home, closeMenu = { menuOpen = false }) }
    }
    val resize = stringResource(R.string.workspace_widget_resize)
    val options = stringResource(R.string.workspace_widget_options)
    val reconfigure = stringResource(R.string.workspace_widget_reconfigure)
    val remove = stringResource(R.string.workspace_remove)
    Box(modifier = modifier) {
        HostedWidget(
            appWidgetId = widget.appWidgetId,
            options = previewOptions ?: widget.options,
            onLongClick = { menuOpen = true },
            dragHandler = dragHandler,
            modifier = Modifier.fillMaxSize(),
        )
        ItemMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            actions = buildList {
                add(MenuAction(resize, Icons.Outlined.AspectRatio) { onResize(widget) })
                add(MenuAction(options, Icons.Outlined.Tune) { editingOptions = true })
                if (widget.provider?.reconfigurable == true) {
                    add(MenuAction(reconfigure, Icons.Outlined.Settings) { viewModel.reconfigureWidget(widget.appWidgetId, binder) })
                }
                add(MenuAction(remove, Icons.Outlined.Close, MenuGroup.Destructive) { viewModel.remove(widget.itemId) })
            },
        )
    }
    if (editingOptions) {
        WidgetOptionsDialog(
            initial = widget.options,
            onPreview = { previewOptions = it },
            onSave = {
                editingOptions = false
                previewOptions = null
                viewModel.updateWidgetOptions(widget.itemId, it)
            },
            onDismiss = {
                editingOptions = false
                previewOptions = null
            },
        )
    }
}

/**
 * a frame with four handles around a widget. handles snap to whole cells as they move, within
 * the limits the provider allows unless the user turned those off for this widget, and the new
 * size is saved when a handle is let go. tapping anywhere else ends resizing.
 */
@Composable
internal fun ResizeFrame(
    widget: WidgetTile,
    geometry: HomeGeometry,
    rootOrigin: Offset,
    viewModel: HomeViewModel,
    onDone: () -> Unit,
) {
    val density = LocalDensity.current
    var preview by remember(widget.placement) { mutableStateOf(widget.placement) }
    val cellWidth = geometry.cellWidthPx()
    val cellHeight = geometry.cellHeightPx()
    val rect = geometry.rectOf(preview)?.translate(-rootOrigin)
    if (rect == null || cellWidth <= 0f || cellHeight <= 0f) return
    val (min, max) = remember(widget.provider, widget.options.ignoreSizeLimits, widget.placement, cellWidth, cellHeight) {
        val spec = widget.provider?.sizeSpec
        if (spec == null) {
            Span(1, 1) to Span(geometry.columns, geometry.rows)
        } else {
            with(density) {
                resizeLimits(
                    spec = spec,
                    current = Span(widget.placement.spanX, widget.placement.spanY),
                    cellWidthDp = cellWidth.toDp().value,
                    cellHeightDp = cellHeight.toDp().value,
                    columns = geometry.columns,
                    rows = geometry.rows,
                    freeResize = widget.options.ignoreSizeLimits,
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
        )
        ResizeEdge.entries.forEach { edge ->
            ResizeHandle(
                center = edge.center(rect),
                onDrag = { total ->
                    preview = edge.resize(widget.placement, total, cellWidth, cellHeight, min, max, geometry.columns, geometry.rows)
                },
                onRelease = { if (preview != widget.placement) viewModel.resizeWidget(widget.itemId, preview) },
            )
        }
    }
}

@Composable
private fun ResizeHandle(center: Offset, onDrag: (Offset) -> Unit, onRelease: () -> Unit) {
    val density = LocalDensity.current
    // the gesture outlives recompositions, so it must read the newest size each time, not the first one
    val latestDrag by rememberUpdatedState(onDrag)
    val latestRelease by rememberUpdatedState(onRelease)
    val size = 28.dp
    val half = with(density) { size.toPx() / 2f }
    Box(
        modifier = Modifier
            .offset { IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt()) }
            .size(size)
            .pointerInput(Unit) {
                var total = Offset.Zero
                detectDragGestures(
                    onDragStart = { total = Offset.Zero },
                    onDragEnd = { latestRelease() },
                    onDragCancel = { latestRelease() },
                ) { change, amount ->
                    change.consume()
                    total += amount
                    latestDrag(total)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .border(2.dp, Color.White, CircleShape),
        )
    }
}

private enum class ResizeEdge {
    Left, Top, Right, Bottom;

    fun center(rect: Rect): Offset = when (this) {
        Left -> Offset(rect.left, rect.center.y)
        Top -> Offset(rect.center.x, rect.top)
        Right -> Offset(rect.right, rect.center.y)
        Bottom -> Offset(rect.center.x, rect.bottom)
    }

    /** moves one edge by the drag, snapped to cells and kept inside the span limits and the grid */
    fun resize(
        start: CellPlacement,
        drag: Offset,
        cellWidth: Float,
        cellHeight: Float,
        min: Span,
        max: Span,
        columns: Int,
        rows: Int,
    ): CellPlacement {
        val dx = (drag.x / cellWidth).roundToInt()
        val dy = (drag.y / cellHeight).roundToInt()
        val right = start.cellX + start.spanX
        val bottom = start.cellY + start.spanY
        return when (this) {
            Right -> start.copy(spanX = (start.spanX + dx).coerceIn(min.x, minOf(max.x, columns - start.cellX)))
            Bottom -> start.copy(spanY = (start.spanY + dy).coerceIn(min.y, minOf(max.y, rows - start.cellY)))
            Left -> {
                val span = (start.spanX - dx).coerceIn(min.x, minOf(max.x, right))
                start.copy(cellX = right - span, spanX = span)
            }
            Top -> {
                val span = (start.spanY - dy).coerceIn(min.y, minOf(max.y, bottom))
                start.copy(cellY = bottom - span, spanY = span)
            }
        }
    }
}
