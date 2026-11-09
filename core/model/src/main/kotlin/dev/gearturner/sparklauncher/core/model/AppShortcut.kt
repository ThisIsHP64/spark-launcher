package dev.gearturner.sparklauncher.core.model

import kotlinx.serialization.Serializable

/** identifies one app shortcut, which is only unique within its package and profile */
@Serializable
data class ShortcutKey(
    val packageName: String,
    val shortcutId: String,
    val userSerial: Long,
)

data class AppShortcut(
    val key: ShortcutKey,
    val shortLabel: String,
    val longLabel: String?,
    val rank: Int,
    /** disabled shortcuts stay pinned but must not launch, matching how the pixel launcher greys them */
    val isEnabled: Boolean,
    /** false when the system could not resolve any label, so [shortLabel] is only the raw id */
    val hasLabel: Boolean = true,
) {
    /** the long label fits a menu row, while home icons use the short one */
    val menuLabel: String get() = longLabel?.takeIf { it.isNotBlank() } ?: shortLabel
}

/** what an icon is drawn from, so apps and shortcuts share one cache and one composable */
sealed interface IconSource {
    data class App(val key: AppKey) : IconSource
    data class Shortcut(val key: ShortcutKey, val owner: AppKey?) : IconSource
}

/** one drawable from an icon pack, chosen by the user for a single app */
data class CustomIcon(val packPackage: String, val drawableName: String)

/** per app choices that apply everywhere the app appears, unlike a single home item's title */
data class AppOverride(
    val key: AppKey,
    val hidden: Boolean = false,
    val customLabel: String? = null,
    val customIcon: CustomIcon? = null,
    val swipeUp: SwipeTarget? = null,
    val swipeDown: SwipeTarget? = null,
    val homeRemoved: Boolean = false,
) {
    fun swipeFor(direction: SwipeDirection): SwipeTarget? = when (direction) {
        SwipeDirection.UP -> swipeUp
        SwipeDirection.DOWN -> swipeDown
    }
}
