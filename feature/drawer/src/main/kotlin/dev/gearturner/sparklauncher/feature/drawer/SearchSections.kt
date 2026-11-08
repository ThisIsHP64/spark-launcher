package dev.gearturner.sparklauncher.feature.drawer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gearturner.sparklauncher.core.apps.LaunchRequest
import dev.gearturner.sparklauncher.core.model.IconSource
import dev.gearturner.sparklauncher.core.ui.rememberIcon
import dev.gearturner.sparklauncher.feature.search.SearchResults

private val RowIconSize = 40.dp
private val RowCorner = 24.dp

/** the calculator answer sits above apps because a typed sum is never looking for an app */
internal fun LazyGridScope.calculatorRow(expression: String, result: String) {
    item(key = "calc", span = { GridItemSpan(maxLineSpan) }) {
        val context = LocalContext.current
        val label = stringResource(R.string.search_copy_result)
        ResultRow(
            title = result,
            subtitle = stringResource(R.string.search_calculator_subtitle, expression),
            highlighted = false,
            leading = { VectorIcon(Icons.Outlined.Calculate) },
            onClick = { copyToClipboard(context, label, result) },
        )
    }
}

/**
 * shortcuts, contacts, and settings as full width rows below the apps. [topHit] is set when no app
 * matched, so the first row here is the one the go key opens and gets the highlight.
 */
internal fun LazyGridScope.searchSections(results: SearchResults, topHit: TopHit?, viewModel: DrawerViewModel) {
    if (results.shortcuts.isNotEmpty()) {
        header("h_shortcuts", R.string.search_section_shortcuts)
        itemsIndexed(results.shortcuts, key = { _, hit -> "s_${hit.shortcut.key}" }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { _, hit ->
            ResultRow(
                title = hit.shortcut.menuLabel,
                subtitle = hit.appLabel,
                highlighted = topHit == TopHit.Shortcut(hit),
                leading = {
                    val icon = rememberIcon(IconSource.Shortcut(hit.shortcut.key, hit.owner), RowIconSize)
                    if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(RowIconSize))
                    else Box(Modifier.size(RowIconSize))
                },
                onClick = { viewModel.openShortcut(hit, LaunchRequest()) },
            )
        }
    }
    if (results.contacts.isNotEmpty()) {
        header("h_contacts", R.string.search_section_contacts)
        itemsIndexed(results.contacts, key = { _, hit -> "c_${hit.lookupUri}" }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { _, hit ->
            ResultRow(
                title = hit.name,
                subtitle = null,
                highlighted = topHit == TopHit.Contact(hit),
                leading = { Monogram(hit.name) },
                onClick = { viewModel.openContact(hit) },
            )
        }
    }
    if (results.settings.isNotEmpty()) {
        header("h_settings", R.string.search_section_settings)
        itemsIndexed(results.settings, key = { _, hit -> "t_${hit.action}" }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { _, hit ->
            ResultRow(
                title = hit.title,
                subtitle = null,
                highlighted = topHit == TopHit.Setting(hit),
                leading = { VectorIcon(Icons.Outlined.Settings) },
                onClick = { viewModel.openSetting(hit) },
            )
        }
    }
}

private fun LazyGridScope.header(key: String, title: Int) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun ResultRow(
    title: String,
    subtitle: String?,
    highlighted: Boolean,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    // the same tinted plate as the top app, so enter's target looks the same in every section
    val plate = if (highlighted) {
        Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(RowCorner))
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .then(plate)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        leading()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun VectorIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(RowIconSize)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** contact photos would need another query per row, so a letter stands in, like the dialer */
@Composable
private fun Monogram(name: String) {
    Box(
        modifier = Modifier
            .size(RowIconSize)
            .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    // android 13 and later confirm the copy themselves, so no toast is added on top
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
