package dev.gearturner.sparklauncher.feature.workspace

import androidx.compose.ui.semantics.CustomAccessibilityAction
import dev.gearturner.sparklauncher.core.apps.DotKey
import dev.gearturner.sparklauncher.core.apps.dotKey
import dev.gearturner.sparklauncher.core.ui.LocalNotificationDots
import dev.gearturner.sparklauncher.core.ui.fallbackDotColor

import dev.gearturner.sparklauncher.core.model.SUBGRID
import dev.gearturner.sparklauncher.core.model.CellPlacement

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SwipeVertical
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.gearturner.sparklauncher.core.ui.AppIcon
import dev.gearturner.sparklauncher.core.ui.DragController
import dev.gearturner.sparklauncher.core.ui.DragOrigin
import dev.gearturner.sparklauncher.core.ui.DragPayload
import dev.gearturner.sparklauncher.core.ui.DismissOnStop
import dev.gearturner.sparklauncher.core.ui.DragSession
import dev.gearturner.sparklauncher.core.ui.IconDragHandler
import dev.gearturner.sparklauncher.core.ui.IconPickerDialog
import dev.gearturner.sparklauncher.core.ui.ItemMenu
import dev.gearturner.sparklauncher.core.ui.LabelStyle
import dev.gearturner.sparklauncher.core.ui.LaunchAnchor
import dev.gearturner.sparklauncher.core.ui.LauncherTile
import dev.gearturner.sparklauncher.core.ui.LocalDragController
import dev.gearturner.sparklauncher.core.ui.LocalWidgetPicker
import dev.gearturner.sparklauncher.core.ui.MenuAction
import dev.gearturner.sparklauncher.core.ui.MenuGroup
import dev.gearturner.sparklauncher.core.ui.RegisterHomeIcon
import dev.gearturner.sparklauncher.core.ui.RenameDialog
import dev.gearturner.sparklauncher.core.ui.SwipeActionsDialog
import dev.gearturner.sparklauncher.core.ui.SwipeChoice
import dev.gearturner.sparklauncher.core.ui.rememberLaunchAnchor
import dev.gearturner.sparklauncher.core.ui.HomeIconRegistry

/** horizontal breathing room around the page grid, shared with the drag math */
internal val GridPadding = 8.dp

@Composable
internal fun PageGrid(
    tiles: List<HomeTile>,
    columns: Int,
    rows: Int,
    iconSize: Dp,
    showLabels: Boolean,
    viewModel: HomeViewModel,
    onOpenFolder: (OpenFolder) -> Unit,
    onResizeWidget: (WidgetTile) -> Unit,
    onEmptyLongPress: (Offset) -> Unit,
    onEmptyDoubleTap: (() -> Unit)?,
) {
    val haptics = LocalHapticFeedback.current
    val doubleTap by rememberUpdatedState(onEmptyDoubleTap)
    val widgetPlacements by rememberUpdatedState(tiles.filterIsInstance<WidgetTile>().map { it.placement })
    var gridCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = GridPadding)
            .onGloballyPositioned { gridCoordinates = it }
            // tiles sit above this layer, so only presses on empty space reach it
            // keyed on whether double tap is used, because a double tap listener delays every single tap
            .pointerInput(onEmptyDoubleTap != null, columns, rows) {
                // a widget with transparent areas lets the touch fall through its view to this layer, but
                // the whole widget rect belongs to the widget, so its own menu is the only one that opens
                fun onWidget(local: Offset) = isOnWidget(
                    local,
                    widgetPlacements,
                    cellWidthPx = size.width / (columns * SUBGRID).toFloat(),
                    cellHeightPx = size.height / (rows * SUBGRID).toFloat(),
                )
                detectTapGestures(
                    onLongPress = { local ->
                        if (onWidget(local)) return@detectTapGestures
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        gridCoordinates?.takeIf { it.isAttached }?.let { onEmptyLongPress(it.localToRoot(local)) }
                    },
                    onDoubleTap = if (onEmptyDoubleTap != null) { local ->
                        if (!onWidget(local)) doubleTap?.invoke()
                    } else null,
                )
            },
    ) {
        // placements are in half cells, so offsets and sizes are measured in those
        val cellWidth = maxWidth / (columns * SUBGRID)
        val cellHeight = maxHeight / (rows * SUBGRID)
        tiles.forEach { tile ->
            // tiles arrive sorted by position, so moving or removing one reorders the rest; keyed by item,
            // each tile keeps its own widget view and state instead of inheriting its neighbour's
            key(tile.itemId) {
                HomeTileView(
                    tile = tile,
                    registryPage = tile.placement.page,
                    iconSize = iconSize,
                    labelStyle = if (showLabels) LabelStyle.OnWallpaper else LabelStyle.Hidden,
                    viewModel = viewModel,
                    onOpenFolder = onOpenFolder,
                    onResizeWidget = onResizeWidget,
                    modifier = Modifier
                        .offset(x = cellWidth * tile.placement.cellX, y = cellHeight * tile.placement.cellY)
                        .size(cellWidth * tile.placement.spanX, cellHeight * tile.placement.spanY),
                )
            }
        }
    }
}

