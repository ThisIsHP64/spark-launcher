package dev.gearturner.sparklauncher.feature.workspace

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
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
import dev.gearturner.sparklauncher.core.model.SUBGRID
import dev.gearturner.sparklauncher.core.ui.DragPayload
import dev.gearturner.sparklauncher.core.ui.DragSession
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
 * where the home grid, dock, and drop bar sit on screen, refreshed on every layout. drag math reads
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
    /** when on, drops snap to half cells instead of whole ones */
    var subgrid: Boolean = false
    /** the placement of an icon or folder covering a half cell spot on a page, so a drop over it can merge */
    var iconAt: (page: Int, x: Int, y: Int) -> CellPlacement? = { _, _, _ -> null }

    /** the size of a whole grid cell */
    fun cellWidthPx(): Float = pagerBounds?.let { (it.width - 2 * gridPaddingPx) / columns } ?: 0f

    fun cellHeightPx(): Float = pagerBounds?.let { it.height / rows } ?: 0f

    /** placements are stored in half cells, so positions and spans are measured in these */
    fun unitWidthPx(): Float = cellWidthPx() / SUBGRID

    fun unitHeightPx(): Float = cellHeightPx() / SUBGRID

    /** how many half cells a drop or resize moves by: one with subgrid positioning, a whole cell without */
    fun snapStep(): Int = if (subgrid) 1 else SUBGRID

    /** the on screen rectangle of a placement on the visible page */
    fun rectOf(placement: CellPlacement): Rect? {
        val pager = pagerBounds ?: return null
        val left = pager.left + gridPaddingPx + placement.cellX * unitWidthPx()
        val top = pager.top + placement.cellY * unitHeightPx()
        return Rect(left, top, left + placement.spanX * unitWidthPx(), top + placement.spanY * unitHeightPx())
    }

    fun resolve(session: DragSession, page: Int): DropZone =
        resolve(session.fingerInRoot, session.fingerInRoot - session.grabOffset, page, session.payload)

    /**
     * single icons land under the finger; widgets land where their top left corner sits, so a
     * widget drops exactly where its lifted copy is drawn. an icon over another icon or folder
     * targets that item's exact spot, so merging works the same with or without subgrid positioning.
     */
    fun resolve(finger: Offset, itemTopLeft: Offset, page: Int, payload: DragPayload): DropZone {
        if (removeBounds?.contains(finger) == true && payload is DragPayload.Existing) return DropZone.Remove
        if (uninstallBounds?.contains(finger) == true && payload.canUninstall()) return DropZone.Uninstall
        when (payload) {
            is DragPayload.NewWidget -> return resolveBlock(itemTopLeft, page, payload.spanX * SUBGRID, payload.spanY * SUBGRID)
            is DragPayload.Existing -> if (payload.isWidget) return resolveBlock(itemTopLeft, page, payload.spanX, payload.spanY)
            is DragPayload.NewApp -> Unit
        }
        dockBounds?.takeIf { it.contains(finger) && dockSlots > 0 }?.let { dock ->
            val slotWidth = dock.width / dockSlots
            val slot = floor((finger.x - dock.left) / slotWidth).toInt().coerceIn(0, dockSlots - 1)
            val rect = Rect(dock.left + slot * slotWidth, dock.top, dock.left + (slot + 1) * slotWidth, dock.bottom)
            return DropZone.Cell(DropTarget.Dock(slot), rect)
        }
        val pager = pagerBounds?.takeIf { it.contains(finger) } ?: return DropZone.None
        val unitWidth = unitWidthPx()
        val unitHeight = unitHeightPx()
        if (unitWidth <= 0f || unitHeight <= 0f) return DropZone.None
        val gridColumns = columns * SUBGRID
        val gridRows = rows * SUBGRID
        val fx = (finger.x - pager.left - gridPaddingPx) / unitWidth
        val fy = (finger.y - pager.top) / unitHeight
        if (fx < 0f || fy < 0f || fx >= gridColumns || fy >= gridRows) return DropZone.None
        // with half cells, only the middle of an icon merges, so its outer edges stay free for the
        // half spots right beside it
        val inset = if (subgrid) MERGE_INSET else 0f
        val spot = iconAt(page, floor(fx).toInt(), floor(fy).toInt())?.takeIf { p ->
            fx >= p.cellX + inset && fx < p.cellX + p.spanX - inset && fy >= p.cellY + inset && fy < p.cellY + p.spanY - inset
        }
            ?: if (subgrid) {
                // the icon is centered on the finger, then snapped to the nearest half cell
                CellPlacement(
                    page,
                    (fx - SUBGRID / 2f).roundToInt().coerceIn(0, gridColumns - SUBGRID),
                    (fy - SUBGRID / 2f).roundToInt().coerceIn(0, gridRows - SUBGRID),
                    SUBGRID,
                    SUBGRID,
                )
            } else {
                CellPlacement(page, floor(fx / SUBGRID).toInt() * SUBGRID, floor(fy / SUBGRID).toInt() * SUBGRID, SUBGRID, SUBGRID)
            }
        val rect = rectOf(spot) ?: return DropZone.None
        return DropZone.Cell(DropTarget.Desktop(page, spot.cellX, spot.cellY), rect)
    }

    private fun resolveBlock(topLeft: Offset, page: Int, spanX: Int, spanY: Int): DropZone {
        val pager = pagerBounds ?: return DropZone.None
        val unitWidth = unitWidthPx()
        val unitHeight = unitHeightPx()
        if (unitWidth <= 0f || unitHeight <= 0f) return DropZone.None
        val gridColumns = columns * SUBGRID
        val gridRows = rows * SUBGRID
        if (spanX > gridColumns || spanY > gridRows) return DropZone.None
        val step = snapStep()
        // rounding to the nearest snap line feels like snapping instead of jumping a cell early
        fun snap(offset: Float, unit: Float, room: Int): Int {
            val steps = (offset / (unit * step)).roundToInt()
            return (steps * step).coerceIn(0, (room / step) * step)
        }
        val x = snap(topLeft.x - pager.left - gridPaddingPx, unitWidth, gridColumns - spanX)
        val y = snap(topLeft.y - pager.top, unitHeight, gridRows - spanY)
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

/**
 * the remove and uninstall targets while something is being dragged: two large circles over a blurred
 * copy of the home content behind them. the wallpaper is drawn by the system outside the app, so a light
 * dim covers it instead, and the blur fades out toward the bottom so it has no hard edge.
 * [backdrop] must hold the home content recorded at the same origin as this bar.
 */
@Composable
internal fun DropBar(
    session: DragSession,
    visible: Boolean,
    hovered: DropZone,
    geometry: HomeGeometry,
    backdrop: GraphicsLayer,
    onGone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appear = remember { Animatable(0f) }
    val latestOnGone by rememberUpdatedState(onGone)
    // fades out as well as in; the caller keeps the bar composed until this reports it has finished
    LaunchedEffect(visible) {
        appear.animateTo(if (visible) 1f else 0f, tween(DROP_BAR_FADE_MS))
        if (!visible) latestOnGone()
    }
    val blurred = rememberGraphicsLayer()
    val blurPx = with(LocalDensity.current) { DROP_BAR_BLUR.toPx() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value
                // offscreen so the fade mask below only cuts into the blur, never into what is behind the bar
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                blurred.renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
                // recorded at the full backdrop size so the blur samples content below the bar, not a clamped edge
                blurred.record(size = backdrop.size) { drawLayer(backdrop) }
                clipRect { this@drawWithContent.drawLayer(blurred) }
                drawRect(
                    Brush.verticalGradient(0f to Color.Black, BLUR_SOLID_UNTIL to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
                drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = DROP_BAR_DIM_ALPHA), Color.Transparent)))
                drawContent()
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 16.dp, bottom = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Top,
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
}

