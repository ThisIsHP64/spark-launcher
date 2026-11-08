package dev.gearturner.sparklauncher.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class IconPickerTest {

    private val names = listOf("calculator", "google_maps", "maps_alt", "camera", "mail")

    @Test
    fun namesSharingAWordWithTheAppComeFirst() {
        assertEquals(listOf("google_maps", "maps_alt", "calculator", "camera", "mail"), rankIconNames(names, "Google Maps", ""))
    }

    @Test
    fun searchFiltersAndTreatsSpacesAsUnderscores() {
        assertEquals(listOf("google_maps"), rankIconNames(names, "Maps", "google m"))
    }

    @Test
    fun shortLabelWordsDoNotReorder() {
        assertEquals(names, rankIconNames(names, "X", ""))
    }
}
