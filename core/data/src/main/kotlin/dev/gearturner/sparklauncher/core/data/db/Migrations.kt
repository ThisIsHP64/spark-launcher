package dev.gearturner.sparklauncher.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.gearturner.sparklauncher.core.model.SUBGRID

/**
 * home rows move from whole cells to half cells, so every desktop position and size doubles and
 * the layout looks exactly as before. dock slots and folder ranks keep their meaning.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE workspace_items SET cell_x = cell_x * $SUBGRID, cell_y = cell_y * $SUBGRID, " +
                "span_x = span_x * $SUBGRID, span_y = span_y * $SUBGRID WHERE container = '${ContainerNames.DESKTOP}'",
        )
    }
}

/** every hand written migration, for the database builder and the migration tests */
val MANUAL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_5_6)
