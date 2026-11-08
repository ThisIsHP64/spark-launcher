package dev.gearturner.sparklauncher.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.gearturner.sparklauncher.core.model.CellPlacement
import dev.gearturner.sparklauncher.core.model.DropTarget
import dev.gearturner.sparklauncher.core.ui.DragPayload
import dev.gearturner.sparklauncher.core.ui.DragSession
import dev.gearturner.sparklauncher.core.ui.spanX
import dev.gearturner.sparklauncher.core.ui.spanY
import kotlin.math.floor
import kotlin.math.roundToInt

/** what the finger is over during a drag */
internal sealed interface DropZone {
    /** [highlight] is the cell's rectangle in root coordinates, for the hover outline */
    data class Cell(val target: DropTarget, val highlight: Rect) : DropZone
    data object Remove : DropZone
    data object Uninstall : DropZone
    data object None : DropZone
}

/**
 * where the home grid, dock and drop bar sit on screen, refreshed on every layout. drag math reads
 * it directly instead of through compose state because it changes only when the layout does.
 */
@Stable
internal class HomeGeometry {
    var pagerBounds: Rect? = null
    var gridPaddingPx: Float = 0f
    var columns: Int = 1
    var rows: Int = 1
    var dockBounds: Rect? = null
    var dockSlots: Int = 0
    var removeBounds: Rect? = null
    var uninstallBounds: Rect? = null

    fun cellWidthPx(): Float = pagerBounds?.let { (it.width - 2 * gridPaddingPx) / columns } ?: 0f

    fun cellHeightPx(): Float = pagerBounds?.let { it.height / rows } ?: 0f

    /** the on screen rectangle of a placement on the visible page */
    fun rectOf(placement: CellPlacement): Rect? {
        val pager = pagerBounds ?: return null
        val left = pager.left + gridPaddingPx + placement.cellX * cellWidthPx()
        val top = pager.top + placement.cellY * cellHeightPx()
        return Rect(left, top, left + placement.spanX * cellWidthPx(), top + placement.spanY * cellHeightPx())
    }

    fun resolve(session: DragSession, page: Int): DropZone =
        resolve(session.fingerInRoot, session.fingerInRoot - session.grabOffset, page, session.payload)

    /**
     * single icons land under the finger; larger items land where their top left cell sits, so a
     * widget drops exactly where its lifted copy is drawn.
     */
    fun resolve(finger: Offset, itemTopLeft: Offset, page: Int, payload: DragPayload): DropZone {
        if (removeBounds?.contains(finger) == true && payload is DragPayload.Existing) return DropZone.Remove
        if (uninstallBounds?.contains(finger) == true && payload.canUninstall()) return DropZone.Uninstall
        val spanX = payload.spanX
        val spanY = payload.spanY
        if (spanX > 1 || spanY > 1) return resolveBlock(itemTopLeft, page, spanX, spanY)
        dockBounds?.takeIf { it.contains(finger) && dockSlots > 0 }?.let { dock ->
            val slotWidth = dock.width / dockSlots
            val slot = floor((finger.x - dock.left) / slotWidth).toInt().coerceIn(0, dockSlots - 1)
            val rect = Rect(dock.left + slot * slotWidth, dock.top, dock.left + (slot + 1) * slotWidth, dock.bottom)
            return DropZone.Cell(DropTarget.Dock(slot), rect)
        }
        val pager = pagerBounds?.takeIf { it.contains(finger) } ?: return DropZone.None
        val gridLeft = pager.left + gridPaddingPx
        val cellWidth = (pager.width - 2 * gridPaddingPx) / columns
        val cellHeight = pager.height / rows
        val x = floor((finger.x - gridLeft) / cellWidth).toInt()
        val y = floor((finger.y - pager.top) / cellHeight).toInt()
        if (x !in 0 until columns || y !in 0 until rows) return DropZone.None
        val rect = Rect(
            gridLeft + x * cellWidth,
            pager.top + y * cellHeight,
            gridLeft + (x + 1) * cellWidth,
            pager.top + (y + 1) * cellHeight,
        )
        return DropZone.Cell(DropTarget.Desktop(page, x, y), rect)
    }

