package dev.gearturner.sparklauncher.feature.workspace

import dev.gearturner.sparklauncher.core.model.DropTarget
import androidx.compose.runtime.CompositionLocalProvider
import dev.gearturner.sparklauncher.core.ui.MenuAction
import dev.gearturner.sparklauncher.core.ui.ItemMenu
import dev.gearturner.sparklauncher.core.model.Gesture
import dev.gearturner.sparklauncher.core.model.GestureAction
import dev.gearturner.sparklauncher.core.model.effectiveAction
import dev.gearturner.sparklauncher.core.ui.LocalGestureActions
import androidx.compose.runtime.rememberUpdatedState
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.ui.unit.IntOffset
import dev.gearturner.sparklauncher.core.model.Span
import dev.gearturner.sparklauncher.core.model.SUBGRID
import dev.gearturner.sparklauncher.core.ui.LocalWidgetPicker
import dev.gearturner.sparklauncher.feature.widgets.LocalWidgetBinder
import dev.gearturner.sparklauncher.feature.widgets.WidgetBinder
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gearturner.sparklauncher.core.data.AddResult
import dev.gearturner.sparklauncher.core.model.Dragged
import dev.gearturner.sparklauncher.core.ui.BaseIconSize
import dev.gearturner.sparklauncher.core.ui.DragController
import dev.gearturner.sparklauncher.core.ui.DragOrigin
import dev.gearturner.sparklauncher.core.ui.DragPayload
import dev.gearturner.sparklauncher.core.ui.DragSession
import dev.gearturner.sparklauncher.core.ui.DropHandler
import dev.gearturner.sparklauncher.core.ui.DrawerController
import dev.gearturner.sparklauncher.core.ui.IconSwipeClaim
import dev.gearturner.sparklauncher.core.ui.LocalDragController
import dev.gearturner.sparklauncher.core.ui.LocalIconSwipeClaim
import dev.gearturner.sparklauncher.core.ui.LocalHomeIconRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    drawer: DrawerController,
    homePresses: Flow<Unit>,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dragController = LocalDragController.current
    val session = dragController?.session
    // a drop onto the empty page past the last one: the page only exists once the item is written, which
    // for a widget waits on system prompts, so the pager keeps it until then instead of snapping back
    var landingPage by remember { mutableStateOf<Int?>(null) }
    var landingSettled by remember { mutableStateOf(false) }
    // while dragging, an empty page waits past the last one so items can start a new page
    val pagerState = rememberPagerState {
        val landing = landingPage
        val waiting = session != null || (landing != null && state.pages.size <= landing)
        state.pages.size + if (waiting) 1 else 0
    }
    val iconRegistry = LocalHomeIconRegistry.current
    val geometry = remember { HomeGeometry() }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var openFolder by remember { mutableStateOf<OpenFolder?>(null) }
    val folderDrag = remember { FolderDrag() }
    var resizingId by remember { mutableStateOf<Long?>(null) }
    var emptyMenuAt by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current
    val binder = LocalWidgetBinder.current
    val picker = LocalWidgetPicker.current

    HomeEvents(viewModel)
    LaunchedEffect(pagerState, iconRegistry) {
        snapshotFlow { pagerState.currentPage }.collect { page -> iconRegistry?.currentPage = page }
    }
    val gestureActions = LocalGestureActions.current
    val runGesture: (Gesture) -> Unit = { gesture ->
        val binding = state.settings.gestures.bindingFor(gesture)
        when (binding.effectiveAction(state.settings.homeMode)) {
            GestureAction.NONE -> Unit
            GestureAction.OPEN_DRAWER -> drawer.open()
            GestureAction.OPEN_SEARCH -> drawer.openSearch()
            else -> gestureActions?.perform(binding)
        }
    }
    val currentRunGesture by rememberUpdatedState(runGesture)
    // pressing home while already home closes the drawer or a folder first, returns to page one,
    // and only then runs whatever the home press gesture is set to
    LaunchedEffect(homePresses) {
        homePresses.collect {
            when {
                drawer.isVisible -> drawer.close()
                picker?.isOpen == true -> picker.close()
                resizingId != null -> resizingId = null
                openFolder != null -> openFolder = null
                pagerState.currentPage != 0 -> pagerState.animateScrollToPage(0)
                else -> currentRunGesture(Gesture.HOME_PRESS)
            }
        }
    }
    // the landing page is released once the item has created it, or shortly after the drop settles
    // without one, which means it was refused or cancelled
    LaunchedEffect(landingPage, landingSettled, state.pages.size) {
        val landing = landingPage ?: return@LaunchedEffect
        if (state.pages.size > landing) {
            landingPage = null
            landingSettled = false
        } else if (landingSettled) {
            delay(LANDING_GRACE_MS)
            landingPage = null
            landingSettled = false
        }
    }
    // coming back from an app launched out of a folder is not a home press, so the folder is closed while
    // the launcher is hidden, where no one sees it shut
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { openFolder = null }
    // resizing ends as soon as anything else moves, so the frame never points at a stale spot
    LaunchedEffect(pagerState) { snapshotFlow { pagerState.currentPage }.collect { resizingId = null } }
    LaunchedEffect(session != null) { if (session != null) resizingId = null }
    DisposableEffect(dragController, viewModel, geometry, pagerState, binder) {
        dragController?.dropHandler = DropHandler { ended, onSettled ->
            // an app held over a folder until it opened lands in the gap it was hovering over
            val open = openFolder
            if (open != null && open.incoming && ended.origin != DragOrigin.Folder && !folderDrag.leftPanel) {
                val rank = folderDrag.hoverRank
                val dragged = when (val payload = ended.payload) {
                    is DragPayload.Existing -> viewModel.existing(payload.itemId)?.let(Dragged::Existing)
                    is DragPayload.NewApp -> Dragged.NewApp(payload.key)
                    is DragPayload.NewWidget -> null
                }
                if (rank != null && dragged != null) viewModel.dropIntoFolder(dragged, open.id, rank, onSettled) else onSettled()
                return@DropHandler
            }
            // a drag that never left the open folder is a reorder inside it, and the folder stays open
            if (ended.origin == DragOrigin.Folder && openFolder != null && !folderDrag.leftPanel) {
                val itemId = (ended.payload as? DragPayload.Existing)?.itemId
                val rank = folderDrag.hoverRank
                if (itemId != null && rank != null) viewModel.reorderInFolder(itemId, rank)
                onSettled()
                return@DropHandler
            }
            val zone = geometry.resolve(ended, pagerState.currentPage)
            val newPage = ((zone as? DropZone.Cell)?.target as? DropTarget.Desktop)?.page?.takeIf { it >= state.pages.size }
            landingSettled = false
            landingPage = newPage
            handleDrop(zone, ended.payload, viewModel, binder) {
                if (newPage != null) landingSettled = true
                onSettled()
            }
            // dragging an app out of a folder closes the folder once the app has landed
            if (ended.origin == DragOrigin.Folder) openFolder = null
        }
        onDispose { dragController?.dropHandler = null }
    }
    // while an open folder owns the drag its own edges flip its pages, so home must stay put underneath
    EdgePageFlip(dragController, geometry, pagerState, suspended = { openFolder != null && !folderDrag.leftPanel })
    val backdrop = rememberGraphicsLayer()
    // the drop bar outlives the drag by its fade out, so it keeps the payload it was built for, and
    // home keeps recording its blurred backdrop until the bar has gone
    var barSession by remember { mutableStateOf<DragSession?>(null) }
    if (session != null && barSession?.payload != session.payload) barSession = session
    val recordBackdrop = barSession != null

    val hovered by remember(dragController) {
        derivedStateOf {
            val active = dragController?.session ?: return@derivedStateOf DropZone.None
            // home cells under an open folder are not targets until the app is dragged out of it
            val folder = openFolder
            if (folder != null && !folderDrag.leftPanel && (active.origin == DragOrigin.Folder || folder.incoming)) {
                return@derivedStateOf DropZone.None
            }
            geometry.resolve(active, pagerState.currentPage)
        }
    }
    // the app or folder a dragged icon would merge into, which grows to show it is a target
    val hoverTarget by remember(dragController) {
        derivedStateOf {
            val active = dragController?.session ?: return@derivedStateOf null
            val cell = hovered as? DropZone.Cell ?: return@derivedStateOf null
            if (!viewModel.isSingleIcon(active.payload)) return@derivedStateOf null
            val tile = tileAt(state, cell.target) ?: return@derivedStateOf null
            // only a drop exactly onto the item merges; one landing half over it is refused instead
            val target = cell.target
            if (target is DropTarget.Desktop && (tile.placement.cellX != target.cellX || tile.placement.cellY != target.cellY)) {
                return@derivedStateOf null
            }
            val draggedId = (active.payload as? DragPayload.Existing)?.itemId
            tile.takeIf { (it is FolderTile || it is HomeCell) && it.itemId != draggedId }
        }
    }
    // holding an app over a folder opens it, so the app can be placed exactly where it should go
    LaunchedEffect(dragController) {
        snapshotFlow { (hoverTarget as? FolderTile)?.itemId?.let { id -> id to (hovered as? DropZone.Cell)?.highlight } }
            .distinctUntilChanged { old, new -> old?.first == new?.first }
            .collectLatest { target ->
                if (target == null || openFolder != null) return@collectLatest
                delay(FOLDER_OPEN_DWELL_MS)
                folderDrag.reset()
                openFolder = OpenFolder(target.first, target.second, incoming = true)
            }
    }

    val homeLabel = stringResource(R.string.workspace_a11y_home)
    val openDrawerLabel = stringResource(R.string.workspace_a11y_open_drawer)
    val widgetsLabel = stringResource(R.string.workspace_widgets)
    val settingsLabel = stringResource(R.string.workspace_a11y_settings)
    val homeActions = remember(drawer, picker, onOpenSettings, openDrawerLabel, widgetsLabel, settingsLabel) {
        buildList {
            add(
                CustomAccessibilityAction(openDrawerLabel) {
                    drawer.open()
                    true
                },
            )
            picker?.let { pickerController ->
                add(
                    CustomAccessibilityAction(widgetsLabel) {
                        pickerController.open()
                        true
                    },
                )
            }
            add(
                CustomAccessibilityAction(settingsLabel) {
                    onOpenSettings()
                    true
                },
            )
        }
    }
    // lets an icon with a swipe action tell the swipe detector below which directions it owns
    val iconSwipeClaim = remember { IconSwipeClaim() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInRoot() },
    ) {
        CompositionLocalProvider(
            LocalDropHover provides hoverTarget?.itemId,
            LocalIconSwipeClaim provides iconSwipeClaim,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // the drawer, widgets, and settings are otherwise reached by a swipe or a long press on
                    // empty space, neither of which talkback can do, so home offers them as actions
                    .semantics {
                        contentDescription = homeLabel
                        customActions = homeActions
                    }
                    // recorded so the drop bar can draw a blurred copy of what sits behind it; only a drag
                    // shows the bar, so otherwise home draws straight through instead of re-recording
                    // every page of it on each scroll frame
                    .drawWithContent {
                        if (recordBackdrop) {
                            backdrop.record { this@drawWithContent.drawContent() }
                            drawLayer(backdrop)
                        } else {
                            drawContent()
                        }
                    }
                    .homeSwipes(
                        drawer = drawer,
                        // read live, since the drag can begin on the very event the swipe is deciding on
                        enabled = { dragController?.session == null },
                        swipeUpDragsDrawer = state.settings.gestures.swipeUp.action == GestureAction.OPEN_DRAWER,
                        startsOnWidget = { local ->
                            val inRoot = local + rootOrigin
                            state.pages.getOrNull(pagerState.currentPage).orEmpty().any { tile ->
                                tile is WidgetTile && geometry.rectOf(tile.placement)?.contains(inRoot) == true
                            }
                        },
                        iconSwipes = iconSwipeClaim,
                        onGesture = runGesture,
                    )
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                if (!state.loading) {
                    val iconSize = BaseIconSize * state.settings.iconScale
                    geometry.columns = state.settings.homeColumns
                    geometry.rows = state.settings.homeRows
                    geometry.dockSlots = state.settings.dockSlots
                    geometry.gridPaddingPx = with(density) { GridPadding.toPx() }
                    geometry.subgrid = state.settings.subgridPositioning
                    geometry.iconAt = { page, x, y ->
                        // the dragged icon still sits in its old spot, and must not catch its own drop
                        val draggedId = (dragController?.session?.payload as? DragPayload.Existing)?.itemId
                        state.pages.getOrNull(page)?.firstOrNull { tile ->
                            val p = tile.placement
                            tile !is WidgetTile && tile.itemId != draggedId &&
                                x in p.cellX until p.cellX + p.spanX && y in p.cellY until p.cellY + p.spanY
                        }?.placement
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onGloballyPositioned {
                                geometry.pagerBounds = it.boundsInRoot()
                                // the picker sizes previews and dragged widgets from the real cell size
                                picker?.let { p ->
                                    with(density) {
                                        p.cellWidthDp = geometry.cellWidthPx().toDp().value
                                        p.cellHeightDp = geometry.cellHeightPx().toDp().value
                                    }
                                    p.columns = geometry.columns
                                    p.rows = geometry.rows
                                }
                            },
                        // the drag gesture lives on the source tile, so its page must stay composed until the drop
                        // or the gesture is cancelled; while dragging every page stays alive for that reason
                        beyondViewportPageCount = if (session != null) maxOf(1, pagerState.pageCount - 1) else 1,
                    ) { page ->
                        PageGrid(
                            tiles = state.pages.getOrNull(page).orEmpty(),
                            columns = state.settings.homeColumns,
                            rows = state.settings.homeRows,
                            iconSize = iconSize,
                            showLabels = state.settings.showHomeLabels,
                            viewModel = viewModel,
                            onOpenFolder = { openFolder = it },
                            onResizeWidget = { resizingId = it.itemId },
                            onEmptyLongPress = { emptyMenuAt = it },
                            onEmptyDoubleTap = if (state.settings.gestures.doubleTap.action == GestureAction.NONE) {
                                null
                            } else {
                                { runGesture(Gesture.DOUBLE_TAP) }
                            },
                        )
                    }
                    PageIndicator(pagerState)
                    if (state.settings.dockSlots > 0) {
                        Dock(
                            tiles = state.dock,
                            slots = state.settings.dockSlots,
                            iconSize = iconSize,
                            viewModel = viewModel,
                            onBounds = { geometry.dockBounds = it },
                            onOpenFolder = { openFolder = it },
                        )
                    } else {
                        geometry.dockBounds = null
                    }
                }
            }
        }
        if (session != null) DropHighlight(hovered, rootOrigin)
        barSession?.let { bar ->
            DropBar(bar, visible = session != null, hovered, geometry, backdrop, onGone = { barSession = null })
        }
        val resizing = resizingId?.let { id ->
            state.pages.getOrNull(pagerState.currentPage)?.firstOrNull { it.itemId == id } as? WidgetTile
        }
        if (resizing != null) {
            BackHandler { resizingId = null }
            ResizeFrame(resizing, geometry, rootOrigin, viewModel, onDone = { resizingId = null })
        }
        EmptySpaceMenu(
            at = emptyMenuAt?.minus(rootOrigin),
            onDismiss = { emptyMenuAt = null },
            onWidgets = { picker?.open() },
            onSettings = onOpenSettings,
        )
        val request = openFolder
        val folderTile = request?.let { open ->
            (state.pages.flatten() + state.dock).firstOrNull { it.itemId == open.id } as? FolderTile
        }
        if (request != null && folderTile != null) {
            // keyed so a different folder starts fresh, with its own name field and open animation
            key(request.id) {
                FolderOverlay(
                    folder = folderTile,
                    request = request,
                    rootOrigin = rootOrigin,
                    drag = folderDrag,
                    iconSize = BaseIconSize * state.settings.iconScale,
                    viewModel = viewModel,
                    acceptsIncoming = viewModel::isSingleIcon,
                    onClose = { openFolder = null },
                )
            }
        } else if (request != null && session == null) {
            // the folder dissolved or was removed while open
            openFolder = null
        }
    }
}

