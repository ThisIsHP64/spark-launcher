package dev.gearturner.sparklauncher.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import dev.gearturner.sparklauncher.core.model.GestureBinding

/**
 * runs gesture actions that reach outside the home screen, such as the notification shade or
 * another app. the drawer and search are opened by home itself, since it owns the drawer.
 */
fun interface GestureActionHandler {
    fun perform(binding: GestureBinding)
}

val LocalGestureActions = staticCompositionLocalOf<GestureActionHandler?> { null }
