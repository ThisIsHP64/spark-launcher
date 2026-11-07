package dev.gearturner.sparklauncher.core.data

import dev.gearturner.sparklauncher.core.apps.ShortcutsRepository
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.AppShortcut
import dev.gearturner.sparklauncher.core.model.WorkspaceItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * keeps the home screen and the system's pinned shortcut list in agreement. apps may delete any
 * shortcut that is not pinned, so every add and remove re-pins exactly what the layout still holds.
 */
@Singleton
class ShortcutPlacement @Inject constructor(
    private val workspace: WorkspaceRepository,
    private val shortcuts: ShortcutsRepository,
) {

    /** pins first so the shortcut cannot be deleted between being placed and being pinned */
    suspend fun addToHome(shortcut: AppShortcut, owner: AppKey, columns: Int, rows: Int): AddResult {
        val key = shortcut.key
        val alreadyPinned = workspace.pinnedShortcutIds(key.packageName, key.userSerial)
        shortcuts.setPinned(key.packageName, key.userSerial, alreadyPinned + key.shortcutId)
        val result = workspace.addShortcutToDesktop(key, owner, shortcut.shortLabel, columns, rows)
        if (result == AddResult.NoSpace) syncPins(key.packageName, key.userSerial)
        return result
    }

    /** for pin requests the system already pinned the shortcut when the user accepted it */
    suspend fun addAccepted(shortcut: AppShortcut, owner: AppKey, columns: Int, rows: Int): AddResult {
        val key = shortcut.key
        val result = workspace.addShortcutToDesktop(key, owner, shortcut.shortLabel, columns, rows)
        if (result == AddResult.NoSpace) syncPins(key.packageName, key.userSerial)
        return result
    }

    /** removing a folder can drop several shortcuts at once, so every affected package re-syncs */
    suspend fun removeFromHome(itemId: Long) {
        releasePins(workspace.remove(itemId))
    }

    suspend fun releasePins(removed: List<WorkspaceItem>) {
        removed.mapNotNull { it.shortcutKey }
            .map { it.packageName to it.userSerial }
            .distinct()
            .forEach { (packageName, serial) -> syncPins(packageName, serial) }
    }

    private suspend fun syncPins(packageName: String, userSerial: Long) {
        shortcuts.setPinned(packageName, userSerial, workspace.pinnedShortcutIds(packageName, userSerial))
    }
}
