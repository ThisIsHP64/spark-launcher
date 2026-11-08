package dev.gearturner.sparklauncher.feature.workspace

import dev.gearturner.sparklauncher.core.ui.DragPayload
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
import dev.gearturner.sparklauncher.core.data.labelFor
import dev.gearturner.sparklauncher.core.model.AppInfo
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.AppOverride
import dev.gearturner.sparklauncher.core.model.AppShortcut
import dev.gearturner.sparklauncher.core.model.CellPlacement
import dev.gearturner.sparklauncher.core.model.Container
import dev.gearturner.sparklauncher.core.model.DropPlan
import dev.gearturner.sparklauncher.core.model.DropTarget
import dev.gearturner.sparklauncher.core.model.Dragged
import dev.gearturner.sparklauncher.core.model.HomeMode
import dev.gearturner.sparklauncher.core.model.IconSource
import dev.gearturner.sparklauncher.core.model.ItemType
import dev.gearturner.sparklauncher.core.model.LauncherSettings
import dev.gearturner.sparklauncher.core.model.ShortcutKey
import dev.gearturner.sparklauncher.core.icons.IconPackInfo
import dev.gearturner.sparklauncher.core.icons.IconPackRepository
import dev.gearturner.sparklauncher.core.model.CustomIcon
import dev.gearturner.sparklauncher.core.model.Span
import dev.gearturner.sparklauncher.core.model.SUBGRID
import dev.gearturner.sparklauncher.core.model.WidgetOptions
import dev.gearturner.sparklauncher.core.model.WorkspaceItem
import dev.gearturner.sparklauncher.feature.widgets.WidgetBinder
import dev.gearturner.sparklauncher.feature.widgets.WidgetHostManager
import dev.gearturner.sparklauncher.feature.widgets.WidgetProvider
import dev.gearturner.sparklauncher.feature.widgets.WidgetRepository
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** anything that occupies cells on a home page or a dock slot */
sealed interface HomeTile {
    val itemId: Long
    val placement: CellPlacement
}

/** one tappable thing on home: an app, or a shortcut that belongs to [app] */
data class HomeCell(
    override val itemId: Long,
    val app: AppInfo,
    val title: String,
    override val placement: CellPlacement,
    val shortcut: ShortcutKey? = null,
    /** true when the user renamed this item, which offers a reset in the rename dialog */
    val renamed: Boolean = false,
) : HomeTile {
    val iconSource: IconSource
        get() = shortcut?.let { IconSource.Shortcut(it, app.key) } ?: IconSource.App(app.key)
}

/** a hosted widget; [provider] is null when the providing app is gone or its profile is paused */
data class WidgetTile(
    override val itemId: Long,
    override val placement: CellPlacement,
    val appWidgetId: Int,
    val providerKey: AppKey,
    val options: WidgetOptions,
    val provider: WidgetProvider?,
) : HomeTile

data class FolderTile(
    override val itemId: Long,
    /** null until the user names it, so the ui can show a localized default */
    val name: String?,
    override val placement: CellPlacement,
    val children: List<HomeCell>,
) : HomeTile

data class HomeUiState(
    val loading: Boolean = true,
    val pages: List<List<HomeTile>> = listOf(emptyList()),
    val dock: List<HomeTile> = emptyList(),
    val settings: LauncherSettings = LauncherSettings(),
)

