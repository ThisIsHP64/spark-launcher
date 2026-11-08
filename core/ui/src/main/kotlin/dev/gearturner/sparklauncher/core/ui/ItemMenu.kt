package dev.gearturner.sparklauncher.core.ui

import android.graphics.Rect as AndroidRect
import androidx.compose.ui.platform.LocalView

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import kotlin.math.roundToInt

import androidx.compose.material3.DropdownMenuPopupPositionProvider
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.AppShortcut
import dev.gearturner.sparklauncher.core.model.IconSource

/** which section of a menu an action sits in; destructive actions get their own, error colored group */
enum class MenuGroup { Primary, Destructive }

@Immutable
data class MenuAction(
    val label: String,
    val icon: ImageVector,
    val group: MenuGroup = MenuGroup.Primary,
    val onClick: () -> Unit,
)

/** one row of a menu, either an app shortcut or an action */
internal sealed interface MenuEntry {
    data class Shortcut(val shortcut: AppShortcut) : MenuEntry
    data class Action(val action: MenuAction) : MenuEntry
}

/**
 * splits a menu into its visual groups: shortcuts, then everyday actions, then destructive ones.
 * empty groups are dropped so the rounded group shapes are computed from what is actually shown.
 */
internal fun groupMenu(shortcuts: List<AppShortcut>, actions: List<MenuAction>): List<List<MenuEntry>> =
    listOf(
        shortcuts.map(MenuEntry::Shortcut),
        actions.filter { it.group == MenuGroup.Primary }.map(MenuEntry::Action),
        actions.filter { it.group == MenuGroup.Destructive }.map(MenuEntry::Action),
    ).filter { it.isNotEmpty() }

/**
 * the long press menu shared by home, drawer, folders, widgets, and empty space, in the grouped
 * material 3 expressive style. shortcuts load when the menu opens because apps change them often
 * and the query is cheap.
 */
