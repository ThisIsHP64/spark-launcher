package dev.gearturner.sparklauncher.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gearturner.sparklauncher.core.model.AppInfo
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.AppOverride
import dev.gearturner.sparklauncher.core.model.AppShortcut
import dev.gearturner.sparklauncher.core.model.ShortcutKey
import dev.gearturner.sparklauncher.core.model.SwipeDirection
import dev.gearturner.sparklauncher.core.model.SwipeTarget

/** a configured swipe target with the name to show for it */
@Immutable
class SwipeChoice(val target: SwipeTarget, val label: String)

/**
 * a stored target as the user should see it, or null when its app or shortcut is gone, which hands
 * the swipe back to its default meaning instead of leaving one that fails. [pinned] is null when the
 * system's shortcut list is unknown, and a stored shortcut is then trusted as is.
 */
fun resolveSwipe(
    target: SwipeTarget?,
    apps: Map<AppKey, AppInfo>,
    overrides: Map<AppKey, AppOverride>,
    pinned: Map<ShortcutKey, AppShortcut>?,
): SwipeChoice? = when (target) {
    null -> null
    is SwipeTarget.App -> apps[target.key]?.let { SwipeChoice(target, overrides[it.key]?.customLabel ?: it.label) }
    is SwipeTarget.Shortcut -> {
        val live = pinned?.get(target.key)
        if (pinned != null && live == null) null else SwipeChoice(target, live?.shortLabel ?: target.key.shortcutId)
    }
}

/**
 * the shortcuts a swipe can open, in the two groups the picker shows. [pinned] are the ones other apps
 * handed to spark, such as websites from a browser, and [placed] are those already tiles on home.
 */
class ShortcutChoices(
    val appShortcuts: List<AppShortcut>,
    val pinned: List<AppShortcut>,
    val placed: Set<ShortcutKey>,
) {
    /** pinned shortcuts that are not already one tap away on home; the current choice always stays listed */
    fun pinnedFor(selected: ShortcutKey?): List<AppShortcut> = pinned.filter { it.key !in placed || it.key == selected }
}

/**
 * sorts every launchable shortcut into the two groups. a shortcut spark has pinned is listed once, as
 * pinned, and pinned ones the system could not label or that are disabled are left out.
 */
fun splitShortcuts(all: List<AppShortcut>, pinned: Collection<AppShortcut>, placed: Set<ShortcutKey>): ShortcutChoices {
    val pinnedKeys = pinned.mapTo(HashSet()) { it.key }
    return ShortcutChoices(
        appShortcuts = all.filter { it.key !in pinnedKeys },
        pinned = pinned.filter { it.isEnabled && it.hasLabel },
        placed = placed,
    )
}

private enum class Step { Directions, Kind, App, Shortcut }

private enum class Kind { None, App, AppShortcut, PinnedShortcut }

/**
 * sets what swiping up and down on one app's icon does. each direction opens a short flow: none, an
 * app, or one of the app shortcuts. the dialog stays open after a choice so the other direction can
 * be set right away.
 */
