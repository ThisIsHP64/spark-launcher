package dev.gearturner.sparklauncher.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class GesturesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun appBindingWithoutAnAppIsCleared() {
        val settings = GestureSettings(doubleTap = GestureBinding(GestureAction.OPEN_APP, app = null))
        assertEquals(GestureAction.NONE, settings.sanitized().doubleTap.action)
    }

    @Test
    fun strayAppOnANonAppBindingIsDropped() {
        val binding = GestureBinding(GestureAction.NOTIFICATIONS, AppKey("a", "b", 0))
        assertEquals(GestureBinding(GestureAction.NOTIFICATIONS), binding.sanitized())
    }

    @Test
    fun settingsWrittenBeforeGesturesExistedDecodeToDefaults() {
        val old = """{"homeMode":"DRAWER","homeColumns":5}"""
        val decoded = json.decodeFromString(LauncherSettings.serializer(), old)
        assertEquals(GestureSettings(), decoded.gestures)
        assertEquals(5, decoded.homeColumns)
    }

    @Test
    fun appBindingSurvivesARoundTrip() {
        val settings = LauncherSettings(
            gestures = GestureSettings(swipeUp = GestureBinding(GestureAction.OPEN_APP, AppKey("com.x", "com.x.Main", 10))),
        )
        val back = json.decodeFromString(LauncherSettings.serializer(), json.encodeToString(LauncherSettings.serializer(), settings))
        assertEquals(settings, back)
    }

    @Test
    fun drawerActsAsSearchInHomeOnlyMode() {
        val binding = GestureBinding(GestureAction.OPEN_DRAWER)
        assertEquals(GestureAction.OPEN_SEARCH, binding.effectiveAction(HomeMode.HOME_ONLY))
        assertEquals(GestureAction.OPEN_DRAWER, binding.effectiveAction(HomeMode.DRAWER))
    }

    @Test
    fun withAndBindingForAgreeForEveryGesture() {
        Gesture.entries.forEach { gesture ->
            val binding = GestureBinding(GestureAction.RECENTS)
            assertEquals(binding, GestureSettings().with(gesture, binding).bindingFor(gesture))
        }
    }
}
