package dev.gearturner.sparklauncher.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppOverrideDao {

    @Query("SELECT * FROM app_overrides")
    fun observeAll(): Flow<List<AppOverrideEntity>>

    @Query("SELECT * FROM app_overrides WHERE package_name = :packageName AND class_name = :className AND user_serial = :userSerial")
    suspend fun get(packageName: String, className: String, userSerial: Long): AppOverrideEntity?

    @Upsert
    suspend fun upsert(entity: AppOverrideEntity)

    @Query("DELETE FROM app_overrides WHERE package_name = :packageName AND class_name = :className AND user_serial = :userSerial")
    suspend fun delete(packageName: String, className: String, userSerial: Long)
}
