package dev.gearturner.sparklauncher.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gearturner.sparklauncher.core.icons.IconRepository
import dev.gearturner.sparklauncher.core.icons.IconRequest
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.IconSource

/** provided once by the activity so every icon in the tree shares one cache */
val LocalIconRepository = staticCompositionLocalOf<IconRepository> {
    error("LocalIconRepository was not provided")
}

/** the pixel launcher's base icon size; the user's scale setting multiplies it */
val BaseIconSize: Dp = 56.dp

enum class LabelStyle {
    /** white text with a soft shadow, readable on any wallpaper */
    OnWallpaper,
    /** theme colored text for opaque surfaces like the drawer */
    OnSurface,
    Hidden,
}

@Composable
fun rememberAppIcon(key: AppKey, size: Dp, cornerRadiusPercent: Int): ImageBitmap? =
    rememberIcon(IconSource.App(key), size, cornerRadiusPercent)

@Composable
fun rememberIcon(source: IconSource, size: Dp, cornerRadiusPercent: Int): ImageBitmap? {
    val repository = LocalIconRepository.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val generation by repository.generation.collectAsState()
    val request = remember(source, sizePx, cornerRadiusPercent) { IconRequest(source, sizePx, cornerRadiusPercent) }
    // seeding from the cache avoids a blank frame when scrolling back to icons already rendered
    val initial = remember(request, generation) { repository.cached(request)?.asImageBitmap() }
    return produceState(initial, request, generation) {
        value = repository.load(request)?.asImageBitmap()
    }.value
}

@Composable
fun AppIcon(
    source: IconSource,
    label: String,
    iconSize: Dp,
    cornerRadiusPercent: Int,
    labelStyle: LabelStyle,
    anchor: LaunchAnchor,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandler: IconDragHandler? = null,
) {
    val bitmap = rememberIcon(source, iconSize, cornerRadiusPercent)
    SideEffect { anchor.icon = bitmap }
    LauncherTile(
        label = label,
        iconSize = iconSize,
        labelStyle = labelStyle,
        anchor = anchor,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        dragHandler = dragHandler,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(iconSize))
        } else {
            // a neutral plate keeps the grid stable while the icon loads or if it failed to render
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        RoundedCornerShape(percent = cornerRadiusPercent),
                    ),
            )
        }
    }
}

/**
 * the shared shape of everything on home: an icon area with a label, tap, long press and drag.
 * apps draw a bitmap in it and folders draw a preview of their contents.
 */
@Composable
fun LauncherTile(
    label: String,
    iconSize: Dp,
    labelStyle: LabelStyle,
    anchor: LaunchAnchor,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandler: IconDragHandler? = null,
    icon: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Column(
        modifier = modifier
            .onGloballyPositioned { coordinates = it }
            .iconGestures(
                coordinates = { coordinates },
                iconBounds = { anchor.boundsInRoot() },
                haptics = haptics,
                onClick = onClick,
                onLongClick = onLongClick,
                dragHandler = dragHandler,
            )
            // the custom detector replaces clickable, so tap and long press are restated for talkback
            .semantics {
                role = Role.Button
                onClick(label = label) {
                    onClick()
                    true
                }
                onLongClick {
                    onLongClick()
                    true
                }
            }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // the anchor sits on the icon itself so the reported source bounds cover the icon, not the label
        Box(
            modifier = Modifier
                .size(iconSize)
                .launchAnchor(anchor)
                .graphicsLayer { alpha = if (anchor.hidden) 0f else 1f },
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        if (labelStyle != LabelStyle.Hidden) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = labelTextStyle(labelStyle),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun labelTextStyle(style: LabelStyle): TextStyle {
    val base = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
    return when (style) {
        LabelStyle.OnWallpaper -> base.copy(
            color = Color.White,
            shadow = Shadow(color = Color.Black.copy(alpha = 0.55f), offset = Offset(0f, 1f), blurRadius = 4f),
        )
        LabelStyle.OnSurface, LabelStyle.Hidden -> base.copy(color = MaterialTheme.colorScheme.onSurface)
    }
}
