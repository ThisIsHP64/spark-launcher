package dev.gearturner.sparklauncher.core.apps

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * whether spark's accessibility service is switched on. it lives in the app module, so it is named
 * here by class rather than by reference; the package follows the build, the class never changes.
 */
object SparkAccessibility {

    const val SERVICE_CLASS: String = "dev.gearturner.sparklauncher.SparkAccessibilityService"

    fun isEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { info ->
            val service = info.resolveInfo?.serviceInfo ?: return@any false
            service.packageName == context.packageName && service.name == SERVICE_CLASS
        }
    }

    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
