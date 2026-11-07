package dev.gearturner.sparklauncher.feature.workspace

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gearturner.sparklauncher.core.ui.AppIcon
import dev.gearturner.sparklauncher.core.ui.BaseIconSize
import dev.gearturner.sparklauncher.core.ui.DrawerController
import dev.gearturner.sparklauncher.core.ui.HomeIconRegistry
import dev.gearturner.sparklauncher.core.ui.LabelStyle
import dev.gearturner.sparklauncher.core.ui.LocalHomeIconRegistry
import dev.gearturner.sparklauncher.core.ui.RegisterHomeIcon
import dev.gearturner.sparklauncher.core.ui.rememberLaunchAnchor
import kotlinx.coroutines.flow.Flow

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    drawer: DrawerController,
    homePresses: Flow<Unit>,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { state.pages.size }
    val iconRegistry = LocalHomeIconRegistry.current
    val context = LocalContext.current
    val resources = LocalResources.current

    LaunchedEffect(viewModel, resources) {
        viewModel.oneShotEvents.collect { event ->
            when (event) {
                is HomeEvent.LaunchFailed -> Toast.makeText(
                    context,
                    resources.getString(R.string.workspace_launch_failed, event.label),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    LaunchedEffect(pagerState, iconRegistry) {
        snapshotFlow { pagerState.currentPage }.collect { page -> iconRegistry?.currentPage = page }
    }
    // pressing home while already home closes the drawer first, then returns to the first page
    LaunchedEffect(homePresses) {
        homePresses.collect {
            if (drawer.isVisible) drawer.close() else pagerState.animateScrollToPage(0)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .swipeUpToOpen(drawer)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        if (!state.loading) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                beyondViewportPageCount = 1,
            ) { page ->
                PageGrid(
                    cells = state.pages.getOrNull(page).orEmpty(),
                    columns = state.settings.homeColumns,
                    rows = state.settings.homeRows,
                    iconSize = BaseIconSize * state.settings.iconScale,
                    cornerRadiusPercent = state.settings.iconCornerRadiusPercent,
                    showLabels = state.settings.showHomeLabels,
                    viewModel = viewModel,
                )
            }
            PageIndicator(pagerState)
            if (state.settings.dockSlots > 0) {
                Dock(
                    cells = state.dock,
                    slots = state.settings.dockSlots,
                    iconSize = BaseIconSize * state.settings.iconScale,
                    cornerRadiusPercent = state.settings.iconCornerRadiusPercent,
                    viewModel = viewModel,
                )
            }
        }
    }
}

/** tracks vertical drags anywhere on home and hands the travel to the drawer */
@Composable
private fun Modifier.swipeUpToOpen(drawer: DrawerController): Modifier {
    val dragState = rememberDraggableState { delta -> drawer.dragBy(-delta) }
    return draggable(
        state = dragState,
        orientation = Orientation.Vertical,
        onDragStopped = { velocity -> drawer.settle(-velocity) },
    )
}

@Composable
private fun PageGrid(
    cells: List<HomeCell>,
    columns: Int,
    rows: Int,
    iconSize: Dp,
    cornerRadiusPercent: Int,
    showLabels: Boolean,
    viewModel: HomeViewModel,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
    ) {
        val cellWidth = maxWidth / columns
        val cellHeight = maxHeight / rows
        cells.forEach { cell ->
            HomeItem(
                cell = cell,
                registryPage = cell.placement.page,
                iconSize = iconSize,
                cornerRadiusPercent = cornerRadiusPercent,
                labelStyle = if (showLabels) LabelStyle.OnWallpaper else LabelStyle.Hidden,
                viewModel = viewModel,
                modifier = Modifier
                    .offset(x = cellWidth * cell.placement.cellX, y = cellHeight * cell.placement.cellY)
                    .size(cellWidth * cell.placement.spanX, cellHeight * cell.placement.spanY),
            )
        }
    }
}

@Composable
private fun Dock(
    cells: List<HomeCell>,
    slots: Int,
    iconSize: Dp,
    cornerRadiusPercent: Int,
    viewModel: HomeViewModel,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .height(iconSize + 24.dp),
    ) {
        val slotWidth = maxWidth / slots
        cells.forEach { cell ->
            HomeItem(
                cell = cell,
                registryPage = HomeIconRegistry.DOCK_PAGE,
                iconSize = iconSize,
                cornerRadiusPercent = cornerRadiusPercent,
                // the pixel dock omits labels so it reads as a distinct row
                labelStyle = LabelStyle.Hidden,
                viewModel = viewModel,
                modifier = Modifier
                    .offset(x = slotWidth * cell.placement.cellX)
                    .size(slotWidth, maxHeight),
            )
        }
    }
}

@Composable
private fun HomeItem(
    cell: HomeCell,
    registryPage: Int,
    iconSize: Dp,
    cornerRadiusPercent: Int,
    labelStyle: LabelStyle,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val anchor = rememberLaunchAnchor()
    RegisterHomeIcon(cell.itemId, cell.app.key, registryPage, anchor)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AppIcon(
            appKey = cell.app.key,
            label = cell.title,
            iconSize = iconSize,
            cornerRadiusPercent = cornerRadiusPercent,
            labelStyle = labelStyle,
            anchor = anchor,
            onClick = { viewModel.launch(cell, anchor.request()) },
            onLongClick = { menuOpen = true },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.workspace_app_info)) },
                onClick = {
                    menuOpen = false
                    viewModel.openAppInfo(cell, anchor.request())
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.workspace_remove)) },
                onClick = {
                    menuOpen = false
                    viewModel.remove(cell)
                },
            )
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

