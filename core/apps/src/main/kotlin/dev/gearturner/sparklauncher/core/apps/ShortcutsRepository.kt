package dev.gearturner.sparklauncher.core.apps

import android.content.ComponentName
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import dev.gearturner.sparklauncher.core.apps.di.ApplicationScope
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.AppShortcut
import dev.gearturner.sparklauncher.core.model.ShortcutKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * app shortcuts through launcherapps. the system only answers the default home app, so every call
 * degrades to empty results while spark is not the default instead of throwing.
 */
@Singleton
class ShortcutsRepository @Inject constructor(
    private val launcherApps: LauncherApps,
    private val userManager: UserManager,
    private val apps: AppsRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    @Volatile
    private var pinnedIndex: Map<ShortcutKey, ShortcutInfo> = emptyMap()

    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** every shortcut spark has pinned, used to hide placed shortcuts their app has since removed */
    val pinned: StateFlow<Map<ShortcutKey, AppShortcut>> = merge(apps.shortcutChanges, refreshRequests)
        .onStart { emit(Unit) }
        .conflate()
        .map { loadPinned() }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** reloads after events launcherapps does not report, such as spark becoming the default home app */
    fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    val hasHostPermission: Boolean
        get() = try {
            launcherApps.hasShortcutHostPermission()
        } catch (e: IllegalStateException) {
            // thrown while the user is locked, when no shortcut can be shown anyway
            false
        }

    /** the static and dynamic shortcuts for a long press menu, in the order the app ranked them */
    suspend fun menuShortcuts(app: AppKey): List<AppShortcut> = withContext(Dispatchers.IO) {
        val user = apps.userFor(app.userSerial) ?: return@withContext emptyList()
        val query = ShortcutQuery()
            .setPackage(app.packageName)
            .setActivity(ComponentName(app.packageName, app.className))
            .setQueryFlags(ShortcutQuery.FLAG_MATCH_MANIFEST or ShortcutQuery.FLAG_MATCH_DYNAMIC)
        queryOrEmpty(query, user)
            // manifest shortcuts come first, like the pixel launcher, then each group by rank
            .sortedWith(compareBy<ShortcutInfo>({ !it.isDeclaredInManifest }, { it.rank }))
            .take(MAX_MENU_SHORTCUTS)
            .map { it.toModel(app.userSerial) }
    }

    fun launch(key: ShortcutKey, request: LaunchRequest): LaunchResult {
        val user = apps.userFor(key.userSerial) ?: return LaunchResult.Failed(IllegalStateException("profile is gone"))
        return try {
            launcherApps.startShortcut(key.packageName, key.shortcutId, request.sourceBounds, request.options, user)
            LaunchResult.Started
        } catch (e: RuntimeException) {
            // apps can disable or delete a shortcut at any moment, and launcher access can be revoked
            Log.w(TAG, "shortcut launch failed for ${key.packageName}", e)
            LaunchResult.Failed(e)
        }
    }

    /**
     * replaces the pinned set for one package. the system keeps exactly this list, so callers pass
     * every id still on the home screen; an id left out is unpinned and may be deleted by the app.
     */
    suspend fun setPinned(packageName: String, userSerial: Long, shortcutIds: Set<String>): Boolean =
        withContext(Dispatchers.IO) {
            val user = apps.userFor(userSerial) ?: return@withContext false
            try {
                launcherApps.pinShortcuts(packageName, shortcutIds.toList(), user)
                true
            } catch (e: RuntimeException) {
                Log.w(TAG, "could not update pinned shortcuts for $packageName", e)
                false
            }
        }

    /**
     * the system's own record of a shortcut spark has pinned, or null when it holds none. pin requests
     * are answered by whichever app sent them, so this is the only trustworthy proof one was accepted.
     */
    suspend fun pinnedShortcut(packageName: String, shortcutId: String, user: UserHandle): ShortcutInfo? =
        withContext(Dispatchers.IO) { launcherApps.findPinnedShortcut(packageName, shortcutId, user) }

    /** looks the shortcut up among pinned ones first, then any shortcut the app still publishes */
    suspend fun iconDrawable(key: ShortcutKey, density: Int): Drawable? = withContext(Dispatchers.IO) {
        val info = pinnedIndex[key] ?: findShortcut(key) ?: return@withContext null
        try {
            launcherApps.getShortcutIconDrawable(info, density)
        } catch (e: RuntimeException) {
            Log.w(TAG, "shortcut icon failed for ${key.packageName}", e)
            null
        }
    }

    /**
     * every enabled, labelled shortcut of every app in every profile, for search and the swipe picker. it is read on demand rather
     * than kept in memory because only an open search needs it.
     */
    suspend fun allLaunchable(): List<AppShortcut> = withContext(Dispatchers.IO) {
        if (!hasHostPermission) return@withContext emptyList()
        val query = ShortcutQuery().setQueryFlags(
            ShortcutQuery.FLAG_MATCH_DYNAMIC or ShortcutQuery.FLAG_MATCH_MANIFEST or ShortcutQuery.FLAG_MATCH_PINNED,
        )
        launcherApps.profiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            queryOrEmpty(query, user).filter { it.isEnabled && hasUsableLabel(it.shortLabel, it.longLabel) }.map { it.toModel(serial) }
        }.distinctBy { it.key }
    }

    private fun findShortcut(key: ShortcutKey): ShortcutInfo? {
        val user = apps.userFor(key.userSerial) ?: return null
        val query = ShortcutQuery()
            .setPackage(key.packageName)
            .setShortcutIds(listOf(key.shortcutId))
            .setQueryFlags(
                ShortcutQuery.FLAG_MATCH_PINNED or ShortcutQuery.FLAG_MATCH_MANIFEST or ShortcutQuery.FLAG_MATCH_DYNAMIC,
            )
        return queryOrEmpty(query, user).firstOrNull()
    }

    private fun loadPinned(): Map<ShortcutKey, AppShortcut> {
        if (!hasHostPermission) return emptyMap()
        val index = HashMap<ShortcutKey, ShortcutInfo>()
        val result = HashMap<ShortcutKey, AppShortcut>()
        for (user in launcherApps.profiles) {
            val serial = userManager.getSerialNumberForUser(user)
            val query = ShortcutQuery().setQueryFlags(ShortcutQuery.FLAG_MATCH_PINNED)
            for (info in queryOrEmpty(query, user)) {
                val model = info.toModel(serial)
                index[model.key] = info
                result[model.key] = model
            }
        }
        pinnedIndex = index
        return result
    }

    private fun queryOrEmpty(query: ShortcutQuery, user: UserHandle): List<ShortcutInfo> = try {
        launcherApps.getShortcuts(query, user).orEmpty()
    } catch (e: SecurityException) {
        // not the default home app, or the profile is locked
        emptyList()
    } catch (e: IllegalStateException) {
        emptyList()
    }

    private fun ShortcutInfo.toModel(userSerial: Long) = AppShortcut(
        key = ShortcutKey(`package`, id, userSerial),
        shortLabel = shortLabel?.toString().orEmpty().ifEmpty { id },
        longLabel = longLabel?.toString(),
        rank = rank,
        isEnabled = isEnabled,
        hasLabel = hasUsableLabel(shortLabel, longLabel),
    )

    private companion object {
        const val TAG = "ShortcutsRepository"
        // matches the pixel launcher, which keeps menus short enough to read at a glance
        const val MAX_MENU_SHORTCUTS = 4
    }
}

