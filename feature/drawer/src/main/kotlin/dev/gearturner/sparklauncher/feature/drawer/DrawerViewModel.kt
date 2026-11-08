package dev.gearturner.sparklauncher.feature.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gearturner.sparklauncher.core.apps.AppsRepository
import dev.gearturner.sparklauncher.core.apps.LaunchRequest
import dev.gearturner.sparklauncher.core.apps.LaunchResult
import dev.gearturner.sparklauncher.core.apps.ShortcutsRepository
import dev.gearturner.sparklauncher.core.data.AddResult
import dev.gearturner.sparklauncher.core.data.AppOverridesRepository
import dev.gearturner.sparklauncher.core.data.SettingsRepository
import dev.gearturner.sparklauncher.core.data.ShortcutPlacement
import dev.gearturner.sparklauncher.core.data.WorkspaceRepository
import dev.gearturner.sparklauncher.core.model.AppInfo
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.AppOverride
import dev.gearturner.sparklauncher.core.model.AppShortcut
import dev.gearturner.sparklauncher.core.icons.IconPackInfo
import dev.gearturner.sparklauncher.core.icons.IconPackRepository
import dev.gearturner.sparklauncher.core.model.CustomIcon
import dev.gearturner.sparklauncher.core.model.LauncherSettings
import dev.gearturner.sparklauncher.feature.search.ContactHit
import dev.gearturner.sparklauncher.feature.search.SearchRepository
import dev.gearturner.sparklauncher.feature.search.SearchResults
import dev.gearturner.sparklauncher.feature.search.SettingHit
import dev.gearturner.sparklauncher.feature.search.ShortcutHit
import dev.gearturner.sparklauncher.feature.widgets.WidgetRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** an app as the drawer shows it, with any rename already applied */
data class DrawerEntry(
    val app: AppInfo,
    val hidden: Boolean,
    val renamed: Boolean,
    val customIcon: Boolean = false,
)

data class DrawerUiState(
    val query: String = "",
    val entries: List<DrawerEntry> = emptyList(),
    val settings: LauncherSettings = LauncherSettings(),
    val results: SearchResults = SearchResults(),
) {
    /** what the keyboard's go key opens, and so what the drawer highlights */
    val topHit: TopHit? get() = if (query.isBlank()) null else topHitOf(entries, results)
}

/** the single result enter opens: the best app, or when no app matches, the first other result */
sealed interface TopHit {
    data class App(val entry: DrawerEntry) : TopHit
    data class Shortcut(val hit: ShortcutHit) : TopHit
    data class Contact(val hit: ContactHit) : TopHit
    data class Setting(val hit: SettingHit) : TopHit
}

/** follows the order the sections are drawn in, so the highlighted row is always the first one shown */
internal fun topHitOf(entries: List<DrawerEntry>, results: SearchResults): TopHit? =
    entries.firstOrNull()?.let(TopHit::App)
        ?: results.shortcuts.firstOrNull()?.let(TopHit::Shortcut)
        ?: results.contacts.firstOrNull()?.let(TopHit::Contact)
        ?: results.settings.firstOrNull()?.let(TopHit::Setting)

sealed interface DrawerEvent {
    data class LaunchFailed(val label: String) : DrawerEvent
    data class AddedToHome(val result: AddResult) : DrawerEvent
}

