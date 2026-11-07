package dev.gearturner.sparklauncher.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [WorkspaceItemEntity::class], version = 1, exportSchema = true)
abstract class SparkDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao

    companion object {
        const val NAME = "spark.db"
    }
}
