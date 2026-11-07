package dev.gearturner.sparklauncher.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import dev.gearturner.sparklauncher.core.data.db.SparkDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SparkDatabase::class.java)

    @Test
    fun version1LayoutSurvivesMigrationTo2() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL(
                "INSERT INTO workspace_items (id, type, container, folder_id, page, cell_x, cell_y, span_x, span_y, " +
                    "package_name, class_name, user_serial, custom_title) " +
                    "VALUES (7, 'APP', 'dock', NULL, 0, 2, 0, 1, 1, 'com.example', 'com.example.Main', 0, 'Mine')",
            )
            close()
        }
        val migrated = helper.runMigrationsAndValidate(DB_NAME, 2, true)
        migrated.query("SELECT id, container, cell_x, custom_title, shortcut_id FROM workspace_items").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7L, cursor.getLong(0))
            assertEquals("dock", cursor.getString(1))
            assertEquals(2, cursor.getInt(2))
            assertEquals("Mine", cursor.getString(3))
            assertTrue(cursor.isNull(4))
        }
        migrated.query("SELECT COUNT(*) FROM app_overrides").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
