package dev.gearturner.sparklauncher.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeTargetTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun appTargetSurvivesARoundTrip() {
        val target: SwipeTarget = SwipeTarget.App(AppKey("com.x", "com.x.Main", 10))
        val back = json.decodeFromString(SwipeTarget.serializer(), json.encodeToString(SwipeTarget.serializer(), target))
        assertEquals(target, back)
    }

    @Test
    fun shortcutTargetSurvivesARoundTrip() {
        val target: SwipeTarget = SwipeTarget.Shortcut(ShortcutKey("com.x", "compose", 0))
        val back = json.decodeFromString(SwipeTarget.serializer(), json.encodeToString(SwipeTarget.serializer(), target))
        assertEquals(target, back)
    }

    @Test
    fun validTargetsAreKept() {
        val app = SwipeTarget.App(AppKey("com.example.app", "com.example.app.Main\$Inner", 0))
        val shortcut = SwipeTarget.Shortcut(ShortcutKey("com.example.app", "id with spaces", 0))
        assertEquals(app, app.sanitized())
        assertEquals(shortcut, shortcut.sanitized())
    }

    @Test
    fun malformedPackageIsRefused() {
        assertNull(SwipeTarget.App(AppKey("not a package", "a.B", 0)).sanitized())
        assertNull(SwipeTarget.Shortcut(ShortcutKey("single", "id", 0)).sanitized())
        assertNull(SwipeTarget.Shortcut(ShortcutKey("../etc.passwd", "id", 0)).sanitized())
    }

    @Test
    fun blankOrOversizedIdsAreRefused() {
        assertNull(SwipeTarget.Shortcut(ShortcutKey("com.x", " ", 0)).sanitized())
        assertNull(SwipeTarget.Shortcut(ShortcutKey("com.x", "a".repeat(513), 0)).sanitized())
        assertNull(SwipeTarget.App(AppKey("com.x", "", 0)).sanitized())
        assertNull(SwipeTarget.App(AppKey("com.x", "bad class!", 0)).sanitized())
    }

    @Test
    fun overrideReportsTheTargetForEachDirection() {
        val up = SwipeTarget.App(AppKey("com.a", "com.a.Main", 0))
        val override = AppOverride(AppKey("com.x", "com.x.Main", 0), swipeUp = up)
        assertEquals(up, override.swipeFor(SwipeDirection.UP))
        assertNull(override.swipeFor(SwipeDirection.DOWN))
    }
}
