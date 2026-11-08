package dev.gearturner.sparklauncher.feature.widgets

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.UserManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gearturner.sparklauncher.core.apps.AppsRepository
import dev.gearturner.sparklauncher.core.apps.di.ApplicationScope
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.WidgetSizeSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** one widget an app offers, with its sizes already in dp */
data class WidgetProvider(
    val key: AppKey,
    val info: AppWidgetProviderInfo,
    val label: String,
    val description: String?,
    val sizeSpec: WidgetSizeSpec,
    /** true when the widget must be set up before it is placed */
    val needsConfiguration: Boolean,
    /** true when the provider lets its setup screen be opened again later */
    val reconfigurable: Boolean,
)

@Singleton
class WidgetRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val launcherApps: LauncherApps,
    private val userManager: UserManager,
    private val hostManager: WidgetHostManager,
    apps: AppsRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** every widget from every profile, refreshed when apps change */
    val providers: StateFlow<List<WidgetProvider>> = merge(apps.packageChanges.map { }, refreshRequests)
        .onStart { emit(Unit) }
        .conflate()
        .map { load() }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    fun provider(key: AppKey): WidgetProvider? = providers.value.firstOrNull { it.key == key }

    /** reads a placed widget's provider straight from the system, which works before the list loads */
    fun providerFor(appWidgetId: Int): WidgetProvider? = hostManager.info(appWidgetId)?.let(::toProvider)

    fun toProvider(info: AppWidgetProviderInfo): WidgetProvider {
        val density = context.resources.displayMetrics.density
        fun dp(px: Int) = if (px <= 0) 0f else px / density
        val features = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) info.widgetFeatures else 0
        val optional = features and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        val reconfigurable = features and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0
        val spec = WidgetSizeSpec(
            minWidthDp = dp(info.minWidth),
            minHeightDp = dp(info.minHeight),
            minResizeWidthDp = dp(info.minResizeWidth),
            minResizeHeightDp = dp(info.minResizeHeight),
            maxResizeWidthDp = dp(info.maxResizeWidth),
            maxResizeHeightDp = dp(info.maxResizeHeight),
            targetCellWidth = info.targetCellWidth,
            targetCellHeight = info.targetCellHeight,
            resizableHorizontally = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
            resizableVertically = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
        )
        return WidgetProvider(
            key = AppKey(info.provider.packageName, info.provider.className, userManager.getSerialNumberForUser(info.profile)),
            info = info,
            label = info.loadLabel(context.packageManager)?.trim().orEmpty().ifEmpty { info.provider.className },
            description = info.loadDescription(context)?.toString()?.trim()?.ifEmpty { null },
            sizeSpec = spec,
            needsConfiguration = info.configure != null && !optional,
            reconfigurable = info.configure != null && reconfigurable,
        )
    }

    private fun load(): List<WidgetProvider> = launcherApps.profiles.flatMap { user ->
        try {
            hostManager.appWidgetManager.getInstalledProvidersForProfile(user).map(::toProvider)
        } catch (e: RuntimeException) {
            // a locked profile must not hide the widgets of every other profile
            Log.w(TAG, "could not list widgets for a profile", e)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "WidgetRepository"
    }
}
