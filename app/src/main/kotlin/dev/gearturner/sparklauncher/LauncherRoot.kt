package dev.gearturner.sparklauncher

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import dev.gearturner.sparklauncher.core.ui.DrawerController
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
    val homeViewModel: HomeViewModel = hiltViewModel()
    val drawerViewModel: DrawerViewModel = hiltViewModel()

    SystemBarAppearance(drawer)
    WallpaperBlurEffect(drawer)
    // leaving for an app resets home quietly so returning never replays the drawer closing
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { drawer.snapClosed() }

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
