package dev.gearturner.sparklauncher.feature.workspace

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.foundation.systemGestureExclusion
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
import dev.gearturner.sparklauncher.core.model.SUBGRID
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize

@Composable
internal fun WidgetTileView(
    widget: WidgetTile,
    viewModel: HomeViewModel,
    onResize: (WidgetTile) -> Unit,
    modifier: Modifier,
    accessibilityActions: List<CustomAccessibilityAction>,
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
        isWidget = true,
    )
    // the widget draws into this layer every frame, so the lifted copy shows its real, updating content
    val live = rememberGraphicsLayer()
    // a still copy for when the source page scrolls out of composition mid drag and the layer is released
    val snapshot = remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(anchor.hidden) {
        if (anchor.hidden && !live.isReleased) snapshot.value = live.toImageBitmap()
    }
    val dragHandler = remember(controller, payload) {
        controller?.let {
            itemDragHandler(it, anchor, payload, DragOrigin.Home, closeMenu = { menuOpen = false }) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (!live.isReleased) {
                        drawLayer(live)
                    } else {
                        snapshot.value?.let { image -> drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())) }
                    }
                }
            }
        }
    }
    val resize = stringResource(R.string.workspace_widget_resize)
    val options = stringResource(R.string.workspace_widget_options)
    val reconfigure = stringResource(R.string.workspace_widget_reconfigure)
    val remove = stringResource(R.string.workspace_remove)
    val widgetMenu = stringResource(R.string.workspace_a11y_widget_menu)
    // the widget's own views stay readable by talkback; moving and the menu are offered beside them
    Box(
        modifier = modifier.semantics {
            customActions = accessibilityActions + CustomAccessibilityAction(widgetMenu) {
                menuOpen = true
                true
            }
        },
    ) {
        HostedWidget(
            appWidgetId = widget.appWidgetId,
            options = previewOptions ?: widget.options,
            onLongClick = { menuOpen = true },
            dragHandler = dragHandler,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    live.record { this@drawWithContent.drawContent() }
                    // the source spot stays empty while its copy follows the finger, like an icon
                    if (!anchor.hidden) drawLayer(live)
                },
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
 * a frame with four handles around a widget. handles snap to whole cells as they move, or half
 * cells with subgrid positioning, within
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
    val step = geometry.snapStep()
    val gridColumns = geometry.columns * SUBGRID
    val gridRows = geometry.rows * SUBGRID
    val (min, max) = remember(widget.provider, widget.options.ignoreSizeLimits, widget.placement, cellWidth, cellHeight, step) {
        val spec = widget.provider?.sizeSpec
        val current = Span(widget.placement.spanX, widget.placement.spanY)
        when {
            spec == null -> Span(step, step) to Span(gridColumns, gridRows)
            step == 1 -> with(density) {
                // measured against half cells, so the limits come out in the same units as placements
                resizeLimits(
                    spec = spec,
                    current = current,
                    cellWidthDp = (cellWidth / SUBGRID).toDp().value,
                    cellHeightDp = (cellHeight / SUBGRID).toDp().value,
                    columns = gridColumns,
                    rows = gridRows,
                    freeResize = widget.options.ignoreSizeLimits,
                )
            }
            else -> with(density) {
                val (cellMin, cellMax) = resizeLimits(
                    spec = spec,
                    current = Span((current.x + SUBGRID - 1) / SUBGRID, (current.y + SUBGRID - 1) / SUBGRID),
                    cellWidthDp = cellWidth.toDp().value,
                    cellHeightDp = cellHeight.toDp().value,
                    columns = geometry.columns,
                    rows = geometry.rows,
                    freeResize = widget.options.ignoreSizeLimits,
                )
                // a widget sized in half cells earlier keeps that size as a valid choice
                Span(minOf(cellMin.x * SUBGRID, current.x), minOf(cellMin.y * SUBGRID, current.y)) to
                    Span(maxOf(cellMax.x * SUBGRID, current.x), maxOf(cellMax.y * SUBGRID, current.y))
            }
        }
    }

    // the handles need a drag, so talkback resizes one snap step at a time through actions instead
    fun stepped(edge: ResizeEdge, sign: Float, horizontal: Boolean): CellPlacement {
        val stepWidth = geometry.unitWidthPx() * step
        val stepHeight = geometry.unitHeightPx() * step
        val drag = if (horizontal) Offset(sign * stepWidth, 0f) else Offset(0f, sign * stepHeight)
        return edge.resize(widget.placement, drag, stepWidth, stepHeight, step, min, max, gridColumns, gridRows)
    }
    val wider = stringResource(R.string.workspace_a11y_wider)
    val narrower = stringResource(R.string.workspace_a11y_narrower)
    val taller = stringResource(R.string.workspace_a11y_taller)
    val shorter = stringResource(R.string.workspace_a11y_shorter)
    val frameLabel = stringResource(R.string.workspace_a11y_resize_frame)
    val doneLabel = stringResource(R.string.workspace_a11y_resize_done)
    val resizeActions = listOf(
        wider to stepped(ResizeEdge.Right, 1f, horizontal = true),
        narrower to stepped(ResizeEdge.Right, -1f, horizontal = true),
        taller to stepped(ResizeEdge.Bottom, 1f, horizontal = false),
        shorter to stepped(ResizeEdge.Bottom, -1f, horizontal = false),
    ).map { (label, next) ->
        CustomAccessibilityAction(label) {
            if (next != widget.placement) viewModel.resizeWidget(widget.itemId, next)
            true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = doneLabel,
                onClick = onDone,
            ),
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                .semantics {
                    contentDescription = frameLabel
                    customActions = resizeActions
                },
        )
        ResizeEdge.entries.forEach { edge ->
            ResizeHandle(
                center = edge.center(rect),
                onDrag = { total ->
                    preview = edge.resize(
                        widget.placement,
                        total,
                        geometry.unitWidthPx() * step,
                        geometry.unitHeightPx() * step,
                        step,
                        min,
                        max,
                        gridColumns,
                        gridRows,
                    )
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
    // the standard minimum touch target, so a thumb that lands off center still grabs the handle
    val size = 48.dp
    val half = with(density) { size.toPx() / 2f }
    Box(
        modifier = Modifier
            .offset { IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt()) }
            // talkback resizes through the frame's actions; a handle alone has nothing to announce
            .clearAndSetSemantics { }
            .size(size)
            // handles of widgets in the outer columns sit inside the back gesture zone, which would
            // otherwise take the drag; the exclusion is read after offset and size so it matches the box
            .systemGestureExclusion()
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

    /**
     * moves one edge by the drag, snapped to [step] half cells and kept inside the span limits and
     * the grid. [stepWidth] and [stepHeight] are the on screen size of one step; spans and the grid
     * size are in half cells.
     */
    fun resize(
        start: CellPlacement,
        drag: Offset,
        stepWidth: Float,
        stepHeight: Float,
        step: Int,
        min: Span,
        max: Span,
        columns: Int,
        rows: Int,
    ): CellPlacement {
        val dx = (drag.x / stepWidth).roundToInt() * step
        val dy = (drag.y / stepHeight).roundToInt() * step
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
