package dev.gearturner.sparklauncher.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test

class MatchRankingTest {

    private data class Item(val name: String, val words: List<String> = emptyList(), val extra: String? = null)

    private fun rank(items: List<Item>, query: String) =
        rankMatches(items, query, label = { it.name }, keywords = { it.words }, fallback = { it.extra }).map { it.name }

    @Test
    fun tiersRunExactThenPrefixThenWordThenSubstring() {
        val items = listOf(Item("Roadmap"), Item("Google Maps"), Item("Mapper"), Item("Map"))
        assertEquals(listOf("Map", "Mapper", "Google Maps", "Roadmap"), rank(items, "map"))
    }

    @Test
    fun keywordsCountAsWordsOfTheLabel() {
        val items = listOf(Item("Internet", words = listOf("wifi")), Item("Display"))
        assertEquals(listOf("Internet"), rank(items, "wif"))
    }

    @Test
    fun fallbackOnlyMatchesAsSubstring() {
        val items = listOf(Item("Bank", extra = "com.konylabs.capitalone"), Item("Komoot"))
        assertEquals(listOf("Komoot", "Bank"), rank(items, "ko"))
    }

    @Test
    fun accentsAndCaseAreIgnored() {
        assertEquals(listOf("Café"), rank(listOf(Item("Café")), "CAFE"))
    }

    @Test
    fun blankQueryKeepsEverythingInOrder() {
        val items = listOf(Item("b"), Item("a"))
        assertEquals(listOf("b", "a"), rank(items, "  "))
    }
}
