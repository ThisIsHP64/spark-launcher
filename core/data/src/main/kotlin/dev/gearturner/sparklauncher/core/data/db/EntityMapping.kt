package dev.gearturner.sparklauncher.core.data.db

import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.CellPlacement
import dev.gearturner.sparklauncher.core.model.Container
import dev.gearturner.sparklauncher.core.model.ItemType
import dev.gearturner.sparklauncher.core.model.WidgetOptions
import dev.gearturner.sparklauncher.core.model.WorkspaceItem
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

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
    if (itemType == ItemType.SHORTCUT && (app == null || shortcutId.isNullOrEmpty())) return null
    if (itemType == ItemType.WIDGET && (app == null || appWidgetId == null)) return null
    return WorkspaceItem(
        id = id,
        type = itemType,
        container = itemContainer,
        placement = placement,
        app = app,
        customTitle = customTitle,
        shortcutId = shortcutId,
        appWidgetId = appWidgetId,
        widgetOptions = decodeWidgetOptions(widgetOptions),
    )
}

internal fun WorkspaceItem.toEntity(layout: String): WorkspaceItemEntity = WorkspaceItemEntity(
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
    shortcutId = shortcutId,
    appWidgetId = appWidgetId,
    layout = layout,
    // defaults are left as null so untouched widgets pick up any future change to the defaults
    widgetOptions = widgetOptions.takeIf { it != WidgetOptions() }?.let { optionsJson.encodeToString(WidgetOptions.serializer(), it) },
)

// unknown keys are ignored so options written by a newer version never break an older one
private val optionsJson = Json { ignoreUnknownKeys = true }

private fun decodeWidgetOptions(raw: String?): WidgetOptions {
    if (raw.isNullOrBlank()) return WidgetOptions()
    return try {
        optionsJson.decodeFromString(WidgetOptions.serializer(), raw).sanitized()
    } catch (e: SerializationException) {
        WidgetOptions()
    } catch (e: IllegalArgumentException) {
        WidgetOptions()
    }
}
