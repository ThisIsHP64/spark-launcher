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
    @ColumnInfo(name = "icon_pack") val iconPack: String? = null,
    @ColumnInfo(name = "icon_drawable") val iconDrawable: String? = null,
    // swipe targets are stored as json so a new target kind never needs another migration
    @ColumnInfo(name = "swipe_up") val swipeUp: String? = null,
    @ColumnInfo(name = "swipe_down") val swipeDown: String? = null,
    // set by removing an icon in home only mode; unlike hidden, the app stays visible to search
    @ColumnInfo(name = "home_removed", defaultValue = "0") val homeRemoved: Boolean = false,
)
