package dev.gearturner.sparklauncher.feature.workspace

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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.gearturner.sparklauncher.core.ui.DragSession
import dev.gearturner.sparklauncher.core.ui.IconDragHandler
import dev.gearturner.sparklauncher.core.ui.ItemMenu
import dev.gearturner.sparklauncher.core.ui.LabelStyle
import dev.gearturner.sparklauncher.core.ui.LaunchAnchor
import dev.gearturner.sparklauncher.core.ui.LauncherTile
import dev.gearturner.sparklauncher.core.ui.LocalDragController
import dev.gearturner.sparklauncher.core.ui.LocalWidgetPicker
import dev.gearturner.sparklauncher.core.ui.MenuAction
import dev.gearturner.sparklauncher.core.ui.RegisterHomeIcon
import dev.gearturner.sparklauncher.core.ui.RenameDialog
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
    cornerRadiusPercent: Int,
    showLabels: Boolean,
    viewModel: HomeViewModel,
    onOpenFolder: (Long) -> Unit,
    onResizeWidget: (WidgetTile) -> Unit,
    onEmptyLongPress: (Offset) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var gridCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = GridPadding)
            .onGloballyPositioned { gridCoordinates = it }
            // tiles sit above this layer, so only presses on empty space reach it
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { local ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    gridCoordinates?.takeIf { it.isAttached }?.let { onEmptyLongPress(it.localToRoot(local)) }
                })
            },
    ) {
        val cellWidth = maxWidth / columns
        val cellHeight = maxHeight / rows
        tiles.forEach { tile ->
            HomeTileView(
                tile = tile,
                registryPage = tile.placement.page,
                iconSize = iconSize,
                cornerRadiusPercent = cornerRadiusPercent,
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

@Composable
internal fun Dock(
    tiles: List<HomeTile>,
    slots: Int,
    iconSize: Dp,
    cornerRadiusPercent: Int,
    viewModel: HomeViewModel,
    onBounds: (Rect) -> Unit,
    onOpenFolder: (Long) -> Unit,
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
            HomeTileView(
                tile = tile,
                registryPage = HomeIconRegistry.DOCK_PAGE,
                iconSize = iconSize,
                cornerRadiusPercent = cornerRadiusPercent,
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

@Composable
private fun HomeTileView(
    tile: HomeTile,
    registryPage: Int,
    iconSize: Dp,
    cornerRadiusPercent: Int,
    labelStyle: LabelStyle,
    viewModel: HomeViewModel,
    onOpenFolder: (Long) -> Unit,
    onResizeWidget: (WidgetTile) -> Unit,
    modifier: Modifier,
) {
    when (tile) {
        is HomeCell -> HomeItem(tile, registryPage, iconSize, cornerRadiusPercent, labelStyle, DragOrigin.Home, viewModel, modifier)
        is FolderTile -> FolderTileView(tile, iconSize, cornerRadiusPercent, labelStyle, viewModel, onOpenFolder, modifier)
        is WidgetTile -> WidgetTileView(tile, viewModel, onResizeWidget, modifier)
    }
}

/**
 * an app or shortcut, on home, in the dock or inside an open folder. [registryPage] is null inside
 * folders, whose apps are not visible targets for the swipe home animation.
 */
@Composable
internal fun HomeItem(
    cell: HomeCell,
    registryPage: Int?,
    iconSize: Dp,
    cornerRadiusPercent: Int,
    labelStyle: LabelStyle,
    origin: DragOrigin,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val anchor = rememberLaunchAnchor()
    // only real app icons are close animation targets; a shortcut is not where its app lives
    if (cell.shortcut == null && registryPage != null) RegisterHomeIcon(cell.itemId, cell.app.key, registryPage, anchor)
    val actions = homeItemActions(cell, viewModel, anchor, onRename = { renaming = true })
    val controller = LocalDragController.current
    val payload = DragPayload.Existing(cell.itemId, cell.app.key, viewModel.canUninstall(cell))
    val dragHandler = remember(controller, payload, origin) {
        controller?.let { itemDragHandler(it, anchor, payload, origin, closeMenu = { menuOpen = false }) }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AppIcon(
            source = cell.iconSource,
            label = cell.title,
            iconSize = iconSize,
            cornerRadiusPercent = cornerRadiusPercent,
            labelStyle = labelStyle,
            anchor = anchor,
            onClick = { viewModel.launch(cell, anchor.request()) },
            onLongClick = { menuOpen = true },
            dragHandler = dragHandler,
        )
        ItemMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            owner = cell.app.key,
            loadShortcuts = { viewModel.menuShortcuts(cell) },
            onShortcutClick = { viewModel.launchShortcut(it, anchor.request()) },
            onPinShortcut = { viewModel.pinShortcut(it, cell.app.key) },
            actions = actions,
            cornerRadiusPercent = cornerRadiusPercent,
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
}

@Composable
private fun FolderTileView(
    folder: FolderTile,
    iconSize: Dp,
    cornerRadiusPercent: Int,
    labelStyle: LabelStyle,
    viewModel: HomeViewModel,
    onOpenFolder: (Long) -> Unit,
    modifier: Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val anchor = rememberLaunchAnchor()
    val controller = LocalDragController.current
    val payload = DragPayload.Existing(folder.itemId, app = null, canUninstall = false)
    val dragHandler = remember(controller, payload) {
        controller?.let { itemDragHandler(it, anchor, payload, DragOrigin.Home, closeMenu = { menuOpen = false }) }
    }
    val name = folder.name ?: stringResource(R.string.workspace_folder_default_name)
    val rename = stringResource(R.string.workspace_rename)
    val remove = stringResource(R.string.workspace_remove_folder)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        LauncherTile(
            label = name,
            iconSize = iconSize,
            labelStyle = labelStyle,
            anchor = anchor,
            onClick = { onOpenFolder(folder.itemId) },
            onLongClick = { menuOpen = true },
            dragHandler = dragHandler,
        ) {
            FolderPreview(folder, iconSize, cornerRadiusPercent)
        }
        ItemMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            owner = null,
            loadShortcuts = { emptyList() },
            onShortcutClick = {},
            onPinShortcut = null,
            actions = listOf(
                MenuAction(rename, Icons.Outlined.Edit) { renaming = true },
                MenuAction(remove, Icons.Outlined.Close) { viewModel.remove(folder.itemId) },
            ),
            cornerRadiusPercent = cornerRadiusPercent,
        )
    }
    if (renaming) {
        RenameDialog(
            currentName = folder.name.orEmpty(),
            canReset = folder.name != null,
            onConfirm = { title ->
                renaming = false
                viewModel.rename(folder.itemId, title)
            },
            onDismiss = { renaming = false },
        )
    }
}

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
): List<MenuAction> {
    val appInfo = stringResource(R.string.workspace_app_info)
    val rename = stringResource(R.string.workspace_rename)
    val remove = stringResource(R.string.workspace_remove)
    val uninstall = stringResource(R.string.workspace_uninstall)
    val widgets = stringResource(R.string.workspace_widgets)
    val picker = LocalWidgetPicker.current
    return buildList {
        add(MenuAction(appInfo, Icons.Outlined.Info) { viewModel.openAppInfo(cell, anchor.request()) })
        if (picker != null && cell.shortcut == null && viewModel.hasWidgets(cell.app.key.packageName)) {
            add(MenuAction(widgets, Icons.Outlined.Widgets) { picker.open(cell.app.key.packageName) })
        }
        add(MenuAction(rename, Icons.Outlined.Edit, onRename))
        add(MenuAction(remove, Icons.Outlined.Close) { viewModel.remove(cell.itemId) })
        if (viewModel.canUninstall(cell)) {
            add(MenuAction(uninstall, Icons.Outlined.Delete) { viewModel.uninstall(cell.app.key) })
        }
    }
}