private fun handleDrop(
    zone: DropZone,
    payload: DragPayload,
    viewModel: HomeViewModel,
    binder: WidgetBinder?,
    onSettled: () -> Unit,
) {
    when (zone) {
        is DropZone.Cell -> {
            if (payload is DragPayload.NewWidget) {
                viewModel.placeWidget(payload.provider, Span(payload.spanX * SUBGRID, payload.spanY * SUBGRID), zone.target, binder, onSettled)
                return
            }
            val dragged = when (payload) {
                is DragPayload.Existing -> viewModel.existing(payload.itemId)?.let(Dragged::Existing)
                is DragPayload.NewApp -> Dragged.NewApp(payload.key)
                is DragPayload.NewWidget -> null
            }
            if (dragged == null) onSettled() else viewModel.drop(dragged, zone.target, onSettled)
        }
        DropZone.Remove -> {
            if (payload is DragPayload.Existing) viewModel.remove(payload.itemId)
            onSettled()
        }
        DropZone.Uninstall -> {
            when (payload) {
                is DragPayload.Existing -> payload.app?.let(viewModel::uninstall)
                is DragPayload.NewApp -> viewModel.uninstall(payload.key)
                is DragPayload.NewWidget -> Unit
            }
            onSettled()
        }
        DropZone.None -> onSettled()
    }
}

