package dev.gearturner.sparklauncher.feature.drawer

import dev.gearturner.sparklauncher.core.apps.dotKey
import dev.gearturner.sparklauncher.core.ui.LocalNotificationDots

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gearturner.sparklauncher.core.data.AddResult
import androidx.compose.material.icons.outlined.AddHome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SwipeVertical
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Widgets
import dev.gearturner.sparklauncher.core.ui.LocalDrawer
import dev.gearturner.sparklauncher.core.ui.LocalWidgetPicker
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import dev.gearturner.sparklauncher.core.model.HomeMode
import dev.gearturner.sparklauncher.core.model.IconSource
import androidx.compose.ui.geometry.Rect
import dev.gearturner.sparklauncher.core.ui.AppIcon
import dev.gearturner.sparklauncher.core.ui.DragController
import dev.gearturner.sparklauncher.core.ui.DragOrigin
import dev.gearturner.sparklauncher.core.ui.DragPayload
import dev.gearturner.sparklauncher.core.ui.DragSession
import dev.gearturner.sparklauncher.core.ui.IconDragHandler
import dev.gearturner.sparklauncher.core.ui.LocalDragController
import dev.gearturner.sparklauncher.core.ui.IconPickerDialog
import dev.gearturner.sparklauncher.core.ui.ItemMenu
import dev.gearturner.sparklauncher.core.ui.LaunchAnchor
import dev.gearturner.sparklauncher.core.ui.MenuAction
import dev.gearturner.sparklauncher.core.ui.MenuGroup
import dev.gearturner.sparklauncher.core.ui.DismissOnStop
import dev.gearturner.sparklauncher.core.ui.RenameDialog
import dev.gearturner.sparklauncher.core.ui.SwipeActionsDialog
import dev.gearturner.sparklauncher.core.ui.BaseIconSize
import dev.gearturner.sparklauncher.core.ui.DrawerController
import dev.gearturner.sparklauncher.core.ui.LabelStyle
import dev.gearturner.sparklauncher.core.ui.rememberLaunchAnchor

private val SearchBarHeight = 56.dp

/** one inset for the top and sides so the search bar sits evenly inside the sheet */
private val SearchBarInset = 16.dp
private val DrawerCellPadding = 8.dp
private val TopHitInset = 4.dp
private val TopHitCorner = 24.dp

// concentric with the pill shaped search bar, so the band around it reads as uniform at the corners too
private val DrawerCornerRadius = SearchBarHeight / 2 + SearchBarInset

