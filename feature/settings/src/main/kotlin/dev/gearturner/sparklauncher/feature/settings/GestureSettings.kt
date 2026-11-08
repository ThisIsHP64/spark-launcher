package dev.gearturner.sparklauncher.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gearturner.sparklauncher.core.apps.SparkAccessibility
import dev.gearturner.sparklauncher.core.model.Gesture
import dev.gearturner.sparklauncher.core.model.GestureAction
import dev.gearturner.sparklauncher.core.model.GestureBinding
import dev.gearturner.sparklauncher.core.model.HomeMode
import dev.gearturner.sparklauncher.core.model.LauncherSettings
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.ui.Choice
import dev.gearturner.sparklauncher.core.ui.ChoiceDialog
import dev.gearturner.sparklauncher.core.ui.rememberAppIcon
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box

/** actions in the order the chooser lists them, roughly from most to least common */
private val ActionOrder = listOf(
    GestureAction.NONE,
    GestureAction.OPEN_DRAWER,
    GestureAction.OPEN_SEARCH,
    GestureAction.NOTIFICATIONS,
    GestureAction.QUICK_SETTINGS,
    GestureAction.LOCK_SCREEN,
    GestureAction.RECENTS,
    GestureAction.OPEN_APP,
    GestureAction.SPARK_SETTINGS,
)

internal fun LazyListScope.gesturesPage(settings: LauncherSettings, viewModel: SettingsViewModel) {
    item { ServiceCard(settings, viewModel) }
    Gesture.entries.forEach { gesture ->
        item(key = gesture.name) { GestureRow(gesture, settings, viewModel) }
    }
}

/** shown only while a chosen gesture needs the service and it is off, with a way to turn it on */
@Composable
private fun ServiceCard(settings: LauncherSettings, viewModel: SettingsViewModel) {
    val enabled by viewModel.serviceEnabled.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshSystemState()
        onPauseOrDispose { }
    }
    if (enabled || !settings.gestures.needsAccessibilityService) return
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(stringResource(R.string.settings_service_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(4.dp))
            Text(stringResource(R.string.settings_service_body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.size(12.dp))
            FilledTonalButton(onClick = {
                try {
                    context.startActivity(SparkAccessibility.settingsIntent())
                } catch (e: ActivityNotFoundException) {
                    Log.w("GestureSettings", "no accessibility settings screen", e)
                }
            }) { Text(stringResource(R.string.settings_service_open)) }
        }
    }
}

