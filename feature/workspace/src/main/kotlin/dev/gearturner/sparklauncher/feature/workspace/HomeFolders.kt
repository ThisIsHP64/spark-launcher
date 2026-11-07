package dev.gearturner.sparklauncher.feature.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.gearturner.sparklauncher.core.ui.DragOrigin
import dev.gearturner.sparklauncher.core.ui.LabelStyle
import dev.gearturner.sparklauncher.core.ui.RenameDialog
import dev.gearturner.sparklauncher.core.ui.rememberIcon

private const val FOLDER_COLUMNS = 4

/** a 2x2 preview of the first apps in a folder on a soft plate, like the pixel launcher */
@Composable
internal fun FolderPreview(folder: FolderTile, iconSize: Dp, cornerRadiusPercent: Int) {
    val miniSize = iconSize * 0.38f
    Box(
        modifier = Modifier
            .size(iconSize)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                RoundedCornerShape(percent = cornerRadiusPercent),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(iconSize * 0.06f)) {
            folder.children.take(4).chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(iconSize * 0.06f)) {
                    pair.forEach { child ->
                        val icon = rememberIcon(child.iconSource, miniSize, cornerRadiusPercent)
                        if (icon != null) {
                            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(miniSize))
                        } else {
                            Box(modifier = Modifier.size(miniSize))
                        }
                    }
                }
            }
        }
    }
}

/**
 * an open folder over a dimmed home screen. while one of its apps is being dragged out the panel
 * stays composed but invisible, because removing it would end the drag the app started.
 */
@Composable
internal fun FolderOverlay(
    folder: FolderTile,
    draggingOut: Boolean,
    iconSize: Dp,
    cornerRadiusPercent: Int,
    viewModel: HomeViewModel,
    onClose: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    val name = folder.name ?: stringResource(R.string.workspace_folder_default_name)
    BackHandler(enabled = !draggingOut, onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (draggingOut) 0f else 1f }
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(28.dp))
                // taps inside the panel must not fall through to the scrim and close it
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { renaming = true }
                    .padding(vertical = 8.dp),
            )
            folder.children.chunked(FOLDER_COLUMNS).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { child ->
                        HomeItem(
                            cell = child,
                            registryPage = null,
                            iconSize = iconSize,
                            cornerRadiusPercent = cornerRadiusPercent,
                            labelStyle = LabelStyle.OnSurface,
                            origin = DragOrigin.Folder,
                            viewModel = viewModel,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp),
                        )
                    }
                    // keeps the last row's icons the same width as full rows
                    repeat(FOLDER_COLUMNS - row.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
    if (renaming) {
        RenameDialog(
            currentName = folder.name.orEmpty(),
            canReset = folder.name != null,
            onConfirm = { title ->
                renaming = false
                viewModel.rename(folder.itemId, title)
            },
            onDismiss = { renaming = false },
        )
    }
}