@Composable
fun AppDrawer(
    viewModel: DrawerViewModel,
    drawer: DrawerController,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    // the field owns its text so typing never waits on the view model, which kept resetting the cursor
    val searchState = rememberTextFieldState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val resources = LocalResources.current

    BackHandler(enabled = drawer.isOpen) { drawer.close() }
    val dragController = LocalDragController.current
    // an app dragged out of the drawer is still owned by its grid cell, so the drawer must stay composed
    val draggingFromDrawer = dragController?.session?.origin == DragOrigin.Drawer

    LaunchedEffect(searchState) {
        snapshotFlow { searchState.text.toString() }.collect(viewModel::onQueryChange)
    }
    // the highlighted top hit is what the go key opens, so it has to stay in view as the query changes
    LaunchedEffect(state.query) {
        if (state.query.isNotBlank()) gridState.scrollToItem(0)
    }
    // a fully closed drawer resets so the next open starts at the top with an empty search
    LaunchedEffect(drawer, dragController) {
        snapshotFlow { drawer.value == 0f && dragController?.session == null }.collect { closed ->
            if (closed) {
                // a search request that never reached a fully open drawer must not linger to the next open
                drawer.consumeSearchRequest()
                keyboard?.hide()
                focusManager.clearFocus()
                searchState.clearText()
                gridState.scrollToItem(0)
            }
        }
    }
    LaunchedEffect(viewModel, resources) {
        viewModel.oneShotEvents.collect { event ->
            val message = when (event) {
                is DrawerEvent.LaunchFailed -> resources.getString(R.string.drawer_launch_failed, event.label)
                DrawerEvent.SwipeNotSaved -> resources.getString(R.string.drawer_swipe_not_saved)
                is DrawerEvent.AddedToHome -> resources.getString(
                    when (event.result) {
                        is AddResult.Added -> R.string.drawer_added
                        AddResult.AlreadyOnHome -> R.string.drawer_already_added
                        AddResult.NoSpace -> R.string.drawer_no_space
                    },
                )
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // in home only mode every app is already on home, so swiping up is just a search
    val searchOnly = state.settings.homeMode == HomeMode.HOME_ONLY
    val searchFocus = remember { FocusRequester() }
    // the keyboard comes up once the sheet has fully arrived, in search only mode or when a
    // gesture asked for search rather than the app list
    LaunchedEffect(drawer, searchOnly) {
        snapshotFlow { drawer.value == 1f && (searchOnly || drawer.searchRequested) }.collect { focus ->
            if (focus) {
                drawer.consumeSearchRequest()
                searchFocus.requestFocus()
            }
        }
    }

    if (!drawer.isVisible && !draggingFromDrawer) return

    val closeOnOverscroll = remember(drawer) { DrawerNestedScroll(drawer) }
    val headerDrag = rememberDraggableState { delta -> drawer.dragBy(-delta) }
    val statusBars = WindowInsets.statusBars
    val shape = RoundedCornerShape(topStart = DrawerCornerRadius, topEnd = DrawerCornerRadius)

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = (1f - drawer.value) * size.height
                alpha = (drawer.value * 2f).coerceAtMost(1f)
            }
            .clip(shape)
            .nestedScroll(closeOnOverscroll),
    ) {
        // a surface sets the theme's text color and swallows touches, so home icons below never react
        Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f), modifier = Modifier.fillMaxSize()) {}
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarClearance(drawer, statusBars),
        ) {
            SearchField(
                state = searchState,
                onSubmit = { state.topHit?.let(viewModel::open) },
                modifier = Modifier
                    .draggable(
                        state = headerDrag,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity -> drawer.settle(-velocity) },
                    )
                    .padding(SearchBarInset)
                    .focusRequester(searchFocus),
            )
            if (searchOnly && state.query.isBlank()) {
                Text(
                    text = stringResource(R.string.drawer_search_only_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                )
            }
            if (state.entries.isEmpty() && state.results.isEmpty && state.query.isNotBlank()) {
                Text(
                    text = stringResource(R.string.drawer_no_results, state.query),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(state.settings.drawerColumns),
                state = gridState,
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                val topHit = state.topHit
                state.results.calculation?.let { calculatorRow(state.query.trim(), it) }
                val shown = if (searchOnly && state.query.isBlank()) emptyList() else state.entries
                itemsIndexed(shown, key = { _, entry -> entry.app.key.toString() }) { index, entry ->
                    DrawerItem(
                        entry = entry,
                        topHit = index == 0 && topHit is TopHit.App,
                        iconScale = state.settings.iconScale,
                        showLabels = state.settings.showDrawerLabels,
                        viewModel = viewModel,
                        drawer = drawer,
                    )
                }
                searchSections(state.results, topHit, viewModel)
            }
        }
    }
}

/**
 * pads the content only by the part of the status bar the drawer's top edge has actually reached,
 * so mid swipe the search bar sits right under the rounded edge instead of below an empty band.
 * this runs in the layout phase, so dragging relayouts without recomposing the grid every frame.
 */
