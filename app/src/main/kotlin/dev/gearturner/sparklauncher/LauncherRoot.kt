package dev.gearturner.sparklauncher

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.gearturner.sparklauncher.core.ui.DragController
import dev.gearturner.sparklauncher.core.ui.DragLayer
import dev.gearturner.sparklauncher.core.ui.DrawerController
import dev.gearturner.sparklauncher.core.ui.LocalDragController
import dev.gearturner.sparklauncher.core.ui.LocalDrawer
import dev.gearturner.sparklauncher.core.ui.LocalWidgetPicker
import dev.gearturner.sparklauncher.core.ui.WidgetPickerController
import dev.gearturner.sparklauncher.feature.widgets.LocalWidgetBinder
import dev.gearturner.sparklauncher.feature.widgets.WidgetPicker
import dev.gearturner.sparklauncher.feature.widgets.WidgetPickerViewModel
import dev.gearturner.sparklauncher.core.ui.rememberDrawerController
import dev.gearturner.sparklauncher.feature.drawer.AppDrawer
import dev.gearturner.sparklauncher.feature.drawer.DrawerViewModel
import dev.gearturner.sparklauncher.feature.workspace.HomeScreen
import dev.gearturner.sparklauncher.feature.workspace.HomeViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun LauncherRoot(homePresses: Flow<Unit>) {
    val drawer = rememberDrawerController()
    val dragController = remember { DragController() }
    val widgetPicker = remember { WidgetPickerController() }
    val widgetPickerViewModel: WidgetPickerViewModel = hiltViewModel()
    val binder = LocalWidgetBinder.current
    val homeViewModel: HomeViewModel = hiltViewModel()
    val drawerViewModel: DrawerViewModel = hiltViewModel()

    SystemBarAppearance(drawer)
    WallpaperBlurEffect(drawer)
    // leaving for an app resets home quietly so returning never replays the drawer closing
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        drawer.snapClosed()
        // a drag cannot survive the launcher being hidden, for example by an incoming call
        dragController.cancel()
        widgetPicker.close()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { homeViewModel.onResume() }

    CompositionLocalProvider(
        LocalDragController provides dragController,
        LocalWidgetPicker provides widgetPicker,
        LocalDrawer provides drawer,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            drawer.travelPx = with(LocalDensity.current) { maxHeight.toPx() }
            // a light scrim follows the drawer so the wallpaper dims as it slides in
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f * drawer.value)),
            )
            HomeScreen(
                viewModel = homeViewModel,
                drawer = drawer,
                homePresses = homePresses,
                modifier = Modifier.blurBehindDrawer(drawer),
            )
            AppDrawer(viewModel = drawerViewModel, drawer = drawer)
            WidgetPicker(
                viewModel = widgetPickerViewModel,
                controller = widgetPicker,
                onAdd = { provider, span ->
                    widgetPicker.close()
                    homeViewModel.addWidget(provider.key, span, binder)
                },
            )
            DragLayer(dragController)
        }
    }
}

/** status and nav bar icons stay light over the wallpaper but turn dark over a light drawer */
@Composable
private fun SystemBarAppearance(drawer: DrawerController) {
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    LaunchedEffect(view, darkTheme) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        snapshotFlow { drawer.isOpen && !darkTheme }
            .distinctUntilChanged()
            .collect { darkIcons ->
                controller.isAppearanceLightStatusBars = darkIcons
                controller.isAppearanceLightNavigationBars = darkIcons
            }
    }
}
