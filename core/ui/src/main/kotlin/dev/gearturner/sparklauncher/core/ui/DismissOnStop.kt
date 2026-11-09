package dev.gearturner.sparklauncher.core.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * runs [dismiss] when the launcher leaves the screen, such as for the app switcher. a menu or dialog
 * left open would still be there on return, covering a home screen the user expects to find clean.
 */
@Composable
fun DismissOnStop(dismiss: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { dismiss() }
}
