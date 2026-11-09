package dev.gearturner.sparklauncher.core.data

import dev.gearturner.sparklauncher.core.apps.ShortcutsRepository
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.AppShortcut
import dev.gearturner.sparklauncher.core.model.ShortcutKey
import dev.gearturner.sparklauncher.core.model.SwipeDirection
import dev.gearturner.sparklauncher.core.model.SwipeTarget
import dev.gearturner.sparklauncher.core.model.WorkspaceItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * keeps the home screen, the swipe targets, and the system's pinned shortcut list in agreement. apps
 * may delete any shortcut that is not pinned, so every change re-pins exactly what home placements
 * and swipe actions still hold.
 */
@Singleton
class ShortcutPlacement @Inject constructor(
    private val workspace: WorkspaceRepository,
    private val shortcuts: ShortcutsRepository,
    private val overrides: AppOverridesRepository,
) {

    /** pins first so the shortcut cannot be deleted between being placed and being pinned */
    suspend fun addToHome(shortcut: AppShortcut, owner: AppKey, columns: Int, rows: Int): AddResult {
        val key = shortcut.key
        val alreadyPinned = heldIds(key.packageName, key.userSerial)
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

    /**
     * sets what an icon does on a swipe, or clears it with a null target. a shortcut is pinned before
     * it is stored so the app cannot delete it in between, and a failed pin refuses the change
     * rather than leaving a swipe that may point at nothing.
     */
    suspend fun bindSwipe(owner: AppKey, direction: SwipeDirection, target: SwipeTarget?): Boolean {
        val valid = target?.let { it.sanitized() ?: return false }
        val previous = overrides.overrides.first()[owner]?.swipeFor(direction)
        if (valid is SwipeTarget.Shortcut) {
            val key = valid.key
            val pinned = shortcuts.setPinned(key.packageName, key.userSerial, heldIds(key.packageName, key.userSerial) + key.shortcutId)
            if (!pinned) return false
        }
        if (!overrides.setSwipe(owner, direction, valid)) return false
        // the replaced shortcut is released only after the new target is stored
        if (previous is SwipeTarget.Shortcut && previous != valid) syncPins(previous.key.packageName, previous.key.userSerial)
        return true
    }

    /**
     * deletes a pinned shortcut everywhere it is used and lets go of it. references go first, so a
     * crash midway leaves a harmless extra pin rather than a swipe or tile pointing at nothing. false
     * means the system refused the unpin, such as when spark is not the default home app.
     */
    suspend fun deleteShortcut(key: ShortcutKey): Boolean {
        workspace.removeShortcut(key)
        overrides.clearShortcut(key)
        return shortcuts.setPinned(key.packageName, key.userSerial, heldIds(key.packageName, key.userSerial))
    }

    /** every shortcut id of a package that home or a swipe action still needs pinned */
    private suspend fun heldIds(packageName: String, userSerial: Long): Set<String> =
        workspace.pinnedShortcutIds(packageName, userSerial) + overrides.swipeShortcutIds(packageName, userSerial)

    private suspend fun syncPins(packageName: String, userSerial: Long) {
        shortcuts.setPinned(packageName, userSerial, heldIds(packageName, userSerial))
    }
}
