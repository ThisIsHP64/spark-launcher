package dev.gearturner.sparklauncher.core.model

import kotlinx.serialization.Serializable

/** the two directions an icon can be swiped in; sideways belongs to the pager */
enum class SwipeDirection { UP, DOWN }

/** what a swipe on an app icon opens: another app, or one of an app's shortcuts */
@Serializable
sealed interface SwipeTarget {
    @Serializable
    data class App(val key: AppKey) : SwipeTarget

    @Serializable
    data class Shortcut(val key: ShortcutKey) : SwipeTarget

    /** the package the target belongs to, which decides what to pin for a shortcut */
    val packageName: String
        get() = when (this) {
            is App -> key.packageName
            is Shortcut -> key.packageName
        }

    /**
     * the stored form comes from disk and the add shortcut screen, which any app can reach, so a target
     * that could not be a real package, activity or shortcut id is refused instead of kept
     */
    fun sanitized(): SwipeTarget? = when (this) {
        is App -> takeIf { isPackage(key.packageName) && isClass(key.className) }
        is Shortcut -> takeIf { isPackage(key.packageName) && key.shortcutId.isNotBlank() && key.shortcutId.length <= MAX_SHORTCUT_ID }
    }

    companion object {
        private const val MAX_PACKAGE = 255
        private const val MAX_CLASS = 512
        private const val MAX_SHORTCUT_ID = 512
        private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
        private val CLASS = Regex("[A-Za-z0-9_.$]+")

        private fun isPackage(name: String) = name.length <= MAX_PACKAGE && PACKAGE.matches(name)
        private fun isClass(name: String) = name.length <= MAX_CLASS && CLASS.matches(name)
    }
}
