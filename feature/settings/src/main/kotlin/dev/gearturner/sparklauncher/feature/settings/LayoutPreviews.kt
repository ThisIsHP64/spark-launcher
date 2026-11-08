package dev.gearturner.sparklauncher.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.gearturner.sparklauncher.core.model.LauncherSettings
import dev.gearturner.sparklauncher.core.ui.BaseIconSize

private val PreviewHeight = 220.dp

// sizes in real screen dp, scaled down with the rest of the sketch
private const val LABEL_GAP_DP = 6f
private const val LABEL_BAR_DP = 6f
private const val DRAWER_LABEL_DP = 20f
private const val DRAWER_CELL_PADDING_DP = 8f
private const val DRAWER_SIDE_PADDING_DP = 8f
private const val STATUS_BAR_DP = 24f
private const val SEARCH_BAR_DP = 48f
private const val SEARCH_GAP_DP = 12f

/**
 * the on screen size of an icon in a preview. a real icon is [BaseIconSize] times [scale] on a screen
 * [screenWidthDp] wide, so on a sketch [previewWidth] wide it shrinks by the same ratio. using the
 * true ratio means big icons at many columns visibly crowd, exactly as they do on the phone.
 */
internal fun previewIconDiameter(scale: Float, previewWidth: Float, screenWidthDp: Float): Float =
    BaseIconSize.value * scale * previewWidth / screenWidthDp.coerceAtLeast(1f)

/** a phone shaped frame at the screen's own proportions; [draw] gets the real screen width in dp */
@Composable
private fun PreviewPhone(description: String, draw: DrawScope.(screenWidthDp: Float) -> Unit) {
    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp.toFloat()
    val aspect = screenWidthDp / config.screenHeightDp.coerceAtLeast(1)
    val frame = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .height(PreviewHeight)
                .aspectRatio(aspect)
                .semantics { contentDescription = description },
        ) {
            drawRoundRect(frame, cornerRadius = CornerRadius(24.dp.toPx()))
            draw(screenWidthDp)
        }
    }
}

/** an icon dot with an optional label bar under it, the pair centered on [center] */
private fun DrawScope.drawIconWithLabel(
    dot: Color,
    labelColor: Color,
    center: Offset,
    diameter: Float,
    unit: Float,
    showLabel: Boolean,
) {
    val labelBlock = if (showLabel) unit * (LABEL_GAP_DP + LABEL_BAR_DP) else 0f
    val top = center.y - (diameter + labelBlock) / 2f
    drawCircle(dot, diameter / 2f, Offset(center.x, top + diameter / 2f))
    if (showLabel) {
        val barWidth = diameter * 0.8f
        val barHeight = unit * LABEL_BAR_DP
        drawRoundRect(
            labelColor,
            topLeft = Offset(center.x - barWidth / 2f, top + diameter + unit * LABEL_GAP_DP),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barHeight / 2f),
        )
    }
}

/** a sketch of the home grid and dock, with icons at the size the slider is set to */
@Composable
internal fun GridPreview(settings: LauncherSettings, iconScale: Float) {
    val dot = MaterialTheme.colorScheme.primary
    val dockDot = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val description = stringResource(R.string.settings_grid_preview, settings.homeColumns, settings.homeRows, settings.dockSlots)
    PreviewPhone(description) { screenWidthDp ->
        val unit = size.width / screenWidthDp
        val diameter = previewIconDiameter(iconScale, size.width, screenWidthDp)
        val pad = 12.dp.toPx()
        val dockHeight = if (settings.dockSlots > 0) size.height * 0.12f else 0f
        val gridHeight = size.height - 2 * pad - dockHeight - pad
        val cellW = (size.width - 2 * pad) / settings.homeColumns
        val cellH = gridHeight / settings.homeRows
        for (row in 0 until settings.homeRows) {
            for (col in 0 until settings.homeColumns) {
                val center = Offset(pad + cellW * (col + 0.5f), pad + cellH * (row + 0.5f))
                drawIconWithLabel(dot, labelColor, center, diameter, unit, settings.showHomeLabels)
            }
        }
        if (settings.dockSlots > 0) {
            val slotW = (size.width - 2 * pad) / settings.dockSlots
            val y = size.height - pad - dockHeight / 2f
            // the dock never shows labels, so only the icons are drawn there
            for (slot in 0 until settings.dockSlots) {
                drawIconWithLabel(dockDot, labelColor, Offset(pad + slotW * (slot + 0.5f), y), diameter, unit, false)
            }
        }
    }
}

/** a sketch of the app drawer: the search bar, then as many rows of icons as fit */
@Composable
internal fun DrawerPreview(columns: Int, iconScale: Float, showLabels: Boolean) {
    val dot = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val searchBar = MaterialTheme.colorScheme.surface
    val description = stringResource(
        if (showLabels) R.string.settings_drawer_preview_labels else R.string.settings_drawer_preview,
        columns,
    )
    PreviewPhone(description) { screenWidthDp ->
        val unit = size.width / screenWidthDp
        val diameter = previewIconDiameter(iconScale, size.width, screenWidthDp)
        val side = DRAWER_SIDE_PADDING_DP * unit
        val barTop = STATUS_BAR_DP * unit
        val barHeight = SEARCH_BAR_DP * unit
        drawRoundRect(
            searchBar,
            topLeft = Offset(side * 2f, barTop),
            size = Size(size.width - side * 4f, barHeight),
            cornerRadius = CornerRadius(barHeight / 2f),
        )
        val gridTop = barTop + barHeight + SEARCH_GAP_DP * unit
        val cellW = (size.width - 2 * side) / columns
        val iconDp = BaseIconSize.value * iconScale
        val cellH = unit * (iconDp + (if (showLabels) DRAWER_LABEL_DP else 0f) + 2 * DRAWER_CELL_PADDING_DP)
        // only whole rows are drawn, as a list that is scrolled to the top would show them
        var row = 0
        while (gridTop + cellH * (row + 1) <= size.height - side) {
            for (col in 0 until columns) {
                val center = Offset(side + cellW * (col + 0.5f), gridTop + cellH * (row + 0.5f))
                drawIconWithLabel(dot, labelColor, center, diameter, unit, showLabels)
            }
            row++
        }
    }
}
