package dev.gearturner.sparklauncher.core.apps

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gearturner.sparklauncher.core.apps.di.ApplicationScope
import dev.gearturner.sparklauncher.core.model.AppInfo
import dev.gearturner.sparklauncher.core.model.AppKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface LaunchResult {
    data object Started : LaunchResult
    data class Failed(val reason: Throwable) : LaunchResult
}

/** the single source of installed, launchable activities across every profile the user has */
@Singleton
class AppsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val launcherApps: LauncherApps,
    private val userManager: UserManager,
    @ApplicationScope scope: CoroutineScope,
) {
    @Volatile
    private var activityIndex: Map<AppKey, LauncherActivityInfo> = emptyMap()

    private val changedPackages = MutableSharedFlow<Set<String>>(extraBufferCapacity = 32)

    /** packages whose resources changed, so icon caches can drop stale bitmaps */
    val packageChanges: SharedFlow<Set<String>> = changedPackages.asSharedFlow()

    private val changedShortcuts = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val removedPackages = MutableSharedFlow<Pair<String, Long>>(extraBufferCapacity = 32)

    /** a package fully uninstalled from one profile, as package name and profile serial */
    val packageRemovals: SharedFlow<Pair<String, Long>> = removedPackages.asSharedFlow()

    /** fires whenever any app publishes, updates, or removes shortcuts, or apps change */
    val shortcutChanges: SharedFlow<Unit> = changedShortcuts.asSharedFlow()

    /** null until the first load finishes, which lets the ui tell "loading" from "no apps" */
    val apps: StateFlow<List<AppInfo>?> = systemChangeSignals()
        .conflate()
        .map { loadAll() }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun awaitApps(): List<AppInfo> = apps.filterNotNull().first()

    fun activityInfo(key: AppKey): LauncherActivityInfo? = activityIndex[key]

    fun userFor(serial: Long): UserHandle? = userManager.getUserForSerialNumber(serial)

    fun launch(key: AppKey, request: LaunchRequest): LaunchResult {
        val user = userFor(key.userSerial) ?: return LaunchResult.Failed(IllegalStateException("profile is gone"))
        return try {
            launcherApps.startMainActivity(
                ComponentName(key.packageName, key.className),
                user,
                request.sourceBounds,
                request.options,
            )
            LaunchResult.Started
        } catch (e: RuntimeException) {
            // covers activity not found, a disabled app, and a profile that was paused while we were showing it
            Log.w(TAG, "launch failed for ${key.packageName}", e)
            LaunchResult.Failed(e)
        }
    }

    fun openAppDetails(key: AppKey, request: LaunchRequest): LaunchResult {
        val user = userFor(key.userSerial) ?: return LaunchResult.Failed(IllegalStateException("profile is gone"))
        return try {
            launcherApps.startAppDetailsActivity(
                ComponentName(key.packageName, key.className),
                user,
                request.sourceBounds,
                request.options,
            )
            LaunchResult.Started
        } catch (e: RuntimeException) {
            Log.w(TAG, "app details failed for ${key.packageName}", e)
            LaunchResult.Failed(e)
        }
    }

    /** false for preinstalled apps, which can only be disabled from settings rather than removed */
    fun canUninstall(key: AppKey): Boolean {
        val info = activityInfo(key) ?: return false
        return info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0
    }

    /** opens the system uninstall confirmation; the user always sees and approves it there */
    fun requestUninstall(key: AppKey): LaunchResult {
        val user = userFor(key.userSerial) ?: return LaunchResult.Failed(IllegalStateException("profile is gone"))
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", key.packageName, null))
            .putExtra(Intent.EXTRA_USER, user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            LaunchResult.Started
        } catch (e: RuntimeException) {
            Log.w(TAG, "uninstall request failed for ${key.packageName}", e)
            LaunchResult.Failed(e)
        }
    }

    /**
     * picks the user's default phone, messages, browser, and camera apps for the starter dock,
     * in that order, skipping any role that has no default or resolves to the system chooser.
     */
    suspend fun defaultDockApps(): List<AppKey> {
        val installed = awaitApps()
        val mySerial = userManager.getSerialNumberForUser(Process.myUserHandle())
        val roleIntents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
            // the browser category rarely has a default, but https links always resolve to the default browser
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        )
        return roleIntents.mapNotNull { intent ->
            val pkg = resolveRolePackage(intent) ?: return@mapNotNull null
            installed.firstOrNull { it.key.packageName == pkg && it.key.userSerial == mySerial }?.key
        }.distinct()
    }

    /**
     * returns the default handler, or the only preinstalled handler when no default was chosen,
     * so a second camera app from the store does not leave the dock slot empty.
     */
    private fun resolveRolePackage(intent: Intent): String? {
        val pm = context.packageManager
        val resolved = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        if (resolved != null && resolved.packageName != SYSTEM_CHOOSER_PACKAGE) return resolved.packageName
        val systemHandlers = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo }
            .filter { it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
            .map { it.packageName }
            .distinct()
        return systemHandlers.singleOrNull()
    }

    private fun systemChangeSignals() = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                // updates arrive as changes, so a removal here means the app is really gone
                removedPackages.tryEmit(packageName to userManager.getSerialNumberForUser(user))
                changed(setOf(packageName))
            }
            override fun onPackageAdded(packageName: String, user: UserHandle) = changed(setOf(packageName))
            override fun onPackageChanged(packageName: String, user: UserHandle) = changed(setOf(packageName))
            override fun onPackagesAvailable(names: Array<out String>, user: UserHandle, replacing: Boolean) =
                changed(names.toSet())
            override fun onPackagesUnavailable(names: Array<out String>, user: UserHandle, replacing: Boolean) =
                changed(names.toSet())
            override fun onPackagesSuspended(names: Array<out String>, user: UserHandle) = changed(names.toSet())
            override fun onPackagesUnsuspended(names: Array<out String>, user: UserHandle) = changed(names.toSet())

            override fun onShortcutsChanged(
                packageName: String,
                shortcuts: MutableList<ShortcutInfo>,
                user: UserHandle,
            ) {
                changedShortcuts.tryEmit(Unit)
            }

            private fun changed(names: Set<String>) {
                changedPackages.tryEmit(names)
                changedShortcuts.tryEmit(Unit)
                trySend(Unit)
            }
        }
        // labels are localized and profiles can appear or pause, none of which launcherapps reports
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_LOCALE_CHANGED)
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        trySend(Unit)
        awaitClose {
            launcherApps.unregisterCallback(callback)
            context.unregisterReceiver(receiver)
        }
    }

    private fun loadAll(): List<AppInfo> {
        val myUser = Process.myUserHandle()
        val index = HashMap<AppKey, LauncherActivityInfo>()
        val result = ArrayList<AppInfo>()
        for (user in launcherApps.profiles) {
            val serial = userManager.getSerialNumberForUser(user)
            val activities = try {
                launcherApps.getActivityList(null, user)
            } catch (e: SecurityException) {
                // a locked or removed profile must not hide the apps of every other profile
                Log.w(TAG, "could not list apps for a profile", e)
                emptyList()
            }
            for (info in activities) {
                val key = AppKey(info.componentName.packageName, info.componentName.className, serial)
                index[key] = info
                result += AppInfo(
                    key = key,
                    label = info.label?.toString()?.trim().orEmpty().ifEmpty { key.packageName },
                    isManagedProfile = user != myUser,
                    firstInstallTime = info.firstInstallTime,
                )
            }
        }
        activityIndex = index
        return result
    }

    private companion object {
        const val TAG = "AppsRepository"
        const val SYSTEM_CHOOSER_PACKAGE = "android"
    }
}

/** where a launch came from on screen, plus any activity options such as the splash style */
data class LaunchRequest(
    val sourceBounds: Rect? = null,
    val options: Bundle? = null,
)
