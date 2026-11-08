package dev.gearturner.sparklauncher.core.theme

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import dev.gearturner.sparklauncher.core.model.ColorSource
import dev.gearturner.sparklauncher.core.model.LauncherSettings
import dev.gearturner.sparklauncher.core.model.PaletteStyleOption
import dev.gearturner.sparklauncher.core.model.ThemeMode

/** whether the resolved theme is dark, after the user's light, dark or system choice */
val LocalSparkDarkTheme = staticCompositionLocalOf { false }

/**
 * material you colors. wallpaper colors with the tonal spot style use the system's own scheme so
 * spark matches the rest of the phone exactly; every other combination is generated from a seed,
 * either the wallpaper's main color or one the user picked.
 */
@Composable
fun SparkTheme(
    settings: LauncherSettings = LauncherSettings(),
    content: @Composable () -> Unit,
) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val wallpaperSeed = rememberWallpaperSeed()
    val useSystemScheme = settings.colorSource == ColorSource.WALLPAPER && settings.paletteStyle == PaletteStyleOption.TONAL_SPOT
    val seed = when (settings.colorSource) {
        ColorSource.WALLPAPER -> wallpaperSeed ?: Color(settings.seedColor)
        ColorSource.CUSTOM -> Color(settings.seedColor)
    }
    val colors = remember(useSystemScheme, seed, dark, settings.paletteStyle, context) {
        if (useSystemScheme) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            dynamicColorScheme(seedColor = seed, isDark = dark, isAmoled = false, style = settings.paletteStyle.toKolor())
        }
    }
    CompositionLocalProvider(LocalSparkDarkTheme provides dark) {
        // the expressive theme brings the springier motion and rounder shapes current pixel software uses
        MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.expressive(), content = content)
    }
}

/**
 * the plate and glyph colors for themed icons, mirroring the pixel: a light accent plate with a
 * deep glyph in light mode, and a dark neutral plate with a light accent glyph in dark mode
 */
fun ColorScheme.themedIconColors(dark: Boolean): Pair<Int, Int> =
    if (dark) surfaceContainerHighest.toArgb() to primary.toArgb() else primaryContainer.toArgb() to onPrimaryContainer.toArgb()

/** the wallpaper's main color, updated live when the wallpaper changes */
@Composable
private fun rememberWallpaperSeed(): Color? {
    val context = LocalContext.current
    val manager = remember(context) { WallpaperManager.getInstance(context) }
    var colors by remember(manager) { mutableStateOf(readColors(manager)) }
    DisposableEffect(manager) {
        val listener = WallpaperManager.OnColorsChangedListener { changed, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) colors = changed
        }
        manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        onDispose { manager.removeOnColorsChangedListener(listener) }
    }
    return colors?.primaryColor?.toArgb()?.let(::Color)
}

private fun readColors(manager: WallpaperManager): WallpaperColors? = try {
    manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
} catch (e: RuntimeException) {
    // some live wallpapers report nothing; the user's seed color stands in
    null
}

private fun PaletteStyleOption.toKolor(): PaletteStyle = when (this) {
    PaletteStyleOption.TONAL_SPOT -> PaletteStyle.TonalSpot
    PaletteStyleOption.VIBRANT -> PaletteStyle.Vibrant
    PaletteStyleOption.EXPRESSIVE -> PaletteStyle.Expressive
    PaletteStyleOption.FIDELITY -> PaletteStyle.Fidelity
    PaletteStyleOption.MONOCHROME -> PaletteStyle.Monochrome
}
