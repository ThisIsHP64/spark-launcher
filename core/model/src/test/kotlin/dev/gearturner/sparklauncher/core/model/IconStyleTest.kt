package dev.gearturner.sparklauncher.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class IconStyleTest {

    @Test
    fun unthemedStylesIgnorePaletteColorsSoTheCacheSurvivesThemeChanges() {
        val settings = LauncherSettings(themedIcons = false)
        assertEquals(IconStyle.from(settings, 1, 2), IconStyle.from(settings, 3, 4))
    }

    @Test
    fun themedStylesCarryPaletteColors() {
        val style = IconStyle.from(LauncherSettings(themedIcons = true), 0x111111, 0x222222)
        assertEquals(0x111111, style.themedBackground)
        assertEquals(0x222222, style.themedForeground)
    }

    @Test
    fun shapesMapToTheCornerPercentUsedForPlates() {
        assertEquals(50, IconStyle.from(LauncherSettings(iconShape = IconShape.CIRCLE), 0, 0).cornerPercent)
        assertEquals(0, IconStyle.from(LauncherSettings(iconShape = IconShape.SQUARE), 0, 0).cornerPercent)
        assertEquals(
            12,
            IconStyle.from(LauncherSettings(iconShape = IconShape.ROUNDED, iconCornerRadiusPercent = 12), 0, 0).cornerPercent,
        )
    }
}
