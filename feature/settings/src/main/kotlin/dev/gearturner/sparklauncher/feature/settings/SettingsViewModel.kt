package dev.gearturner.sparklauncher.feature.settings

import android.content.Intent
import dev.gearturner.sparklauncher.core.apps.NotificationDots

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gearturner.sparklauncher.core.apps.SparkAccessibility
import dev.gearturner.sparklauncher.core.model.AppInfo
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gearturner.sparklauncher.core.apps.AppsRepository
import dev.gearturner.sparklauncher.core.apps.ShortcutsRepository
import dev.gearturner.sparklauncher.core.data.ShortcutPlacement
import dev.gearturner.sparklauncher.core.model.ShortcutKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import dev.gearturner.sparklauncher.core.data.AppOverridesRepository
import dev.gearturner.sparklauncher.core.data.SettingsRepository
import dev.gearturner.sparklauncher.core.model.AppOverride
import kotlinx.coroutines.flow.combine
import dev.gearturner.sparklauncher.core.data.WorkspaceRepository
import dev.gearturner.sparklauncher.core.icons.IconPackInfo
import dev.gearturner.sparklauncher.core.icons.IconPackRepository
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.LauncherSettings
import java.text.Collator
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val workspace: WorkspaceRepository,
    apps: AppsRepository,
    private val iconPacks: IconPackRepository,
    private val notificationDots: NotificationDots,
    private val overrides: AppOverridesRepository,
    private val shortcuts: ShortcutsRepository,
    private val shortcutPlacement: ShortcutPlacement,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _serviceEnabled = MutableStateFlow(false)

    /** whether spark's accessibility service is on, rechecked whenever settings comes back to the front */
    val serviceEnabled: StateFlow<Boolean> = _serviceEnabled

    private val _contactsGranted = MutableStateFlow(false)
    val contactsGranted: StateFlow<Boolean> = _contactsGranted

    private val _dotsAccess = MutableStateFlow(true)

    /** whether notification access is granted, which dots need; starts true so no prompt flashes */
    val dotsAccess: StateFlow<Boolean> = _dotsAccess

    fun notificationAccessIntent(): Intent = notificationDots.settingsIntent()

    /** these can all change in system settings while spark is in the background */
    fun refreshSystemState() {
        _serviceEnabled.value = SparkAccessibility.isEnabled(context)
        _dotsAccess.value = notificationDots.isAccessGranted()
        _contactsGranted.value =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    /** every launchable app that is not hidden, by name, for choosing what a gesture opens */
    val allApps: StateFlow<List<AppInfo>> = combine(apps.apps.filterNotNull(), overrides.overrides) { list, appOverrides ->
        pickableAppsOf(list, appOverrides, Collator.getInstance())
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** apps hidden from the drawer, by the name the drawer would show; null until apps have loaded */
    val hiddenApps: StateFlow<List<HiddenApp>?> = combine(apps.apps.filterNotNull(), overrides.overrides) { installed, appOverrides ->
        hiddenAppsOf(installed, appOverrides, Collator.getInstance())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _hiddenUnlocked = MutableStateFlow(false)

    /**
     * true once the user has passed the lock this time settings was on screen. it only guards a page
     * restored from saved state, since every entry to the page asks again, and it is cleared whenever
     * settings leaves the screen.
     */
    val hiddenUnlocked: StateFlow<Boolean> = _hiddenUnlocked

    fun unlockHidden() {
        _hiddenUnlocked.value = true
    }

    fun lockHidden() {
        _hiddenUnlocked.value = false
    }

    /** puts an app back in the drawer; in home only mode it also returns to the home pages */
    fun unhide(key: AppKey) {
        viewModelScope.launch { overrides.setHidden(key, false) }
    }

    /** every shortcut spark holds pinned and where it is used; null until apps have loaded */
    val pinnedShortcuts: StateFlow<List<PinnedShortcut>?> = combine(
        shortcuts.pinned,
        workspace.placedShortcutKeys,
        overrides.overrides,
        apps.apps.filterNotNull(),
    ) { pinned, placed, appOverrides, installed ->
        pinnedShortcutsOf(pinned, placed, appOverrides, installed, Collator.getInstance())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** the system only lists pinned shortcuts to the default home app, so an empty list may mean no access */
    val hasShortcutAccess: Boolean get() = shortcuts.hasHostPermission

    private val unpinFailures = Channel<Unit>(Channel.BUFFERED)

    /** fires when the system refused to let go of a deleted shortcut */
    val unpinFailed: Flow<Unit> = unpinFailures.receiveAsFlow()

    /** removes the shortcut from home and any swipe that uses it, then lets the system release it */
    fun deleteShortcut(key: ShortcutKey) {
        viewModelScope.launch {
            if (!shortcutPlacement.deleteShortcut(key)) unpinFailures.trySend(Unit)
            shortcuts.refresh()
        }
    }

    private val _packs = MutableStateFlow<List<IconPackInfo>?>(null)

    /** null while the installed packs are being listed */
    val packs: StateFlow<List<IconPackInfo>?> = _packs

    init {
        viewModelScope.launch { _packs.value = iconPacks.installedPacks() }
    }

    val settings: StateFlow<LauncherSettings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** a few real apps, so icon settings preview on icons the user knows */
    val previewApps: StateFlow<List<AppKey>> = apps.apps.filterNotNull()
        .map { list ->
            val collator = Collator.getInstance()
            list.filterNot { it.isManagedProfile }.sortedWith { a, b -> collator.compare(a.label, b.label) }
                .take(PREVIEW_COUNT)
                .map { it.key }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _previewSeed = MutableStateFlow<Int?>(null)

    /** a seed color shown while the hue slider moves, before it is saved; null when nothing is previewing */
    val previewSeed: StateFlow<Int?> = _previewSeed

    fun previewSeed(color: Int) {
        _previewSeed.value = color
    }

    /**
     * saves the chosen seed and drops the preview only once the saved value is what settings report,
     * so the screen never flashes back to the old color between the two
     */
    fun commitSeed(color: Int) {
        _previewSeed.value = color
        viewModelScope.launch {
            repository.update { it.copy(seedColor = color) }
            repository.settings.first { it.seedColor == (color or OPAQUE) }
            if (_previewSeed.value == color) _previewSeed.value = null
        }
    }

    fun update(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    /** grid changes also rearrange the layouts so no item is left outside the new grid */
    fun updateGrid(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch {
            repository.update(transform)
            val next = repository.current()
            workspace.reflow(next.homeColumns, next.homeRows, next.dockSlots)
        }
    }

    private companion object {
        const val PREVIEW_COUNT = 5
        // settings store seeds fully opaque, so the comparison has to match that
        const val OPAQUE: Int = 0xFF000000.toInt()
    }
}

/** the apps a settings picker may offer: hidden apps stay secret outside the locked hidden apps page */
internal fun pickableAppsOf(installed: List<AppInfo>, overrides: Map<AppKey, AppOverride>, collator: Collator): List<AppInfo> =
    installed.filterNot { overrides[it.key]?.hidden == true }
        .sortedWith { a, b -> collator.compare(a.label, b.label) }

/** an app hidden from the drawer, as the hidden apps list shows it */
data class HiddenApp(val key: AppKey, val label: String)

/**
 * the installed apps that are hidden, by their renamed label when they have one, in the drawer's
 * order. apps hidden and later uninstalled are left out, since there is nothing left to show.
 */
internal fun hiddenAppsOf(installed: List<AppInfo>, overrides: Map<AppKey, AppOverride>, collator: Collator): List<HiddenApp> =
    installed.filter { overrides[it.key]?.hidden == true }
        .map { app -> HiddenApp(app.key, overrides[app.key]?.customLabel ?: app.label) }
        .sortedWith { a, b -> collator.compare(a.label, b.label) }
