package dev.gearturner.sparklauncher.feature.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gearturner.sparklauncher.core.apps.di.ApplicationScope
import dev.gearturner.sparklauncher.core.data.WorkspaceRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * owns spark's single widget host. every widget id the system hands out is either on the home
 * screen or freed here, because leaked ids keep their widgets running in the background.
 */
@Singleton
class WidgetHostManager @Inject constructor(
    @ApplicationContext context: Context,
    private val workspace: WorkspaceRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    val host = AppWidgetHost(context, HOST_ID)
    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)

    /** ids of widgets taken off every layout, so views held for them can be forgotten as well */
    val releasedIds: SharedFlow<List<Int>> get() = workspace.releasedWidgetIds

    init {
        scope.launch {
            workspace.releasedWidgetIds.collect { ids -> ids.forEach(::delete) }
        }
    }

    /** widgets only refresh while the host listens, which spark does whenever it is visible */
    fun startListening() = guarded("start listening") { host.startListening() }

    fun stopListening() = guarded("stop listening") { host.stopListening() }

    fun allocate(): Int = host.allocateAppWidgetId()

    fun delete(appWidgetId: Int) = guarded("delete widget id") { host.deleteAppWidgetId(appWidgetId) }

    /** succeeds silently once the user has allowed spark to bind widgets, otherwise asks first */
    fun bindIfAllowed(appWidgetId: Int, info: AppWidgetProviderInfo): Boolean = try {
        appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.profile, info.provider, null)
    } catch (e: RuntimeException) {
        Log.w(TAG, "bind check failed", e)
        false
    }

    fun info(appWidgetId: Int): AppWidgetProviderInfo? = try {
        appWidgetManager.getAppWidgetInfo(appWidgetId)
    } catch (e: RuntimeException) {
        null
    }

    /** frees ids left behind by a crash or a cancelled placement, run once when the launcher starts */
    suspend fun releaseOrphans() {
        val used = workspace.widgetIds()
        val allocated = try {
            host.appWidgetIds
        } catch (e: RuntimeException) {
            Log.w(TAG, "could not list widget ids", e)
            return
        }
        allocated.filter { it !in used }.forEach(::delete)
    }

    private inline fun guarded(action: String, block: () -> Unit) {
        try {
            block()
        } catch (e: RuntimeException) {
            // the widget service can die or reject a call; one failed call must not take home down
            Log.w(TAG, "could not $action", e)
        }
    }

    companion object {
        private const val TAG = "WidgetHostManager"
        const val HOST_ID = 1_024
    }
}
