package dev.gearturner.sparklauncher

import org.junit.Assert.fail
import org.junit.Test

/**
 * hilt only creates view models whose generated modules exist; a missing one compiles fine but
 * crashes the launcher on start. this fails the build instead if code generation ever skips one.
 */
class HiltViewModelsTest {

    private val viewModels = listOf(
        "dev.gearturner.sparklauncher.feature.workspace.HomeViewModel",
        "dev.gearturner.sparklauncher.feature.drawer.DrawerViewModel",
        "dev.gearturner.sparklauncher.feature.widgets.WidgetPickerViewModel",
        "dev.gearturner.sparklauncher.feature.settings.SettingsViewModel",
    )

    @Test
    fun everyViewModelHasGeneratedHiltModules() {
        val missing = viewModels.filter { name ->
            listOf("_HiltModules\$KeyModule", "_HiltModules\$BindsModule").any { suffix ->
                runCatching { Class.forName(name + suffix) }.isFailure
            }
        }
        if (missing.isNotEmpty()) fail("hilt modules were not generated for: $missing")
    }
}
