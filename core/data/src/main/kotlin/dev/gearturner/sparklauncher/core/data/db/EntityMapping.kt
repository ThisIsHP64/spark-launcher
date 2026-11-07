package dev.gearturner.sparklauncher.core.data.db

import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.CellPlacement
import dev.gearturner.sparklauncher.core.model.Container
import dev.gearturner.sparklauncher.core.model.ItemType
import dev.gearturner.sparklauncher.core.model.WorkspaceItem

/** returns null for rows that cannot be represented, so one corrupt row never breaks the whole home screen */
internal fun WorkspaceItemEntity.toModelOrNull(): WorkspaceItem? {
    val itemType = ItemType.entries.firstOrNull { it.name == type } ?: return null
    val itemContainer = when (container) {
        ContainerNames.DESKTOP -> Container.Desktop
        ContainerNames.DOCK -> Container.Dock
        ContainerNames.FOLDER -> Container.Folder(folderId ?: return null)
        else -> return null
    }
    val placement = runCatching { CellPlacement(page, cellX, cellY, spanX, spanY) }.getOrNull() ?: return null
    val app = if (packageName != null && className != null && userSerial != null) {
        AppKey(packageName, className, userSerial)
    } else {
        null
    }
    if (itemType == ItemType.APP && app == null) return null
    return WorkspaceItem(id, itemType, itemContainer, placement, app, customTitle)
}

internal fun WorkspaceItem.toEntity(): WorkspaceItemEntity = WorkspaceItemEntity(
    id = id,
    type = type.name,
    container = when (container) {
        Container.Desktop -> ContainerNames.DESKTOP
        Container.Dock -> ContainerNames.DOCK
        is Container.Folder -> ContainerNames.FOLDER
    },
    folderId = (container as? Container.Folder)?.folderId,
    page = placement.page,
    cellX = placement.cellX,
    cellY = placement.cellY,
    spanX = placement.spanX,
    spanY = placement.spanY,
    packageName = app?.packageName,
    className = app?.className,
    userSerial = app?.userSerial,
    customTitle = customTitle,
)