/**
 * whether [point], in the page grid's own coordinates, lies inside any of the widget [placements],
 * which are in half cells. the far edges are exclusive, matching how cells are laid out.
 */
internal fun isOnWidget(point: Offset, placements: List<CellPlacement>, cellWidthPx: Float, cellHeightPx: Float): Boolean =
    placements.any { p ->
        point.x >= p.cellX * cellWidthPx && point.x < (p.cellX + p.spanX) * cellWidthPx &&
            point.y >= p.cellY * cellHeightPx && point.y < (p.cellY + p.spanY) * cellHeightPx
    }

@Composable
internal fun Dock(
    tiles: List<HomeTile>,
    slots: Int,
    iconSize: Dp,
    viewModel: HomeViewModel,
    onBounds: (Rect) -> Unit,
    onOpenFolder: (OpenFolder) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GridPadding, vertical = 8.dp)
            .height(iconSize + 24.dp)
            .onGloballyPositioned { onBounds(it.boundsInRoot()) },
    ) {
        val slotWidth = maxWidth / slots
        tiles.forEach { tile ->
            key(tile.itemId) {
                HomeTileView(
                    tile = tile,
                    registryPage = HomeIconRegistry.DOCK_PAGE,
                    iconSize = iconSize,
                    // the pixel dock omits labels so it reads as a distinct row
                    labelStyle = LabelStyle.Hidden,
                    viewModel = viewModel,
                    onOpenFolder = onOpenFolder,
                    onResizeWidget = {},
                    modifier = Modifier
                        .offset(x = slotWidth * tile.placement.cellX)
                        .size(slotWidth, maxHeight),
                )
            }
        }
    }
}

@Composable
private fun HomeTileView(
    tile: HomeTile,
    registryPage: Int,
    iconSize: Dp,
    labelStyle: LabelStyle,
    viewModel: HomeViewModel,
    onOpenFolder: (OpenFolder) -> Unit,
    onResizeWidget: (WidgetTile) -> Unit,
    modifier: Modifier,
) {
    // an app or folder that a dragged app would merge into grows, so it reads as a drop target
    val targeted = LocalDropHover.current == tile.itemId
    val scale by animateFloatAsState(if (targeted) DROP_TARGET_SCALE else 1f, label = "drop_target")
    val lifted = modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
    // dragging is the only other way to move things, so talkback gets single step moves instead
    val moves = if (registryPage == HomeIconRegistry.DOCK_PAGE) emptyList() else moveActions(tile.itemId, viewModel)
    when (tile) {
        is HomeCell -> HomeItem(tile, registryPage, iconSize, labelStyle, DragOrigin.Home, viewModel, lifted, moves)
        is FolderTile -> FolderTileView(tile, iconSize, labelStyle, viewModel, onOpenFolder, lifted, moves)
        is WidgetTile -> WidgetTileView(tile, viewModel, onResizeWidget, modifier, moves)
    }
}

