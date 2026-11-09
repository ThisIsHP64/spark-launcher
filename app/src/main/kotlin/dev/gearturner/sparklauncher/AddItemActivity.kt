package dev.gearturner.sparklauncher

import androidx.compose.material3.FilledTonalButton
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.os.Bundle
import android.os.UserManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.gearturner.sparklauncher.core.apps.AppsRepository
import dev.gearturner.sparklauncher.core.apps.ShortcutsRepository
import dev.gearturner.sparklauncher.core.icons.IconRepository
import dev.gearturner.sparklauncher.core.data.AddResult
import dev.gearturner.sparklauncher.core.data.SettingsRepository
import dev.gearturner.sparklauncher.core.data.ShortcutPlacement
import dev.gearturner.sparklauncher.core.data.WorkspaceRepository
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.AppInfo
import dev.gearturner.sparklauncher.core.model.AppShortcut
import dev.gearturner.sparklauncher.core.model.DropPlan
import dev.gearturner.sparklauncher.core.model.DropTarget
import dev.gearturner.sparklauncher.core.model.Dragged
import dev.gearturner.sparklauncher.core.model.ShortcutKey
import dev.gearturner.sparklauncher.core.model.SwipeDirection
import dev.gearturner.sparklauncher.core.model.SwipeTarget
import dev.gearturner.sparklauncher.core.model.defaultSpan
import dev.gearturner.sparklauncher.core.model.SUBGRID
import dev.gearturner.sparklauncher.core.model.Span
import dev.gearturner.sparklauncher.core.theme.SparkTheme
import dev.gearturner.sparklauncher.core.ui.AppChoiceIcon
import dev.gearturner.sparklauncher.core.ui.Choice
import dev.gearturner.sparklauncher.core.ui.ChoiceDialog
import dev.gearturner.sparklauncher.core.ui.LocalIconRepository
import dev.gearturner.sparklauncher.feature.widgets.WidgetHostManager
import dev.gearturner.sparklauncher.feature.widgets.WidgetPreview
import dev.gearturner.sparklauncher.feature.widgets.WidgetProvider
import dev.gearturner.sparklauncher.feature.widgets.WidgetRepository
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * confirms a shortcut or widget another app asked to pin, such as a website from a browser. the
 * system sends these only to the default home app, and nothing is placed until the user taps add.
 */
@AndroidEntryPoint
class AddItemActivity : ComponentActivity() {

    @Inject lateinit var launcherApps: LauncherApps

    @Inject lateinit var userManager: UserManager

    @Inject lateinit var apps: AppsRepository

    @Inject lateinit var shortcuts: ShortcutsRepository

    @Inject lateinit var placement: ShortcutPlacement

    @Inject lateinit var workspace: WorkspaceRepository

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var widgetHost: WidgetHostManager

    @Inject lateinit var widgets: WidgetRepository

    @Inject lateinit var iconRepository: IconRepository

