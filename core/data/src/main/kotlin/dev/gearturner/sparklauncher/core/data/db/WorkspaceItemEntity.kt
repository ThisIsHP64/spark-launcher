package dev.gearturner.sparklauncher.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** flat row shape so every item type shares one table, mirroring how aosp stores its favorites */
@Entity(
    tableName = "workspace_items",
    indices = [Index(value = ["container", "folder_id"]), Index(value = ["package_name", "user_serial"])],
)
data class WorkspaceItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val container: String,
    @ColumnInfo(name = "folder_id") val folderId: Long? = null,
    val page: Int,
    @ColumnInfo(name = "cell_x") val cellX: Int,
    @ColumnInfo(name = "cell_y") val cellY: Int,
    @ColumnInfo(name = "span_x") val spanX: Int = 1,
    @ColumnInfo(name = "span_y") val spanY: Int = 1,
    @ColumnInfo(name = "package_name") val packageName: String? = null,
    @ColumnInfo(name = "class_name") val className: String? = null,
    @ColumnInfo(name = "user_serial") val userSerial: Long? = null,
    @ColumnInfo(name = "custom_title") val customTitle: String? = null,
    @ColumnInfo(name = "shortcut_id") val shortcutId: String? = null,
)

internal object ContainerNames {
    const val DESKTOP = "desktop"
    const val DOCK = "dock"
    const val FOLDER = "folder"
}
