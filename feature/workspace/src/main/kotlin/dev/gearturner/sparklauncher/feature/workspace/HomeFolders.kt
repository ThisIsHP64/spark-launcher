package dev.gearturner.sparklauncher.feature.workspace

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.floor
import kotlin.math.ceil
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.animation.core.Spring
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.gearturner.sparklauncher.core.ui.DragController
import dev.gearturner.sparklauncher.core.ui.DragOrigin
import dev.gearturner.sparklauncher.core.ui.DragPayload
import dev.gearturner.sparklauncher.core.ui.LabelStyle
import dev.gearturner.sparklauncher.core.ui.LocalDragController
import dev.gearturner.sparklauncher.core.ui.LocalLabelLines
import dev.gearturner.sparklauncher.core.ui.rememberIcon
import dev.gearturner.sparklauncher.core.ui.rememberIconShape
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * which folder to open, where its icon sits so the panel can grow out of it, whether to rename, and
 * whether it was opened by holding a dragged app over it, in which case that app can be placed inside
 */
internal data class OpenFolder(val id: Long, val from: Rect?, val editName: Boolean = false, val incoming: Boolean = false)

/**
 * the state of a drag that started inside the open folder. it lives in the home screen so the
 * drop handler, which runs outside the folder's composition, can tell a reorder from a drag out.
 */
@Stable
internal class FolderDrag {
    /** set once the finger leaves the panel; from then on the drag belongs to home */
    var leftPanel by mutableStateOf(false)
    var hoverRank by mutableStateOf<Int?>(null)
    val geometry = FolderGeometry()

    fun reset() {
        leftPanel = false
        hoverRank = null
    }
}

/** a 2x2 preview of the first apps in a folder on a soft plate in the icon shape, like the pixel launcher */
@Composable
internal fun FolderPreview(folder: FolderTile, iconSize: Dp) {
    val miniSize = iconSize * 0.4f
    val gap = iconSize * 0.05f
    Box(
        modifier = Modifier
            .size(iconSize)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, rememberIconShape()),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            folder.children.take(4).chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    pair.forEach { child ->
                        val icon = rememberIcon(child.iconSource, miniSize)
                        if (icon != null) {
                            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(miniSize))
                        } else {
                            Box(modifier = Modifier.size(miniSize))
                        }
                    }
                }
            }
        }
    }
}

/**
 * an open folder that grows out of its icon. apps inside can be dragged to a new spot, with the
 * others sliding aside; dragging one past the panel's edge closes the folder and hands the app to
 * home. while that happens the panel stays composed but invisible, because removing it would end
 * the drag the app started.
 */