/** the item a dragged app is hovering over and would join, or null */
internal val LocalDropHover = compositionLocalOf<Long?> { null }

private const val DROP_TARGET_SCALE = 1.15f

/**
 * an app or shortcut, on home, in the dock, or inside an open folder. [registryPage] is null inside
 * folders, whose apps are not visible targets for the swipe home animation.
 */
@Composable
internal fun HomeItem(
    cell: HomeCell,
    registryPage: Int?,
    iconSize: Dp,
    labelStyle: LabelStyle,
    origin: DragOrigin,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    accessibilityActions: List<CustomAccessibilityAction> = emptyList(),
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var changingIcon by remember { mutableStateOf(false) }
    var editingSwipes by remember { mutableStateOf(false) }
    DismissOnStop {
        menuOpen = false
        renaming = false
        changingIcon = false
        editingSwipes = false
    }
    val anchor = rememberLaunchAnchor()
    // only real app icons are close animation targets; a shortcut is not where its app lives
    if (cell.shortcut == null && registryPage != null) RegisterHomeIcon(cell.itemId, cell.app.key, registryPage, anchor)
    val actions = homeItemActions(
        cell, viewModel, anchor,
        onRename = { renaming = true },
        onChangeIcon = { changingIcon = true },
        onSwipeActions = { editingSwipes = true },
    )
    val swipeUp = cell.swipeUp?.let { action -> { viewModel.launchSwipe(action, anchor.request()) } }
    val swipeDown = cell.swipeDown?.let { action -> { viewModel.launchSwipe(action, anchor.request()) } }
    // swipes cannot be done with talkback's gestures, so each configured one is offered as an action
    val swipeLabels = listOf(
        cell.swipeUp?.let { stringResource(R.string.workspace_a11y_swipe_up, it.label) to swipeUp },
        cell.swipeDown?.let { stringResource(R.string.workspace_a11y_swipe_down, it.label) to swipeDown },
    )
    val allAccessibilityActions = accessibilityActions + swipeLabels.mapNotNull { entry ->
        entry?.second?.let { run -> CustomAccessibilityAction(entry.first) { run(); true } }
    }
    val controller = LocalDragController.current
    val payload = DragPayload.Existing(cell.itemId, cell.app.key, viewModel.canUninstall(cell))
    val dragHandler = remember(controller, payload, origin, cell.title) {
        controller?.let { itemDragHandler(it, anchor, payload, origin, closeMenu = { menuOpen = false }, label = cell.title) }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AppIcon(
            source = cell.iconSource,
            label = cell.title,
            iconSize = iconSize,
            labelStyle = labelStyle,
            anchor = anchor,
            onClick = { viewModel.launch(cell, anchor.request()) },
            onLongClick = { menuOpen = true },
            dragHandler = dragHandler,
            showDot = cell.hasDot(LocalNotificationDots.current),
            accessibilityActions = allAccessibilityActions,
            swipeUp = swipeUp,
            swipeDown = swipeDown,
        )
        ItemMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            iconBounds = anchor::boundsInRoot,
            owner = cell.app.key,
            loadShortcuts = { viewModel.menuShortcuts(cell) },
            onShortcutClick = { viewModel.launchShortcut(it, anchor.request()) },
            onPinShortcut = { viewModel.pinShortcut(it, cell.app.key) },
            actions = actions,
        )
    }
    if (renaming) {
        RenameDialog(
            currentName = cell.title,
            canReset = cell.renamed,
            onConfirm = { title ->
                renaming = false
                viewModel.rename(cell.itemId, title)
            },
            onDismiss = { renaming = false },
        )
    }
    if (editingSwipes && cell.shortcut == null) {
        SwipeActionsDialog(
            appLabel = cell.app.label,
            up = cell.swipeUp,
            down = cell.swipeDown,
            apps = remember { viewModel.swipeApps() },
            hasShortcutAccess = viewModel.hasShortcutAccess,
            loadShortcuts = viewModel::swipeShortcuts,
            onSet = { direction, target -> viewModel.setSwipe(cell.app.key, direction, target) },
            onDismiss = { editingSwipes = false },
        )
    }
    if (changingIcon) {
        IconPickerDialog(
            appLabel = cell.app.label,
            hasCustomIcon = viewModel.hasCustomIcon(cell.app.key),
            loadPacks = viewModel::iconPacks,
            loadNames = viewModel::packIconNames,
            onPick = {
                changingIcon = false
                viewModel.setCustomIcon(cell.app.key, it)
            },
            onReset = {
                changingIcon = false
                viewModel.setCustomIcon(cell.app.key, null)
            },
            onDismiss = { changingIcon = false },
        )
    }
}

