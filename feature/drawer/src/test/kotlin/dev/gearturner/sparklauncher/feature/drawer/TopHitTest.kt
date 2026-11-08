package dev.gearturner.sparklauncher.feature.drawer

import dev.gearturner.sparklauncher.core.model.AppInfo
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.AppShortcut
import dev.gearturner.sparklauncher.core.model.ShortcutKey
import dev.gearturner.sparklauncher.feature.search.SearchResults
import dev.gearturner.sparklauncher.feature.search.SettingHit
import dev.gearturner.sparklauncher.feature.search.ShortcutHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopHitTest {

    private val entry = DrawerEntry(AppInfo(AppKey("com.a", "Main", 0), "Alpha", false, 0), hidden = false, renamed = false)
    private val shortcut = ShortcutHit(AppShortcut(ShortcutKey("com.b", "home", 0), "Home", null, 0, true), null, "Maps")
    private val setting = SettingHit("android.settings.WIFI_SETTINGS", "Wi-Fi", listOf("wifi"))

    @Test
    fun anAppAlwaysWins() {
        val results = SearchResults(query = "a", shortcuts = listOf(shortcut), settings = listOf(setting))
        assertEquals(TopHit.App(entry), topHitOf(listOf(entry), results))
    }

    @Test
    fun withoutAppsTheFirstSectionShownWins() {
        val results = SearchResults(query = "h", shortcuts = listOf(shortcut), settings = listOf(setting))
        assertEquals(TopHit.Shortcut(shortcut), topHitOf(emptyList(), results))
        assertEquals(TopHit.Setting(setting), topHitOf(emptyList(), results.copy(shortcuts = emptyList())))
    }

    @Test
    fun aCalculationAloneIsNotOpenedByEnter() {
        assertNull(topHitOf(emptyList(), SearchResults(query = "1+1", calculation = "2")))
    }

    @Test
    fun blankQueryHasNoTopHit() {
        assertNull(DrawerUiState(query = " ", entries = listOf(entry)).topHit)
    }
}
