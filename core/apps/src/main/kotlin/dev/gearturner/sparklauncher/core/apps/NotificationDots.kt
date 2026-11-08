package dev.gearturner.sparklauncher.core.apps

import android.app.NotificationChannel
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gearturner.sparklauncher.core.model.AppKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** an app in one profile; notifications belong to a package, not to one of its activities */
data class DotKey(val packageName: String, val userSerial: Long)

val AppKey.dotKey: DotKey get() = DotKey(packageName, userSerial)

/** the parts of a notification the dot rule looks at, pulled out so the rule runs without android */
data class NotificationFacts(
    val canShowBadge: Boolean,
    val isGroupSummary: Boolean,
    val isOngoing: Boolean,
    val channelId: String?,
)

/**
 * the pixel launcher's rule for which notifications earn a dot: the app's channel must allow badges,
 * which also goes false when the system wide dots setting is off; group summaries only repeat
 * their children; and ongoing notifications in the catch all channel are background work, not news.
 */
fun badgeable(facts: NotificationFacts): Boolean =
    facts.canShowBadge &&
        !facts.isGroupSummary &&
        !(facts.isOngoing && facts.channelId == NotificationChannel.DEFAULT_CHANNEL_ID)

/**
 * which apps currently have a notification worth a dot. the listener service feeds it; only the
 * owning app and profile of each notification is kept, never its title, text, or anything else.
 */
@Singleton
class NotificationDots @Inject constructor(@ApplicationContext private val context: Context) {

    // tracked per notification so dismissing one of several leaves the dot in place
    private val byNotification = HashMap<String, DotKey>()
    private val _dots = MutableStateFlow<Set<DotKey>>(emptySet())
    val dots: StateFlow<Set<DotKey>> = _dots.asStateFlow()

    /** everything active right now, as the listener sees it when it connects or rankings change */
    @Synchronized
    fun replaceAll(active: Map<String, DotKey>) {
        byNotification.clear()
        byNotification.putAll(active)
        publish()
    }

    /** [dot] is null when the notification does not earn one, such as after its channel stopped badging */
    @Synchronized
    fun posted(notificationKey: String, dot: DotKey?) {
        if (dot == null) byNotification.remove(notificationKey) else byNotification[notificationKey] = dot
        publish()
    }

    @Synchronized
    fun removed(notificationKey: String) {
        if (byNotification.remove(notificationKey) != null) publish()
    }

    /** the listener lost access, so nothing it reported can be trusted to still be there */
    @Synchronized
    fun clear() = replaceAll(emptyMap())

    private fun publish() {
        _dots.value = byNotification.values.toSet()
    }

    fun isAccessGranted(): Boolean = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    /** the system page for this one listener, where the user grants or revokes access */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context.packageName, LISTENER_CLASS).flattenToString(),
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        /** the service lives in the app module, so it is named here by class; the package follows the build */
        const val LISTENER_CLASS: String = "dev.gearturner.sparklauncher.SparkNotificationListener"
    }
}
