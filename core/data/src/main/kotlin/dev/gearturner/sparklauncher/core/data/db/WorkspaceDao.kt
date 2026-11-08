package dev.gearturner.sparklauncher.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {

    @Query("SELECT * FROM workspace_items WHERE layout = :layout ORDER BY container, page, cell_y, cell_x")
    fun observe(layout: String): Flow<List<WorkspaceItemEntity>>

    @Query("SELECT * FROM workspace_items WHERE layout = :layout")
    suspend fun getAll(layout: String): List<WorkspaceItemEntity>

    /** every layout at once, for ids that are shared between them such as widgets */
    @Query("SELECT * FROM workspace_items")
    suspend fun getEverywhere(): List<WorkspaceItemEntity>

    @Query("SELECT * FROM workspace_items WHERE id = :id")
    suspend fun getById(id: Long): WorkspaceItemEntity?

    @Query("SELECT COUNT(*) FROM workspace_items WHERE layout = :layout")
    suspend fun count(layout: String): Int

    @Insert
    suspend fun insert(item: WorkspaceItemEntity): Long

    @Update
    suspend fun update(item: WorkspaceItemEntity)

    @Query("DELETE FROM workspace_items WHERE id = :id")
    suspend fun delete(id: Long)
}