private fun Modifier.statusBarClearance(drawer: DrawerController, statusBars: WindowInsets): Modifier =
    layout { measurable, constraints ->
        val height = constraints.maxHeight
        val drawerTop = ((1f - drawer.value) * height).toInt()
        val clearance = (statusBars.getTop(this) - drawerTop).coerceAtLeast(0)
        val placeable = measurable.measure(
            constraints.copy(
                minHeight = (constraints.minHeight - clearance).coerceAtLeast(0),
                maxHeight = (height - clearance).coerceAtLeast(0),
            ),
        )
        layout(placeable.width, placeable.height + clearance) { placeable.place(0, clearance) }
    }

@Composable
private fun SearchField(
    state: TextFieldState,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        state = state,
        lineLimits = TextFieldLineLimits.SingleLine,
        placeholder = { Text(stringResource(R.string.drawer_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (state.text.isNotEmpty()) {
                IconButton(onClick = { state.clearText() }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.drawer_clear_search))
                }
            }
        },
        // pressing enter opens the top result, the fastest way to launch anything by name
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        onKeyboardAction = { onSubmit() },
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(SearchBarHeight),
    )
}

@Composable
private fun DrawerItem(
    entry: DrawerEntry,
    topHit: Boolean,
    iconScale: Float,
    showLabels: Boolean,
    viewModel: DrawerViewModel,
    drawer: DrawerController,
) {
    val app = entry.app
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
    val actions = drawerItemActions(
        entry, viewModel, anchor,
        onRename = { renaming = true },
        onChangeIcon = { changingIcon = true },
        onSwipeActions = { editingSwipes = true },
    )
    val dragController = LocalDragController.current
    val canUninstall = viewModel.canUninstall(app)
    val dragHandler = remember(dragController, app.key, canUninstall, drawer, app.label) {
        dragController?.let { controller ->
            drawerDragHandler(controller, drawer, anchor, DragPayload.NewApp(app.key, canUninstall), app.label) { menuOpen = false }
        }
    }
    // the top hit sits on a tinted plate so it is obvious which app the keyboard's go key opens;
    // the paddings add up to the same height as a plain cell so the grid does not jump
    val plate = if (topHit) {
        Modifier
            .padding(TopHitInset)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(TopHitCorner))
            .padding(vertical = DrawerCellPadding - TopHitInset)
    } else {
        Modifier.padding(vertical = DrawerCellPadding)
    }
    Box(
        modifier = Modifier
            .then(plate),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            source = IconSource.App(app.key),
            label = app.label,
            iconSize = BaseIconSize * iconScale,
            labelStyle = if (showLabels || topHit) LabelStyle.OnSurface else LabelStyle.Hidden,
            anchor = anchor,
            onClick = { viewModel.launch(app, anchor.request()) },
            onLongClick = { menuOpen = true },
            dragHandler = dragHandler,
            showDot = app.key.dotKey in LocalNotificationDots.current,
        )
        ItemMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            iconBounds = anchor::boundsInRoot,
            owner = app.key,
            loadShortcuts = { viewModel.menuShortcuts(app) },
            onShortcutClick = { viewModel.launchShortcut(it, anchor.request()) },
            onPinShortcut = { viewModel.pinShortcut(it, app.key) },
            actions = actions,
        )
    }
    if (renaming) {
        RenameDialog(
            currentName = app.label,
            canReset = entry.renamed,
            onConfirm = { label ->
                renaming = false
                viewModel.rename(app, label)
            },
            onDismiss = { renaming = false },
        )
    }
    if (editingSwipes) {
        SwipeActionsDialog(
            appLabel = app.label,
            up = entry.swipeUp,
            down = entry.swipeDown,
            apps = remember { viewModel.swipeApps() },
            hasShortcutAccess = viewModel.hasShortcutAccess,
            loadShortcuts = viewModel::swipeShortcuts,
            onSet = { direction, target -> viewModel.setSwipe(app.key, direction, target) },
            onDismiss = { editingSwipes = false },
        )
    }
    if (changingIcon) {
        IconPickerDialog(
            appLabel = app.label,
            hasCustomIcon = entry.customIcon,
            loadPacks = viewModel::iconPacks,
            loadNames = viewModel::packIconNames,
            onPick = {
                changingIcon = false
                viewModel.setCustomIcon(app, it)
            },
            onReset = {
                changingIcon = false
                viewModel.setCustomIcon(app, null)
            },
            onDismiss = { changingIcon = false },
        )
    }
}

