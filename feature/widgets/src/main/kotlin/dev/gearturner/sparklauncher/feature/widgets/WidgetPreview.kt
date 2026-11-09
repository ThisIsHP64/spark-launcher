package dev.gearturner.sparklauncher.feature.widgets

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.widget.RemoteViews
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * shows what a widget looks like, scaled to fit [box]. android 12 widgets can ship a live layout
 * as their preview, which is drawn through a throwaway host view; older ones ship an image.
 */
@Composable
fun WidgetPreview(provider: WidgetProvider, fullWidth: Dp, fullHeight: Dp, box: Dp) {
    val scale = min(box / fullWidth, box / fullHeight).coerceAtMost(1f)
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(fullWidth * scale, fullHeight * scale)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val layoutId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) provider.info.previewLayout else 0
        if (layoutId != 0) {
            AndroidView(
                factory = { context ->
                    InertHostView(context).apply {
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        setAppWidget(NO_WIDGET_ID, provider.info)
                        try {
                            updateAppWidget(RemoteViews(provider.info.provider.packageName, layoutId))
                        } catch (e: RuntimeException) {
                            // a broken preview layout falls back to the host view's own error state
                        }
                    }
                },
                modifier = Modifier
                    .requiredSize(fullWidth, fullHeight)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            )
        } else {
            val image = rememberPreviewImage(provider)
            if (image != null) {
                Image(bitmap = image, contentDescription = null, modifier = Modifier.size(fullWidth * scale, fullHeight * scale))
            } else {
                Box(
                    modifier = Modifier
                        .size(fullWidth * scale, fullHeight * scale)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape),
                )
            }
        }
    }
}

/**
 * a preview is only a picture, so its buttons must never fire. intercepting stops every child from
 * seeing a touch, and not handling it lets the card's own tap and drag gestures still receive it.
 */
private class InertHostView(context: Context) : AppWidgetHostView(context) {
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean = false
}

@Composable
fun rememberPreviewImage(provider: WidgetProvider): ImageBitmap? {
    val context = LocalContext.current
    val density = remember(context) { context.resources.displayMetrics.densityDpi }
    return produceState<ImageBitmap?>(null, provider.key) {
        value = withContext(Dispatchers.IO) {
            try {
                (provider.info.loadPreviewImage(context, density) ?: provider.info.loadIcon(context, density))
                    ?.toBitmap()
                    ?.asImageBitmap()
            } catch (e: RuntimeException) {
                null
            }
        }
    }.value
}

private const val NO_WIDGET_ID = -1