@Composable
private fun GestureRow(gesture: Gesture, settings: LauncherSettings, viewModel: SettingsViewModel) {
    var choosing by remember { mutableStateOf(false) }
    var pickingApp by remember { mutableStateOf(false) }
    val binding = settings.gestures.bindingFor(gesture)
    val apps by viewModel.allApps.collectAsStateWithLifecycle()
    val appLabel = binding.app?.let { key -> apps.firstOrNull { it.key == key }?.label }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { choosing = true }
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(gestureTitle(gesture), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = if (binding.action == GestureAction.OPEN_APP && appLabel != null) {
                stringResource(R.string.gesture_action_open_named_app, appLabel)
            } else {
                actionTitle(binding.action, settings.homeMode)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val save: (GestureBinding) -> Unit = { next -> viewModel.update { it.copy(gestures = it.gestures.with(gesture, next)) } }
    if (choosing) {
        // in home only mode the drawer is search only, so its entry would just repeat open search
        val homeOnly = settings.homeMode == HomeMode.HOME_ONLY
        val actions = if (homeOnly) ActionOrder - GestureAction.OPEN_DRAWER else ActionOrder
        val selected = if (homeOnly && binding.action == GestureAction.OPEN_DRAWER) GestureAction.OPEN_SEARCH else binding.action
        val serviceHint = stringResource(R.string.gesture_needs_service_hint)
        ChoiceDialog(
            title = gestureTitle(gesture),
            choices = actions.map { action ->
                Choice(action, actionTitle(action, settings.homeMode), supporting = serviceHint.takeIf { action.needsAccessibilityService })
            },
            selected = selected,
            onSelect = { action ->
                choosing = false
                if (action == GestureAction.OPEN_APP) pickingApp = true else save(GestureBinding(action))
            },
            onDismiss = { choosing = false },
        )
    }
    if (pickingApp) {
        ChoiceDialog(
            title = stringResource(R.string.gesture_pick_app),
            choices = apps.map { app ->
                Choice(app.key, app.label, leading = { AppChoiceIcon(app.key) })
            },
            selected = binding.app,
            onSelect = { key ->
                pickingApp = false
                save(GestureBinding(GestureAction.OPEN_APP, key))
            },
            onDismiss = { pickingApp = false },
        )
    }
}

@Composable
private fun AppChoiceIcon(key: AppKey) {
    val icon = rememberAppIcon(key, ChoiceIconSize)
    if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(ChoiceIconSize))
    else Box(Modifier.size(ChoiceIconSize))
}

private val ChoiceIconSize = 36.dp

@Composable
private fun gestureTitle(gesture: Gesture): String = stringResource(
    when (gesture) {
        Gesture.SWIPE_UP -> R.string.gesture_swipe_up
        Gesture.SWIPE_DOWN -> R.string.gesture_swipe_down
        Gesture.TWO_FINGER_SWIPE_UP -> R.string.gesture_two_finger_up
        Gesture.TWO_FINGER_SWIPE_DOWN -> R.string.gesture_two_finger_down
        Gesture.DOUBLE_TAP -> R.string.gesture_double_tap
        Gesture.HOME_PRESS -> R.string.gesture_home_press
    },
)

@Composable
private fun actionTitle(action: GestureAction, mode: HomeMode): String = stringResource(
    when (action) {
        GestureAction.NONE -> R.string.gesture_action_none
        // the drawer is search only in home only mode, and the label says what will actually open
        GestureAction.OPEN_DRAWER -> if (mode == HomeMode.HOME_ONLY) R.string.gesture_action_search else R.string.gesture_action_drawer
        GestureAction.OPEN_SEARCH -> R.string.gesture_action_search
        GestureAction.NOTIFICATIONS -> R.string.gesture_action_notifications
        GestureAction.QUICK_SETTINGS -> R.string.gesture_action_quick_settings
        GestureAction.LOCK_SCREEN -> R.string.gesture_action_lock
        GestureAction.RECENTS -> R.string.gesture_action_recents
        GestureAction.OPEN_APP -> R.string.gesture_action_open_app
        GestureAction.SPARK_SETTINGS -> R.string.gesture_action_spark_settings
    },
)

internal fun LazyListScope.searchPage(settings: LauncherSettings, viewModel: SettingsViewModel) {
    item {
        SwitchRow(
            title = stringResource(R.string.settings_search_shortcuts),
            summary = stringResource(R.string.settings_search_shortcuts_summary),
            checked = settings.searchShortcuts,
        ) { on -> viewModel.update { it.copy(searchShortcuts = on) } }
    }
    item {
        SwitchRow(
            title = stringResource(R.string.settings_search_settings),
            summary = stringResource(R.string.settings_search_settings_summary),
            checked = settings.searchSettings,
        ) { on -> viewModel.update { it.copy(searchSettings = on) } }
    }
    item { ContactsRow(settings, viewModel) }
}

/**
 * contacts are off until the user both turns them on and grants access. the reason is shown
 * before the system prompt, so the permission is never asked for without context.
 */
@Composable
private fun ContactsRow(settings: LauncherSettings, viewModel: SettingsViewModel) {
    val granted by viewModel.contactsGranted.collectAsStateWithLifecycle()
    var explaining by remember { mutableStateOf(false) }
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshSystemState()
        onPauseOrDispose { }
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        viewModel.refreshSystemState()
        if (ok) viewModel.update { it.copy(searchContacts = true) }
    }
    SwitchRow(
        title = stringResource(R.string.settings_search_contacts),
        summary = stringResource(
            if (settings.searchContacts && !granted) R.string.settings_search_contacts_revoked else R.string.settings_search_contacts_summary,
        ),
        checked = settings.searchContacts && granted,
    ) { on ->
        when {
            !on -> viewModel.update { it.copy(searchContacts = false) }
            granted -> viewModel.update { it.copy(searchContacts = true) }
            else -> explaining = true
        }
    }
    if (explaining) {
        AlertDialog(
            onDismissRequest = { explaining = false },
            title = { Text(stringResource(R.string.settings_search_contacts)) },
            text = { Text(stringResource(R.string.settings_search_contacts_explain)) },
            confirmButton = {
                FilledTonalButton(onClick = {
                    explaining = false
                    request.launch(Manifest.permission.READ_CONTACTS)
                }) { Text(stringResource(R.string.settings_continue)) }
            },
            dismissButton = { TextButton(onClick = { explaining = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}
