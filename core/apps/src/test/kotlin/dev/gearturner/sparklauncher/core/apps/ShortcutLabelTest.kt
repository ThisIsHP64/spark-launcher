package dev.gearturner.sparklauncher.core.apps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutLabelTest {

    @Test
    fun shortOrLongLabelIsEnough() {
        assertTrue(hasUsableLabel("Add contact", null))
        assertTrue(hasUsableLabel(null, "New contact"))
        assertTrue(hasUsableLabel("", "New contact"))
    }

    @Test
    fun noLabelAtAllIsNotUsable() {
        assertFalse(hasUsableLabel(null, null))
        assertFalse(hasUsableLabel("", "  "))
    }
}
