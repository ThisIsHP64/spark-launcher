package dev.gearturner.sparklauncher.feature.settings

import dev.gearturner.sparklauncher.core.model.AppInfo
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.AppOverride
import dev.gearturner.sparklauncher.core.model.AppShortcut
import dev.gearturner.sparklauncher.core.model.ShortcutKey
import dev.gearturner.sparklauncher.core.model.SwipeDirection
import dev.gearturner.sparklauncher.core.model.SwipeTarget
import java.text.Collator

/** an app whose icon runs a pinned shortcut when swiped in [direction] */
data class SwipeUse(val appLabel: String, val direction: SwipeDirection)

/** a shortcut spark holds pinned, with every place it is used so deleting it can say what goes with it */
data class PinnedShortcut(
    val shortcut: AppShortcut,
    val appLabel: String,
    val onHome: Boolean,
    val swipeUses: List<SwipeUse>,
) {
    val inUse: Boolean get() = onHome || swipeUses.isNotEmpty()
}

/**
 * every shortcut spark has pinned, by owning app and then name. a shortcut counts as used when it is
 * a tile in either home layout or the target of an icon swipe; one that is neither is still listed,
 * since it stays pinned until deleted. shortcuts of hidden apps, and swipes owned by hidden apps, are
 * left out so this page never names an app the user hid.
 */
internal fun pinnedShortcutsOf(
    pinned: Map<ShortcutKey, AppShortcut>,
    placed: Set<ShortcutKey>,
    overrides: Map<AppKey, AppOverride>,
    installed: List<AppInfo>,
    collator: Collator,
): List<PinnedShortcut> {
    val labelOf = installed.associate { it.key to (overrides[it.key]?.customLabel ?: it.label) }
    val appLabelFor = { packageName: String, userSerial: Long ->
        installed.firstOrNull { it.key.packageName == packageName && it.key.userSerial == userSerial }
            ?.let { labelOf.getValue(it.key) }
            ?: packageName
    }
    val hiddenOwners = overrides.values.filter { it.hidden }.map { it.key.packageName to it.key.userSerial }.toSet()
    val swipeUses = HashMap<ShortcutKey, MutableList<SwipeUse>>()
    for ((owner, override) in overrides) {
        if (override.hidden) continue
        val ownerLabel = labelOf[owner] ?: continue
        for (direction in SwipeDirection.entries) {
            val target = override.swipeFor(direction) as? SwipeTarget.Shortcut ?: continue
            swipeUses.getOrPut(target.key) { mutableListOf() }.add(SwipeUse(ownerLabel, direction))
        }
    }
    return pinned.values
        .filterNot { (it.key.packageName to it.key.userSerial) in hiddenOwners }
        .map { shortcut ->
            val uses = swipeUses[shortcut.key].orEmpty()
                .sortedWith(compareBy<SwipeUse>({ it.appLabel.lowercase() }, { it.direction }))
            PinnedShortcut(
                shortcut = shortcut,
                appLabel = appLabelFor(shortcut.key.packageName, shortcut.key.userSerial),
                onHome = shortcut.key in placed,
                swipeUses = uses,
            )
        }
        .sortedWith { a, b ->
            collator.compare(a.appLabel, b.appLabel).takeIf { it != 0 } ?: collator.compare(a.shortcut.menuLabel, b.shortcut.menuLabel)
        }
}
