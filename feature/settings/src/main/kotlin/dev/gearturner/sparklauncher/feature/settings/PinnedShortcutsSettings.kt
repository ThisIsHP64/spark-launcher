package dev.gearturner.sparklauncher.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.gearturner.sparklauncher.core.model.ShortcutKey
import dev.gearturner.sparklauncher.core.model.SwipeDirection
import dev.gearturner.sparklauncher.core.ui.DismissOnStop
import dev.gearturner.sparklauncher.core.ui.ShortcutChoiceIcon

/** every shortcut spark holds pinned, each with where it is used and a way to delete it */
internal fun LazyListScope.pinnedShortcutsPage(
    shortcuts: List<PinnedShortcut>?,
    hasAccess: Boolean,
    onDelete: (ShortcutKey) -> Unit,
) {
    // nothing is drawn until the list has loaded, so the empty message never flashes before it
    if (shortcuts == null) return
    if (shortcuts.isEmpty()) {
        item {
            SettingsCard {
                Text(
                    stringResource(if (hasAccess) R.string.settings_pinned_empty else R.string.settings_pinned_no_access),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
        return
    }
    item {
        SettingsCard {
            shortcuts.forEach { entry -> PinnedShortcutRow(entry, onDelete) }
        }
    }
    item {
        Text(
            stringResource(R.string.settings_pinned_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
        )
    }
}

@Composable
private fun PinnedShortcutRow(entry: PinnedShortcut, onDelete: (ShortcutKey) -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    DismissOnStop { confirming = false }
    val name = entry.shortcut.menuLabel
    val usage = usageLines(entry)
    val removeLabel = stringResource(R.string.settings_pinned_remove_named, name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShortcutChoiceIcon(entry.shortcut.key, owner = null)
        Spacer(modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_pinned_from, entry.appLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            usage.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // the icon alone says nothing, so talkback hears which shortcut it removes
        IconButton(onClick = { confirming = true }, modifier = Modifier.semantics { contentDescription = removeLabel }) {
            Icon(Icons.Outlined.Delete, contentDescription = null)
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.settings_pinned_confirm_title, name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (entry.inUse) Text(stringResource(R.string.settings_pinned_confirm_used))
                    usage.forEach { line -> Text(line) }
                    Text(stringResource(R.string.settings_pinned_confirm_release))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onDelete(entry.shortcut.key)
                    },
                ) { Text(stringResource(R.string.settings_pinned_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.settings_pinned_cancel)) } },
        )
    }
}

/** where a shortcut is used, one line each, or a single line saying it is not used anywhere */
@Composable
private fun usageLines(entry: PinnedShortcut): List<String> = buildList {
    if (entry.onHome) add(stringResource(R.string.settings_pinned_on_home))
    entry.swipeUses.forEach { use ->
        add(
            stringResource(
                if (use.direction == SwipeDirection.UP) R.string.settings_pinned_swipe_up else R.string.settings_pinned_swipe_down,
                use.appLabel,
            ),
        )
    }
    if (isEmpty()) add(stringResource(R.string.settings_pinned_unused))
}