/** the menu for a long press on empty home space, placed where the finger was */
@Composable
private fun EmptySpaceMenu(at: Offset?, onDismiss: () -> Unit, onWidgets: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val wallpaperTitle = stringResource(R.string.workspace_wallpaper)
    // the menu animates out after it is dismissed, so it keeps its last spot instead of jumping to the corner
    var anchor by remember { mutableStateOf(Offset.Zero) }
    if (at != null) anchor = at
    val actions = listOf(
        MenuAction(stringResource(R.string.workspace_widgets), Icons.Outlined.Widgets, onClick = onWidgets),
        MenuAction(wallpaperTitle, Icons.Outlined.Wallpaper) {
            try {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), wallpaperTitle))
            } catch (e: ActivityNotFoundException) {
                // no wallpaper app at all is unusual but possible on stripped down builds
                Toast.makeText(context, R.string.workspace_no_wallpaper_app, Toast.LENGTH_SHORT).show()
            }
        },
        MenuAction(stringResource(R.string.workspace_settings), Icons.Outlined.Settings, onClick = onSettings),
    )
    Box(
        modifier = Modifier.offset { IntOffset(anchor.x.roundToInt(), anchor.y.roundToInt()) },
    ) {
        ItemMenu(expanded = at != null, onDismiss = onDismiss, actions = actions)
    }
}

