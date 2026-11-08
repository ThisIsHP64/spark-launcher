package dev.gearturner.sparklauncher.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.gearturner.sparklauncher.core.icons.IconPackInfo
import dev.gearturner.sparklauncher.core.model.CustomIcon
import java.util.Locale

/** one icon pack drawable, rendered at [size] in the current style */
@Composable
fun rememberPackIcon(pack: String, drawableName: String, size: Dp): ImageBitmap? {
    val repository = LocalIconRepository.current
    val style = LocalIconStyle.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    return produceState<ImageBitmap?>(null, pack, drawableName, sizePx, style) {
        value = repository.loadPackDrawable(pack, drawableName, sizePx, style)?.asImageBitmap()
    }.value
}

/**
 * picks one icon for one app: first an installed pack, then one of its icons. icons whose names
 * contain words from the app's name are listed first, since packs name icons after their apps.
 */
@Composable
fun IconPickerDialog(
    appLabel: String,
    hasCustomIcon: Boolean,
    loadPacks: suspend () -> List<IconPackInfo>,
    loadNames: suspend (String) -> List<String>,
    onPick: (CustomIcon) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pack by remember { mutableStateOf<IconPackInfo?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 640.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (pack != null) {
                        IconButton(onClick = { pack = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ui_icon_picker_back))
                        }
                    }
                    Text(
                        pack?.label ?: stringResource(R.string.ui_icon_picker_title, appLabel),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                val chosen = pack
                if (chosen == null) {
                    PackList(loadPacks, onChoose = { pack = it })
                } else {
                    PackIcons(chosen, appLabel, loadNames) { name -> onPick(CustomIcon(chosen.packageName, name)) }
                }
                // reset stays in the same spot on every step, so it is always easy to find
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                    if (hasCustomIcon) TextButton(onClick = onReset) { Text(stringResource(R.string.ui_icon_picker_reset)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
                }
            }
        }
    }
}

@Composable
private fun PackList(
    loadPacks: suspend () -> List<IconPackInfo>,
    onChoose: (IconPackInfo) -> Unit,
) {
    val packs = produceState<List<IconPackInfo>?>(null) { value = loadPacks() }.value
    LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
        when {
            packs == null -> item { Loading() }
            packs.isEmpty() -> item {
                Text(
                    stringResource(R.string.ui_icon_picker_no_packs),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            else -> items(packs, key = { it.packageName }) { pack -> ListRow(pack.label) { onChoose(pack) } }
        }
    }
}

@Composable
private fun PackIcons(pack: IconPackInfo, appLabel: String, loadNames: suspend (String) -> List<String>, onPick: (String) -> Unit) {
    val names = produceState<List<String>?>(null, pack.packageName) { value = loadNames(pack.packageName) }.value
    val search = rememberTextFieldState()
    var query by remember { mutableStateOf("") }
    LaunchedEffect(search) { snapshotFlow { search.text.toString() }.collect { query = it } }
    OutlinedTextField(
        state = search,
        lineLimits = TextFieldLineLimits.SingleLine,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        placeholder = { Text(stringResource(R.string.ui_icon_picker_search)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
    if (names == null) {
        Loading()
        return
    }
    val shown = remember(names, query, appLabel) { rankIconNames(names, appLabel, query) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(72.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .padding(horizontal = 8.dp),
    ) {
        items(shown, key = { it }) { name ->
            val icon = rememberPackIcon(pack.packageName, name, 48.dp)
            Column(
                modifier = Modifier
                    .clickable { onPick(name) }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (icon != null) Image(icon, contentDescription = name, modifier = Modifier.size(48.dp))
                }
                Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** names matching the query, with names sharing a word with the app's label first */
internal fun rankIconNames(names: List<String>, appLabel: String, query: String): List<String> {
    val needle = query.trim().lowercase(Locale.ROOT).replace(' ', '_')
    val filtered = if (needle.isEmpty()) names else names.filter { it.lowercase(Locale.ROOT).contains(needle) }
    val words = appLabel.lowercase(Locale.ROOT).split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
    if (words.isEmpty()) return filtered
    val (matching, rest) = filtered.partition { name -> words.any { name.lowercase(Locale.ROOT).contains(it) } }
    return matching + rest
}

@Composable
private fun ListRow(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
