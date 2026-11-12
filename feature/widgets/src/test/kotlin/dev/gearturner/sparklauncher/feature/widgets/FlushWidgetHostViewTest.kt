package dev.gearturner.sparklauncher.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlushWidgetHostViewTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private fun FlushWidgetHostView.padding() = listOf(paddingLeft, paddingTop, paddingRight, paddingBottom)

    @Test
    fun configuringTheWidgetLeavesNoPadding() {
        val view = FlushWidgetHostView(context)

        view.setAppWidget(1, null)

        assertEquals(listOf(0, 0, 0, 0), view.padding())
    }

    @Test
    fun configuringAgainAfterAProviderUpdateClearsPaddingTheFrameworkRestored() {
        val view = FlushWidgetHostView(context)
        view.setAppWidget(1, null)

        // on a device the framework puts its 8dp default inset back at this point
        view.setPadding(8, 8, 8, 8)
        view.setAppWidget(1, null)

        assertEquals(listOf(0, 0, 0, 0), view.padding())
    }

    @Test
    fun theHostBuildsFlushViews() {
        val host = FlushWidgetHost(context, 77)

        val view = host.createView(context, 5, null)

        assertTrue(view is FlushWidgetHostView)
    }
}
