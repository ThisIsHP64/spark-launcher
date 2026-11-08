package dev.gearturner.sparklauncher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

/**
 * exists only to run system actions android reserves for accessibility services: locking the
 * screen, showing recents, and opening the shade when the direct route is blocked. its config asks
 * for no events and no window content, so it cannot see anything on screen.
 */
class SparkAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        current = WeakReference(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (current?.get() === this) current = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        // weak so a service the user switches off is never kept alive by the launcher
        @Volatile
        private var current: WeakReference<SparkAccessibilityService>? = null

        /** false when the service is off or the system refused the action */
        fun perform(globalAction: Int): Boolean = current?.get()?.performGlobalAction(globalAction) == true
    }
}