@Composable
fun SwipeActionsDialog(
    appLabel: String,
    up: SwipeChoice?,
    down: SwipeChoice?,
    apps: List<AppInfo>,
    hasShortcutAccess: Boolean,
    loadShortcuts: suspend () -> ShortcutChoices,
    onSet: (SwipeDirection, SwipeTarget?) -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(Step.Directions) }
    var direction by remember { mutableStateOf(SwipeDirection.UP) }
    var shortcutKind by remember { mutableStateOf(Kind.AppShortcut) }
    val current = if (direction == SwipeDirection.UP) up else down
    val none = stringResource(R.string.ui_swipe_none)
    val backToDirections = { step = Step.Directions }
    when (step) {
        Step.Directions -> ChoiceDialog(
            title = stringResource(R.string.ui_swipe_title, appLabel),
            choices = listOf(
                Choice(SwipeDirection.UP, stringResource(R.string.ui_swipe_up), supporting = up?.label ?: none),
                Choice(SwipeDirection.DOWN, stringResource(R.string.ui_swipe_down), supporting = down?.label ?: none),
            ),
            selected = null,
            onSelect = {
                direction = it
                step = Step.Kind
            },
            onDismiss = onDismiss,
        )
        Step.Kind -> {
            // which shortcut group a stored shortcut belongs to is only known once they load, so none is marked
            val kind = when (current?.target) {
                null -> Kind.None
                is SwipeTarget.App -> Kind.App
                is SwipeTarget.Shortcut -> null
            }
            ChoiceDialog(
                title = stringResource(if (direction == SwipeDirection.UP) R.string.ui_swipe_up else R.string.ui_swipe_down),
                choices = listOf(
                    Choice(Kind.None, none),
                    Choice(Kind.App, stringResource(R.string.ui_swipe_kind_app)),
                    Choice(Kind.AppShortcut, stringResource(R.string.ui_swipe_kind_app_shortcut)),
                    Choice(Kind.PinnedShortcut, stringResource(R.string.ui_swipe_kind_pinned_shortcut)),
                ),
                selected = kind,
                onSelect = { picked ->
                    when (picked) {
                        Kind.None -> {
                            onSet(direction, null)
                            step = Step.Directions
                        }
                        Kind.App -> step = Step.App
                        Kind.AppShortcut, Kind.PinnedShortcut -> {
                            shortcutKind = picked
                            step = Step.Shortcut
                        }
                    }
                },
                onDismiss = backToDirections,
            )
        }
        Step.App -> ChoiceDialog(
            title = stringResource(R.string.ui_swipe_pick_app),
            choices = apps.map { app -> Choice(app.key, app.label, leading = { AppChoiceIcon(app.key) }) },
            selected = (current?.target as? SwipeTarget.App)?.key,
            onSelect = { key ->
                onSet(direction, SwipeTarget.App(key))
                step = Step.Directions
            },
            onDismiss = backToDirections,
        )
        Step.Shortcut -> ShortcutStep(
            pinned = shortcutKind == Kind.PinnedShortcut,
            selected = (current?.target as? SwipeTarget.Shortcut)?.key,
            apps = apps,
            hasShortcutAccess = hasShortcutAccess,
            loadShortcuts = loadShortcuts,
            onPick = { shortcut ->
                onSet(direction, SwipeTarget.Shortcut(shortcut.key))
                step = Step.Directions
            },
            onDismiss = backToDirections,
        )
    }
}

@Composable
private fun ShortcutStep(
    pinned: Boolean,
    selected: ShortcutKey?,
    apps: List<AppInfo>,
    hasShortcutAccess: Boolean,
    loadShortcuts: suspend () -> ShortcutChoices,
    onPick: (AppShortcut) -> Unit,
    onDismiss: () -> Unit,
) {
    // null while loading, so the empty state message never flashes before the list arrives
    val loaded by produceState<ShortcutChoices?>(null) { value = loadShortcuts() }
    val labels = remember(apps) { apps.associate { (it.key.packageName to it.key.userSerial) to it.label } }
    val owners = remember(apps) { apps.associateBy { it.key.packageName to it.key.userSerial } }
    val sorted = remember(loaded, labels, pinned, selected) {
        val list = loaded?.let { if (pinned) it.pinnedFor(selected) else it.appShortcuts }.orEmpty()
        list.sortedWith(
            compareBy<AppShortcut>({ labels[it.key.packageName to it.key.userSerial].orEmpty().lowercase() }, { it.menuLabel.lowercase() }),
        )
    }
    ChoiceDialog(
        title = stringResource(if (pinned) R.string.ui_swipe_pick_pinned_shortcut else R.string.ui_swipe_pick_app_shortcut),
        choices = sorted.map { shortcut ->
            Choice(
                value = shortcut,
                label = shortcut.menuLabel,
                supporting = labels[shortcut.key.packageName to shortcut.key.userSerial],
                leading = { ShortcutChoiceIcon(shortcut.key, owners[shortcut.key.packageName to shortcut.key.userSerial]?.key) },
            )
        },
        selected = sorted.firstOrNull { it.key == selected },
        onSelect = onPick,
        onDismiss = onDismiss,
        footer = {
            if (loaded != null && sorted.isEmpty()) {
                val message = when {
                    !hasShortcutAccess -> R.string.ui_swipe_needs_default
                    pinned -> R.string.ui_swipe_no_pinned_shortcuts
                    else -> R.string.ui_swipe_no_shortcuts
                }
                Text(
                    text = stringResource(message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
    )
}
