package dev.gearturner.sparklauncher.feature.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gearturner.sparklauncher.core.apps.AppsRepository
import dev.gearturner.sparklauncher.core.apps.LaunchRequest
import dev.gearturner.sparklauncher.core.apps.LaunchResult
import dev.gearturner.sparklauncher.core.data.AddResult
import dev.gearturner.sparklauncher.core.data.SettingsRepository
import dev.gearturner.sparklauncher.core.data.WorkspaceRepository
import dev.gearturner.sparklauncher.core.model.AppInfo
import dev.gearturner.sparklauncher.core.model.LauncherSettings
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

data class DrawerUiState(
    val query: String = "",
    val apps: List<AppInfo> = emptyList(),
    val settings: LauncherSettings = LauncherSettings(),
)

sealed interface DrawerEvent {
    data class LaunchFailed(val label: String) : DrawerEvent
    data class AddedToHome(val result: AddResult) : DrawerEvent
}

@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val apps: AppsRepository,
    private val workspace: WorkspaceRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val events = Channel<DrawerEvent>(Channel.BUFFERED)
    val oneShotEvents: Flow<DrawerEvent> = events.receiveAsFlow()

    // sorting once per app list change keeps typing in the search field cheap
    private val sortedApps = apps.apps.filterNotNull().map { sortApps(it) }.flowOn(Dispatchers.Default)

    val state: StateFlow<DrawerUiState> = combine(sortedApps, query, settingsRepository.settings) { sorted, q, s ->
        DrawerUiState(query = q, apps = filterApps(sorted, q), settings = s)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrawerUiState())

    fun onQueryChange(value: String) {
        // a cap keeps a pasted wall of text from making every keystroke scan megabytes
        query.value = value.take(MAX_QUERY_LENGTH)
    }

    fun clearQuery() {
        query.value = ""
    }

    fun launch(app: AppInfo, request: LaunchRequest) {
        if (apps.launch(app.key, request) is LaunchResult.Failed) {
            events.trySend(DrawerEvent.LaunchFailed(app.label))
        }
    }

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

    private companion object {
        const val MAX_QUERY_LENGTH = 64
    }
}
