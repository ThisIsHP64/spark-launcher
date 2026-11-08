package dev.gearturner.sparklauncher.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gearturner.sparklauncher.core.model.LauncherSettings

/** shown only while dots are on but notification access is missing, with a way to grant it */
@Composable
internal fun NotificationAccessCard(settings: LauncherSettings, viewModel: SettingsViewModel) {
    val granted by viewModel.dotsAccess.collectAsStateWithLifecycle()
    // access is granted on a system page, so it is checked again whenever settings returns
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshSystemState()
        onPauseOrDispose { }
    }
    if (granted || !settings.notificationDots) return
    AccessCard(
        icon = Icons.Outlined.NotificationsActive,
        title = stringResource(R.string.settings_dots_access_title),
        body = stringResource(R.string.settings_dots_access_body),
        action = stringResource(R.string.settings_dots_access_open),
        intent = viewModel::notificationAccessIntent,
    )
}
