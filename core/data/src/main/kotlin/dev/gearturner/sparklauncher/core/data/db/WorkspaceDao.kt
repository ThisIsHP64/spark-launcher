package dev.gearturner.sparklauncher.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {

    @Query("SELECT * FROM workspace_items ORDER BY container, page, cell_y, cell_x")
    fun observeAll(): Flow<List<WorkspaceItemEntity>>

    @Query("SELECT * FROM workspace_items")
    suspend fun getAll(): List<WorkspaceItemEntity>

    @Query("SELECT * FROM workspace_items WHERE id = :id")
    suspend fun getById(id: Long): WorkspaceItemEntity?

    @Query("SELECT COUNT(*) FROM workspace_items")
    suspend fun count(): Int

    @Insert
    suspend fun insert(item: WorkspaceItemEntity): Long

    @Insert
    suspend fun insertAll(items: List<WorkspaceItemEntity>)

    @Update
    suspend fun update(item: WorkspaceItemEntity)

    @Query("DELETE FROM workspace_items WHERE id = :id")
    suspend fun delete(id: Long)
}
