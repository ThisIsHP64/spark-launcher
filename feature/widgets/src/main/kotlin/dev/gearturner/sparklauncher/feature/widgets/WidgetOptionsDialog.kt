package dev.gearturner.sparklauncher.feature.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.gearturner.sparklauncher.core.model.WidgetOptions
import kotlin.math.roundToInt

/** the per widget controls; changes preview live through [onPreview] and are kept only on save */
@Composable
fun WidgetOptionsDialog(
    initial: WidgetOptions,
    onPreview: (WidgetOptions) -> Unit,
    onSave: (WidgetOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    var options by remember { mutableStateOf(initial) }
    fun change(next: WidgetOptions) {
        options = next.sanitized()
        onPreview(options)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.widget_options_title)) },
        text = {
            // one segmented group, so the five controls read as a single settings card
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                ToggleRow(stringResource(R.string.widget_option_padding), options.padding, 0) { change(options.copy(padding = it)) }
                SliderRow(
                    label = stringResource(R.string.widget_option_corners, options.cornerRadiusDp),
                    value = options.cornerRadiusDp.toFloat(),
                    range = 0f..WidgetOptions.MAX_CORNER_RADIUS_DP.toFloat(),
                    index = 1,
                ) { change(options.copy(cornerRadiusDp = it.roundToInt())) }
                SliderRow(
                    label = stringResource(R.string.widget_option_opacity, options.opacityPercent),
                    value = options.opacityPercent.toFloat(),
                    range = WidgetOptions.MIN_OPACITY_PERCENT.toFloat()..100f,
                    index = 2,
                ) { change(options.copy(opacityPercent = it.roundToInt())) }
                ToggleRow(stringResource(R.string.widget_option_overlap), options.allowOverlap, 3) { change(options.copy(allowOverlap = it)) }
                ToggleRow(stringResource(R.string.widget_option_free_resize), options.ignoreSizeLimits, 4) {
                    change(options.copy(ignoreSizeLimits = it))
                }
            }
        },
        confirmButton = { FilledTonalButton(onClick = { onSave(options) }) { Text(stringResource(R.string.widget_save)) } },
        dismissButton = {
            TextButton(onClick = {
                onPreview(initial)
                onDismiss()
            }) { Text(stringResource(R.string.widget_cancel)) }
        },
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, index: Int, onChange: (Boolean) -> Unit) {
    SegmentedListItem(
        onClick = { onChange(!checked) },
        shapes = ListItemDefaults.segmentedShapes(index, ROW_COUNT),
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    ) {
        Text(label)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, index: Int, onChange: (Float) -> Unit) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index, ROW_COUNT),
        supportingContent = { Slider(value = value, onValueChange = onChange, valueRange = range) },
    ) {
        Text(label)
    }
}

private const val ROW_COUNT = 5
