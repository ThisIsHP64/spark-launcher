package dev.gearturner.sparklauncher.feature.widgets

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * the steps that need an activity: asking permission to bind a widget and running its setup
 * screen. the launcher activity provides it, since both steps return results to an activity.
 */
interface WidgetBinder {
    /** returns the bound widget id, or null if the user declined either step */
    suspend fun bindAndConfigure(info: AppWidgetProviderInfo, needsConfiguration: Boolean): Int?

    /** reopens a placed widget's setup screen */
    suspend fun reconfigure(appWidgetId: Int): Boolean
}

val LocalWidgetBinder = staticCompositionLocalOf<WidgetBinder?> { null }