@Composable
private fun FolderTileView(
    folder: FolderTile,
    iconSize: Dp,
    labelStyle: LabelStyle,
    viewModel: HomeViewModel,
    onOpenFolder: (OpenFolder) -> Unit,
    modifier: Modifier,
    accessibilityActions: List<CustomAccessibilityAction>,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val anchor = rememberLaunchAnchor()
    val controller = LocalDragController.current
    val payload = DragPayload.Existing(folder.itemId, app = null, canUninstall = false)
    val currentFolder by rememberUpdatedState(folder)
    val name = folder.name ?: stringResource(R.string.workspace_folder_default_name)
    val dots = LocalNotificationDots.current
    val dragHandler = remember(controller, payload, iconSize, name) {
        controller?.let {
            // the lifted copy is the folder's own preview, so it still looks like this folder mid drag
            itemDragHandler(it, anchor, payload, DragOrigin.Home, closeMenu = { menuOpen = false }, label = name) {
                FolderPreview(currentFolder, iconSize)
            }
        }
    }
    val rename = stringResource(R.string.workspace_rename)
    val remove = stringResource(R.string.workspace_remove_folder)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        LauncherTile(
            label = name,
            iconSize = iconSize,
            labelStyle = labelStyle,
            anchor = anchor,
            onClick = { onOpenFolder(OpenFolder(folder.itemId, anchor.boundsInRoot())) },
            onLongClick = { menuOpen = true },
            dragHandler = dragHandler,
            // a folder holds many apps with different colors, so its dot takes the theme color instead
            dotColor = fallbackDotColor().takeIf { folder.children.any { it.hasDot(dots) } },
            accessibilityActions = accessibilityActions,
        ) {
            FolderPreview(folder, iconSize)
        }
        ItemMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            iconBounds = anchor::boundsInRoot,
            actions = listOf(
                // renaming happens in the open folder's title, so the menu opens it ready to type
                MenuAction(rename, Icons.Outlined.Edit) { onOpenFolder(OpenFolder(folder.itemId, anchor.boundsInRoot(), editName = true)) },
                MenuAction(remove, Icons.Outlined.Close, MenuGroup.Destructive) { viewModel.remove(folder.itemId) },
            ),
        )
    }
}

/** one cell up, down, left, or right, or to the neighbouring page, as talkback custom actions */
@Composable
internal fun moveActions(itemId: Long, viewModel: HomeViewModel): List<CustomAccessibilityAction> {
    val left = stringResource(R.string.workspace_a11y_move_left)
    val right = stringResource(R.string.workspace_a11y_move_right)
    val up = stringResource(R.string.workspace_a11y_move_up)
    val down = stringResource(R.string.workspace_a11y_move_down)
    val previous = stringResource(R.string.workspace_a11y_move_previous_page)
    val next = stringResource(R.string.workspace_a11y_move_next_page)
    return remember(itemId, viewModel, left, right, up, down, previous, next) {
        fun move(label: String, dx: Int, dy: Int, dPage: Int) = CustomAccessibilityAction(label) {
            viewModel.moveBy(itemId, dx, dy, dPage)
            true
        }
        listOf(
            move(left, -1, 0, 0),
            move(right, 1, 0, 0),
            move(up, 0, -1, 0),
            move(down, 0, 1, 0),
            move(previous, 0, 0, -1),
            move(next, 0, 0, 1),
        )
    }
}