    private fun resolveBlock(topLeft: Offset, page: Int, spanX: Int, spanY: Int): DropZone {
        val pager = pagerBounds ?: return DropZone.None
        val cellWidth = cellWidthPx()
        val cellHeight = cellHeightPx()
        if (cellWidth <= 0f || cellHeight <= 0f) return DropZone.None
        // rounding to the nearest cell edge feels like snapping instead of jumping a cell early
        val x = ((topLeft.x - pager.left - gridPaddingPx) / cellWidth).roundToInt().coerceIn(0, (columns - spanX).coerceAtLeast(0))
        val y = ((topLeft.y - pager.top) / cellHeight).roundToInt().coerceIn(0, (rows - spanY).coerceAtLeast(0))
        if (spanX > columns || spanY > rows) return DropZone.None
        val rect = rectOf(CellPlacement(page, x, y, spanX, spanY)) ?: return DropZone.None
        return DropZone.Cell(DropTarget.Desktop(page, x, y), rect)
    }

    /** -1 near the left edge, 1 near the right, 0 elsewhere; used to flip pages mid drag */
    fun edgeDirection(finger: Offset, edgePx: Float): Int {
        val pager = pagerBounds ?: return 0
        if (finger.y !in pager.top..pager.bottom) return 0
        return when {
            finger.x < pager.left + edgePx -> -1
            finger.x > pager.right - edgePx -> 1
            else -> 0
        }
    }
}

internal fun DragPayload.canUninstall(): Boolean = when (this) {
    is DragPayload.Existing -> canUninstall
    is DragPayload.NewApp -> canUninstall
    is DragPayload.NewWidget -> false
}

/** the remove and uninstall targets along the top while something is being dragged */
@Composable
internal fun DropBar(session: DragSession, hovered: DropZone, geometry: HomeGeometry, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (session.payload is DragPayload.Existing) {
            DropBarTarget(
                label = stringResource(R.string.workspace_remove),
                icon = Icons.Outlined.Close,
                active = hovered == DropZone.Remove,
                onBounds = { geometry.removeBounds = it },
            )
        } else {
            geometry.removeBounds = null
        }
        if (session.payload.canUninstall()) {
            Spacer(modifier = Modifier.width(24.dp))
            DropBarTarget(
                label = stringResource(R.string.workspace_uninstall),
                icon = Icons.Outlined.Delete,
                active = hovered == DropZone.Uninstall,
                onBounds = { geometry.uninstallBounds = it },
            )
        } else {
            geometry.uninstallBounds = null
        }
    }
}

@Composable
private fun DropBarTarget(label: String, icon: ImageVector, active: Boolean, onBounds: (Rect) -> Unit) {
    val container = if (active) MaterialTheme.colorScheme.errorContainer else Color.Black.copy(alpha = 0.35f)
    val content = if (active) MaterialTheme.colorScheme.onErrorContainer else Color.White
    Row(
        modifier = Modifier
            // the hit area is grown well past the pill so a quick flick upward still lands on it
            .onGloballyPositioned { coords ->
                val b = coords.boundsInRoot()
                onBounds(Rect(b.left - 48f, b.top - 200f, b.right + 48f, b.bottom + 48f))
            }
            .background(container, CircleShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = content, style = MaterialTheme.typography.labelLarge)
    }
}

/** an outline on the cell the dragged icon would land in */
@Composable
internal fun DropHighlight(zone: DropZone, rootOrigin: Offset) {
    val cell = zone as? DropZone.Cell ?: return
    val density = LocalDensity.current
    val rect = cell.highlight.translate(-rootOrigin)
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                .padding(4.dp)
                .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp)),
        )
    }
}