sealed interface HomeEvent {
    data class LaunchFailed(val label: String) : HomeEvent
    data class ShortcutAdded(val result: AddResult) : HomeEvent
    data object DropRefused : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workspace: WorkspaceRepository,
    private val apps: AppsRepository,
    private val shortcuts: ShortcutsRepository,
    private val shortcutPlacement: ShortcutPlacement,
    private val overrides: AppOverridesRepository,
    private val settingsRepository: SettingsRepository,
    private val widgets: WidgetRepository,
    private val widgetHost: WidgetHostManager,
    private val iconPacks: IconPackRepository,
) : ViewModel() {

    @Volatile
    private var latestOverrides: Map<AppKey, AppOverride> = emptyMap()


    private val events = Channel<HomeEvent>(Channel.BUFFERED)
    val oneShotEvents: Flow<HomeEvent> = events.receiveAsFlow()

    @Volatile
    private var latestItems: List<WorkspaceItem> = emptyList()

    // shortcut and override state travel together so the main combine stays within five sources
    private val decorations = combine(
        shortcuts.pinned,
        overrides.overrides.onEach { latestOverrides = it },
        widgets.providers,
    ) { pinned, appOverrides, providers ->
        Triple(pinned, appOverrides, providers)
    }

    val state: StateFlow<HomeUiState> = combine(
        workspace.items.onEach { latestItems = it },
        apps.apps,
        settingsRepository.settings,
        decorations,
    ) { items, installed, settings, (pinned, appOverrides, providers) ->
        if (installed == null) {
            HomeUiState(loading = true, settings = settings)
        } else {
            // without host access the pinned list reads empty, so placed shortcuts are trusted as is
            val pinnedOrNull = pinned.takeIf { shortcuts.hasHostPermission }
            buildState(items, installed, settings, pinnedOrNull, appOverrides, providers.associateBy { it.key })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    init {
        viewModelScope.launch { applyDefaultLayoutOnce() }
    }

    fun launch(cell: HomeCell, request: LaunchRequest) {
        val result = cell.shortcut?.let { shortcuts.launch(it, request) } ?: apps.launch(cell.app.key, request)
        if (result is LaunchResult.Failed) events.trySend(HomeEvent.LaunchFailed(cell.title))
    }

    fun launchShortcut(shortcut: AppShortcut, request: LaunchRequest) {
        if (shortcuts.launch(shortcut.key, request) is LaunchResult.Failed) {
            events.trySend(HomeEvent.LaunchFailed(shortcut.shortLabel))
        }
    }

    fun openAppInfo(cell: HomeCell, request: LaunchRequest) {
        apps.openAppDetails(cell.app.key, request)
    }

    /** shortcut items have no shortcuts of their own, so their menu stays short */
    suspend fun menuShortcuts(cell: HomeCell): List<AppShortcut> =
        if (cell.shortcut != null) emptyList() else shortcuts.menuShortcuts(cell.app.key)

    fun pinShortcut(shortcut: AppShortcut, owner: AppKey) {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            val result = shortcutPlacement.addToHome(shortcut, owner, settings.homeColumns, settings.homeRows)
            events.send(HomeEvent.ShortcutAdded(result))
        }
    }

    fun canUninstall(cell: HomeCell): Boolean = cell.shortcut == null && apps.canUninstall(cell.app.key)

    /** places a dragged app inside an open folder; a refused drop is reported like any other */
    fun dropIntoFolder(dragged: Dragged, folderId: Long, rank: Int, onSettled: () -> Unit) {
        viewModelScope.launch {
            if (!workspace.dropIntoFolder(dragged, folderId, rank)) events.send(HomeEvent.DropRefused)
            onSettled()
        }
    }

    fun reorderInFolder(itemId: Long, toRank: Int) {
        viewModelScope.launch { workspace.reorderInFolder(itemId, toRank) }
    }

    fun rename(itemId: Long, title: String?) {
        viewModelScope.launch { workspace.rename(itemId, title) }
    }

    /**
     * in home only mode every app lives on home, so removing an app hides it instead; otherwise it
     * would be put straight back. hidden apps still answer search and can be shown again there.
     */
    fun remove(itemId: Long) {
        viewModelScope.launch {
            val item = latestItems.firstOrNull { it.id == itemId }
            val app = item?.app
            if (settingsRepository.current().homeMode == HomeMode.HOME_ONLY && item?.type == ItemType.APP && app != null) {
                overrides.setHidden(app, true)
            }
            shortcutPlacement.removeFromHome(itemId)
        }
    }

    fun canUninstall(key: AppKey): Boolean = apps.canUninstall(key)

    fun uninstall(key: AppKey) {
        apps.requestUninstall(key)
    }

    /**
     * writes a drop. [onSettled] runs once the layout is saved or the drop refused, which is when
     * the dragged icon's original spot can show again without flashing the old position.
     */
    fun drop(dragged: Dragged, target: DropTarget, onSettled: () -> Unit) {
        viewModelScope.launch {
            try {
                val settings = settingsRepository.current()
                val plan = workspace.drop(dragged, target, settings.homeColumns, settings.homeRows, settings.dockSlots)
                if (plan == DropPlan.Reject) events.send(HomeEvent.DropRefused)
            } finally {
                onSettled()
            }
        }
    }

    /** looks the dragged item up by id; the repository re-reads it inside the transaction anyway */
    fun existing(itemId: Long): WorkspaceItem? = latestItems.firstOrNull { it.id == itemId }

    /** whether a drag carries one app or shortcut, the only things that can join a folder */
    fun isSingleIcon(payload: DragPayload): Boolean = when (payload) {
        is DragPayload.NewApp -> true
        is DragPayload.NewWidget -> false
        is DragPayload.Existing -> existing(payload.itemId)?.type.let { it == ItemType.APP || it == ItemType.SHORTCUT }
    }

    /** spark becoming the default home app is the moment shortcut access appears */
    fun onResume() {
        shortcuts.refresh()
        widgets.refresh()
    }

    suspend fun iconPacks(): List<IconPackInfo> = iconPacks.installedPacks()

    suspend fun packIconNames(pack: String): List<String> = iconPacks.drawableNames(pack)

    fun hasCustomIcon(key: AppKey): Boolean = latestOverrides[key]?.customIcon != null

    /** change icon is offered only when it can lead somewhere: a pack to pick from, or an icon to reset */
    fun canChangeIcon(key: AppKey): Boolean = iconPacks.packs.value.isNotEmpty() || hasCustomIcon(key)

    /** null restores the app's own icon, or the selected pack's */
    fun setCustomIcon(key: AppKey, icon: CustomIcon?) {
        viewModelScope.launch { overrides.setIcon(key, icon) }
    }

    fun hasWidgets(packageName: String): Boolean = widgets.providers.value.any { it.key.packageName == packageName }

    /**
     * places a widget from the picker. the spot is checked before the system is asked to bind, so
     * the user never grants access or fills in a setup screen for a widget that cannot land.
     * [span] is in half cells, like every home placement.
     */
    fun placeWidget(providerKey: AppKey, span: Span, target: DropTarget, binder: WidgetBinder?, onSettled: () -> Unit) {
        viewModelScope.launch {
            try {
                val provider = widgets.provider(providerKey)
                val settings = settingsRepository.current()
                val probe = Dragged.NewWidget(providerKey, NO_WIDGET_ID, span)
                val fits = workspace.previewDrop(probe, target, settings.homeColumns, settings.homeRows, settings.dockSlots)
                if (provider == null || binder == null || fits !is DropPlan.Place) {
                    events.send(HomeEvent.DropRefused)
                    return@launch
                }
                val appWidgetId = binder.bindAndConfigure(provider.info, provider.needsConfiguration) ?: return@launch
                val placed = workspace.drop(
                    Dragged.NewWidget(providerKey, appWidgetId, span),
                    target,
                    settings.homeColumns,
                    settings.homeRows,
                    settings.dockSlots,
                )
                // the spot can fill up while a setup screen is open; the bound widget is then released
                if (placed !is DropPlan.Place) {
                    widgetHost.delete(appWidgetId)
                    events.send(HomeEvent.DropRefused)
                }
            } finally {
                onSettled()
            }
        }
    }

    /** tapping a widget in the picker puts it in the first spot it fits; [cells] is its size in whole cells */
    fun addWidget(providerKey: AppKey, cells: Span, binder: WidgetBinder?) {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            val span = Span(cells.x * SUBGRID, cells.y * SUBGRID)
            val spot = workspace.firstFreeDesktopSpot(span.x, span.y, settings.homeColumns, settings.homeRows)
            if (spot == null) {
                events.send(HomeEvent.ShortcutAdded(AddResult.NoSpace))
                return@launch
            }
            placeWidget(providerKey, span, DropTarget.Desktop(spot.page, spot.cellX, spot.cellY), binder) {}
        }
    }

    fun resizeWidget(itemId: Long, placement: CellPlacement) {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            if (!workspace.resize(itemId, placement, settings.homeColumns, settings.homeRows)) {
                events.send(HomeEvent.DropRefused)
            }
        }
    }

    fun updateWidgetOptions(itemId: Long, options: WidgetOptions) {
        viewModelScope.launch { workspace.updateWidgetOptions(itemId, options) }
    }

    fun reconfigureWidget(appWidgetId: Int, binder: WidgetBinder?) {
        binder ?: return
        viewModelScope.launch { binder.reconfigure(appWidgetId) }
    }

    private suspend fun applyDefaultLayoutOnce() {
        if (settingsRepository.current().defaultLayoutApplied) return
        if (workspace.isEmpty()) {
            val settings = settingsRepository.current()
            workspace.seedDock(apps.defaultDockApps().take(settings.dockSlots))
        }
        settingsRepository.update { it.copy(defaultLayoutApplied = true) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        // a placeholder for checking whether a spot fits before any id is allocated
        const val NO_WIDGET_ID = 0
    }
}