@Composable
private fun DropBarTarget(label: String, icon: ImageVector, active: Boolean, onBounds: (Rect) -> Unit) {
    val container by animateColorAsState(
        if (active) MaterialTheme.colorScheme.errorContainer else Color.White.copy(alpha = INACTIVE_TARGET_ALPHA),
        label = "drop_target_color",
    )
    val content = if (active) MaterialTheme.colorScheme.onErrorContainer else Color.White
    val scale by animateFloatAsState(if (active) ACTIVE_TARGET_SCALE else 1f, label = "drop_target_scale")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                // the hit area is grown well past the circle so a quick flick upward still lands on it
                .onGloballyPositioned { coords ->
                    val b = coords.boundsInRoot()
                    onBounds(Rect(b.left - 48f, b.top - 200f, b.right + 48f, b.bottom + 96f))
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(TARGET_SIZE)
                .background(container, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

private const val DROP_BAR_FADE_MS = 150
/** how far in from each side of an icon, in half cells, a finger must be to merge with it */
private const val MERGE_INSET = 0.5f
private const val DROP_BAR_DIM_ALPHA = 0.55f
private const val BLUR_SOLID_UNTIL = 0.6f
private val DROP_BAR_BLUR = 40.dp
private const val INACTIVE_TARGET_ALPHA = 0.2f
private const val ACTIVE_TARGET_SCALE = 1.15f
private val TARGET_SIZE = 72.dp

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
