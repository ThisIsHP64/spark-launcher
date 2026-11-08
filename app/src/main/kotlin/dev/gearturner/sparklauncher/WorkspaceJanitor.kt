package dev.gearturner.sparklauncher

import dev.gearturner.sparklauncher.core.apps.AppsRepository
import dev.gearturner.sparklauncher.core.apps.di.ApplicationScope
import dev.gearturner.sparklauncher.core.data.AppOverridesRepository
import dev.gearturner.sparklauncher.core.data.SettingsRepository
import dev.gearturner.sparklauncher.core.data.ShortcutPlacement
import dev.gearturner.sparklauncher.core.data.WorkspaceRepository
import dev.gearturner.sparklauncher.core.model.HomeMode
import dev.gearturner.sparklauncher.feature.widgets.WidgetHostManager
import java.text.Collator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * keeps layouts consistent with the apps actually installed. uninstalled apps leave every layout;
 * apps that are only unavailable, such as on removed storage or in a paused work profile, keep
 * their spots because they come back. in home only mode, every visible app is kept on home.
 */
@Singleton
class WorkspaceJanitor @Inject constructor(
    private val apps: AppsRepository,
    private val workspace: WorkspaceRepository,
    private val shortcutPlacement: ShortcutPlacement,
    private val settings: SettingsRepository,
    private val overrides: AppOverridesRepository,
    // injected so the host exists from process start and frees ids of widgets removed here
    @Suppress("unused") private val widgetHost: WidgetHostManager,
    @ApplicationScope private val scope: CoroutineScope,
) {
    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            apps.packageRemovals.collect { (packageName, serial) ->
                shortcutPlacement.releasePins(workspace.removePackage(packageName, serial))
            }
        }
        scope.launch {
            combine(settings.settings, apps.apps.filterNotNull(), overrides.overrides) { current, installed, appOverrides ->
                if (current.homeMode != HomeMode.HOME_ONLY) return@combine null
                val collator = Collator.getInstance()
                // hidden apps stay off home, which is also what removing an app does in this mode
                val visible = installed.filterNot { appOverrides[it.key]?.hidden == true }
                    .sortedWith { a, b -> collator.compare(appOverrides[a.key]?.customLabel ?: a.label, appOverrides[b.key]?.customLabel ?: b.label) }
                    .map { it.key }
                Triple(visible, current.homeColumns, current.homeRows)
            }
                .filterNotNull()
                // installs and settings changes arrive in bursts; one sync after they settle is enough
                .debounce(SYNC_DEBOUNCE_MS)
                .collect { (visible, columns, rows) -> workspace.syncHomeOnly(visible, columns, rows) }
        }
    }

    private companion object {
        const val SYNC_DEBOUNCE_MS = 300L
    }
}
