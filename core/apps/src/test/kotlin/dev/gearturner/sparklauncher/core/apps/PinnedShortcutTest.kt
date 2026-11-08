package dev.gearturner.sparklauncher.core.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PinnedShortcutTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun shortcut(id: String, pinned: Boolean): ShortcutInfo {
        val info = ShortcutInfo.Builder(context, id)
            .setShortLabel("Real $id")
            .setIntent(Intent(Intent.ACTION_VIEW))
            .build()
        // the pinned flag is only ever set by the system, so the test sets it the same hidden way
        if (pinned) ReflectionHelpers.callInstanceMethod<Unit>(info, "addFlags", ClassParameter.from(Int::class.javaPrimitiveType, FLAG_PINNED))
        return info
    }

    @Test
    fun acceptedRequestIsFoundWithTheSystemLabel() {
        val found = pinnedMatch(listOf(shortcut("other", true), shortcut("site", true)), context.packageName, "site")
        assertEquals("site", found?.id)
        assertEquals("Real site", found?.shortLabel?.toString())
    }

    @Test
    fun shortcutThatWasNeverPinnedIsNotProof() {
        assertNull(pinnedMatch(listOf(shortcut("site", false)), context.packageName, "site"))
    }

    @Test
    fun anotherPackagesShortcutIsNotProof() {
        assertNull(pinnedMatch(listOf(shortcut("site", true)), "com.example.bank", "site"))
    }

    @Test
    fun missingShortcutIsNotProof() {
        assertNull(pinnedMatch(listOf(shortcut("site", true)), context.packageName, "made-up"))
        assertNull(pinnedMatch(emptyList(), context.packageName, "site"))
    }

    private companion object {
        // the pinned bit of shortcutinfo flags, which is hidden from apps
        const val FLAG_PINNED = 1 shl 1
    }
}
