package dev.gearturner.sparklauncher

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.util.Log
import android.widget.Toast
import dev.gearturner.sparklauncher.core.apps.AppsRepository
import dev.gearturner.sparklauncher.core.apps.LaunchRequest
import dev.gearturner.sparklauncher.core.apps.LaunchResult
import dev.gearturner.sparklauncher.core.apps.SparkAccessibility
import dev.gearturner.sparklauncher.core.model.GestureAction
import dev.gearturner.sparklauncher.core.model.GestureBinding
import dev.gearturner.sparklauncher.core.ui.GestureActionHandler
import java.lang.reflect.InvocationTargetException

/** runs the gesture actions that reach outside spark's own screens */
class SystemActions(
    private val context: Context,
    private val apps: AppsRepository,
    private val openSettings: () -> Unit,
) : GestureActionHandler {

    override fun perform(binding: GestureBinding) {
        when (binding.action) {
            GestureAction.NOTIFICATIONS -> expandPanel(STATUS_BAR_NOTIFICATIONS, AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            GestureAction.QUICK_SETTINGS -> expandPanel(STATUS_BAR_QUICK_SETTINGS, AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            GestureAction.LOCK_SCREEN -> viaService(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            GestureAction.RECENTS -> viaService(AccessibilityService.GLOBAL_ACTION_RECENTS)
            GestureAction.OPEN_APP -> binding.app?.let { key ->
                if (apps.launch(key, LaunchRequest()) is LaunchResult.Failed) toast(R.string.gesture_app_missing)
            }
            GestureAction.SPARK_SETTINGS -> openSettings()
            // home opens the drawer and search itself, and none does nothing by design
            GestureAction.NONE, GestureAction.OPEN_DRAWER, GestureAction.OPEN_SEARCH -> Unit
        }
    }

    /**
     * the status bar service is what nova and lawnchair call. it is not public api, so every failure
     * falls through to the accessibility service, which can do the same thing when switched on.
     */
    @SuppressLint("WrongConstant")
    private fun expandPanel(method: String, fallback: Int) {
        val expanded = try {
            val statusBar = context.getSystemService(STATUS_BAR_SERVICE) ?: error("no status bar service")
            statusBar.javaClass.getMethod(method).invoke(statusBar)
            true
        } catch (e: InvocationTargetException) {
            Log.w(TAG, "status bar $method refused", e)
            false
        } catch (e: ReflectiveOperationException) {
            Log.w(TAG, "status bar $method unavailable", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "status bar $method not permitted", e)
            false
        } catch (e: IllegalStateException) {
            false
        }
        if (!expanded) viaService(fallback)
    }

    private fun viaService(globalAction: Int) {
        if (SparkAccessibilityService.perform(globalAction)) return
        if (SparkAccessibility.isEnabled(context)) {
            // switched on but not yet bound, which settles within a moment of enabling it
            toast(R.string.gesture_service_starting)
            return
        }
        toast(R.string.gesture_needs_service)
        try {
            context.startActivity(SparkAccessibility.settingsIntent())
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no accessibility settings screen", e)
        }
    }

    private fun toast(message: Int) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val TAG = "SystemActions"
        // the hidden Context.STATUS_BAR_SERVICE name and its two panel methods
        const val STATUS_BAR_SERVICE = "statusbar"
        const val STATUS_BAR_NOTIFICATIONS = "expandNotificationsPanel"
        const val STATUS_BAR_QUICK_SETTINGS = "expandSettingsPanel"
    }
}
