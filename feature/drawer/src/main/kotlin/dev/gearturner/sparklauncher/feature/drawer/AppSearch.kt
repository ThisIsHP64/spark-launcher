package dev.gearturner.sparklauncher.feature.drawer

import dev.gearturner.sparklauncher.core.model.AppInfo
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** locale aware ordering so accented and non latin names sort where a reader expects them */
internal fun sortApps(apps: List<AppInfo>, locale: Locale = Locale.getDefault()): List<AppInfo> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    return apps.sortedWith { a, b -> collator.compare(a.label, b.label) }
}

/** strips accents and case so "cafe" finds "Café" */
internal fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .lowercase(Locale.ROOT)
        .trim()

/**
 * filters a pre-sorted list, ranking exact names, then label prefixes, then word prefixes, then any
 * substring. the first result is what the keyboard's go key opens, so an exact name must win.
 * matching the package name too lets people find apps whose label differs from the brand.
 */
internal fun filterApps(sorted: List<AppInfo>, query: String): List<AppInfo> {
    val needle = normalizeForSearch(query)
    if (needle.isEmpty()) return sorted
    val exact = ArrayList<AppInfo>()
    val starts = ArrayList<AppInfo>()
    val wordStarts = ArrayList<AppInfo>()
    val contains = ArrayList<AppInfo>()
    for (app in sorted) {
        val label = normalizeForSearch(app.label)
        when {
            label == needle -> exact += app
            label.startsWith(needle) -> starts += app
            label.split(WORD_BREAK).any { it.startsWith(needle) } -> wordStarts += app
            needle in label || needle in app.key.packageName.lowercase(Locale.ROOT) -> contains += app
        }
    }
    return exact + starts + wordStarts + contains
}

private val DIACRITICS = Regex("\\p{Mn}+")
private val WORD_BREAK = Regex("[\\s._-]+")
