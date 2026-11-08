package dev.gearturner.sparklauncher.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.AppShortcut
import dev.gearturner.sparklauncher.core.model.IconSource

@Immutable
data class MenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * the long press menu shared by home and drawer: the app's shortcuts first, then actions.
 * shortcuts load when the menu opens because apps change them often and the query is cheap.
 */
@Composable
fun ItemMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    owner: AppKey?,
    loadShortcuts: suspend () -> List<AppShortcut>,
    onShortcutClick: (AppShortcut) -> Unit,
    onPinShortcut: ((AppShortcut) -> Unit)?,
    actions: List<MenuAction>,
) {
    var shortcuts by remember { mutableStateOf<List<AppShortcut>>(emptyList()) }
    LaunchedEffect(expanded) {
        shortcuts = if (expanded) loadShortcuts() else emptyList()
    }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        shortcuts.forEach { shortcut ->
            val icon = rememberIcon(IconSource.Shortcut(shortcut.key, owner), MenuIconSize)
            val label = if (shortcut.isEnabled) {
                shortcut.menuLabel
            } else {
                stringResource(R.string.ui_shortcut_disabled, shortcut.shortLabel)
            }
            DropdownMenuItem(
                text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                enabled = shortcut.isEnabled,
                leadingIcon = {
                    if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(MenuIconSize))
                },
                trailingIcon = onPinShortcut?.takeIf { shortcut.isEnabled }?.let { pin ->
                    {
                        IconButton(onClick = {
                            onDismiss()
                            pin(shortcut)
                        }) {
                            Icon(
                                Icons.Outlined.PushPin,
                                contentDescription = stringResource(R.string.ui_pin_shortcut, shortcut.shortLabel),
                            )
                        }
                    }
                },
                onClick = {
                    onDismiss()
                    onShortcutClick(shortcut)
                },
            )
        }
        if (shortcuts.isNotEmpty() && actions.isNotEmpty()) HorizontalDivider()
        actions.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
                leadingIcon = { Icon(action.icon, contentDescription = null) },
                onClick = {
                    onDismiss()
                    action.onClick()
                },
            )
        }
    }
}

private val MenuIconSize = 24.dp
