package dev.gearturner.sparklauncher.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * every schema change ships as a migration, never a destructive reset, because the database is
 * the user's hand built home screen. version 2 adds shortcut ids and per app overrides.
 */
@Database(
    entities = [WorkspaceItemEntity::class, AppOverrideEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class SparkDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun appOverrideDao(): AppOverrideDao

    companion object {
        const val NAME = "spark.db"
    }
}
