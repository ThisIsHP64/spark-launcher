package dev.gearturner.sparklauncher.core.model

/**
 * everything that changes how an icon is drawn. it is part of each icon's cache key, so changing
 * any setting here redraws icons instead of serving stale bitmaps.
 */
data class IconStyle(
    val shape: IconShape = IconShape.CIRCLE,
    val cornerPercent: Int = 50,
    val themed: Boolean = false,
    val themedFallback: Boolean = true,
    /** argb colors for themed icons, taken from the active palette */
    val themedBackground: Int = 0,
    val themedForeground: Int = 0,
    val iconPack: String? = null,
) {
    companion object {
        fun from(settings: LauncherSettings, themedBackground: Int, themedForeground: Int) = IconStyle(
            shape = settings.iconShape,
            cornerPercent = settings.effectiveCornerPercent,
            themed = settings.themedIcons,
            themedFallback = settings.themedIconFallback,
            // unthemed icons ignore these colors, so leaving them out keeps the cache warm across theme switches
            themedBackground = if (settings.themedIcons) themedBackground else 0,
            themedForeground = if (settings.themedIcons) themedForeground else 0,
            iconPack = settings.iconPack,
        )
    }
}