@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val apps: AppsRepository,
    private val shortcuts: ShortcutsRepository,
    private val shortcutPlacement: ShortcutPlacement,
    private val overrides: AppOverridesRepository,
    private val workspace: WorkspaceRepository,
    private val settingsRepository: SettingsRepository,
    widgets: WidgetRepository,
    private val iconPacks: IconPackRepository,
    private val search: SearchRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    // which apps offer widgets, so the menu only shows a widgets entry where it leads somewhere
    private val widgetPackages: StateFlow<Set<Pair<String, Long>>> = widgets.providers
        .map { list -> list.map { it.key.packageName to it.key.userSerial }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    private val events = Channel<DrawerEvent>(Channel.BUFFERED)
    val oneShotEvents: Flow<DrawerEvent> = events.receiveAsFlow()

    // renames change sort order, so sorting happens after overrides apply but not on every keystroke
    private val sortedEntries = combine(apps.apps.filterNotNull(), overrides.overrides) { installed, appOverrides ->
        val labeled = installed.map { app -> app.copy(label = appOverrides[app.key]?.customLabel ?: app.label) }
        sortApps(labeled).map { app -> app.toEntry(appOverrides[app.key]) }
    }.flowOn(Dispatchers.Default)

    private val results = search.results(query)

    val state: StateFlow<DrawerUiState> = combine(sortedEntries, query, settingsRepository.settings, results) { sorted, q, s, r ->
        // results trail the query by a short debounce; stale ones are dropped rather than shown for the wrong text
        DrawerUiState(query = q, entries = visibleEntries(sorted, q), settings = s, results = if (r.query == q) r else SearchResults())
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrawerUiState())

    fun onQueryChange(value: String) {
        // a cap keeps a pasted wall of text from making every keystroke scan megabytes
        query.value = value.take(MAX_QUERY_LENGTH)
    }

    fun launch(app: AppInfo, request: LaunchRequest) {
        if (apps.launch(app.key, request) is LaunchResult.Failed) {
            events.trySend(DrawerEvent.LaunchFailed(app.label))
        }
    }

    fun open(hit: TopHit) {
        when (hit) {
            is TopHit.App -> launch(hit.entry.app, LaunchRequest())
            is TopHit.Shortcut -> openShortcut(hit.hit, LaunchRequest())
            is TopHit.Contact -> openContact(hit.hit)
            is TopHit.Setting -> openSetting(hit.hit)
        }
    }

    fun openShortcut(hit: ShortcutHit, request: LaunchRequest) {
        if (!search.openShortcut(hit, request)) events.trySend(DrawerEvent.LaunchFailed(hit.shortcut.shortLabel))
    }

    fun openContact(hit: ContactHit) {
        if (!search.openContact(hit)) events.trySend(DrawerEvent.LaunchFailed(hit.name))
    }

    fun openSetting(hit: SettingHit) {
        if (!search.openSetting(hit)) events.trySend(DrawerEvent.LaunchFailed(hit.title))
    }

    fun launchShortcut(shortcut: AppShortcut, request: LaunchRequest) {
        if (shortcuts.launch(shortcut.key, request) is LaunchResult.Failed) {
            events.trySend(DrawerEvent.LaunchFailed(shortcut.shortLabel))
        }
    }

    suspend fun menuShortcuts(app: AppInfo): List<AppShortcut> = shortcuts.menuShortcuts(app.key)

    fun openAppInfo(app: AppInfo, request: LaunchRequest) {
        apps.openAppDetails(app.key, request)
    }

    fun addToHome(app: AppInfo) {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            val result = workspace.addAppToDesktop(app.key, settings.homeColumns, settings.homeRows)
            events.send(DrawerEvent.AddedToHome(result))
        }
    }

    fun pinShortcut(shortcut: AppShortcut, owner: AppKey) {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            val result = shortcutPlacement.addToHome(shortcut, owner, settings.homeColumns, settings.homeRows)
            events.send(DrawerEvent.AddedToHome(result))
        }
    }

    fun setHidden(app: AppInfo, hidden: Boolean) {
        viewModelScope.launch { overrides.setHidden(app.key, hidden) }
    }

    /** drawer renames apply to the app everywhere, unlike renaming a single home icon */
    fun rename(app: AppInfo, label: String?) {
        viewModelScope.launch { overrides.setLabel(app.key, label) }
    }

    fun canUninstall(app: AppInfo): Boolean = apps.canUninstall(app.key)

    suspend fun iconPacks(): List<IconPackInfo> = iconPacks.installedPacks()

    /** change icon is offered only when it can lead somewhere: a pack to pick from, or an icon to reset */
    fun canChangeIcon(entry: DrawerEntry): Boolean = iconPacks.packs.value.isNotEmpty() || entry.customIcon

    suspend fun packIconNames(pack: String): List<String> = iconPacks.drawableNames(pack)

    fun setCustomIcon(app: AppInfo, icon: CustomIcon?) {
        viewModelScope.launch { overrides.setIcon(app.key, icon) }
    }

    fun hasWidgets(app: AppInfo): Boolean = widgetPackages.value.contains(app.key.packageName to app.key.userSerial)

    fun uninstall(app: AppInfo) {
        apps.requestUninstall(app.key)
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 64
    }
}

private fun AppInfo.toEntry(override: AppOverride?) = DrawerEntry(
    app = this,
    hidden = override?.hidden == true,
    renamed = override?.customLabel != null,
    customIcon = override?.customIcon != null,
)

/**
 * hidden apps leave the list but still answer a search, which is how they are found and unhidden
 * without a separate settings screen.
 */
internal fun visibleEntries(sorted: List<DrawerEntry>, query: String): List<DrawerEntry> {
    if (query.isBlank()) return sorted.filterNot { it.hidden }
    val byKey = sorted.associateBy { it.app.key }
    return filterApps(sorted.map { it.app }, query).mapNotNull { byKey[it.key] }
}
