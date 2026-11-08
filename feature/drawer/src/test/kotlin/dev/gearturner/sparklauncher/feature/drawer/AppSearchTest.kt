package dev.gearturner.sparklauncher.feature.drawer

import dev.gearturner.sparklauncher.core.model.AppInfo
import dev.gearturner.sparklauncher.core.model.AppKey
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSearchTest {

    private fun app(label: String, pkg: String = "com.example.${label.lowercase().filter(Char::isLetter)}") =
        AppInfo(AppKey(pkg, "$pkg.Main", 0), label, isManagedProfile = false, firstInstallTime = 0)

    @Test
    fun sortingIsCaseAndAccentInsensitive() {
        val sorted = sortApps(listOf(app("zoom"), app("Éclair"), app("apple"), app("Banana")), Locale.US)
        assertEquals(listOf("apple", "Banana", "Éclair", "zoom"), sorted.map { it.label })
    }

    @Test
    fun prefixMatchesRankAboveWordAndSubstringMatches() {
        val apps = sortApps(listOf(app("Google Maps"), app("Mapper"), app("Roadmap"), app("Camera")), Locale.US)
        assertEquals(listOf("Mapper", "Google Maps", "Roadmap"), filterApps(apps, "map").map { it.label })
    }

    @Test
    fun accentsInLabelsMatchPlainQueries() {
        assertEquals(listOf("Café"), filterApps(listOf(app("Café")), "cafe").map { it.label })
    }

    @Test
    fun packageNameMatchesAreIncluded() {
        val apps = listOf(app("Messages", pkg = "com.google.android.apps.messaging"))
        assertEquals(1, filterApps(apps, "messaging").size)
    }

    @Test
    fun blankQueryReturnsEverything() {
        val apps = listOf(app("A"), app("B"))
        assertEquals(apps, filterApps(apps, "   "))
    }

    @Test
    fun hiddenAppsOnlyAppearInSearch() {
        val entries = listOf(
            DrawerEntry(app("Bank"), hidden = true, renamed = false),
            DrawerEntry(app("Browser"), hidden = false, renamed = false),
        )
        assertEquals(listOf("Browser"), visibleEntries(entries, "").map { it.app.label })
        assertEquals(listOf("Bank"), visibleEntries(entries, "ban").map { it.app.label })
    }

    @Test
    fun exactNameRanksAboveLongerPrefixMatches() {
        val apps = sortApps(listOf(app("Photo Editor"), app("Photos"), app("Photo")), Locale.US)
        assertEquals("Photo", filterApps(apps, "photo").first().label)
    }
}