/**
 * joins stored items with installed apps and shortcuts. items whose app or shortcut is missing are
 * hidden rather than deleted, because apps on adopted storage or in a paused profile come back later.
 * [pinned] is null when shortcut state is unknown, in which case shortcut items are shown as stored.
 */
internal fun buildState(
    items: List<WorkspaceItem>,
    installed: List<AppInfo>,
    settings: LauncherSettings,
    pinned: Map<ShortcutKey, AppShortcut>? = null,
    appOverrides: Map<AppKey, AppOverride> = emptyMap(),
    widgetProviders: Map<AppKey, WidgetProvider> = emptyMap(),
): HomeUiState {
    val byKey: Map<AppKey, AppInfo> = installed.associateBy { it.key }
    fun WorkspaceItem.toCell(): HomeCell? {
        val info = app?.let(byKey::get) ?: return null
        val appLabel = appOverrides.labelFor(info.key, info.label)
        return when (type) {
            ItemType.APP -> HomeCell(id, info, customTitle ?: appLabel, placement, renamed = customTitle != null)
            ItemType.SHORTCUT -> {
                val key = shortcutKey ?: return null
                val live = pinned?.let { it[key] ?: return null }
                // a shortcut's stored title is its label from when it was placed, so it is not a rename
                val title = customTitle ?: live?.shortLabel ?: appLabel
                HomeCell(id, info, title, placement, shortcut = key, renamed = live != null && customTitle != live.shortLabel)
            }
            ItemType.FOLDER, ItemType.WIDGET -> null
        }
    }
    fun WorkspaceItem.toTile(): HomeTile? {
        if (type == ItemType.WIDGET) {
            val key = app ?: return null
            val widgetId = appWidgetId ?: return null
            // a widget keeps its cells even while unavailable, and shows a placeholder there
            return WidgetTile(id, placement, widgetId, key, widgetOptions, widgetProviders[key])
        }
        if (type != ItemType.FOLDER) return toCell()
        val children = items.filter { it.container == Container.Folder(id) }
            .sortedBy { it.placement.cellX }
            .mapNotNull { it.toCell() }
        // a folder whose apps are all unavailable has nothing to show or open
        if (children.isEmpty()) return null
        return FolderTile(id, customTitle, placement, children)
    }
    val desktop = items.filter { it.container == Container.Desktop }
        // placements are in half cells, so the grid is measured in those too
        .filter { it.placement.cellX < settings.homeColumns * SUBGRID && it.placement.cellY < settings.homeRows * SUBGRID }
        .mapNotNull { it.toTile() }
    val pageCount = (desktop.maxOfOrNull { it.placement.page } ?: 0) + 1
    val pages = List(pageCount) { page -> desktop.filter { it.placement.page == page } }
    val dock = items.filter { it.container == Container.Dock && it.placement.cellX < settings.dockSlots }
        .mapNotNull { it.toTile() }
        .sortedBy { it.placement.cellX }
    return HomeUiState(loading = false, pages = pages, dock = dock, settings = settings)
}
