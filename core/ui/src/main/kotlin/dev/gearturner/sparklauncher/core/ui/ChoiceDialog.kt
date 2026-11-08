package dev.gearturner.sparklauncher.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale

/** one option in a [ChoiceDialog]; [leading] is an optional icon, such as an app icon */
@Immutable
class Choice<T>(
    val value: T,
    val label: String,
    val supporting: String? = null,
    val leading: (@Composable () -> Unit)? = null,
)

/**
 * a single choice picker in the material 3 expressive style: a segmented list whose first and last
 * rows take the rounder corners, with the current choice filled and checked. long lists get a
 * filter field so a choice among a hundred apps is a few letters away.
 */
@Composable
fun <T> ChoiceDialog(
    title: String,
    choices: List<Choice<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    searchable: Boolean = choices.size > SEARCH_THRESHOLD,
    footer: (@Composable () -> Unit)? = null,
) {
    val search = rememberTextFieldState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        shape = MaterialTheme.shapes.extraLarge,
        text = {
            Column {
                if (searchable) {
                    OutlinedTextField(
                        state = search,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text(stringResource(R.string.ui_choice_search)) },
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    )
                }
                val shown = filterChoices(choices, search.text.toString())
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    modifier = Modifier.heightIn(max = MAX_LIST_HEIGHT),
                ) {
                    itemsIndexed(shown) { index, choice ->
                        val isSelected = choice.value == selected
                        SegmentedListItem(
                            selected = isSelected,
                            onClick = { onSelect(choice.value) },
                            shapes = ListItemDefaults.segmentedShapes(index, shown.size),
                            leadingContent = choice.leading,
                            trailingContent = if (isSelected) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else {
                                null
                            },
                            supportingContent = choice.supporting?.let { text -> { Text(text) } },
                        ) {
                            Text(choice.label)
                        }
                    }
                }
                footer?.invoke()
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_close)) } },
    )
}

/** keeps the original order, which callers have already sorted the way they want it shown */
internal fun <T> filterChoices(choices: List<Choice<T>>, query: String): List<Choice<T>> {
    val needle = query.trim().lowercase(Locale.ROOT)
    if (needle.isEmpty()) return choices
    return choices.filter { needle in it.label.lowercase(Locale.ROOT) }
}

private const val SEARCH_THRESHOLD = 12
private val MAX_LIST_HEIGHT = 480.dp
