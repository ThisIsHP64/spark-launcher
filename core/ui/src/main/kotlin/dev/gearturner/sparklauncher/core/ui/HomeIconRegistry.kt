package dev.gearturner.sparklauncher.core.ui

import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.gearturner.sparklauncher.core.model.AppKey

/**
 * tracks where each home and dock icon is on screen so the swipe home gesture can shrink an app
 * back into its own icon. only icons on the visible page or the dock count, because the system
 * animates to whatever rectangle we report and an off screen page would send it nowhere useful.
 */
@Stable
class HomeIconRegistry {

    private class Entry(val key: AppKey, val page: Int, val anchor: LaunchAnchor)

    private val entries = HashMap<Long, Entry>()

    /** the page the user is looking at, updated by the home pager */
    var currentPage: Int = 0

    internal fun register(itemId: Long, key: AppKey, page: Int, anchor: LaunchAnchor) {
        entries[itemId] = Entry(key, page, anchor)
    }

    internal fun unregister(itemId: Long, anchor: LaunchAnchor) {
        // a recomposed item may have registered a new anchor under the same id before this disposal ran
        if (entries[itemId]?.anchor === anchor) entries.remove(itemId)
    }

    class Target(val bounds: RectF, val anchor: LaunchAnchor)

    /** dock icons win because they are always visible, then the current page */
    fun findOnScreen(key: AppKey): Target? {
        val candidates = entries.values.filter { it.key == key }
        val ordered = candidates.filter { it.page == DOCK_PAGE } + candidates.filter { it.page == currentPage }
        return ordered.firstNotNullOfOrNull { entry -> entry.anchor.boundsOnScreen()?.let { Target(it, entry.anchor) } }
    }

    companion object {
        const val DOCK_PAGE = -1
    }
}

val LocalHomeIconRegistry = staticCompositionLocalOf<HomeIconRegistry?> { null }

/** keeps one home icon registered for as long as it is composed */
@Composable
fun RegisterHomeIcon(itemId: Long, key: AppKey, page: Int, anchor: LaunchAnchor) {
    val registry = LocalHomeIconRegistry.current ?: return
    DisposableEffect(registry, itemId, key, page, anchor) {
        registry.register(itemId, key, page, anchor)
        onDispose { registry.unregister(itemId, anchor) }
    }
}
