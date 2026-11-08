package dev.gearturner.sparklauncher.feature.widgets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gearturner.sparklauncher.core.model.Span
import dev.gearturner.sparklauncher.core.model.defaultSpan
import dev.gearturner.sparklauncher.core.ui.DragController
import dev.gearturner.sparklauncher.core.ui.DragOrigin
import dev.gearturner.sparklauncher.core.ui.DragPayload
import dev.gearturner.sparklauncher.core.ui.DragSession
import dev.gearturner.sparklauncher.core.ui.IconDragHandler
import dev.gearturner.sparklauncher.core.ui.LocalDragController
import dev.gearturner.sparklauncher.core.ui.WidgetPickerController
import dev.gearturner.sparklauncher.core.ui.iconGestures
import dev.gearturner.sparklauncher.core.ui.rememberAppIcon

private val PreviewBox = 160.dp

/**
 * lists every widget by app. tapping one adds it to the first free spot; long pressing and
 * dragging carries it onto home. it lives in the launcher window, not a dialog, so a drag that
 * starts here can end on the home screen.
 */
@Composable
fun WidgetPicker(
    viewModel: WidgetPickerViewModel,
    controller: WidgetPickerController,
    onAdd: (WidgetProvider, Span) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dragController = LocalDragController.current
    val draggingFromPicker = dragController?.session?.origin == DragOrigin.WidgetPicker
    // a widget being dragged out still belongs to its card, so the picker stays composed but hidden
    if (!controller.isOpen && !draggingFromPicker) return
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val search = rememberTextFieldState()
    LaunchedEffect(controller.packageFilter) { viewModel.setPackageFilter(controller.packageFilter) }
    LaunchedEffect(search) { snapshotFlow { search.text.toString() }.collect(viewModel::setQuery) }
    BackHandler(enabled = controller.isOpen) { controller.close() }

    // a surface sets the theme's text color and swallows touches, so nothing behind it reacts
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (controller.isOpen) 1f else 0f },
    ) {
    Column(modifier = Modifier.statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.widget_picker_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = controller::close) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.widget_picker_close))
            }
        }
        TextField(
            state = search,
            lineLimits = TextFieldLineLimits.SingleLine,
            placeholder = { Text(stringResource(R.string.widget_picker_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        if (groups.isEmpty()) {
            Text(
                stringResource(R.string.widget_picker_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(groups, key = { "${it.appLabel}:${it.widgets.first().key}" }) { group ->
                WidgetGroupRow(group, controller, dragController, onAdd)
            }
        }
    }
    }
}

@Composable
private fun WidgetGroupRow(
    group: WidgetGroup,
    controller: WidgetPickerController,
    dragController: DragController?,
    onAdd: (WidgetProvider, Span) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            group.appKey?.let { key ->
                rememberAppIcon(key, 32.dp)?.let { Image(it, contentDescription = null, modifier = Modifier.size(32.dp)) }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(group.appLabel, style = MaterialTheme.typography.titleMedium)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(group.widgets, key = { it.key.toString() }) { provider ->
                WidgetCard(provider, controller, dragController, onAdd)
            }
        }
    }
}

@Composable
private fun WidgetCard(
    provider: WidgetProvider,
    controller: WidgetPickerController,
    dragController: DragController?,
    onAdd: (WidgetProvider, Span) -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val cellWidth = controller.cellWidthDp.takeIf { it > 0f } ?: DEFAULT_CELL_DP
    val cellHeight = controller.cellHeightDp.takeIf { it > 0f } ?: DEFAULT_CELL_DP
    val span = defaultSpan(provider.sizeSpec, cellWidth, cellHeight, controller.columns.coerceAtLeast(1), controller.rows.coerceAtLeast(1))
    val fullWidth = (cellWidth * span.x).dp
    val fullHeight = (cellHeight * span.y).dp
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var previewBounds by remember { mutableStateOf<Rect?>(null) }
    val preview = rememberPreviewImage(provider)
    val addLabel = stringResource(R.string.widget_picker_add, provider.label)
    val dragHandler = remember(dragController, provider.key, span, preview) {
        dragController?.let {
            pickerDragHandler(it, controller, DragPayload.NewWidget(provider.key, span.x, span.y), preview) {
                with(density) { Size(fullWidth.toPx(), fullHeight.toPx()) }
            }
        }
    }
    Column(
        modifier = Modifier
            .width(PreviewBox + 16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp))
            .onGloballyPositioned { coordinates = it }
            .iconGestures(
                coordinates = { coordinates },
                iconBounds = { previewBounds },
                haptics = haptics,
                onClick = { onAdd(provider, span) },
                onLongClick = {},
                dragHandler = dragHandler,
            )
            .semantics {
                onClick(label = addLabel) {
                    onAdd(provider, span)
                    true
                }
            }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(PreviewBox)
                .onGloballyPositioned { previewBounds = it.boundsInRoot() },
            contentAlignment = Alignment.Center,
        ) {
            WidgetPreview(provider, fullWidth, fullHeight, PreviewBox)
        }
        Spacer(modifier = Modifier.size(8.dp))
        Text(provider.label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(R.string.widget_picker_span, span.x, span.y),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        provider.description?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** dragging a widget hides the picker so home is there to drop on, and lifts it at its real size */
private fun pickerDragHandler(
    dragController: DragController,
    picker: WidgetPickerController,
    payload: DragPayload.NewWidget,
    preview: ImageBitmap?,
    fullSize: () -> Size,
): IconDragHandler = object : IconDragHandler {
    override fun onDragStart(fingerInRoot: Offset, iconBoundsInRoot: Rect) {
        picker.close()
        val size = fullSize()
        dragController.start(
            DragSession(
                payload = payload,
                origin = DragOrigin.WidgetPicker,
                icon = preview,
                sizePx = size,
                // centered under the finger, since the card is far smaller than the placed widget
                grabOffset = Offset(size.width / 2f, size.height / 2f),
                fingerInRoot = fingerInRoot,
            ),
        )
    }

    override fun onDragMove(fingerInRoot: Offset) = dragController.move(fingerInRoot)

    override fun onDragEnd() = dragController.drop {}

    override fun onDragCancel() = dragController.cancel()
}

private const val DEFAULT_CELL_DP = 90f
