package dev.gearturner.sparklauncher.core.data

import dev.gearturner.sparklauncher.core.data.db.AppOverrideDao
import dev.gearturner.sparklauncher.core.data.db.AppOverrideEntity
import dev.gearturner.sparklauncher.core.data.db.decodeSwipeTarget
import dev.gearturner.sparklauncher.core.data.db.encode
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.AppOverride
import dev.gearturner.sparklauncher.core.model.CustomIcon
import dev.gearturner.sparklauncher.core.model.ShortcutKey
import dev.gearturner.sparklauncher.core.model.SwipeDirection
import dev.gearturner.sparklauncher.core.model.SwipeTarget
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class AppOverridesRepository @Inject constructor(
    private val dao: AppOverrideDao,
) {
    // read modify write per app, serialized so hiding and renaming at once cannot drop either change
    private val lock = Mutex()

    val overrides: Flow<Map<AppKey, AppOverride>> = dao.observeAll().map { rows ->
        rows.associate { row ->
            val key = AppKey(row.packageName, row.className, row.userSerial)
            val icon = if (row.iconPack != null && row.iconDrawable != null) CustomIcon(row.iconPack, row.iconDrawable) else null
            key to AppOverride(
                key, row.hidden, row.customLabel, icon,
                swipeUp = decodeSwipeTarget(row.swipeUp),
                swipeDown = decodeSwipeTarget(row.swipeDown),
                homeRemoved = row.homeRemoved,
            )
        }
    }

    suspend fun setHidden(key: AppKey, hidden: Boolean) = update(key) { it.copy(hidden = hidden) }

    /** keeps an app off home in home only mode without making it a secret, so search still finds it */
    suspend fun setHomeRemoved(key: AppKey, removed: Boolean) = update(key) { it.copy(homeRemoved = removed) }

    /** a blank label clears the override so the app's own name comes back */
    suspend fun setLabel(key: AppKey, label: String?) = update(key) {
        it.copy(customLabel = label?.trim()?.take(MAX_LABEL_LENGTH)?.ifEmpty { null })
    }

    /** null puts the app's own icon, or the icon pack's, back */
    suspend fun setIcon(key: AppKey, icon: CustomIcon?) = update(key) {
        val valid = icon?.takeIf { isValidPackage(it.packPackage) && DRAWABLE_NAME.matches(it.drawableName) }
        it.copy(iconPack = valid?.packPackage, iconDrawable = valid?.drawableName)
    }

    /**
     * null clears the swipe. a target that fails validation is refused and nothing changes, because
     * storing a cleaned up guess would launch something the user never picked.
     */
    suspend fun setSwipe(key: AppKey, direction: SwipeDirection, target: SwipeTarget?): Boolean {
        val valid = target?.let { it.sanitized() ?: return false }
        val encoded = valid?.encode()
        update(key) {
            when (direction) {
                SwipeDirection.UP -> it.copy(swipeUp = encoded)
                SwipeDirection.DOWN -> it.copy(swipeDown = encoded)
            }
        }
        return true
    }

    /** the shortcut ids of one package and profile that a swipe still needs pinned */
    suspend fun swipeShortcutIds(packageName: String, userSerial: Long): Set<String> =
        dao.withSwipes().asSequence()
            .flatMap { sequenceOf(decodeSwipeTarget(it.swipeUp), decodeSwipeTarget(it.swipeDown)) }
            .filterIsInstance<SwipeTarget.Shortcut>()
            .filter { it.key.packageName == packageName && it.key.userSerial == userSerial }
            .map { it.key.shortcutId }
            .toSet()

    /** clears every swipe that points at this shortcut, for when the shortcut is deleted */
    suspend fun clearShortcut(shortcut: ShortcutKey) = lock.withLock {
        for (row in dao.withSwipes()) {
            val up = decodeSwipeTarget(row.swipeUp)
            val down = decodeSwipeTarget(row.swipeDown)
            val clearUp = up is SwipeTarget.Shortcut && up.key == shortcut
            val clearDown = down is SwipeTarget.Shortcut && down.key == shortcut
            if (clearUp || clearDown) {
                persist(row.copy(swipeUp = if (clearUp) null else row.swipeUp, swipeDown = if (clearDown) null else row.swipeDown))
            }
        }
    }

    private suspend fun update(key: AppKey, transform: (AppOverrideEntity) -> AppOverrideEntity) = lock.withLock {
        val current = dao.get(key.packageName, key.className, key.userSerial)
            ?: AppOverrideEntity(key.packageName, key.className, key.userSerial)
        persist(transform(current))
    }

    /** callers hold the lock; rows that override nothing are deleted so the table only holds real choices */
    private suspend fun persist(next: AppOverrideEntity) {
        if (!next.hidden && !next.homeRemoved && next.customLabel == null && next.iconPack == null && next.swipeUp == null && next.swipeDown == null) {
            dao.delete(next.packageName, next.className, next.userSerial)
        } else {
            dao.upsert(next)
        }
    }

    companion object {
        const val MAX_LABEL_LENGTH = 48

        // resource entry names; anything else cannot be a drawable and is refused before it is stored
        private val DRAWABLE_NAME = Regex("[A-Za-z0-9_]{1,128}")
        private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

        private fun isValidPackage(name: String) = name.length <= 255 && PACKAGE_NAME.matches(name)
    }
}

/** applies a label override to a display name, used by every surface that shows app names */
fun Map<AppKey, AppOverride>.labelFor(key: AppKey, fallback: String): String = this[key]?.customLabel ?: fallback
