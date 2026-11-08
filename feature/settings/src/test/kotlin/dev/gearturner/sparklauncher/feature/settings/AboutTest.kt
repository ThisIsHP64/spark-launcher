package dev.gearturner.sparklauncher.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AboutTest {

    @Test
    fun hardWrappedLinesFlowIntoParagraphs() {
        val text = "                    GNU GENERAL PUBLIC LICENSE\n                       Version 3\n\n" +
            "  Everyone is permitted to copy and\n distribute verbatim copies.\r\n\r\n\n  Preamble\n"
        assertEquals(
            listOf("GNU GENERAL PUBLIC LICENSE Version 3", "Everyone is permitted to copy and distribute verbatim copies.", "Preamble"),
            licenseParagraphs(text),
        )
    }

    @Test
    fun blankTextHasNoParagraphs() {
        assertTrue(licenseParagraphs("\n\n   \n").isEmpty())
    }

    @Test
    fun everyLibraryIsListedOnceAndInOrder() {
        val names = OpenSourceLibraries.map { it.name }
        assertEquals(names.distinct(), names)
        assertEquals(names.sortedBy { it.lowercase() }, names)
    }

    @Test
    fun theBundledGplMatchesTheProjectLicense() {
        // the about page shows the copy in res/raw, which must not drift from the license the repo ships
        val bundled = File("src/main/res/raw/license_gpl3.txt")
        val project = File("../../LICENSE")
        assertTrue("run through gradle so the module is the working directory", bundled.isFile && project.isFile)
        assertEquals(project.readText(), bundled.readText())
    }
}