@Composable
internal fun FolderOverlay(
    folder: FolderTile,
    request: OpenFolder,
    rootOrigin: Offset,
    drag: FolderDrag,
    iconSize: Dp,
    viewModel: HomeViewModel,
    acceptsIncoming: (DragPayload) -> Boolean,
    onClose: () -> Unit,
) {
    val controller = LocalDragController.current
    val session = controller?.session
    val draggedId = (session?.payload as? DragPayload.Existing)?.itemId
        ?.takeIf { id -> session.origin == DragOrigin.Folder && folder.children.any { it.itemId == id } }
    val draggingOut = draggedId != null && drag.leftPanel
    // an app from outside held over the folder gets an extra slot, shown as a gap under the finger
    val incomingActive = request.incoming && session != null && draggedId == null && !drag.leftPanel &&
        acceptsIncoming(session.payload)

    val progress = remember(folder.itemId) { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val motion = MaterialTheme.motionScheme
    val defaultName = stringResource(R.string.workspace_folder_default_name)
    val nameState = rememberTextFieldState(folder.name.orEmpty())
    val nameFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    fun saveName() {
        val typed = nameState.text.toString().trim().ifEmpty { null }
        if (typed != folder.name) viewModel.rename(folder.itemId, typed)
    }

    LaunchedEffect(folder.itemId) { progress.animateTo(1f, motion.defaultSpatialSpec()) }
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        focusManager.clearFocus()
        saveName()
        progress.animateTo(0f, motion.fastSpatialSpec())
        onClose()
    }
    LaunchedEffect(request.editName) {
        if (!request.editName) return@LaunchedEffect
        // waits for the panel to be laid out so the keyboard opens over a placed field
        delay(FOCUS_DELAY_MS)
        nameState.edit { selectAll() }
        nameFocus.requestFocus()
    }
    TrackFolderDrag(controller, folder, request.incoming, acceptsIncoming, drag) { if (!closing) closing = true }
    BackHandler(enabled = !draggingOut && !closing) { closing = true }

    val count = folder.children.size + if (incomingActive) 1 else 0
    val columns = folderColumns(count)
    val rows = folderRows(count)
    val pages = folderPageCount(count)
    val scope = rememberCoroutineScope()
    val paging = remember { FolderPaging(scope) }
    paging.pageCount = pages
    paging.clamp()
    FolderEdgeFlip(controller, drag, paging)

    val density = LocalDensity.current
    val labelLines = LocalLabelLines.current
    val insets = WindowInsets.systemBars
    val placement = remember { PanelPlacement() }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // the panel never runs wider than the screen allows, so a cramped screen shrinks cells instead
        val cellWidth = minOf(maxOf(iconSize * 1.45f, MIN_CELL_WIDTH), (maxWidth - PANEL_MARGIN * 2 - PANEL_PADDING * 2) / columns)
        val cellHeight = iconSize + LABEL_SPACE + LINE_HEIGHT * (labelLines - 1)
        val gridWidthPx = with(density) { (cellWidth * columns).toPx() }
        drag.geometry.apply {
            this.cellWidth = gridWidthPx / columns
            this.cellHeight = with(density) { cellHeight.toPx() }
            this.columns = columns
            this.rows = rows
            this.count = count
            this.page = paging.currentPage
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (draggingOut) 0f else progress.value }
                .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (!closing) closing = true
                },
        )
        Column(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val margin = PANEL_MARGIN.roundToPx()
                    val panel = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = constraints.maxWidth - margin * 2))
                    // centered over the folder icon, then kept clear of the screen edges and system bars
                    val anchor = request.from?.translate(-rootOrigin)
                    val centerX = anchor?.center?.x ?: (constraints.maxWidth / 2f)
                    val centerY = anchor?.center?.y ?: (constraints.maxHeight / 2f)
                    val top = insets.getTop(this) + margin
                    val bottom = constraints.maxHeight - insets.getBottom(this) - margin
                    val x = (centerX - panel.width / 2f).roundToInt().coerceIn(margin, (constraints.maxWidth - margin - panel.width).coerceAtLeast(margin))
                    val y = (centerY - panel.height / 2f).roundToInt().coerceIn(top, (bottom - panel.height).coerceAtLeast(top))
                    placement.update(x, y, panel.width, panel.height, anchor)
                    layout(constraints.maxWidth, constraints.maxHeight) { panel.place(x, y) }
                }
                .onGloballyPositioned { drag.geometry.panelBounds = it.boundsInRoot() }
                .graphicsLayer {
                    val p = progress.value
                    val start = placement.startScale()
                    scaleX = lerp(start, 1f, p)
                    scaleY = lerp(start, 1f, p)
                    alpha = if (draggingOut) 0f else p.coerceIn(0f, 1f)
                    transformOrigin = placement.pivot()
                }
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.extraLarge)
                // taps inside the panel must not fall through to the scrim and close it
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    focusManager.clearFocus()
                    saveName()
                }
                .padding(horizontal = PANEL_PADDING, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val nameLabel = stringResource(R.string.workspace_a11y_folder_name)
            BasicTextField(
                state = nameState,
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                onKeyboardAction = {
                    focusManager.clearFocus()
                    saveName()
                },
                decorator = { field ->
                    Box(contentAlignment = Alignment.Center) {
                        // an unnamed folder shows its default name as a hint, so tapping it reads as renaming
                        if (nameState.text.isEmpty()) {
                            Text(
                                defaultName,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        field()
                    }
                },
                modifier = Modifier
                    .size(width = cellWidth * columns, height = NAME_HEIGHT)
                    .focusRequester(nameFocus)
                    .semantics { contentDescription = nameLabel },
            )
            FolderGrid(
                folder = folder,
                draggedId = draggedId,
                hoverRank = drag.hoverRank.takeIf { !drag.leftPanel },
                incomingRank = drag.hoverRank.takeIf { incomingActive },
                paging = paging,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                columns = columns,
                rows = rows,
                iconSize = iconSize,
                viewModel = viewModel,
                onGridBounds = { drag.geometry.gridBounds = it },
            )
            if (pages > 1) PageDots(paging, pages)
        }
    }
}

