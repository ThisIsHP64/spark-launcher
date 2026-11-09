package dev.gearturner.sparklauncher.core.ui

import dev.gearturner.sparklauncher.core.model.AppShortcut
import dev.gearturner.sparklauncher.core.model.ShortcutKey
import org.junit.Assert.assertEquals
import org.junit.Test

class SplitShortcutsTest {

    private fun key(id: String) = ShortcutKey("com.example", id, 0)
    private fun shortcut(id: String, enabled: Boolean = true, labelled: Boolean = true) =
        AppShortcut(key(id), id, null, 0, enabled, labelled)

    @Test
    fun pinnedShortcutsAreListedOnceAsPinned() {
        val menu = shortcut("compose")
        val site = shortcut("site")
        val result = splitShortcuts(listOf(menu, site), listOf(site), emptySet())
        assertEquals(listOf(menu), result.appShortcuts)
        assertEquals(listOf(site), result.pinned)
    }

    @Test
    fun pinnedShortcutsAlreadyOnHomeAreHiddenUnlessSelected() {
        val onHome = shortcut("home")
        val spare = shortcut("spare")
        val result = splitShortcuts(emptyList(), listOf(onHome, spare), setOf(onHome.key))
        assertEquals(listOf(spare), result.pinnedFor(null))
        assertEquals(listOf(onHome, spare), result.pinnedFor(onHome.key))
    }

    @Test
    fun unlabelledAndDisabledPinnedShortcutsAreLeftOut() {
        val good = shortcut("good")
        val result = splitShortcuts(
            emptyList(),
            listOf(good, shortcut("blank", labelled = false), shortcut("off", enabled = false)),
            emptySet(),
        )
        assertEquals(listOf(good), result.pinnedFor(null))
    }

    @Test
    fun noPinnedShortcutsLeavesEveryAppShortcut() {
        val all = listOf(shortcut("a"), shortcut("b"))
        val result = splitShortcuts(all, emptyList(), emptySet())
        assertEquals(all, result.appShortcuts)
        assertEquals(emptyList<AppShortcut>(), result.pinned)
    }
}
