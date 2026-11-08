package dev.gearturner.sparklauncher.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class HomeMode {
    /** pixel style: home pages hold favorites and a swipe up opens the drawer */
    DRAWER,
    /** iphone style: every app lives on the home pages */
    HOME_ONLY,
}

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class ColorSource {
    /** material you colors taken from the current wallpaper */
    WALLPAPER,
    /** colors generated from [LauncherSettings.seedColor] */
    CUSTOM,
}

/** the material palette styles the pixel's wallpaper and style picker offers */
@Serializable
enum class PaletteStyleOption { TONAL_SPOT, VIBRANT, EXPRESSIVE, FIDELITY, MONOCHROME }

@Serializable
enum class IconShape {
    CIRCLE,
    SQUIRCLE,
    /** a rounded square whose corners follow [LauncherSettings.iconCornerRadiusPercent] */
    ROUNDED,
    SQUARE,
    TEARDROP,
}

/**
 * every user preference in one immutable value.
 * new fields must have defaults so settings written by older versions still decode.
 */
@Serializable
data class LauncherSettings(
    val homeMode: HomeMode = HomeMode.DRAWER,
    val homeColumns: Int = 4,
    val homeRows: Int = 5,
    val dockSlots: Int = 4,
    val drawerColumns: Int = 4,
    val iconScale: Float = 1f,
    val showHomeLabels: Boolean = true,
    val showDrawerLabels: Boolean = true,
    /** long names wrap onto a second line instead of being cut off */
    val labelLines: Int = 1,
    val iconShape: IconShape = IconShape.CIRCLE,
    /** used by [IconShape.ROUNDED]; 50 is a circle and 0 a square, matching how the slider reads */
    val iconCornerRadiusPercent: Int = 30,
    /** tints icons to the palette with each app's monochrome layer, like android 13 themed icons */
    val themedIcons: Boolean = false,
    /** builds a monochrome version for apps that ship none; off leaves those apps in full color */
    val themedIconFallback: Boolean = true,
    /** package of the chosen icon pack, or null for the apps' own icons */
    val iconPack: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorSource: ColorSource = ColorSource.WALLPAPER,
    /** argb; a calm blue close to the pixel's default accent */
    val seedColor: Int = DEFAULT_SEED_COLOR,
    val paletteStyle: PaletteStyleOption = PaletteStyleOption.TONAL_SPOT,
    /** set once the starter layout is written so clearing the home screen does not bring it back */
    val defaultLayoutApplied: Boolean = false,
    val gestures: GestureSettings = GestureSettings(),
    /** off until the user grants contacts access, so search never reads contacts unasked */
    val searchContacts: Boolean = false,
    val searchShortcuts: Boolean = true,
    val searchSettings: Boolean = true,
) {
    /** clamps values a hand-edited or imported file could push out of the supported range */
    fun sanitized(): LauncherSettings = copy(
        homeColumns = homeColumns.coerceIn(GridLimits.HOME_COLUMNS),
        homeRows = homeRows.coerceIn(GridLimits.HOME_ROWS),
        dockSlots = dockSlots.coerceIn(GridLimits.DOCK_SLOTS),
        drawerColumns = drawerColumns.coerceIn(GridLimits.DRAWER_COLUMNS),
        iconScale = iconScale.coerceIn(GridLimits.ICON_SCALE_MIN, GridLimits.ICON_SCALE_MAX),
        iconCornerRadiusPercent = iconCornerRadiusPercent.coerceIn(0, 50),
        labelLines = labelLines.coerceIn(1, 2),
        // an imported file could carry a transparent seed, which would generate a broken palette
        seedColor = seedColor or OPAQUE,
        iconPack = iconPack?.takeIf { it.isNotBlank() && it.length <= MAX_PACKAGE_LENGTH },
        gestures = gestures.sanitized(),
    )

    /** the corner percent the current shape implies, for plates and placeholders drawn behind icons */
    val effectiveCornerPercent: Int
        get() = when (iconShape) {
            IconShape.CIRCLE -> 50
            IconShape.SQUARE -> 0
            IconShape.SQUIRCLE -> 40
            IconShape.TEARDROP -> 35
            IconShape.ROUNDED -> iconCornerRadiusPercent
        }

    companion object {
        const val DEFAULT_SEED_COLOR: Int = 0xFF4A6FA5.toInt()
        private const val OPAQUE: Int = 0xFF000000.toInt()
        // android package names are capped well below this; anything longer is not a real package
        private const val MAX_PACKAGE_LENGTH = 255
    }
}

object GridLimits {
    val HOME_COLUMNS = 3..8
    val HOME_ROWS = 3..9
    val DOCK_SLOTS = 0..7
    val DRAWER_COLUMNS = 3..7
    const val ICON_SCALE_MIN = 0.7f
    const val ICON_SCALE_MAX = 1.3f
}