/** apps carry their notification dot; a pinned shortcut is not where the app's notifications live */
private fun HomeCell.hasDot(dots: Set<DotKey>): Boolean = shortcut == null && app.key.dotKey in dots

/**
 * turns a long press drag into a shared drag session. the source icon hides while its copy
 * follows the finger and comes back once the drop has been written or refused.
 */
internal fun itemDragHandler(
    controller: DragController,
    anchor: LaunchAnchor,
    payload: DragPayload,
    origin: DragOrigin,
    closeMenu: () -> Unit,
    label: String? = null,
    preview: (@Composable () -> Unit)? = null,
): IconDragHandler = object : IconDragHandler {
    override fun onDragStart(fingerInRoot: Offset, iconBoundsInRoot: Rect) {
        closeMenu()
        anchor.hidden = true
        controller.start(
            DragSession(
                payload = payload,
                origin = origin,
                icon = anchor.icon,
                sizePx = iconBoundsInRoot.size,
                grabOffset = fingerInRoot - iconBoundsInRoot.topLeft,
                fingerInRoot = fingerInRoot,
                preview = preview,
                label = label,
            ),
        )
    }

    override fun onDragMove(fingerInRoot: Offset) = controller.move(fingerInRoot)

    override fun onDragEnd() = controller.drop { anchor.hidden = false }

    override fun onDragCancel() {
        controller.cancel()
        anchor.hidden = false
    }
}

@Composable
private fun homeItemActions(
    cell: HomeCell,
    viewModel: HomeViewModel,
    anchor: LaunchAnchor,
    onRename: () -> Unit,
    onChangeIcon: () -> Unit,
    onSwipeActions: () -> Unit,
): List<MenuAction> {
    val appInfo = stringResource(R.string.workspace_app_info)
    val changeIcon = stringResource(R.string.workspace_change_icon)
    val rename = stringResource(R.string.workspace_rename)
    val remove = stringResource(R.string.workspace_remove)
    val hide = stringResource(R.string.workspace_hide)
    val uninstall = stringResource(R.string.workspace_uninstall)
    val swipeActions = stringResource(R.string.workspace_swipe_actions)
    val widgets = stringResource(R.string.workspace_widgets)
    val picker = LocalWidgetPicker.current
    return buildList {
        add(MenuAction(appInfo, Icons.Outlined.Info) { viewModel.openAppInfo(cell, anchor.request()) })
        if (picker != null && cell.shortcut == null && viewModel.hasWidgets(cell.app.key.packageName)) {
            add(MenuAction(widgets, Icons.Outlined.Widgets) { picker.open(cell.app.key.packageName) })
        }
        add(MenuAction(rename, Icons.Outlined.Edit, onClick = onRename))
        if (cell.shortcut == null && viewModel.canChangeIcon(cell.app.key)) add(MenuAction(changeIcon, Icons.Outlined.Image, onClick = onChangeIcon))
        if (cell.shortcut == null) add(MenuAction(swipeActions, Icons.Outlined.SwipeVertical, onClick = onSwipeActions))
        if (viewModel.canHide(cell)) add(MenuAction(hide, Icons.Outlined.VisibilityOff) { viewModel.hide(cell) })
        add(MenuAction(remove, Icons.Outlined.Close, MenuGroup.Destructive) { viewModel.remove(cell.itemId) })
        if (viewModel.canUninstall(cell)) {
            add(MenuAction(uninstall, Icons.Outlined.Delete, MenuGroup.Destructive) { viewModel.uninstall(cell.app.key) })
        }
    }
}
