package dev.gearturner.sparklauncher.feature.workspace

import dev.gearturner.sparklauncher.core.model.AppInfo
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.CellPlacement
import dev.gearturner.sparklauncher.core.model.Container
import dev.gearturner.sparklauncher.core.model.ItemType
import dev.gearturner.sparklauncher.core.model.LauncherSettings
import dev.gearturner.sparklauncher.core.model.WorkspaceItem
import org.junit.Assert.assertEquals
import org.junit.Test

class BuildStateTest {

    private fun key(name: String) = AppKey("com.example.$name", "Main", 0)
    private fun info(name: String) = AppInfo(key(name), name, isManagedProfile = false, firstInstallTime = 0)
    private fun item(id: Long, name: String, container: Container, placement: CellPlacement, title: String? = null) =
        WorkspaceItem(id, ItemType.APP, container, placement, key(name), title)

    @Test
    fun missingAppsAreHiddenNotShown() {
        val items = listOf(
            item(1, "here", Container.Desktop, CellPlacement(0, 0, 0)),
            item(2, "uninstalled", Container.Desktop, CellPlacement(0, 1, 0)),
        )
        val state = buildState(items, listOf(info("here")), LauncherSettings())
        assertEquals(listOf(1L), state.pages.single().map { it.itemId })
    }

    @Test
    fun itemsAreGroupedIntoPagesAndDockIsOrderedBySlot() {
        val items = listOf(
            item(1, "a", Container.Desktop, CellPlacement(1, 0, 0)),
            item(2, "b", Container.Dock, CellPlacement(0, 2, 0)),
            item(3, "c", Container.Dock, CellPlacement(0, 0, 0)),
        )
        val state = buildState(items, listOf(info("a"), info("b"), info("c")), LauncherSettings())
        assertEquals(2, state.pages.size)
        assertEquals(emptyList<HomeCell>(), state.pages[0])
        assertEquals(listOf(3L, 2L), state.dock.map { it.itemId })
    }

    @Test
    fun itemsOutsideTheCurrentGridAreHidden() {
        val items = listOf(item(1, "a", Container.Desktop, CellPlacement(0, 6, 0)))
        val state = buildState(items, listOf(info("a")), LauncherSettings(homeColumns = 4))
        assertEquals(0, state.pages.single().size)
    }

    @Test
    fun customTitleOverridesLabel() {
        val items = listOf(item(1, "a", Container.Desktop, CellPlacement(0, 0, 0), title = "Work"))
        val state = buildState(items, listOf(info("a")), LauncherSettings())
        assertEquals("Work", state.pages.single().single().title)
    }
}
