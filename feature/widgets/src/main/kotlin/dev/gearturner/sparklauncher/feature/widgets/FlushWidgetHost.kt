package dev.gearturner.sparklauncher.feature.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

/**
 * a host view that keeps zero padding for good. the platform applies its own 8dp inset whenever
 * a widget is configured, which happens at creation and again whenever the provider app is
 * updated, so clearing padding once after creation is undone by the next update. spark draws
 * its own inset.
 */
internal class FlushWidgetHostView(context: Context) : AppWidgetHostView(context) {
    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        super.setAppWidget(appWidgetId, info)
        setPadding(0, 0, 0, 0)
    }
}

/** creates flush host views so every widget spark hosts is edge to edge in its cell */
internal class FlushWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        FlushWidgetHostView(context)
}
