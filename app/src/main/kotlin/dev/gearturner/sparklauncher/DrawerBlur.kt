package dev.gearturner.sparklauncher

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import dev.gearturner.sparklauncher.core.ui.DrawerController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

private val HomeContentBlur = 12.dp
private val WallpaperBlur = 28.dp

/**
 * blurs the home screen layer in step with the drawer.
 * the radius is read inside the graphics layer so dragging only redraws, never recomposes.
 */
@Composable
fun Modifier.blurBehindDrawer(drawer: DrawerController): Modifier {
    val maxRadiusPx = with(LocalDensity.current) { HomeContentBlur.toPx() }
    return graphicsLayer {
        val radius = drawer.value * maxRadiusPx
        // decal keeps icons near the screen edge from smearing into a solid band
        renderEffect = if (radius >= 1f) BlurEffect(radius, radius, TileMode.Decal) else null
    }
}

/**
 * blurs the system wallpaper behind the launcher window, which compose cannot reach because it is
 * drawn by another window. android turns cross window blur off in battery saver and on some
 * devices, so the effect follows that switch and simply stays off when blur is unavailable.
 */
@Composable
fun WallpaperBlurEffect(drawer: DrawerController) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    val windowManager = view.context.getSystemService(WindowManager::class.java) ?: return
    val maxRadiusPx = with(LocalDensity.current) { WallpaperBlur.toPx() }
    var blurEnabled by remember { mutableStateOf(windowManager.isCrossWindowBlurEnabled) }

    DisposableEffect(windowManager) {
        val listener = java.util.function.Consumer<Boolean> { enabled -> blurEnabled = enabled }
        windowManager.addCrossWindowBlurEnabledListener(view.context.mainExecutor, listener)
        onDispose {
            windowManager.removeCrossWindowBlurEnabledListener(listener)
            window.setBackgroundBlurRadius(0)
        }
    }

    LaunchedEffect(window, blurEnabled, maxRadiusPx) {
        if (!blurEnabled) {
            window.setBackgroundBlurRadius(0)
            return@LaunchedEffect
        }
        snapshotFlow { (drawer.value * maxRadiusPx).roundToInt() }
            .distinctUntilChanged()
            .collect { radius -> window.setBackgroundBlurRadius(radius) }
    }
}
