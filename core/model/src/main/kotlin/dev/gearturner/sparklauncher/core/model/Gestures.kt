package dev.gearturner.sparklauncher.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class GestureAction {
    NONE,
    OPEN_DRAWER,
    OPEN_SEARCH,
    NOTIFICATIONS,
    QUICK_SETTINGS,
    LOCK_SCREEN,
    RECENTS,
    OPEN_APP,
    SPARK_SETTINGS,
    ;

    /** android only lets an accessibility service lock the screen or show recents for another app */
    val needsAccessibilityService: Boolean
        get() = this == LOCK_SCREEN || this == RECENTS
}

/** what one gesture does; [app] is only meaningful for [GestureAction.OPEN_APP] */
@Serializable
data class GestureBinding(
    val action: GestureAction = GestureAction.NONE,
    val app: AppKey? = null,
) {
    fun sanitized(): GestureBinding = when {
        // an app binding with no app would do nothing while looking set, so it is cleared instead
        action == GestureAction.OPEN_APP && app == null -> GestureBinding()
        action != GestureAction.OPEN_APP && app != null -> copy(app = null)
        else -> this
    }
}

@Serializable
enum class Gesture {
    SWIPE_UP,
    SWIPE_DOWN,
    TWO_FINGER_SWIPE_UP,
    TWO_FINGER_SWIPE_DOWN,
    DOUBLE_TAP,
    /** pressing home while already on the first page with nothing open */
    HOME_PRESS,
}

@Serializable
data class GestureSettings(
    val swipeUp: GestureBinding = GestureBinding(GestureAction.OPEN_DRAWER),
    val swipeDown: GestureBinding = GestureBinding(GestureAction.NOTIFICATIONS),
    val twoFingerSwipeUp: GestureBinding = GestureBinding(),
    val twoFingerSwipeDown: GestureBinding = GestureBinding(GestureAction.QUICK_SETTINGS),
    val doubleTap: GestureBinding = GestureBinding(),
    val homePress: GestureBinding = GestureBinding(),
) {
    fun bindingFor(gesture: Gesture): GestureBinding = when (gesture) {
        Gesture.SWIPE_UP -> swipeUp
        Gesture.SWIPE_DOWN -> swipeDown
        Gesture.TWO_FINGER_SWIPE_UP -> twoFingerSwipeUp
        Gesture.TWO_FINGER_SWIPE_DOWN -> twoFingerSwipeDown
        Gesture.DOUBLE_TAP -> doubleTap
        Gesture.HOME_PRESS -> homePress
    }

    fun with(gesture: Gesture, binding: GestureBinding): GestureSettings = when (gesture) {
        Gesture.SWIPE_UP -> copy(swipeUp = binding)
        Gesture.SWIPE_DOWN -> copy(swipeDown = binding)
        Gesture.TWO_FINGER_SWIPE_UP -> copy(twoFingerSwipeUp = binding)
        Gesture.TWO_FINGER_SWIPE_DOWN -> copy(twoFingerSwipeDown = binding)
        Gesture.DOUBLE_TAP -> copy(doubleTap = binding)
        Gesture.HOME_PRESS -> copy(homePress = binding)
    }

    fun sanitized(): GestureSettings = GestureSettings(
        swipeUp = swipeUp.sanitized(),
        swipeDown = swipeDown.sanitized(),
        twoFingerSwipeUp = twoFingerSwipeUp.sanitized(),
        twoFingerSwipeDown = twoFingerSwipeDown.sanitized(),
        doubleTap = doubleTap.sanitized(),
        homePress = homePress.sanitized(),
    )

    /** true when any gesture needs the accessibility service, so settings can say so up front */
    val needsAccessibilityService: Boolean
        get() = Gesture.entries.any { bindingFor(it).action.needsAccessibilityService }
}

/**
 * the action that actually runs. in home only mode the drawer is search only, so opening it is
 * the same as opening search; mapping it here keeps both entry points behaving identically.
 */
fun GestureBinding.effectiveAction(mode: HomeMode): GestureAction =
    if (action == GestureAction.OPEN_DRAWER && mode == HomeMode.HOME_ONLY) GestureAction.OPEN_SEARCH else action
