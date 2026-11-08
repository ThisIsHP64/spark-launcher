package dev.gearturner.sparklauncher.feature.widgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gearturner.sparklauncher.core.apps.AppsRepository
import dev.gearturner.sparklauncher.core.model.AppKey
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** one app's widgets, headed by the app's name and icon */
data class WidgetGroup(
    val appLabel: String,
    /** a launchable activity of the app for its icon, or null for apps with no icon of their own */
    val appKey: AppKey?,
    val widgets: List<WidgetProvider>,
)

@HiltViewModel
class WidgetPickerViewModel @Inject constructor(
    widgets: WidgetRepository,
    apps: AppsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val packageFilter = MutableStateFlow<String?>(null)

    val groups: StateFlow<List<WidgetGroup>> = combine(
        widgets.providers,
        apps.apps,
        query,
        packageFilter,
    ) { providers, installed, q, filter ->
        groupWidgets(providers, installed.orEmpty().map { it.key to it.label }, q, filter)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        query.value = value.take(64)
    }

    fun setPackageFilter(packageName: String?) {
        packageFilter.value = packageName
    }
}

/** groups by app and profile, sorts apps and widgets by name, and filters by app or widget name */
internal fun groupWidgets(
    providers: List<WidgetProvider>,
    appLabels: List<Pair<AppKey, String>>,
    query: String,
    packageFilter: String?,
): List<WidgetGroup> {
    val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
    val needle = query.trim().lowercase(Locale.ROOT)
    return providers
        .filter { packageFilter == null || it.key.packageName == packageFilter }
        .groupBy { it.key.packageName to it.key.userSerial }
        .map { (owner, widgets) ->
            val app = appLabels.firstOrNull { it.first.packageName == owner.first && it.first.userSerial == owner.second }
            WidgetGroup(
                appLabel = app?.second ?: widgets.first().label,
                appKey = app?.first,
                widgets = widgets.sortedWith { a, b -> collator.compare(a.label, b.label) },
            )
        }
        .mapNotNull { group ->
            if (needle.isEmpty() || group.appLabel.lowercase(Locale.ROOT).contains(needle)) return@mapNotNull group
            val matching = group.widgets.filter { it.label.lowercase(Locale.ROOT).contains(needle) }
            if (matching.isEmpty()) null else group.copy(widgets = matching)
        }
        .sortedWith { a, b -> collator.compare(a.appLabel, b.appLabel) }
}
