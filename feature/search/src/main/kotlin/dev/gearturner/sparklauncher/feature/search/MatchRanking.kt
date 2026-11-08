package dev.gearturner.sparklauncher.feature.search

import java.text.Normalizer
import java.util.Locale

/** strips accents and case so "cafe" finds "Café" */
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .lowercase(Locale.ROOT)
        .trim()

/**
 * filters [items] while keeping their incoming order within each tier: exact labels, then label
 * prefixes, then word or keyword prefixes, then any substring. every result list in search uses
 * this so a query ranks apps, shortcuts and settings the same way.
 *
 * [keywords] count as words of the label, so "wifi" finds a settings page titled "Internet".
 * [fallback] text only matches as a substring, which suits package names that would otherwise
 * crowd the prefix tiers with every "com." app.
 */
fun <T> rankMatches(
    items: List<T>,
    query: String,
    label: (T) -> String,
    keywords: (T) -> List<String> = { emptyList() },
    fallback: (T) -> String? = { null },
): List<T> {
    val needle = normalizeForSearch(query)
    if (needle.isEmpty()) return items
    val exact = ArrayList<T>()
    val starts = ArrayList<T>()
    val wordStarts = ArrayList<T>()
    val contains = ArrayList<T>()
    for (item in items) {
        val name = normalizeForSearch(label(item))
        val words = name.split(WORD_BREAK) + keywords(item).map(::normalizeForSearch)
        when {
            name == needle -> exact += item
            name.startsWith(needle) -> starts += item
            words.any { it.startsWith(needle) } -> wordStarts += item
            needle in name || fallback(item)?.let { needle in normalizeForSearch(it) } == true -> contains += item
        }
    }
    return exact + starts + wordStarts + contains
}

private val DIACRITICS = Regex("\\p{Mn}+")
private val WORD_BREAK = Regex("[\\s._-]+")
