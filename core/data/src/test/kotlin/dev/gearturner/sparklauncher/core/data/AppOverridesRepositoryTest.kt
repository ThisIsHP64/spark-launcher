package dev.gearturner.sparklauncher.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.gearturner.sparklauncher.core.data.db.SparkDatabase
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.CustomIcon
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
}
