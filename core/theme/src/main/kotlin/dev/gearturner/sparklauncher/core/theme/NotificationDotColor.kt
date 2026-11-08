package dev.gearturner.sparklauncher.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.gearturner.sparklauncher.core.model.PaletteStyleOption

/**
 * the dot color for icons with no color of their own, such as themed icons and folders. in light
 * mode the primary role is the glyph color and nearly black in monochrome, which made the dot vanish.
 */
fun ColorScheme.notificationDotColor(style: PaletteStyleOption, dark: Boolean): Color = when {
    // dark plates are neutral and primary is a light tone there, so it already stands out
    dark -> primary
    // the glyph color is a dark tone in these styles and vanishes on dark wallpapers; a pale tertiary
    // dot stays visible there, and the knockout gap separates it from the pale plate
    style == PaletteStyleOption.VIBRANT || style == PaletteStyleOption.EXPRESSIVE || style == PaletteStyleOption.TONAL_SPOT ->
        tertiaryContainer
    // fidelity and monochrome take the glyph color so the dot reads as part of the icon
    else -> onPrimaryContainer
}

/** the resolved fallback dot color, provided by [SparkTheme] */
val LocalSparkNotificationDotColor = staticCompositionLocalOf { Color(0xFFB3261E) }
