package dev.gearturner.sparklauncher

import dev.gearturner.sparklauncher.core.apps.AppsRepository
import dev.gearturner.sparklauncher.core.apps.di.ApplicationScope
import dev.gearturner.sparklauncher.core.data.ShortcutPlacement
import dev.gearturner.sparklauncher.core.data.WorkspaceRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * removes home items for apps that were uninstalled. apps that are only unavailable, such as on
 * removed storage or in a paused work profile, keep their spots because they come back.
 */
@Singleton
class WorkspaceJanitor @Inject constructor(
    private val apps: AppsRepository,
    private val workspace: WorkspaceRepository,
    private val shortcutPlacement: ShortcutPlacement,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            apps.packageRemovals.collect { (packageName, serial) ->
                shortcutPlacement.releasePins(workspace.removePackage(packageName, serial))
            }
        }
    }
}
