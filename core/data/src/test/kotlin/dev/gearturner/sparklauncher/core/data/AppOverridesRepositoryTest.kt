package dev.gearturner.sparklauncher.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.gearturner.sparklauncher.core.data.db.AppOverrideEntity
import dev.gearturner.sparklauncher.core.data.db.SparkDatabase
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.CustomIcon
import dev.gearturner.sparklauncher.core.model.ShortcutKey
import dev.gearturner.sparklauncher.core.model.SwipeDirection
import dev.gearturner.sparklauncher.core.model.SwipeTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppOverridesRepositoryTest {

    private lateinit var db: SparkDatabase
    private lateinit var repository: AppOverridesRepository
    private val key = AppKey("com.example", "com.example.Main", 0)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SparkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AppOverridesRepository(db.appOverrideDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun hidingAndRenamingCombineOnOneRow() = runTest {
        repository.setHidden(key, true)
        repository.setLabel(key, "  Work Mail  ")
        val override = repository.overrides.first().getValue(key)
        assertTrue(override.hidden)
        assertEquals("Work Mail", override.customLabel)
    }

    @Test
    fun clearingEverythingDeletesTheRow() = runTest {
        repository.setHidden(key, true)
        repository.setLabel(key, "x")
        repository.setHidden(key, false)
        repository.setLabel(key, "   ")
        assertTrue(repository.overrides.first().isEmpty())
    }

    @Test
    fun removedFromHomeIsSeparateFromHidden() = runTest {
        repository.setHomeRemoved(key, true)
        val override = repository.overrides.first().getValue(key)
        assertTrue(override.homeRemoved)
        assertFalse(override.hidden)
        repository.setHomeRemoved(key, false)
        assertTrue(repository.overrides.first().isEmpty())
    }

    @Test
    fun labelsAreCappedInLength() = runTest {
        repository.setLabel(key, "a".repeat(500))
        assertEquals(AppOverridesRepository.MAX_LABEL_LENGTH, repository.overrides.first().getValue(key).customLabel?.length)
    }

    @Test
    fun labelForFallsBackToTheAppName() = runTest {
        assertEquals("Mail", repository.overrides.first().labelFor(key, "Mail"))
        repository.setLabel(key, "Post")
        assertEquals("Post", repository.overrides.first().labelFor(key, "Mail"))
        assertNull(repository.overrides.first()[AppKey("other", "x", 0)])
    }

    @Test
    fun customIconsAreStoredValidatedAndCleared() = runTest {
        repository.setIcon(key, CustomIcon("com.pack.icons", "maps_round"))
        assertEquals(CustomIcon("com.pack.icons", "maps_round"), repository.overrides.first().getValue(key).customIcon)
        repository.setIcon(key, CustomIcon("com.pack.icons", "../../evil"))
        assertTrue(repository.overrides.first().isEmpty())
        repository.setIcon(key, CustomIcon("not a package", "maps"))
        assertTrue(repository.overrides.first().isEmpty())
    }

    @Test
    fun swipeTargetsAreStoredPerDirectionAndCleared() = runTest {
        val app = SwipeTarget.App(AppKey("com.other", "com.other.Main", 0))
        val shortcut = SwipeTarget.Shortcut(ShortcutKey("com.maps", "home", 0))
        assertTrue(repository.setSwipe(key, SwipeDirection.UP, app))
        assertTrue(repository.setSwipe(key, SwipeDirection.DOWN, shortcut))
        val stored = repository.overrides.first().getValue(key)
        assertEquals(app, stored.swipeUp)
        assertEquals(shortcut, stored.swipeDown)
        repository.setSwipe(key, SwipeDirection.UP, null)
        assertNull(repository.overrides.first().getValue(key).swipeUp)
        repository.setSwipe(key, SwipeDirection.DOWN, null)
        assertTrue(repository.overrides.first().isEmpty())
    }

    @Test
    fun invalidSwipeTargetIsRefusedAndChangesNothing() = runTest {
        val good = SwipeTarget.App(AppKey("com.other", "com.other.Main", 0))
        repository.setSwipe(key, SwipeDirection.UP, good)
        assertFalse(repository.setSwipe(key, SwipeDirection.UP, SwipeTarget.App(AppKey("bad package", "x", 0))))
        assertEquals(good, repository.overrides.first().getValue(key).swipeUp)
    }

    @Test
    fun swipeDoesNotDropOtherOverrides() = runTest {
        repository.setLabel(key, "Post")
        repository.setSwipe(key, SwipeDirection.UP, SwipeTarget.App(AppKey("com.other", "com.other.Main", 0)))
        repository.setSwipe(key, SwipeDirection.UP, null)
        assertEquals("Post", repository.overrides.first().getValue(key).customLabel)
    }

    @Test
    fun swipeShortcutIdsAreScopedToPackageAndProfile() = runTest {
        val other = AppKey("com.second", "com.second.Main", 0)
        repository.setSwipe(key, SwipeDirection.UP, SwipeTarget.Shortcut(ShortcutKey("com.maps", "home", 0)))
        repository.setSwipe(key, SwipeDirection.DOWN, SwipeTarget.Shortcut(ShortcutKey("com.maps", "work", 0)))
        repository.setSwipe(other, SwipeDirection.UP, SwipeTarget.Shortcut(ShortcutKey("com.maps", "gym", 10)))
        repository.setSwipe(other, SwipeDirection.DOWN, SwipeTarget.App(AppKey("com.maps", "com.maps.Main", 0)))
        assertEquals(setOf("home", "work"), repository.swipeShortcutIds("com.maps", 0))
        assertEquals(setOf("gym"), repository.swipeShortcutIds("com.maps", 10))
        assertTrue(repository.swipeShortcutIds("com.none", 0).isEmpty())
    }

    @Test
    fun clearingAShortcutRemovesOnlyTheSwipesPointingAtIt() = runTest {
        val gone = ShortcutKey("com.maps", "home", 0)
        val other = AppKey("com.second", "com.second.Main", 0)
        val app = SwipeTarget.App(AppKey("com.third", "com.third.Main", 0))
        repository.setSwipe(key, SwipeDirection.UP, SwipeTarget.Shortcut(gone))
        repository.setSwipe(key, SwipeDirection.DOWN, app)
        repository.setSwipe(other, SwipeDirection.DOWN, SwipeTarget.Shortcut(gone))
        repository.setSwipe(other, SwipeDirection.UP, SwipeTarget.Shortcut(ShortcutKey("com.maps", "work", 0)))

        repository.clearShortcut(gone)

        val stored = repository.overrides.first()
        assertNull(stored.getValue(key).swipeUp)
        assertEquals(app, stored.getValue(key).swipeDown)
        assertNull(stored.getValue(other).swipeDown)
        assertEquals(SwipeTarget.Shortcut(ShortcutKey("com.maps", "work", 0)), stored.getValue(other).swipeUp)
    }

    @Test
    fun clearingTheOnlyOverrideDeletesTheRow() = runTest {
        val gone = ShortcutKey("com.maps", "home", 0)
        repository.setSwipe(key, SwipeDirection.UP, SwipeTarget.Shortcut(gone))
        repository.clearShortcut(gone)
        assertTrue(repository.overrides.first().isEmpty())
    }

    @Test
    fun corruptSwipeCellReadsAsNoTarget() = runTest {
        db.appOverrideDao().upsert(
            AppOverrideEntity(
                key.packageName, key.className, key.userSerial, swipeUp = "{not json", swipeDown = "{\"type\":\"nope\"}",
            ),
        )
        val stored = repository.overrides.first().getValue(key)
        assertNull(stored.swipeUp)
        assertNull(stored.swipeDown)
    }
}
