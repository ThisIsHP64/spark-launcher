package dev.gearturner.sparklauncher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.util.Log
import dev.gearturner.sparklauncher.feature.widgets.WidgetBinder
import dev.gearturner.sparklauncher.feature.widgets.WidgetHostManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/**
 * runs the two activity steps of placing a widget: the system's "allow spark to create widgets"
 * prompt and the widget's own setup screen. the setup screen can only be started through
 * appwidgethost's request code api, so both steps report back through onActivityResult.
 * any failure or refusal frees the allocated id, so declined widgets never linger.
 */
class WidgetBindFlow(
    private val activity: Activity,
    private val hostManager: WidgetHostManager,
) : WidgetBinder {

    private val pending = HashMap<Int, CompletableDeferred<Boolean>>()
    private var nextRequestCode = FIRST_REQUEST_CODE

    override suspend fun bindAndConfigure(info: AppWidgetProviderInfo, needsConfiguration: Boolean): Int? {
        val appWidgetId = hostManager.allocate()
        try {
            if (!hostManager.bindIfAllowed(appWidgetId, info)) {
                val ask = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
                @Suppress("DEPRECATION")
                val allowed = awaitResult { code -> activity.startActivityForResult(ask, code) }
                if (!allowed) return release(appWidgetId)
            }
            if (needsConfiguration && !runConfiguration(appWidgetId)) return release(appWidgetId)
            return appWidgetId
        } catch (e: CancellationException) {
            hostManager.delete(appWidgetId)
            throw e
        } catch (e: RuntimeException) {
            Log.w(TAG, "widget could not be bound", e)
            return release(appWidgetId)
        }
    }

    override suspend fun reconfigure(appWidgetId: Int): Boolean = try {
        runConfiguration(appWidgetId)
    } catch (e: RuntimeException) {
        Log.w(TAG, "widget setup could not be opened", e)
        false
    }

    /** called from the activity; returns true when the result belonged to this flow */
    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        val waiter = pending.remove(requestCode) ?: return false
        waiter.complete(resultCode == Activity.RESULT_OK)
        return true
    }

    private suspend fun runConfiguration(appWidgetId: Int): Boolean = awaitResult { code ->
        hostManager.host.startAppWidgetConfigureActivityForResult(activity, appWidgetId, 0, code, null)
    }

    private suspend fun awaitResult(start: (Int) -> Unit): Boolean {
        // request codes must fit in 16 bits, so they wrap within spark's own range
        val code = nextRequestCode
        nextRequestCode = if (nextRequestCode >= LAST_REQUEST_CODE) FIRST_REQUEST_CODE else nextRequestCode + 1
        val waiter = CompletableDeferred<Boolean>()
        pending[code] = waiter
        try {
            start(code)
        } catch (e: RuntimeException) {
            pending.remove(code)
            throw e
        }
        return waiter.await()
    }

    private fun release(appWidgetId: Int): Int? {
        hostManager.delete(appWidgetId)
        return null
    }

    private companion object {
        const val TAG = "WidgetBindFlow"
        const val FIRST_REQUEST_CODE = 0x5100
        const val LAST_REQUEST_CODE = 0x51FF
    }
}
