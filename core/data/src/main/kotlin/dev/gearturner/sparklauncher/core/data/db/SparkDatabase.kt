package dev.gearturner.sparklauncher.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * every schema change ships as a migration, never a destructive reset, because the database is
 * the user's hand built home screen. version 2 adds shortcut ids and per app overrides,
 * version 3 adds widget ids and widget options, version 4 adds per app custom icons, and
 * version 5 tags rows with the home mode they belong to, version 6 stores home placements in
 * half cells for subgrid positioning, version 7 adds per app swipe up and swipe down targets, and
 * version 8 adds the per app flag for an app removed from home in home only mode.
 */
@Database(
    entities = [WorkspaceItemEntity::class, AppOverrideEntity::class],
    version = 8,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
    ],
)
abstract class SparkDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun appOverrideDao(): AppOverrideDao

    companion object {
        const val NAME = "spark.db"
    }
}
