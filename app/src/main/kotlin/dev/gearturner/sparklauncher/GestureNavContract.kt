package dev.gearturner.sparklauncher

import android.content.ComponentName
import android.content.Intent
import android.graphics.RectF
import android.os.Bundle
import android.os.Message
import android.os.RemoteException
import android.os.UserHandle
import android.util.Log
import android.view.SurfaceControl
import androidx.core.os.BundleCompat

/**
 * the swipe home handshake that pixel's recents component offers third party home apps.
 * when the user swipes home from an app, the home intent carries this contract; replying with the
 * app icon's screen rectangle lets the system shrink the window into that icon instead of a
 * generic spot. the extra names are fixed by the system side, matching aosp launcher3.
 *
 * the home activity is exported, so any app in front can send a home intent carrying its own
 * contract and learn whether an app's icon sits on the visible page. android gives no reliable way
 * to tell the system's recents component from another app here, since it does not share its
 * identity with the activity it starts, so the sender is not checked. what such an app could learn
 * is limited to one icon's position, and the floating icon it is handed removes itself within seconds.
 */
class GestureNavContract private constructor(
    val component: ComponentName,
    val user: UserHandle,
    private val replyMessage: Message,
) {

    /**
     * sends where the window should land. with an icon surface the system crossfades the window
     * into it, and messages [onFinished] once the animation ends so the real icon can return.
     * returns false when the system side went away, which only costs this one animation.
     */
    fun sendEndPosition(position: RectF, iconSurface: SurfaceControl?, onFinished: Message?): Boolean {
        val reply = Message.obtain().apply {
            copyFrom(replyMessage)
            data = Bundle().apply {
                putParcelable(EXTRA_ICON_POSITION, position)
                iconSurface?.let { putParcelable(EXTRA_ICON_SURFACE, it) }
                onFinished?.let { putParcelable(EXTRA_ON_FINISH_CALLBACK, it) }
            }
        }
        return try {
            replyMessage.replyTo.send(reply)
            true
        } catch (e: RemoteException) {
            Log.w(TAG, "gesture contract callback died", e)
            false
        }
    }

    companion object {
        private const val TAG = "GestureNavContract"
        private const val EXTRA_GESTURE_CONTRACT = "gesture_nav_contract_v1"
        private const val EXTRA_ICON_POSITION = "gesture_nav_contract_icon_position"
        private const val EXTRA_ICON_SURFACE = "gesture_nav_contract_surface_control"
        private const val EXTRA_ON_FINISH_CALLBACK = "gesture_nav_contract_finish_callback"
        private const val EXTRA_REMOTE_CALLBACK = "android.intent.extra.REMOTE_CALLBACK"

        /**
         * reads and strips the contract so a later configuration change cannot replay it.
         * anything malformed is ignored, since the extras come from outside the app.
         */
        fun consume(intent: Intent?): GestureNavContract? {
            val extras = try {
                intent?.getBundleExtra(EXTRA_GESTURE_CONTRACT)
            } catch (e: RuntimeException) {
                // a bad parcel from another process must not crash the home screen
                Log.w(TAG, "unreadable gesture contract", e)
                null
            } ?: return null
            intent?.removeExtra(EXTRA_GESTURE_CONTRACT)
            // the system only attaches the contract to a home intent, so it is ignored on any other route in
            if (!isHomeIntent(intent?.action, intent?.categories)) return null
            return try {
                val component = BundleCompat.getParcelable(extras, Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
                val user = BundleCompat.getParcelable(extras, Intent.EXTRA_USER, UserHandle::class.java)
                val callback = BundleCompat.getParcelable(extras, EXTRA_REMOTE_CALLBACK, Message::class.java)
                if (component != null && user != null && callback?.replyTo != null) {
                    GestureNavContract(component, user, callback)
                } else {
                    null
                }
            } catch (e: RuntimeException) {
                Log.w(TAG, "malformed gesture contract", e)
                null
            }
        }

        internal fun isHomeIntent(action: String?, categories: Set<String>?): Boolean =
            action == Intent.ACTION_MAIN && categories?.contains(Intent.CATEGORY_HOME) == true
    }
}