    private lateinit var widgetBinder: WidgetBindFlow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetBinder = WidgetBindFlow(this, widgetHost)
        // the request object comes from outside the app, so anything unexpected simply closes the dialog
        val request = try {
            launcherApps.getPinItemRequest(intent)?.takeIf { it.isValid }
        } catch (e: RuntimeException) {
            Log.w(TAG, "unreadable pin request", e)
            null
        }
        when (request?.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> request.shortcutInfo?.let { showShortcut(request, it) } ?: finish()
            LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET ->
                request.getAppWidgetProviderInfo(this)?.let { showWidget(request, widgets.toProvider(it)) } ?: finish()
            else -> finish()
        }
    }

    // binding a widget may ask the user for permission, which reports back here
    @Deprecated("routes widget bind results, which have no activity result api equivalent")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!widgetBinder.onActivityResult(requestCode, resultCode)) {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun showShortcut(request: LauncherApps.PinItemRequest, info: ShortcutInfo) {
        val label = info.shortLabel?.toString().orEmpty().ifEmpty { info.id }
        val icon = loadIcon(info)
        showSheet(
            label = label,
            sourceLabel = sourceLabel(info.`package`, info.userHandle),
            onAdd = { acceptShortcut(request, info) },
            onUseForSwipe = { acceptForSwipe(request, info) },
        ) {
            // the system already masks shortcut icons, so the plate only frames them
            Box(
                modifier = Modifier
                    .size(ICON_PLATE)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(56.dp))
            }
        }
    }

    private fun showWidget(request: LauncherApps.PinItemRequest, provider: WidgetProvider) {
        showSheet(
            label = provider.label,
            sourceLabel = sourceLabel(provider.info.provider.packageName, provider.info.profile),
            onAdd = { acceptWidget(request, provider) },
        ) {
            Box(
                modifier = Modifier
                    .heightIn(max = 200.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(28.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val (cellWidth, cellHeight) = approximateCellDp()
                WidgetPreview(provider, (cellWidth * 2).dp, (cellHeight * 2).dp, 160.dp)
            }
        }
    }

    /**
     * the confirmation as a bottom sheet over whatever app made the request. the sheet never finishes
     * on stop, because binding a widget can open a system permission screen on top of it.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    private fun showSheet(
        label: String,
        sourceLabel: String?,
        onAdd: () -> Unit,
        onUseForSwipe: (() -> Unit)? = null,
        preview: @Composable () -> Unit,
    ) {
        setContent {
            SparkTheme {
                ModalBottomSheet(
                    onDismissRequest = ::finish,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        preview()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (sourceLabel != null) {
                            Text(
                                text = stringResource(R.string.add_shortcut_from, sourceLabel),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = onAdd,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                        ) { Text(stringResource(R.string.add_shortcut_confirm)) }
                        if (onUseForSwipe != null) {
                            FilledTonalButton(
                                onClick = onUseForSwipe,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .heightIn(min = 56.dp),
                            ) { Text(stringResource(R.string.add_shortcut_use_for_swipe)) }
                        }
                        TextButton(
                            onClick = ::finish,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        ) { Text(stringResource(R.string.add_shortcut_cancel)) }
                    }
                }
            }
        }
    }

    /**
     * the name of the app that made the request, read from the system's own record of its package.
     * null when it cannot be read, so the sheet shows nothing rather than a guess.
     */
    private fun sourceLabel(packageName: String, user: UserHandle): String? = try {
        val info = launcherApps.getApplicationInfo(packageName, 0, user)
        packageManager.getApplicationLabel(info).toString().takeIf { it.isNotBlank() }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (e: RuntimeException) {
        Log.w(TAG, "could not read the requesting app", e)
        null
    }

    /**
     * this activity is exported, so any app can start it with a request it made up and answer that
     * request's own checks. only the system's pinned copy is trusted, which also means the label and
     * owning app shown on home come from the system rather than from whoever sent the request.
     */
    private fun acceptShortcut(request: LauncherApps.PinItemRequest, requested: ShortcutInfo) {
        lifecycleScope.launch {
            val accepted = acceptSafely { request.accept() }
            val pinned = if (accepted) shortcuts.pinnedShortcut(requested.`package`, requested.id, requested.userHandle) else null
            val serial = pinned?.let { userManager.getSerialNumberForUser(it.userHandle) }
            val owner = if (pinned != null && serial != null) ownerFor(pinned, serial) else null
            val message = if (pinned == null || serial == null || owner == null) {
                if (accepted && pinned == null) Log.w(TAG, "pin request was not backed by a pinned shortcut")
                R.string.add_shortcut_failed
            } else {
                val label = pinned.shortLabel?.toString().orEmpty().ifEmpty { pinned.id }
                val shortcut = AppShortcut(ShortcutKey(pinned.`package`, pinned.id, serial), label, null, pinned.rank, true)
                val current = settings.current()
                messageFor(placement.addAccepted(shortcut, owner, current.homeColumns, current.homeRows))
            }
            finishWith(message)
        }
    }

    /**
     * keeps the shortcut for a swipe instead of placing it, the way a shortcut from a widget app is
     * usually wanted. the same proof as [acceptShortcut] applies: only the system's pinned copy is
     * trusted, so a request an app made up cannot bind anything it did not really pin.
     */
    private fun acceptForSwipe(request: LauncherApps.PinItemRequest, requested: ShortcutInfo) {
        lifecycleScope.launch {
            val accepted = acceptSafely { request.accept() }
            val pinned = if (accepted) shortcuts.pinnedShortcut(requested.`package`, requested.id, requested.userHandle) else null
            val serial = pinned?.let { userManager.getSerialNumberForUser(it.userHandle) }
            if (pinned == null || serial == null) return@launch finishWith(R.string.add_shortcut_failed)
            val key = ShortcutKey(pinned.`package`, pinned.id, serial)
            showSwipePicker(key, apps.awaitApps().sortedBy { it.label.lowercase() })
        }
    }

    /** asks which app's icon gets the shortcut and in which direction, then binds it */
    private fun showSwipePicker(shortcut: ShortcutKey, installed: List<AppInfo>) {
        setContent {
            SparkTheme {
                CompositionLocalProvider(LocalIconRepository provides iconRepository) {
                    var owner by remember { mutableStateOf<AppInfo?>(null) }
                    val chosen = owner
                    if (chosen == null) {
                        ChoiceDialog(
                            title = stringResource(R.string.add_shortcut_swipe_pick_app),
                            choices = installed.map { app -> Choice(app, app.label, leading = { AppChoiceIcon(app.key) }) },
                            selected = null,
                            onSelect = { owner = it },
                            onDismiss = ::finish,
                        )
                    } else {
                        ChoiceDialog(
                            title = stringResource(R.string.add_shortcut_swipe_pick_direction, chosen.label),
                            choices = listOf(
                                Choice(SwipeDirection.UP, stringResource(R.string.add_shortcut_swipe_up)),
                                Choice(SwipeDirection.DOWN, stringResource(R.string.add_shortcut_swipe_down)),
                            ),
                            selected = null,
                            onSelect = { direction -> bindSwipe(chosen.key, direction, shortcut) },
                            onDismiss = ::finish,
                        )
                    }
                }
            }
        }
    }

    private fun bindSwipe(owner: AppKey, direction: SwipeDirection, shortcut: ShortcutKey) {
        lifecycleScope.launch {
            val saved = placement.bindSwipe(owner, direction, SwipeTarget.Shortcut(shortcut))
            finishWith(if (saved) R.string.add_shortcut_swipe_saved else R.string.add_shortcut_failed)
        }
    }

    /** binds first, then hands the id back to the requesting app, then finds the widget a spot */
    private fun acceptWidget(request: LauncherApps.PinItemRequest, provider: WidgetProvider) {
        lifecycleScope.launch {
            val current = settings.current()
            val (cellWidth, cellHeight) = approximateCellDp(current.homeColumns, current.homeRows)
            val cells = defaultSpan(provider.sizeSpec, cellWidth, cellHeight, current.homeColumns, current.homeRows)
            val span = Span(cells.x * SUBGRID, cells.y * SUBGRID)
            val spot = workspace.firstFreeDesktopSpot(span.x, span.y, current.homeColumns, current.homeRows)
            if (spot == null) return@launch finishWith(R.string.add_shortcut_no_space)
            // the app configured its widget before asking, so its setup screen is skipped
            val appWidgetId = widgetBinder.bindAndConfigure(provider.info, needsConfiguration = false)
                ?: return@launch finishWith(R.string.add_shortcut_failed)
            val extras = Bundle().apply { putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId) }
            if (!acceptSafely { request.accept(extras) }) {
                widgetHost.delete(appWidgetId)
                return@launch finishWith(R.string.add_shortcut_failed)
            }
            val plan = workspace.drop(
                Dragged.NewWidget(provider.key, appWidgetId, span),
                DropTarget.Desktop(spot.page, spot.cellX, spot.cellY),
                current.homeColumns,
                current.homeRows,
                current.dockSlots,
            )
            if (plan !is DropPlan.Place) widgetHost.delete(appWidgetId)
            finishWith(if (plan is DropPlan.Place) R.string.add_shortcut_added else R.string.add_shortcut_no_space)
        }
    }

    /**
     * this screen has no home grid to measure, so cells are estimated from the display the same
     * way home divides it: full width by columns, and the height left after bars and dock by rows
     */
    private fun approximateCellDp(columns: Int = DEFAULT_COLUMNS, rows: Int = DEFAULT_ROWS): Pair<Float, Float> {
        val config = resources.configuration
        return (config.screenWidthDp - 16f) / columns to (config.screenHeightDp - RESERVED_HEIGHT_DP) / rows
    }

    private inline fun acceptSafely(accept: () -> Boolean): Boolean = try {
        accept()
    } catch (e: RuntimeException) {
        Log.w(TAG, "pin request could not be accepted", e)
        false
    }

    private fun messageFor(result: AddResult): Int = when (result) {
        is AddResult.Added -> R.string.add_shortcut_added
        AddResult.AlreadyOnHome -> R.string.add_shortcut_already
        AddResult.NoSpace -> R.string.add_shortcut_no_space
    }

    private fun finishWith(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    /** a shortcut is shown on behalf of a launchable activity of its app, which also supplies its badge */
    private suspend fun ownerFor(info: ShortcutInfo, serial: Long): AppKey? {
        info.activity?.let { activity -> return AppKey(activity.packageName, activity.className, serial) }
        return apps.awaitApps().firstOrNull { it.key.packageName == info.`package` && it.key.userSerial == serial }?.key
    }

    private fun loadIcon(info: ShortcutInfo): ImageBitmap? = try {
        launcherApps.getShortcutIconDrawable(info, resources.displayMetrics.densityDpi)?.toBitmap()?.asImageBitmap()
    } catch (e: RuntimeException) {
        Log.w(TAG, "pin request icon failed", e)
        null
    }

    private companion object {
        const val TAG = "AddItemActivity"
        val ICON_PLATE = 88.dp
        const val DEFAULT_COLUMNS = 4
        const val DEFAULT_ROWS = 5
        // status bar, page dots, dock, and nav bar together take roughly this much height
        const val RESERVED_HEIGHT_DP = 220f
    }
}
