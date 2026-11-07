package dev.gearturner.sparklauncher.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class HomeMode {
    /** pixel style: home pages hold favorites and a swipe up opens the drawer */
    DRAWER,
    /** iphone style: every app lives on the home pages */
    HOME_ONLY,
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
    /** 50 is a circle, 0 is a square, matching how the corner slider will read */
    val iconCornerRadiusPercent: Int = 50,
    /** set once the starter layout is written so clearing the home screen does not bring it back */
    val defaultLayoutApplied: Boolean = false,
) {
    /** clamps values a hand-edited or imported file could push out of the supported range */
    fun sanitized(): LauncherSettings = copy(
        homeColumns = homeColumns.coerceIn(GridLimits.HOME_COLUMNS),
        homeRows = homeRows.coerceIn(GridLimits.HOME_ROWS),
        dockSlots = dockSlots.coerceIn(GridLimits.DOCK_SLOTS),
        drawerColumns = drawerColumns.coerceIn(GridLimits.DRAWER_COLUMNS),
        iconScale = iconScale.coerceIn(GridLimits.ICON_SCALE_MIN, GridLimits.ICON_SCALE_MAX),
        iconCornerRadiusPercent = iconCornerRadiusPercent.coerceIn(0, 50),
    )
}

object GridLimits {
    val HOME_COLUMNS = 3..8
    val HOME_ROWS = 3..9
    val DOCK_SLOTS = 0..7
    val DRAWER_COLUMNS = 3..7
    const val ICON_SCALE_MIN = 0.7f
    const val ICON_SCALE_MAX = 1.3f
}
