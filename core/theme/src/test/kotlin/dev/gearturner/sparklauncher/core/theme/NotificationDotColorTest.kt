package dev.gearturner.sparklauncher.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import dev.gearturner.sparklauncher.core.model.PaletteStyleOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDotColorTest {
    private val seed = Color(0xFF3A5BA9)

    private fun scheme(style: PaletteStyle, dark: Boolean): ColorScheme =
        dynamicColorScheme(seedColor = seed, isDark = dark, isAmoled = false, style = style)

    @Test
    fun monochromeAndFidelityLightUseTheGlyphColor() {
        mapOf(
            PaletteStyle.Monochrome to PaletteStyleOption.MONOCHROME,
            PaletteStyle.Fidelity to PaletteStyleOption.FIDELITY,
        ).forEach { (kolor, option) ->
            val colors = scheme(kolor, dark = false)
            assertEquals("$option", colors.onPrimaryContainer, colors.notificationDotColor(option, dark = false))
        }
    }

    @Test
    fun paletteStylesWithDarkGlyphsLightUsePaleTertiaryThatStaysVisibleOnDarkWallpapers() {
        listOf(
            PaletteStyle.Vibrant to PaletteStyleOption.VIBRANT,
            PaletteStyle.Expressive to PaletteStyleOption.EXPRESSIVE,
            PaletteStyle.TonalSpot to PaletteStyleOption.TONAL_SPOT,
        )
            .forEach { (kolor, option) ->
                val colors = scheme(kolor, dark = false)
                val dot = colors.notificationDotColor(option, dark = false)
                assertEquals("$option", colors.tertiaryContainer, dot)
                assertTrue("$option luminance", dot.luminance() > 0.5f)
            }
    }

    @Test
    fun darkModeKeepsPrimaryInEveryStyle() {
        PaletteStyleOption.entries.forEach { option ->
            val dark = scheme(PaletteStyle.Monochrome, dark = true)
            assertEquals("$option", dark.primary, dark.notificationDotColor(option, dark = true))
        }
    }
}
