package dev.gearturner.sparklauncher.feature.settings

import dev.gearturner.sparklauncher.core.model.AppInfo
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.AppOverride
import java.text.Collator
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenAppsTest {

    private val collator = Collator.getInstance(Locale.US)

    private fun app(name: String) = AppInfo(AppKey("com.example.$name", "Main", 0), name, isManagedProfile = false, firstInstallTime = 0)

    private fun hidden(app: AppInfo, label: String? = null) = app.key to AppOverride(app.key, hidden = true, customLabel = label)

    @Test
    fun onlyHiddenAppsAreListed() {
        val bank = app("Bank")
        val camera = app("Camera")
        val result = hiddenAppsOf(listOf(bank, camera), mapOf(hidden(bank)), collator)
        assertEquals(listOf(HiddenApp(bank.key, "Bank")), result)
    }

    @Test
    fun aRenamedAppIsListedByItsNewNameAndSortedByIt() {
        val bank = app("Bank")
        val zoo = app("Zoo")
        val result = hiddenAppsOf(listOf(bank, zoo), mapOf(hidden(bank, label = "Wallet"), hidden(zoo, label = "Animals")), collator)
        assertEquals(listOf("Animals", "Wallet"), result.map { it.label })
    }

    @Test
    fun overridesThatOnlyRenameDoNotCountAsHidden() {
        val bank = app("Bank")
        val renamedOnly = bank.key to AppOverride(bank.key, hidden = false, customLabel = "Wallet")
        assertTrue(hiddenAppsOf(listOf(bank), mapOf(renamedOnly), collator).isEmpty())
    }

    @Test
    fun anUninstalledAppThatWasHiddenIsLeftOut() {
        val bank = app("Bank")
        val gone = app("Gone")
        val result = hiddenAppsOf(listOf(bank), mapOf(hidden(bank), hidden(gone)), collator)
        assertEquals(listOf(bank.key), result.map { it.key })
    }

    @Test
    fun sortingIgnoresCaseLikeTheDrawer() {
        val apple = app("apple")
        val banana = app("Banana")
        val result = hiddenAppsOf(listOf(banana, apple), mapOf(hidden(apple), hidden(banana)), collator)
        assertEquals(listOf("apple", "Banana"), result.map { it.label })
    }
}