@Composable
fun ItemMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: List<MenuAction>,
    owner: AppKey? = null,
    loadShortcuts: (suspend () -> List<AppShortcut>)? = null,
    onShortcutClick: (AppShortcut) -> Unit = {},
    onPinShortcut: ((AppShortcut) -> Unit)? = null,
    iconBounds: (() -> Rect?)? = null,
) {
    val latestIconBounds by rememberUpdatedState(iconBounds)
    var shortcuts by remember { mutableStateOf<List<AppShortcut>>(emptyList()) }
    LaunchedEffect(expanded) {
        // cleared on close too, so a reopened menu never flashes the previous app's shortcuts
        shortcuts = if (expanded && loadShortcuts != null) loadShortcuts() else emptyList()
    }
    val groups = groupMenu(shortcuts, actions)
    val density = LocalDensity.current
    val view = LocalView.current
    val placement = remember(density, view) {
        ItemMenuPlacement(
            marginPx = with(density) { MenuMargin.roundToPx() },
            gapPx = with(density) { MenuGap.roundToPx() },
            iconBounds = { latestIconBounds?.invoke() },
            // the launcher draws edge to edge, but the system keeps menus out of the status and
            // navigation bars, so the room a menu has is the visible frame, not the whole window
            visibleArea = {
                val frame = AndroidRect().also(view::getWindowVisibleDisplayFrame)
                val window = IntArray(2).also(view::getLocationOnScreen)
                IntRect(frame.left - window[0], frame.top - window[1], frame.right - window[0], frame.bottom - window[1])
            },
        )
    }
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss, popupPositionProvider = placement) {
        groups.forEachIndexed { groupIndex, entries ->
            if (groupIndex > 0) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(groupIndex, groups.size)) {
                entries.forEachIndexed { index, entry ->
                    val shape = MenuDefaults.itemShape(index, entries.size).shape
                    when (entry) {
                        is MenuEntry.Shortcut -> ShortcutItem(entry.shortcut, shape, owner, onDismiss, onShortcutClick, onPinShortcut)
                        is MenuEntry.Action -> ActionItem(entry.action, shape, onDismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutItem(
    shortcut: AppShortcut,
    shape: Shape,
    owner: AppKey?,
    onDismiss: () -> Unit,
    onClick: (AppShortcut) -> Unit,
    onPin: ((AppShortcut) -> Unit)?,
) {
    val icon = rememberIcon(IconSource.Shortcut(shortcut.key, owner), MenuIconSize)
    val label = if (shortcut.isEnabled) shortcut.menuLabel else stringResource(R.string.ui_shortcut_disabled, shortcut.shortLabel)
    DropdownMenuItem(
        onClick = {
            onDismiss()
            onClick(shortcut)
        },
        text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        shape = shape,
        enabled = shortcut.isEnabled,
        leadingIcon = {
            if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(MenuIconSize))
        },
        trailingContent = onPin?.takeIf { shortcut.isEnabled }?.let { pin ->
            {
                IconButton(onClick = {
                    onDismiss()
                    pin(shortcut)
                }) {
                    Icon(Icons.Outlined.PushPin, contentDescription = stringResource(R.string.ui_pin_shortcut, shortcut.shortLabel))
                }
            }
        },
    )
}

@Composable
private fun ActionItem(action: MenuAction, shape: Shape, onDismiss: () -> Unit) {
    val colors = if (action.group == MenuGroup.Destructive) {
        val error = MaterialTheme.colorScheme.error
        MenuDefaults.itemColors(textColor = error, leadingIconColor = error)
    } else {
        MenuDefaults.itemColors()
    }
    DropdownMenuItem(
        onClick = {
            onDismiss()
            action.onClick()
        },
        text = { Text(action.label) },
        shape = shape,
        leadingIcon = { Icon(action.icon, contentDescription = null) },
        colors = colors,
    )
}

private val MenuIconSize = 24.dp
private val MenuMargin = 8.dp
private val MenuGap = 4.dp

/**
 * where a long press menu goes relative to the item it belongs to, in window pixels. like the pixel
 * launcher it sits above the item, lined up with the item's leading edge and slid inward to stay on
 * screen. a menu too tall for the room above opens beside the item instead, then below it, and it
 * only covers the item when it fits nowhere else.
 */
internal fun menuPosition(
    anchor: IntRect,
    area: IntRect,
    content: IntSize,
    marginPx: Int,
    gapPx: Int,
    rtl: Boolean = false,
): IntOffset {
    val minX = area.left + marginPx
    val maxX = (area.right - marginPx - content.width).coerceAtLeast(minX)
    val leading = if (rtl) anchor.right - content.width else anchor.left
    val trailing = if (rtl) anchor.left else anchor.right - content.width
    val x = when {
        leading in minX..maxX -> leading
        trailing in minX..maxX -> trailing
        // wider than either alignment allows, so it starts as close to the item's leading edge as it can
        else -> leading.coerceIn(minX, maxX)
    }
    val highest = area.top + marginPx
    val lowest = (area.bottom - marginPx - content.height).coerceAtLeast(highest)
    val above = anchor.top - gapPx - content.height
    val below = anchor.bottom + gapPx
    if (above >= highest) return IntOffset(x, above)
    // beside the item, on whichever side has room, starting level with the item's top
    val besideY = anchor.top.coerceIn(highest, lowest)
    val start = anchor.left - gapPx - content.width
    val end = anchor.right + gapPx
    val sides = if (rtl) listOf(end, start) else listOf(start, end)
    sides.firstOrNull { it >= minX && it + content.width <= area.right - marginPx }?.let { return IntOffset(it, besideY) }
    val y = when {
        below <= lowest -> below
        // too tall for anywhere, so it hugs the roomier side and overlaps as little as it can
        anchor.top - area.top > area.bottom - anchor.bottom -> highest
        else -> lowest
    }
    return IntOffset(x, y)
}

/**
 * feeds [menuPosition] to the popup and grows the menu out of the item it opened from. a grid cell
 * reaches well above its icon, so when [iconBounds] is known the menu measures from the icon's top
 * instead, which leaves room for it above icons that are lower on the page.
 */
private class ItemMenuPlacement(
    private val marginPx: Int,
    private val gapPx: Int,
    private val iconBounds: () -> Rect?,
    private val visibleArea: () -> IntRect,
) : DropdownMenuPopupPositionProvider {
    override var transformOrigin: TransformOrigin = TransformOrigin.Center
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val icon = iconBounds()
        val item = if (icon == null) {
            anchorBounds
        } else {
            IntRect(anchorBounds.left, maxOf(anchorBounds.top, icon.top.roundToInt()).coerceAtMost(anchorBounds.bottom), anchorBounds.right, anchorBounds.bottom)
        }
        val area = visibleArea().takeIf { it.width > 0 && it.height > 0 } ?: IntRect(IntOffset.Zero, windowSize)
        val position = menuPosition(item, area, popupContentSize, marginPx, gapPx, layoutDirection == LayoutDirection.Rtl)
        if (popupContentSize.width > 0 && popupContentSize.height > 0) {
            val pivotX = (item.center.x - position.x).toFloat() / popupContentSize.width
            val pivotY = if (position.y + popupContentSize.height <= item.top) 1f else 0f
            transformOrigin = TransformOrigin(pivotX.coerceIn(0f, 1f), pivotY)
        }
        return position
    }
}