/** holding a dragged icon at either edge turns the page, repeatedly while it stays there */
@Composable
private fun EdgePageFlip(
    controller: DragController?,
    geometry: HomeGeometry,
    pagerState: PagerState,
    suspended: () -> Boolean,
) {
    val edgePx = with(LocalDensity.current) { EDGE_ZONE.toPx() }
    // keyed on the controller rather than the finger, so moving within an edge keeps the dwell timer running
    LaunchedEffect(controller, pagerState) {
        if (controller == null) return@LaunchedEffect
        snapshotFlow {
            if (suspended()) 0 else controller.session?.fingerInRoot?.let { geometry.edgeDirection(it, edgePx) } ?: 0
        }
            .distinctUntilChanged()
            .collectLatest { direction ->
                while (direction != 0) {
                    delay(EDGE_DWELL_MS)
                    val target = (pagerState.currentPage + direction).coerceIn(0, pagerState.pageCount - 1)
                    if (target == pagerState.currentPage) break
                    pagerState.animateScrollToPage(target)
                }
            }
    }
}

@Composable
private fun HomeEvents(viewModel: HomeViewModel) {
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(viewModel, resources) {
        viewModel.oneShotEvents.collect { event ->
            val message = when (event) {
                is HomeEvent.LaunchFailed -> resources.getString(R.string.workspace_launch_failed, event.label)
                is HomeEvent.ShortcutAdded -> resources.getString(
                    when (event.result) {
                        is AddResult.Added -> R.string.workspace_shortcut_added
                        AddResult.AlreadyOnHome -> R.string.workspace_already_added
                        AddResult.NoSpace -> R.string.workspace_no_space
                    },
                )
                HomeEvent.DropRefused -> resources.getString(R.string.workspace_drop_refused)
                HomeEvent.SwipeNotSaved -> resources.getString(R.string.workspace_swipe_not_saved)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun PageIndicator(pagerState: PagerState) {
    if (pagerState.pageCount <= 1) return
    val description = stringResource(
        R.string.workspace_page_indicator,
        pagerState.currentPage + 1,
        pagerState.pageCount,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(pagerState.pageCount) { index ->
            val alpha = if (index == pagerState.currentPage) 1f else 0.45f
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(6.dp)
                    .background(Color.White.copy(alpha = alpha), CircleShape),
            )
        }
    }
}

private val EDGE_ZONE = 32.dp
private const val EDGE_DWELL_MS = 550L

/** the app, folder, or widget covering [target] on home or in the dock, if any */
private fun tileAt(state: HomeUiState, target: DropTarget): HomeTile? = when (target) {
    is DropTarget.Desktop -> state.pages.getOrNull(target.page)?.firstOrNull { tile ->
        val p = tile.placement
        target.cellX in p.cellX until p.cellX + p.spanX && target.cellY in p.cellY until p.cellY + p.spanY
    }
    is DropTarget.Dock -> state.dock.firstOrNull { it.placement.cellX == target.slot }
}

private const val FOLDER_OPEN_DWELL_MS = 600L

// long enough for the database to report a freshly written page after a drop settles
private const val LANDING_GRACE_MS = 1000L
