package dev.gearturner.sparklauncher.feature.search

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsSearchTest {

    private val resolved = SettingsSearch.resolve(ApplicationProvider.getApplicationContext<Context>().resources)

    @Test
    fun everyEntryHasATitleAnActionAndKeywords() {
        assertEquals(SettingsSearch.entries.size, resolved.size)
        resolved.forEach { hit ->
            assertTrue(hit.title.isNotBlank())
            assertTrue(hit.action.isNotBlank())
            assertTrue("${hit.title} has no keywords", hit.keywords.isNotEmpty())
        }
        assertEquals("actions repeat", resolved.size, resolved.map { it.action }.toSet().size)
    }

    @Test
    fun keywordsFindScreensWhoseTitleDiffers() {
        assertEquals("Wi-Fi", SettingsSearch.search(resolved, "wifi").first().title)
        assertEquals("Display", SettingsSearch.search(resolved, "brightness").first().title)
        assertEquals("Do Not Disturb", SettingsSearch.search(resolved, "dnd").first().title)
    }

    @Test
    fun resultsAreCapped() {
        assertTrue(SettingsSearch.search(resolved, "s").size <= 4)
    }
}
