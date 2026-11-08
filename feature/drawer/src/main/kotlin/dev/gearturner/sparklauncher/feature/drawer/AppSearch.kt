package dev.gearturner.sparklauncher.feature.drawer

import dev.gearturner.sparklauncher.core.model.AppInfo
import dev.gearturner.sparklauncher.feature.search.rankMatches
import java.text.Collator
import java.util.Locale

/** locale aware ordering so accented and non latin names sort where a reader expects them */
internal fun sortApps(apps: List<AppInfo>, locale: Locale = Locale.getDefault()): List<AppInfo> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    return apps.sortedWith { a, b -> collator.compare(a.label, b.label) }
}

/**
 * filters a pre-sorted list with the ranking every search section shares. the first result is what
 * the keyboard's go key opens, so an exact name wins. matching the package name too lets people
 * find apps whose label differs from the brand.
 */
internal fun filterApps(sorted: List<AppInfo>, query: String): List<AppInfo> =
    rankMatches(sorted, query, label = { it.label }, fallback = { it.key.packageName })
