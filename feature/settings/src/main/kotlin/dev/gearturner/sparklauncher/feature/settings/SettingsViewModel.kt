package dev.gearturner.sparklauncher.feature.settings

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
import dev.gearturner.sparklauncher.core.data.SettingsRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val workspace: WorkspaceRepository,
    apps: AppsRepository,
    private val iconPacks: IconPackRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _serviceEnabled = MutableStateFlow(false)

    /** whether spark's accessibility service is on, rechecked whenever settings comes back to the front */
    val serviceEnabled: StateFlow<Boolean> = _serviceEnabled

    private val _contactsGranted = MutableStateFlow(false)
    val contactsGranted: StateFlow<Boolean> = _contactsGranted

    /** both can change in system settings while spark is in the background */
    fun refreshSystemState() {
        _serviceEnabled.value = SparkAccessibility.isEnabled(context)
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
    }
}