@Composable
private fun drawerItemActions(
    entry: DrawerEntry,
    viewModel: DrawerViewModel,
    anchor: LaunchAnchor,
    onRename: () -> Unit,
    onChangeIcon: () -> Unit,
    onSwipeActions: () -> Unit,
): List<MenuAction> {
    val app = entry.app
    val swipeActions = stringResource(R.string.drawer_swipe_actions)
    val changeIcon = stringResource(R.string.drawer_change_icon)
    val addToHome = stringResource(R.string.drawer_add_to_home)
    val appInfo = stringResource(R.string.drawer_app_info)
    val rename = stringResource(R.string.drawer_rename)
    val hide = stringResource(R.string.drawer_hide)
    val uninstall = stringResource(R.string.drawer_uninstall)
    val widgets = stringResource(R.string.drawer_widgets)
    val picker = LocalWidgetPicker.current
    val drawer = LocalDrawer.current
    return buildList {
        add(MenuAction(addToHome, Icons.Outlined.AddHome) { viewModel.addToHome(app) })
        if (picker != null && viewModel.hasWidgets(app)) {
            add(
                MenuAction(widgets, Icons.Outlined.Widgets) {
                    drawer?.close()
                    picker.open(app.key.packageName)
                },
            )
        }
        add(MenuAction(appInfo, Icons.Outlined.Info) { viewModel.openAppInfo(app, anchor.request()) })
        add(MenuAction(rename, Icons.Outlined.Edit, onClick = onRename))
        if (viewModel.canChangeIcon(entry)) add(MenuAction(changeIcon, Icons.Outlined.Image, onClick = onChangeIcon))
        add(MenuAction(swipeActions, Icons.Outlined.SwipeVertical, onClick = onSwipeActions))
        add(MenuAction(hide, Icons.Outlined.VisibilityOff) { viewModel.setHidden(app, true) })
        if (viewModel.canUninstall(app)) {
            add(MenuAction(uninstall, Icons.Outlined.Delete, MenuGroup.Destructive) { viewModel.uninstall(app) })
        }
    }
}


/** dragging an app out closes the drawer so the home screen is there to drop it on, as on pixel */
private fun drawerDragHandler(
    controller: DragController,
    drawer: DrawerController,
    anchor: LaunchAnchor,
    payload: DragPayload,
    label: String,
    closeMenu: () -> Unit,
): IconDragHandler = object : IconDragHandler {
    override fun onDragStart(fingerInRoot: Offset, iconBoundsInRoot: Rect) {
        closeMenu()
        drawer.close()
        controller.start(
            DragSession(
                payload = payload,
                origin = DragOrigin.Drawer,
                icon = anchor.icon,
                sizePx = iconBoundsInRoot.size,
                grabOffset = fingerInRoot - iconBoundsInRoot.topLeft,
                fingerInRoot = fingerInRoot,
                label = label,
            ),
        )
    }

    override fun onDragMove(fingerInRoot: Offset) = controller.move(fingerInRoot)

    override fun onDragEnd() = controller.drop {}

    override fun onDragCancel() = controller.cancel()
}

/**
 * lets the grid hand leftover scroll to the drawer: pulling down past the top closes it,
 * and while partly closed, scrolling up reopens it before the grid moves.
 */
private class DrawerNestedScroll(private val drawer: DrawerController) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && drawer.value < 1f && available.y < 0f) {
            drawer.dragBy(-available.y)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y > 0f) {
            drawer.dragBy(-available.y)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (drawer.value < 1f) {
            drawer.settle(-available.y)
            return available
        }
        return Velocity.Zero
    }
}
