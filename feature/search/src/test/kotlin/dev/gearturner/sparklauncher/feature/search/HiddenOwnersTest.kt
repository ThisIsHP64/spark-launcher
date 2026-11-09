package dev.gearturner.sparklauncher.feature.search

import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.AppOverride
import dev.gearturner.sparklauncher.core.model.AppShortcut
import dev.gearturner.sparklauncher.core.model.ShortcutKey
import org.junit.Assert.assertEquals
import org.junit.Test

class HiddenOwnersTest {

    private fun shortcut(pkg: String, id: String, user: Long = 0) =
        AppShortcut(ShortcutKey(pkg, id, user), id, null, rank = 0, isEnabled = true)

    private fun override(pkg: String, cls: String, hidden: Boolean, user: Long = 0) =
        AppKey(pkg, cls, user).let { it to AppOverride(it, hidden = hidden) }

    @Test
    fun onlyHiddenOverridesCount() {
        val overrides = mapOf(override("com.bank", "A", hidden = true), override("com.mail", "A", hidden = false))
        assertEquals(setOf("com.bank" to 0L), hiddenOwners(overrides))
    }

    @Test
    fun hidingOneActivityHidesThePackagesShortcuts() {
        val overrides = mapOf(override("com.bank", "A", hidden = true), override("com.bank", "B", hidden = false))
        val shortcuts = listOf(shortcut("com.bank", "pay"), shortcut("com.mail", "compose"))
        val shown = withoutHiddenOwners(shortcuts, hiddenOwners(overrides))
        assertEquals(listOf("compose"), shown.map { it.key.shortcutId })
    }

    @Test
    fun theSamePackageInAnotherProfileIsUnaffected() {
        val hidden = hiddenOwners(mapOf(override("com.bank", "A", hidden = true, user = 0)))
        val shortcuts = listOf(shortcut("com.bank", "pay", user = 0), shortcut("com.bank", "pay", user = 10))
        assertEquals(listOf(10L), withoutHiddenOwners(shortcuts, hidden).map { it.key.userSerial })
    }

    @Test
    fun nothingHiddenLeavesTheListAsIs() {
        val shortcuts = listOf(shortcut("com.bank", "pay"))
        assertEquals(shortcuts, withoutHiddenOwners(shortcuts, emptySet()))
    }
}
