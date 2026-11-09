package dev.gearturner.sparklauncher.feature.settings

import dev.gearturner.sparklauncher.core.model.AppInfo
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.AppOverride
import dev.gearturner.sparklauncher.core.model.AppShortcut
import dev.gearturner.sparklauncher.core.model.ShortcutKey
import dev.gearturner.sparklauncher.core.model.SwipeDirection
import dev.gearturner.sparklauncher.core.model.SwipeTarget
import java.text.Collator
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedShortcutsTest {

    private val collator = Collator.getInstance(Locale.ENGLISH)
    private fun app(name: String) = AppInfo(AppKey("com.$name", "Main", 0), name, false, 0)
    private fun key(pkg: String, id: String) = ShortcutKey("com.$pkg", id, 0)
    private fun shortcut(pkg: String, id: String, label: String = id) = AppShortcut(key(pkg, id), label, null, 0, true)
    private fun pinned(vararg items: AppShortcut) = items.associateBy { it.key }

    @Test
    fun unusedShortcutsAreStillListed() {
        val site = shortcut("chrome", "site", "News")
        val result = pinnedShortcutsOf(pinned(site), emptySet(), emptyMap(), listOf(app("chrome")), collator)
        val entry = result.single()
        assertFalse(entry.inUse)
        assertEquals("chrome", entry.appLabel)
    }

    @Test
    fun shortcutsOfHiddenAppsAreLeftOut() {
        val secret = shortcut("bank", "pay")
        val open = shortcut("chrome", "site")
        val bank = app("bank")
        val overrides = mapOf(bank.key to AppOverride(bank.key, hidden = true))
        val result = pinnedShortcutsOf(pinned(secret, open), emptySet(), overrides, listOf(bank, app("chrome")), collator)
        assertEquals(listOf("site"), result.map { it.shortcut.key.shortcutId })
    }

    @Test
    fun swipesOwnedByHiddenAppsAreNotNamed() {
        val site = shortcut("chrome", "site")
        val bank = app("bank")
        val overrides = mapOf(bank.key to AppOverride(bank.key, hidden = true, swipeUp = SwipeTarget.Shortcut(site.key)))
        val result = pinnedShortcutsOf(pinned(site), emptySet(), overrides, listOf(bank, app("chrome")), collator)
        assertTrue(result.single().swipeUses.isEmpty())
    }

    @Test
    fun homeTilesMarkTheShortcutAsOnHome() {
        val site = shortcut("chrome", "site")
        val result = pinnedShortcutsOf(pinned(site), setOf(site.key), emptyMap(), listOf(app("chrome")), collator)
        assertTrue(result.single().onHome)
        assertTrue(result.single().inUse)
    }

    @Test
    fun swipeUsesNameTheIconAndDirection() {
        val site = shortcut("chrome", "site")
        val phone = app("phone")
        val camera = app("camera")
        val overrides = mapOf(
            phone.key to AppOverride(phone.key, swipeDown = SwipeTarget.Shortcut(site.key)),
            camera.key to AppOverride(camera.key, swipeUp = SwipeTarget.Shortcut(site.key), customLabel = "Photos"),
        )
        val result = pinnedShortcutsOf(pinned(site), emptySet(), overrides, listOf(app("chrome"), phone, camera), collator)
        assertEquals(
            listOf(SwipeUse("Photos", SwipeDirection.UP), SwipeUse("phone", SwipeDirection.DOWN)),
            result.single().swipeUses.sortedBy { it.direction },
        )
        assertTrue(result.single().inUse)
    }

    @Test
    fun appTargetsAndOtherShortcutsAreNotCountedAsUses() {
        val site = shortcut("chrome", "site")
        val phone = app("phone")
        val overrides = mapOf(
            phone.key to AppOverride(
                phone.key,
                swipeUp = SwipeTarget.App(AppKey("com.chrome", "Main", 0)),
                swipeDown = SwipeTarget.Shortcut(key("chrome", "other")),
            ),
        )
        val result = pinnedShortcutsOf(pinned(site), emptySet(), overrides, listOf(app("chrome"), phone), collator)
        assertTrue(result.single().swipeUses.isEmpty())
    }

    @Test
    fun entriesAreSortedByAppThenName() {
        val result = pinnedShortcutsOf(
            pinned(shortcut("b", "z", "Zed"), shortcut("a", "y", "Beta"), shortcut("a", "x", "Alpha")),
            emptySet(),
            emptyMap(),
            listOf(app("a"), app("b")),
            collator,
        )
        assertEquals(listOf("Alpha", "Beta", "Zed"), result.map { it.shortcut.menuLabel })
    }

    @Test
    fun missingOwnerFallsBackToThePackageName() {
        val result = pinnedShortcutsOf(pinned(shortcut("gone", "id")), emptySet(), emptyMap(), emptyList(), collator)
        assertEquals("com.gone", result.single().appLabel)
    }
}
