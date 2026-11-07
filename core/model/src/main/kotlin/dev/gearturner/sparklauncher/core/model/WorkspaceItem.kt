package dev.gearturner.sparklauncher.core.model

enum class ItemType { APP, SHORTCUT, FOLDER, WIDGET }

sealed interface Container {
    data object Desktop : Container
    data object Dock : Container
    data class Folder(val folderId: Long) : Container
}

/** for dock items only cellX is meaningful; it is the slot index */
data class CellPlacement(
    val page: Int,
    val cellX: Int,
    val cellY: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
) {
    init {
        require(page >= 0 && cellX >= 0 && cellY >= 0) { "placement coordinates must not be negative" }
        require(spanX >= 1 && spanY >= 1) { "spans must be at least one cell" }
    }
}

data class WorkspaceItem(
    val id: Long,
    val type: ItemType,
    val container: Container,
    val placement: CellPlacement,
    val app: AppKey?,
    val customTitle: String? = null,
)
