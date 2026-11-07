package dev.gearturner.sparklauncher.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "app_overrides", primaryKeys = ["package_name", "class_name", "user_serial"])
data class AppOverrideEntity(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "class_name") val className: String,
    @ColumnInfo(name = "user_serial") val userSerial: Long,
    val hidden: Boolean = false,
    @ColumnInfo(name = "custom_label") val customLabel: String? = null,
)
