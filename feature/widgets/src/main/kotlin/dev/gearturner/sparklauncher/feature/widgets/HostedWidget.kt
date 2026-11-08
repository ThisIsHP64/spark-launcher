package dev.gearturner.sparklauncher.feature.widgets

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.gearturner.sparklauncher.core.model.WidgetOptions
import dev.gearturner.sparklauncher.core.ui.IconDragHandler
import dev.gearturner.sparklauncher.core.ui.widgetGestures

/**
 * keeps one host view per widget for the life of the launcher window, so paging away and back
 * reattaches the same live widget instead of rebuilding it and losing its state.
 */
@Stable
class WidgetViews(private val context: Context, private val hostManager: WidgetHostManager) {

    private val views = HashMap<Int, AppWidgetHostView>()

    fun get(appWidgetId: Int): AppWidgetHostView? {
        views[appWidgetId]?.let { return it }
        val info = hostManager.info(appWidgetId) ?: return null
        val view = try {
            hostManager.host.createView(context, appWidgetId, info)
        } catch (e: RuntimeException) {
            return null
        }
        // spark applies its own padding option, so the platform's default inset is cleared
        view.setPadding(0, 0, 0, 0)
        views[appWidgetId] = view
        return view
    }

    /** drops views tied to the launcher window; the widgets themselves keep running */
    fun clear() = views.clear()
}

val LocalWidgetViews = staticCompositionLocalOf<WidgetViews?> { null }

/** the standard inset nova and the pixel launcher leave around widgets when padding is on */
private val WidgetInset = 8.dp

@Composable
fun HostedWidget(
    appWidgetId: Int,
    options: WidgetOptions,
    onLongClick: () -> Unit,
    dragHandler: IconDragHandler?,
    modifier: Modifier = Modifier,
    onBounds: (Rect) -> Unit = {},
) {
    val views = LocalWidgetViews.current
    val view = remember(views, appWidgetId) { views?.get(appWidgetId) }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val shape = RoundedCornerShape(options.cornerRadiusDp.dp)
    Box(
        modifier = modifier
            .onGloballyPositioned {
                coordinates = it
                onBounds(it.boundsInRoot())
            }
            .widgetGestures(
                coordinates = { coordinates },
                bounds = { coordinates?.takeIf { it.isAttached }?.boundsInRoot() },
                haptics = haptics,
                onLongClick = onLongClick,
                dragHandler = dragHandler,
            )
            .padding(if (options.padding) WidgetInset else 0.dp)
            .graphicsLayer { alpha = options.opacityPercent / 100f }
            .clip(shape),
    ) {
        if (view == null) {
            // the provider was uninstalled or its profile paused; the cell stays put until it returns
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f), shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.widget_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp),
                )
            }
        } else {
            AndroidView(
                factory = {
                    // the cached view may still belong to a page that is being disposed
                    (view.parent as? ViewGroup)?.removeView(view)
                    view
                },
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        // telling the widget its real size lets it pick the right layout
                        val sizeDp = with(density) { SizeF(size.width.toDp().value, size.height.toDp().value) }
                        if (sizeDp.width > 0 && sizeDp.height > 0) {
                            try {
                                view.updateAppWidgetSize(Bundle(), listOf(sizeDp))
                            } catch (e: RuntimeException) {
                                // a widget whose provider just died ignores the update
                            }
                        }
                    },
            )
        }
    }
}
