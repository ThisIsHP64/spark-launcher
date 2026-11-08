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

/**
 * apps use [app]; shortcuts keep the owning app in [app] plus [shortcutId], so the pair forms the
 * shortcut key; folders use [customTitle] as their name and hold children whose container points
 * back at the folder id, ordered by cellX; widgets keep their provider in [app] (its receiver
 * class) plus the system [appWidgetId] and the user's [widgetOptions].
 */
data class WorkspaceItem(
    val id: Long,
    val type: ItemType,
    val container: Container,
    val placement: CellPlacement,
    val app: AppKey?,
    val customTitle: String? = null,
    val shortcutId: String? = null,
    val appWidgetId: Int? = null,
    val widgetOptions: WidgetOptions = WidgetOptions(),
) {
    /** overlapping widgets neither block nor are blocked by other items, as nova allows */
    val overlaps: Boolean get() = type == ItemType.WIDGET && widgetOptions.allowOverlap

    val shortcutKey: ShortcutKey?
        get() = if (type == ItemType.SHORTCUT && app != null && shortcutId != null) {
            ShortcutKey(app.packageName, shortcutId, app.userSerial)
        } else {
            null
        }
}
