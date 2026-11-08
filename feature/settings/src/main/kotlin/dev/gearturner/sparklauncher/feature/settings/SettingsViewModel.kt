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

    /** every launchable app by name, for choosing what a gesture opens */
    val allApps: StateFlow<List<AppInfo>> = apps.apps.filterNotNull()
        .map { list ->
            val collator = Collator.getInstance()
            list.sortedWith { a, b -> collator.compare(a.label, b.label) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** apps hidden from the drawer, by the name the drawer would show; null until apps have loaded */
    val hiddenApps: StateFlow<List<HiddenApp>?> = combine(apps.apps.filterNotNull(), overrides.overrides) { installed, appOverrides ->
        hiddenAppsOf(installed, appOverrides, Collator.getInstance())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** puts an app back in the drawer; in home only mode it also returns to the home pages */
    fun unhide(key: AppKey) {
        viewModelScope.launch { overrides.setHidden(key, false) }
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
