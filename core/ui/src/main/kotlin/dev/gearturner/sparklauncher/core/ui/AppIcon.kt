package dev.gearturner.sparklauncher.core.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import dev.gearturner.sparklauncher.core.apps.DotKey
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

/** how many lines an icon label may use, from settings */
val LocalLabelLines = staticCompositionLocalOf { 1 }

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
fun rememberAppIcon(key: AppKey, size: Dp): ImageBitmap? =
    rememberIcon(IconSource.App(key), size)

@Composable
fun rememberIcon(source: IconSource, size: Dp): ImageBitmap? {
    val repository = LocalIconRepository.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val generation by repository.generation.collectAsState()
    val style = LocalIconStyle.current
    val request = remember(source, sizePx, style) { IconRequest(source, sizePx, style) }
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
    labelStyle: LabelStyle,
    anchor: LaunchAnchor,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandler: IconDragHandler? = null,
    showDot: Boolean = false,
) {
    val bitmap = rememberIcon(source, iconSize)
    SideEffect { anchor.icon = bitmap }
    val accent = remember(bitmap) { bitmap?.accentColor() }
    LauncherTile(
        label = label,
        iconSize = iconSize,
        labelStyle = labelStyle,
        anchor = anchor,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        dragHandler = dragHandler,
        dotColor = if (showDot) accent ?: MaterialTheme.colorScheme.primary else null,
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
                        rememberIconShape(),
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
    dotColor: Color? = null,
    icon: @Composable () -> Unit,
) {
    val hasNotifications = stringResource(R.string.ui_has_notifications)
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
                if (dotColor != null) stateDescription = hasNotifications
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
            NotificationDot(dotColor, iconSize, Modifier.align(Alignment.TopEnd))
        }
        if (labelStyle != LabelStyle.Hidden) {
            Spacer(modifier = Modifier.height(6.dp))
            // the label travels with the dragged copy, so it leaves the source spot along with the icon
            Box(modifier = Modifier.graphicsLayer { alpha = if (anchor.hidden) 0f else 1f }) {
                IconLabel(label, labelStyle)
            }
        }
    }
}

/**
 * a label on the wallpaper is drawn twice, the way the pixel launcher does it: a wide, soft ambient
 * shadow under a tight key shadow. one shadow alone either smears or vanishes on bright wallpapers.
 */
@Composable
internal fun IconLabel(label: String, style: LabelStyle) {
    val base = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
    val lines = LocalLabelLines.current
    if (style != LabelStyle.OnWallpaper) {
        Text(
            text = label,
            style = base.copy(color = MaterialTheme.colorScheme.onSurface),
            maxLines = lines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        return
    }
    val density = LocalDensity.current
    val ambient = with(density) {
        Shadow(color = Color.Black.copy(alpha = AMBIENT_SHADOW_ALPHA), offset = Offset.Zero, blurRadius = 4.dp.toPx())
    }
    val key = with(density) {
        Shadow(color = Color.Black.copy(alpha = KEY_SHADOW_ALPHA), offset = Offset(0f, 0.75.dp.toPx()), blurRadius = 1.5.dp.toPx())
    }
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = label,
            // both layers share the weight so their glyphs sit exactly on top of each other
            style = base.copy(color = Color.White, shadow = ambient, fontWeight = FontWeight.Medium),
            maxLines = lines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            style = base.copy(color = Color.White, shadow = key, fontWeight = FontWeight.Medium),
            maxLines = lines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * the pixel style dot at an icon's top right corner. it keeps its last color while fading out, so
 * it does not flash to another color as it disappears.
 */
@Composable
private fun NotificationDot(color: Color?, iconSize: Dp, modifier: Modifier) {
    var lastColor by remember { mutableStateOf(color ?: Color.Transparent) }
    if (color != null) lastColor = color
    AnimatedVisibility(
        visible = color != null,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(iconSize * DOT_FRACTION)
                .graphicsLayer {
                    shadowElevation = 2.dp.toPx()
                    shape = CircleShape
                    clip = true
                }
                .background(lastColor, CircleShape),
        )
    }
}

/** apps with a notification dot, by package and profile; empty while dots are off or access is missing */
val LocalNotificationDots = compositionLocalOf<Set<DotKey>> { emptySet() }

private const val DOT_FRACTION = 0.22f
private const val AMBIENT_SHADOW_ALPHA = 0.6f
private const val KEY_SHADOW_ALPHA = 0.85f
