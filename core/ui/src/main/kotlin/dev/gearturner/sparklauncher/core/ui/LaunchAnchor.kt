package dev.gearturner.sparklauncher.core.ui

import android.app.ActivityOptions
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.View
import android.window.SplashScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import dev.gearturner.sparklauncher.core.apps.LaunchRequest
import kotlin.math.roundToInt

/**
 * remembers where an icon sits on screen. launches use the system's own open animation, as
 * android ignores custom ones from third party launchers, but the bounds still go to the app
 * so it knows where it was opened from.
 */
@Stable
class LaunchAnchor internal constructor(private val view: View) {

    private var coordinates: LayoutCoordinates? = null

    /** the rendered icon, kept so the swipe home animation can show it in a floating surface */
    var icon: ImageBitmap? = null
        internal set

    /** hides the real icon while a floating copy stands in for it during the close animation */
    var hidden by mutableStateOf(false)

    internal fun update(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
    }

    /** the icon's rectangle in screen pixels, or null when it is not laid out */
    fun boundsOnScreen(): RectF? {
        val coords = coordinates?.takeIf { it.isAttached } ?: return null
        val inRoot = coords.boundsInRoot()
        val origin = IntArray(2).also(view::getLocationOnScreen)
        return RectF(inRoot.left + origin[0], inRoot.top + origin[1], inRoot.right + origin[0], inRoot.bottom + origin[1])
    }

    fun request(): LaunchRequest {
        // the pixel launcher shows the app icon on splash screens, so spark asks for the same
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityOptions.makeBasic().setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON).toBundle()
        } else {
            null
        }
        val coords = coordinates?.takeIf { it.isAttached } ?: return LaunchRequest(options = options)
        val inRoot = coords.boundsInRoot()
        val origin = IntArray(2).also(view::getLocationOnScreen)
        val onScreen = Rect(
            inRoot.left.roundToInt() + origin[0],
            inRoot.top.roundToInt() + origin[1],
            inRoot.right.roundToInt() + origin[0],
            inRoot.bottom.roundToInt() + origin[1],
        )
        return LaunchRequest(sourceBounds = onScreen, options = options)
    }
}

@Composable
fun rememberLaunchAnchor(): LaunchAnchor {
    val view = LocalView.current
    return remember(view) { LaunchAnchor(view) }
}

fun Modifier.launchAnchor(anchor: LaunchAnchor): Modifier = onGloballyPositioned { anchor.update(it) }
