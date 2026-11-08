package dev.gearturner.sparklauncher.core.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationDotsTest {

    private val normal = NotificationFacts(canShowBadge = true, isGroupSummary = false, isOngoing = false, channelId = "messages")
    private val mail = DotKey("com.example.mail", 0)
    private val chat = DotKey("com.example.chat", 0)
    private val workMail = DotKey("com.example.mail", 10)

    private fun dots() = NotificationDots(RuntimeEnvironment.getApplication())

    @Test
    fun anOrdinaryNotificationEarnsADot() {
        assertTrue(badgeable(normal))
        // ongoing work in an app's own channel, like a running timer, still counts
        assertTrue(badgeable(normal.copy(isOngoing = true)))
    }

    @Test
    fun channelsWithBadgesOffGroupSummariesAndBackgroundWorkDoNot() {
        assertFalse(badgeable(normal.copy(canShowBadge = false)))
        assertFalse(badgeable(normal.copy(isGroupSummary = true)))
        assertFalse(badgeable(normal.copy(isOngoing = true, channelId = "miscellaneous")))
    }

    @Test
    fun aDotStaysUntilTheAppsLastNotificationIsGone() {
        val store = dots()
        store.posted("a", mail)
        store.posted("b", mail)
        store.posted("c", chat)
        assertEquals(setOf(mail, chat), store.dots.value)
        store.removed("a")
        assertEquals(setOf(mail, chat), store.dots.value)
        store.removed("b")
        assertEquals(setOf(chat), store.dots.value)
    }

    @Test
    fun aNotificationThatStopsQualifyingLosesItsDot() {
        val store = dots()
        store.posted("a", mail)
        store.posted("a", null)
        assertTrue(store.dots.value.isEmpty())
    }

    @Test
    fun profilesAreKeptApart() {
        val store = dots()
        store.posted("a", workMail)
        assertEquals(setOf(workMail), store.dots.value)
        assertFalse(mail in store.dots.value)
    }

    @Test
    fun reconnectingReplacesEverythingAndLosingAccessClearsIt() {
        val store = dots()
        store.posted("old", chat)
        store.replaceAll(mapOf("x" to mail))
        assertEquals(setOf(mail), store.dots.value)
        store.clear()
        assertTrue(store.dots.value.isEmpty())
    }
}