@Composable
private fun FolderGrid(
    folder: FolderTile,
    draggedId: Long?,
    hoverRank: Int?,
    incomingRank: Int?,
    paging: FolderPaging,
    cellWidth: Dp,
    cellHeight: Dp,
    columns: Int,
    rows: Int,
    iconSize: Dp,
    viewModel: HomeViewModel,
    onGridBounds: (Rect) -> Unit,
) {
    val density = LocalDensity.current
    val shownRank = if (incomingRank != null) {
        folder.children.withIndex().associate { (index, child) -> child.itemId to rankWithGap(index, incomingRank) }
    } else {
        displayOrder(folder.children, { it.itemId }, draggedId, hoverRank).withIndex().associate { (rank, child) -> child.itemId to rank }
    }
    val pageWidthPx = with(density) { (cellWidth * columns).roundToPx() }
    paging.pageWidth = pageWidthPx.toFloat()
    val swipe = rememberDraggableState { delta -> paging.dragBy(delta) }
    // every page lives on one strip that slides under a page sized window, so each app keeps the same
    // place in the layout however the order or the page changes, and a drag it started never breaks
    Box(
        modifier = Modifier
            .size(cellWidth * columns, cellHeight * rows)
            .clipToBounds()
            .onGloballyPositioned { onGridBounds(it.boundsInRoot()) }
            .draggable(
                state = swipe,
                orientation = Orientation.Horizontal,
                enabled = paging.pageCount > 1,
                onDragStopped = { velocity -> paging.settle(velocity) },
            ),
    ) {
        folder.children.forEach { child ->
            key(child.itemId) {
                val slot = slotOf(shownRank[child.itemId] ?: 0, columns)
                val target = with(density) {
                    IntOffset(
                        slot.page * pageWidthPx + (cellWidth * slot.column).roundToPx(),
                        (cellHeight * slot.row).roundToPx(),
                    )
                }
                val offset by animateIntOffsetAsState(target, spring(stiffness = SLIDE_STIFFNESS), label = "folder_slot")
                HomeItem(
                    cell = child,
                    registryPage = null,
                    iconSize = iconSize,
                    labelStyle = LabelStyle.OnSurface,
                    origin = DragOrigin.Folder,
                    viewModel = viewModel,
                    modifier = Modifier
                        .offset { IntOffset(offset.x - paging.offset.roundToInt(), offset.y) }
                        .size(cellWidth, cellHeight),
                )
            }
        }
    }
}

/**
 * the folder's horizontal page position in pixels. a finger drag moves it directly and a release
 * springs it to the nearest page, or the next one when flung.
 */
@Stable
private class FolderPaging(private val scope: CoroutineScope) {
    var offset by mutableFloatStateOf(0f)
        private set
    var pageWidth: Float = 1f
        set(value) {
            field = value.coerceAtLeast(1f)
        }
    var pageCount by mutableIntStateOf(1)
    private var settleJob: Job? = null

    val currentPage: Int get() = (offset / pageWidth).roundToInt().coerceIn(0, pageCount - 1)

    fun dragBy(delta: Float) {
        settleJob?.cancel()
        offset = (offset - delta).coerceIn(0f, (pageCount - 1) * pageWidth)
    }

    fun settle(velocity: Float) {
        val position = offset / pageWidth
        val target = when {
            velocity < -FLING_VELOCITY -> floor(position).toInt() + 1
            velocity > FLING_VELOCITY -> ceil(position).toInt() - 1
            else -> position.roundToInt()
        }
        animateToPage(target)
    }

    fun animateToPage(page: Int) {
        val target = page.coerceIn(0, pageCount - 1) * pageWidth
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(offset, target, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> offset = value }
        }
    }

    /** keeps the position valid when the folder shrinks to fewer pages */
    fun clamp() {
        val max = (pageCount - 1) * pageWidth
        if (offset > max) offset = max
    }
}

@Composable
private fun PageDots(paging: FolderPaging, pages: Int) {
    Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(pages) { index ->
            val color = if (index == paging.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
        }
    }
}

