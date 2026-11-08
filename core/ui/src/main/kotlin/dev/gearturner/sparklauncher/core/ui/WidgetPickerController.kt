package dev.gearturner.sparklauncher.core.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * opens the widget picker from anywhere, such as an app's long press menu in the drawer, and
 * carries the home grid's cell size so the picker can show and drag widgets at their real size.
 */
@Stable
class WidgetPickerController {
    var isOpen by mutableStateOf(false)
        private set

    /** when set, the picker shows only this app's widgets */
    var packageFilter by mutableStateOf<String?>(null)
        private set

    var cellWidthDp: Float = 0f
    var cellHeightDp: Float = 0f
    var columns: Int = 1
    var rows: Int = 1

    fun open(packageName: String? = null) {
        packageFilter = packageName
        isOpen = true
    }

    fun close() {
        isOpen = false
    }
}

val LocalWidgetPicker = staticCompositionLocalOf<WidgetPickerController?> { null }
