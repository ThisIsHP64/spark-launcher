package dev.gearturner.sparklauncher.core.ui

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
 * the long press menu shared by home, drawer, folders, widgets and empty space, in the grouped
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
) {
    var shortcuts by remember { mutableStateOf<List<AppShortcut>>(emptyList()) }
    LaunchedEffect(expanded) {
        // cleared on close too, so a reopened menu never flashes the previous app's shortcuts
        shortcuts = if (expanded && loadShortcuts != null) loadShortcuts() else emptyList()
    }
    val groups = groupMenu(shortcuts, actions)
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
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