/**
 * keeps [drag] in step with a drag that started in this folder, or with an app from outside held
 * over it: the hovered slot, or that it left. an outside app that leaves closes the folder, since
 * nothing in it belongs to that drag.
 */
@Composable
private fun TrackFolderDrag(
    controller: DragController?,
    folder: FolderTile,
    incoming: Boolean,
    acceptsIncoming: (DragPayload) -> Boolean,
    drag: FolderDrag,
    onIncomingLeft: () -> Unit,
) {
    val slack = with(LocalDensity.current) { EXIT_SLACK.toPx() }
    LaunchedEffect(controller, folder.itemId, incoming) {
        if (controller == null) return@LaunchedEffect
        snapshotFlow { controller.session }.collect { session ->
            // the drop handler reads this state synchronously before the session clears, so a reset
            // here never races it, and it also covers drags that were cancelled rather than dropped
            if (session == null) {
                drag.reset()
                return@collect
            }
            val payload = session?.payload as? DragPayload.Existing
            val fromHere = session.origin == DragOrigin.Folder &&
                folder.children.any { it.itemId == payload?.itemId }
            val fromOutside = incoming && !fromHere && acceptsIncoming(session.payload)
            if ((!fromHere && !fromOutside) || drag.leftPanel) return@collect
            val finger = session.fingerInRoot
            if (drag.geometry.contains(finger, slack)) {
                drag.hoverRank = drag.geometry.rankAt(finger)
            } else {
                drag.leftPanel = true
                drag.hoverRank = null
                if (fromOutside) onIncomingLeft()
            }
        }
    }
}

/**
 * holding a dragged app just past the grid's left or right edge, in the panel's margin, turns the
 * page with the same dwell home uses. the band sits outside the grid so the edge columns stay
 * ordinary drop spots.
 */
@Composable
private fun FolderEdgeFlip(controller: DragController?, drag: FolderDrag, paging: FolderPaging) {
    val slack = with(LocalDensity.current) { EXIT_SLACK.toPx() }
    LaunchedEffect(controller, paging) {
        if (controller == null) return@LaunchedEffect
        snapshotFlow {
            val session = controller.session
            if (session == null || drag.leftPanel || drag.hoverRank == null) 0
            else drag.geometry.edgeDirection(session.fingerInRoot, slack)
        }
            .distinctUntilChanged()
            .collectLatest { direction ->
                while (direction != 0) {
                    delay(FOLDER_EDGE_DWELL_MS)
                    val target = (paging.currentPage + direction).coerceIn(0, paging.pageCount - 1)
                    if (target == paging.currentPage) break
                    paging.animateToPage(target)
                }
            }
    }
}

/** where the panel was placed and the icon it opens from, read by the scale animation */
private class PanelPlacement {
    private var x = 0
    private var y = 0
    private var width = 1
    private var height = 1
    private var anchor: Rect? = null

    fun update(x: Int, y: Int, width: Int, height: Int, anchor: Rect?) {
        this.x = x
        this.y = y
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        this.anchor = anchor
    }

    /** the panel starts at the folder icon's size, so it reads as the icon opening up */
    fun startScale(): Float = anchor?.let { (it.width / width).coerceIn(0.1f, 1f) } ?: OPEN_FALLBACK_SCALE

    fun pivot(): TransformOrigin {
        val center = anchor?.center ?: return TransformOrigin.Center
        return TransformOrigin(((center.x - x) / width).coerceIn(0f, 1f), ((center.y - y) / height).coerceIn(0f, 1f))
    }
}

private const val SCRIM_ALPHA = 0.25f
private const val OPEN_FALLBACK_SCALE = 0.8f
private const val SLIDE_STIFFNESS = 600f
private const val FOCUS_DELAY_MS = 150L
private const val FOLDER_EDGE_DWELL_MS = 550L
private const val FLING_VELOCITY = 600f
private val PANEL_MARGIN = 16.dp
private val PANEL_PADDING = 16.dp
private val MIN_CELL_WIDTH = 76.dp
private val LABEL_SPACE = 32.dp
private val LINE_HEIGHT = 16.dp
private val NAME_HEIGHT = 48.dp
private val EXIT_SLACK = 24.dp
