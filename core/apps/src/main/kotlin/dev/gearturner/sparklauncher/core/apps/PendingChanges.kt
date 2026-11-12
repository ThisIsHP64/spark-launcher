package dev.gearturner.sparklauncher.core.apps

import java.util.concurrent.ConcurrentHashMap

/**
 * package names reported by system callbacks that no reload has covered yet. names are taken
 * before a reload starts, so one that arrives mid reload waits for the next reload instead of
 * being announced against an index that may predate it.
 */
internal class PendingChanges {
    private val names = ConcurrentHashMap.newKeySet<String>()

    fun add(changed: Set<String>) {
        names.addAll(changed)
    }

    fun drain(): Set<String> {
        val taken = names.toSet()
        names.removeAll(taken)
        return taken
    }
}

/**
 * runs [load] and only then calls [announce] with the packages that were pending when it began.
 * listeners react to an announcement by reading fresh data, so announcing earlier lets them read
 * the stale index, which for icons means a cached bitmap of the platform default icon.
 */
internal fun <T> reloadThenAnnounce(pending: PendingChanges, load: () -> T, announce: (Set<String>) -> Unit): T {
    val names = pending.drain()
    val loaded = load()
    if (names.isNotEmpty()) announce(names)
    return loaded
}
