package dev.gearturner.sparklauncher.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gearturner.sparklauncher.core.apps.AppsRepository
import dev.gearturner.sparklauncher.core.apps.LaunchRequest
import dev.gearturner.sparklauncher.core.apps.LaunchResult
import dev.gearturner.sparklauncher.core.data.SettingsRepository
import dev.gearturner.sparklauncher.core.data.WorkspaceRepository
import dev.gearturner.sparklauncher.core.model.AppInfo
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.CellPlacement
import dev.gearturner.sparklauncher.core.model.Container
import dev.gearturner.sparklauncher.core.model.ItemType
import dev.gearturner.sparklauncher.core.model.LauncherSettings
import dev.gearturner.sparklauncher.core.model.WorkspaceItem
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeCell(
    val itemId: Long,
    val app: AppInfo,
    val title: String,
    val placement: CellPlacement,
)

data class HomeUiState(
    val loading: Boolean = true,
    val pages: List<List<HomeCell>> = listOf(emptyList()),
    val dock: List<HomeCell> = emptyList(),
    val settings: LauncherSettings = LauncherSettings(),
)

sealed interface HomeEvent {
    data class LaunchFailed(val label: String) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workspace: WorkspaceRepository,
    private val apps: AppsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val events = Channel<HomeEvent>(Channel.BUFFERED)
    val oneShotEvents: Flow<HomeEvent> = events.receiveAsFlow()

    val state: StateFlow<HomeUiState> = combine(
        workspace.items,
        apps.apps,
        settingsRepository.settings,
    ) { items, installed, settings ->
        if (installed == null) HomeUiState(loading = true, settings = settings) else buildState(items, installed, settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    init {
        viewModelScope.launch { applyDefaultLayoutOnce() }
    }

    fun launch(cell: HomeCell, request: LaunchRequest) {
        if (apps.launch(cell.app.key, request) is LaunchResult.Failed) {
            events.trySend(HomeEvent.LaunchFailed(cell.title))
        }
    }

    fun openAppInfo(cell: HomeCell, request: LaunchRequest) {
        apps.openAppDetails(cell.app.key, request)
    }

    fun remove(cell: HomeCell) {
        viewModelScope.launch { workspace.remove(cell.itemId) }
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
    }
}

/**
 * joins stored items with installed apps. items whose app is missing are hidden rather than deleted,
 * because apps on adopted storage or in a paused profile come back later.
 */
internal fun buildState(
    items: List<WorkspaceItem>,
    installed: List<AppInfo>,
    settings: LauncherSettings,
): HomeUiState {
    val byKey: Map<AppKey, AppInfo> = installed.associateBy { it.key }
    fun WorkspaceItem.toCell(): HomeCell? {
        if (type != ItemType.APP) return null
        val info = app?.let(byKey::get) ?: return null
        return HomeCell(id, info, customTitle ?: info.label, placement)
    }
    val desktop = items.filter { it.container == Container.Desktop }
        .filter { it.placement.cellX < settings.homeColumns && it.placement.cellY < settings.homeRows }
        .mapNotNull { it.toCell() }
    val pageCount = (desktop.maxOfOrNull { it.placement.page } ?: 0) + 1
    val pages = List(pageCount) { page -> desktop.filter { it.placement.page == page } }
    val dock = items.filter { it.container == Container.Dock && it.placement.cellX < settings.dockSlots }
        .mapNotNull { it.toCell() }
        .sortedBy { it.placement.cellX }
    return HomeUiState(loading = false, pages = pages, dock = dock, settings = settings)
}
