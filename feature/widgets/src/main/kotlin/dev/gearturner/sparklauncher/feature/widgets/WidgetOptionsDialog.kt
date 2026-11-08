package dev.gearturner.sparklauncher.feature.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
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
            Column {
                ToggleRow(stringResource(R.string.widget_option_padding), options.padding) { change(options.copy(padding = it)) }
                SliderRow(
                    label = stringResource(R.string.widget_option_corners, options.cornerRadiusDp),
                    value = options.cornerRadiusDp.toFloat(),
                    range = 0f..WidgetOptions.MAX_CORNER_RADIUS_DP.toFloat(),
                ) { change(options.copy(cornerRadiusDp = it.roundToInt())) }
                SliderRow(
                    label = stringResource(R.string.widget_option_opacity, options.opacityPercent),
                    value = options.opacityPercent.toFloat(),
                    range = WidgetOptions.MIN_OPACITY_PERCENT.toFloat()..100f,
                ) { change(options.copy(opacityPercent = it.roundToInt())) }
                ToggleRow(stringResource(R.string.widget_option_overlap), options.allowOverlap) { change(options.copy(allowOverlap = it)) }
                ToggleRow(stringResource(R.string.widget_option_free_resize), options.ignoreSizeLimits) {
                    change(options.copy(ignoreSizeLimits = it))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(options) }) { Text(stringResource(R.string.widget_save)) } },
        dismissButton = {
            TextButton(onClick = {
                onPreview(initial)
                onDismiss()
            }) { Text(stringResource(R.string.widget_cancel)) }
        },
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
