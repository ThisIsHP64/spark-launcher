package dev.gearturner.sparklauncher.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import dev.gearturner.sparklauncher.core.model.AppShortcut
import dev.gearturner.sparklauncher.core.model.ShortcutKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuGroupingTest {

    private val shortcut = AppShortcut(ShortcutKey("com.a", "s", 0), "Short", null, 0, true)
    private val info = MenuAction("Info", Icons.Outlined.Info) {}
    private val remove = MenuAction("Remove", Icons.Outlined.Close, MenuGroup.Destructive) {}

    @Test
    fun groupsRunShortcutsThenActionsThenDestructive() {
        val groups = groupMenu(listOf(shortcut), listOf(remove, info))
        assertEquals(3, groups.size)
        assertEquals(listOf(MenuEntry.Shortcut(shortcut)), groups[0])
        assertEquals(listOf(MenuEntry.Action(info)), groups[1])
        assertEquals(listOf(MenuEntry.Action(remove)), groups[2])
    }

    @Test
    fun emptyGroupsAreDroppedSoShapesMatchWhatIsShown() {
        assertEquals(1, groupMenu(emptyList(), listOf(info)).size)
        assertEquals(2, groupMenu(emptyList(), listOf(info, remove)).size)
        assertTrue(groupMenu(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun actionOrderIsKeptWithinAGroup() {
        val second = MenuAction("Second", Icons.Outlined.Info) {}
        assertEquals(listOf(MenuEntry.Action(info), MenuEntry.Action(second)), groupMenu(emptyList(), listOf(info, second)).single())
    }
}

class ChoiceFilterTest {

    private val choices = listOf(Choice(1, "Google Maps"), Choice(2, "Messages"), Choice(3, "Maps Go"))

    @Test
    fun blankQueryKeepsEveryChoiceInOrder() {
        assertEquals(listOf(1, 2, 3), filterChoices(choices, "  ").map { it.value })
    }

    @Test
    fun matchingIgnoresCaseAndKeepsOrder() {
        assertEquals(listOf(1, 3), filterChoices(choices, "MAPS").map { it.value })
    }
}
