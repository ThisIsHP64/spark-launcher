package dev.gearturner.sparklauncher.core.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization

/**
 * edits a display name. saving a blank name or tapping reset hands back null, which tells the
 * caller to drop the rename and show the original name again.
 */
@Composable
fun RenameDialog(
    currentName: String,
    canReset: Boolean,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTextFieldState(currentName)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // selecting everything lets a quick retype replace the name instead of appending to it
        state.edit { selectAll() }
        focus.requestFocus()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_rename_title)) },
        text = {
            OutlinedTextField(
                state = state,
                label = { Text(stringResource(R.string.ui_rename_hint)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                onKeyboardAction = { onConfirm(state.text.toString().ifBlank { null }) },
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.text.toString().ifBlank { null }) }) {
                Text(stringResource(R.string.ui_save))
            }
        },
        dismissButton = {
            Row {
                if (canReset) {
                    TextButton(onClick = { onConfirm(null) }) { Text(stringResource(R.string.ui_rename_reset)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
            }
        },
    )
}