/**
 * the system leaves both labels empty for a shortcut whose app cannot resolve its strings, such as
 * an app that is frozen in a work profile. showing its raw id would be noise, so such shortcuts are
 * left out of lists, and a shortcut the user placed keeps its own saved title instead.
 */
internal fun hasUsableLabel(shortLabel: CharSequence?, longLabel: CharSequence?): Boolean =
    !shortLabel.isNullOrBlank() || !longLabel.isNullOrBlank()

/**
 * the pinned shortcut with this package and id, straight from the system. kept apart from the
 * repository so it can be checked against a real launcherapps without the rest of the app.
 */
internal fun LauncherApps.findPinnedShortcut(packageName: String, shortcutId: String, user: UserHandle): ShortcutInfo? {
    val query = ShortcutQuery()
        .setPackage(packageName)
        .setShortcutIds(listOf(shortcutId))
        .setQueryFlags(ShortcutQuery.FLAG_MATCH_PINNED)
    return try {
        pinnedMatch(getShortcuts(query, user).orEmpty(), packageName, shortcutId)
    } catch (e: SecurityException) {
        // not the default home app, or a profile that does not belong to this device user
        null
    } catch (e: IllegalStateException) {
        // the profile is locked
        null
    }
}

/** the query already narrows results, but each one is checked again so nothing else is mistaken for proof */
internal fun pinnedMatch(results: List<ShortcutInfo>, packageName: String, shortcutId: String): ShortcutInfo? =
    results.firstOrNull { it.`package` == packageName && it.id == shortcutId && it.isPinned }
