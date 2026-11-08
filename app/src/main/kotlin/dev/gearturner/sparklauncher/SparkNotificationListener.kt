package dev.gearturner.sparklauncher

import android.app.Notification
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import dev.gearturner.sparklauncher.core.apps.DotKey
import dev.gearturner.sparklauncher.core.apps.NotificationDots
import dev.gearturner.sparklauncher.core.apps.NotificationFacts
import dev.gearturner.sparklauncher.core.apps.badgeable
import javax.inject.Inject

/**
 * tells the launcher which apps have notifications so their icons can show a dot. it reads only
 * each notification's flags, channel and owner; titles, text and extras are never looked at or kept.
 */
@AndroidEntryPoint
class SparkNotificationListener : NotificationListenerService() {

    @Inject
    lateinit var dots: NotificationDots

    private val userManager by lazy { getSystemService(UserManager::class.java) }

    override fun onListenerConnected() = refreshAll()

    override fun onListenerDisconnected() = dots.clear()

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        dots.posted(sbn.key, dotFor(sbn, rankingMap))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) {
        dots.removed(sbn.key)
    }

    // a channel's badge setting or the system wide dots switch can change without any new notification
    override fun onNotificationRankingUpdate(rankingMap: RankingMap) = refreshAll()

    private fun refreshAll() {
        val ranking = currentRanking ?: return
        val active = try {
            activeNotifications.orEmpty()
        } catch (e: SecurityException) {
            // access was revoked between the callback and this call
            Log.w(TAG, "notification access lost", e)
            dots.clear()
            return
        }
        dots.replaceAll(active.mapNotNull { sbn -> dotFor(sbn, ranking)?.let { sbn.key to it } }.toMap())
    }

    private fun dotFor(sbn: StatusBarNotification, rankingMap: RankingMap): DotKey? {
        val ranking = Ranking()
        val canShowBadge = rankingMap.getRanking(sbn.key, ranking) && ranking.canShowBadge()
        val flags = sbn.notification.flags
        val facts = NotificationFacts(
            canShowBadge = canShowBadge,
            isGroupSummary = flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isOngoing = flags and Notification.FLAG_ONGOING_EVENT != 0,
            channelId = sbn.notification.channelId,
        )
        if (!badgeable(facts)) return null
        val serial = userManager?.getSerialNumberForUser(sbn.user) ?: return null
        return DotKey(sbn.packageName, serial)
    }

    private companion object {
        const val TAG = "SparkNotifications"
    }
}
