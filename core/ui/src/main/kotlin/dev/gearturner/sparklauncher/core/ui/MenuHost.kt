package dev.gearturner.sparklauncher.core.ui

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.DropdownMenuPopupPositionProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.round

/**
 * one long press menu as the host draws it. the menu's owner keeps these fields current, and the
 * host reads them, so the menu can live in the launcher's own window instead of a popup window.
 */
@Stable
class HostedMenu internal constructor() {
    /** what the owner wants; the host keeps drawing a closed menu until its exit animation ends */
    var expanded by mutableStateOf(false)
        internal set

    /** the owning item's bounds in window pixels, which the menu is placed against */
    internal var anchor by mutableStateOf<IntRect?>(null)
    internal var placement by mutableStateOf<DropdownMenuPopupPositionProvider?>(null)
    internal var onDismiss by mutableStateOf<() -> Unit>({})
    internal var model by mutableStateOf<MenuModel?>(null)
}

/**
 * keeps the one open menu for the launcher window. menus used to be popup windows, but android
 * plays a window animation on every new window and defers removing one until it ends, so a drag
 * that began while a menu was opening left it stuck on screen. drawing in the same window avoids that.
 */
@Stable
class MenuHost {
    var current by mutableStateOf<HostedMenu?>(null)
        private set

    /** true while a menu is open and not yet closing, which is when back should close it */
    val isOpen: Boolean get() = current?.expanded == true

    /** shows [menu], closing any other open menu first so their owners stay in step */
    fun show(menu: HostedMenu) {
        val previous = current
        if (previous != null && previous !== menu && previous.expanded) {
            previous.expanded = false
            previous.onDismiss()
        }
        menu.expanded = true
        current = menu
    }

    /** starts closing [menu]; it stays drawn while its exit animation plays */
    fun hide(menu: HostedMenu) {
        menu.expanded = false
    }

    /** asks the open menu's owner to close it, as a tap outside or the back button does */
    fun dismiss() {
        current?.takeIf { it.expanded }?.onDismiss?.invoke()
    }

    /** drops [menu] at once, with no exit animation, when its owner leaves or a drag begins */
    fun remove(menu: HostedMenu) {
        menu.expanded = false
        if (current === menu) current = null
    }

    internal fun finished(menu: HostedMenu) {
        if (current === menu && !menu.expanded) current = null
    }

    internal fun newMenu(): HostedMenu = HostedMenu()
}

val LocalMenuHost = staticCompositionLocalOf<MenuHost?> { null }

/**
 * draws the open menu above everything it is placed after. it must fill the launcher window, since
 * menus are positioned in window pixels. while a menu is open, a press anywhere outside it closes
 * the menu and goes no further, matching how the popup menus behaved.
 */
@Composable
fun MenuHostLayer(host: MenuHost, dragController: DragController?, modifier: Modifier = Modifier) {
    val menu = host.current ?: return
    // a drag lifts the item out from under its menu, so the menu goes in the same frame, unanimated
    if (dragController?.isDragging == true) {
        SideEffect {
            if (menu.expanded) menu.onDismiss()
            host.remove(menu)
        }
        return
    }
    val transition = remember(menu) { MutableTransitionState(false) }
    transition.targetState = menu.expanded
    if (!transition.currentState && !transition.targetState && transition.isIdle) {
        SideEffect { host.finished(menu) }
        return
    }
    val anchor = menu.anchor
    val placement = menu.placement
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    val paneLabel = stringResource(R.string.ui_menu)
    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow().round() },
    ) {
        if (menu.expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(menu) {
                        awaitEachGesture {
                            // the whole press is taken so the item underneath does not also react to it
                            awaitFirstDown(requireUnconsumed = false).consume()
                            host.dismiss()
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            )
        }
        if (anchor != null && placement != null) {
            Layout(
                content = {
                    MenuContent(
                        modifier = Modifier.semantics {
                            paneTitle = paneLabel
                            dismiss {
                                host.dismiss()
                                true
                            }
                        },
                        expandedState = transition,
                        transformOrigin = { placement.transformOrigin },
                        content = { menu.model?.let { MenuItems(it) } },
                    )
                },
            ) { measurables, constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val content = measurables.single().measure(Constraints(maxWidth = width, maxHeight = height))
                val windowSize = IntSize(width + origin.x, height + origin.y)
                val position = placement.calculatePosition(
                    anchorBounds = anchor,
                    windowSize = windowSize,
                    layoutDirection = layoutDirection,
                    popupContentSize = IntSize(content.width, content.height),
                )
                layout(width, height) {
                    content.place(position - origin)
                }
            }
        }
    }
}

/**
 * the same frame material's popup menu draws its items in: as wide as the widest item, growing in
 * from 80 percent scale while fading in, around the point nearest the item it opened from.
 */
@Composable
private fun MenuContent(
    modifier: Modifier,
    expandedState: MutableTransitionState<Boolean>,
    transformOrigin: () -> TransformOrigin,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val transition = rememberTransition(expandedState, label = "menu")
    val scale by transition.animateFloat(transitionSpec = { motion.fastSpatialSpec() }, label = "menu_scale") { open ->
        if (open) 1f else CLOSED_SCALE
    }
    val alpha by transition.animateFloat(transitionSpec = { motion.fastEffectsSpec() }, label = "menu_alpha") { open ->
        if (open) 1f else 0f
    }
    Column(
        modifier = modifier
            .width(IntrinsicSize.Max)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                this.transformOrigin = transformOrigin()
            },
        content = content,
    )
}

private const val CLOSED_SCALE = 0.8f
