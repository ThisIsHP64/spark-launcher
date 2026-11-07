package dev.gearturner.sparklauncher.feature.workspace

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gearturner.sparklauncher.core.data.AddResult
import dev.gearturner.sparklauncher.core.model.Dragged
import dev.gearturner.sparklauncher.core.ui.BaseIconSize
import dev.gearturner.sparklauncher.core.ui.DragController
import dev.gearturner.sparklauncher.core.ui.DragOrigin
import dev.gearturner.sparklauncher.core.ui.DragPayload
import dev.gearturner.sparklauncher.core.ui.DropHandler
import dev.gearturner.sparklauncher.core.ui.DrawerController
import dev.gearturner.sparklauncher.core.ui.LocalDragController
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
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dragController = LocalDragController.current
    val session = dragController?.session
    // while dragging, an empty page waits past the last one so items can start a new page
    val pagerState = rememberPagerState { state.pages.size + if (session != null) 1 else 0 }
    val iconRegistry = LocalHomeIconRegistry.current
    val geometry = remember { HomeGeometry() }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var openFolderId by remember { mutableStateOf<Long?>(null) }
    val density = LocalDensity.current

    HomeEvents(viewModel)
    LaunchedEffect(pagerState, iconRegistry) {
        snapshotFlow { pagerState.currentPage }.collect { page -> iconRegistry?.currentPage = page }
    }
    // pressing home while already home closes the drawer or a folder first, then returns to page one
    LaunchedEffect(homePresses) {
        homePresses.collect {
            when {
                drawer.isVisible -> drawer.close()
                openFolderId != null -> openFolderId = null
                else -> pagerState.animateScrollToPage(0)
            }
        }
    }
    DisposableEffect(dragController, viewModel, geometry, pagerState) {
        dragController?.dropHandler = DropHandler { ended, onSettled ->
            val zone = geometry.resolve(ended.fingerInRoot, pagerState.currentPage, ended.payload)
            handleDrop(zone, ended.payload, viewModel, onSettled)
            // dragging an app out of a folder closes the folder once the app has landed
            if (ended.origin == DragOrigin.Folder) openFolderId = null
        }
        onDispose { dragController?.dropHandler = null }
    }
    EdgePageFlip(dragController, geometry, pagerState)

    val hovered by remember(dragController) {
        derivedStateOf {
            val active = dragController?.session ?: return@derivedStateOf DropZone.None
            geometry.resolve(active.fingerInRoot, pagerState.currentPage, active.payload)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInRoot() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .swipeUpToOpen(drawer, enabled = session == null)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            if (!state.loading) {
                val iconSize = BaseIconSize * state.settings.iconScale
                geometry.columns = state.settings.homeColumns
                geometry.rows = state.settings.homeRows
                geometry.dockSlots = state.settings.dockSlots
                geometry.gridPaddingPx = with(density) { GridPadding.toPx() }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { geometry.pagerBounds = it.boundsInRoot() },
                    beyondViewportPageCount = 1,
                ) { page ->
                    PageGrid(
                        tiles = state.pages.getOrNull(page).orEmpty(),
                        columns = state.settings.homeColumns,
                        rows = state.settings.homeRows,
                        iconSize = iconSize,
                        cornerRadiusPercent = state.settings.iconCornerRadiusPercent,
                        showLabels = state.settings.showHomeLabels,
                        viewModel = viewModel,
                        onOpenFolder = { openFolderId = it },
                    )
                }
                PageIndicator(pagerState)
                if (state.settings.dockSlots > 0) {
                    Dock(
                        tiles = state.dock,
                        slots = state.settings.dockSlots,
                        iconSize = iconSize,
                        cornerRadiusPercent = state.settings.iconCornerRadiusPercent,
                        viewModel = viewModel,
                        onBounds = { geometry.dockBounds = it },
                        onOpenFolder = { openFolderId = it },
                    )
                } else {
                    geometry.dockBounds = null
                }
            }
        }
        if (session != null) {
            DropHighlight(hovered, rootOrigin)
            DropBar(session, hovered, geometry)
        }
        val openFolder = openFolderId?.let { id ->
            (state.pages.flatten() + state.dock).firstOrNull { it.itemId == id } as? FolderTile
        }
        if (openFolder != null) {
            FolderOverlay(
                folder = openFolder,
                draggingOut = session?.origin == DragOrigin.Folder,
                iconSize = BaseIconSize * state.settings.iconScale,
                cornerRadiusPercent = state.settings.iconCornerRadiusPercent,
                viewModel = viewModel,
                onClose = { openFolderId = null },
            )
        } else if (openFolderId != null && session == null) {
            // the folder dissolved or was removed while open
            openFolderId = null
        }
    }
}

private fun handleDrop(zone: DropZone, payload: DragPayload, viewModel: HomeViewModel, onSettled: () -> Unit) {
    when (zone) {
        is DropZone.Cell -> {
            val dragged = when (payload) {
                is DragPayload.Existing -> viewModel.existing(payload.itemId)?.let(Dragged::Existing)
                is DragPayload.NewApp -> Dragged.NewApp(payload.key)
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
            }
            onSettled()
        }
        DropZone.None -> onSettled()
    }
}

/** holding a dragged icon at either edge turns the page, repeatedly while it stays there */
@Composable
private fun EdgePageFlip(controller: DragController?, geometry: HomeGeometry, pagerState: PagerState) {
    val edgePx = with(LocalDensity.current) { EDGE_ZONE.toPx() }
    // keyed on the controller rather than the finger, so moving within an edge keeps the dwell timer running
    LaunchedEffect(controller, pagerState) {
        if (controller == null) return@LaunchedEffect
        snapshotFlow { controller.session?.fingerInRoot?.let { geometry.edgeDirection(it, edgePx) } ?: 0 }
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
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}

/** tracks vertical drags anywhere on home and hands the travel to the drawer */
@Composable
private fun Modifier.swipeUpToOpen(drawer: DrawerController, enabled: Boolean): Modifier {
    val dragState = rememberDraggableState { delta -> drawer.dragBy(-delta) }
    return draggable(
        state = dragState,
        orientation = Orientation.Vertical,
        enabled = enabled,
        onDragStopped = { velocity -> drawer.settle(-velocity) },
    )
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
