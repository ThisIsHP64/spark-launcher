package dev.gearturner.sparklauncher

import androidx.compose.material3.FilledTonalButton
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Bundle
import android.os.UserManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.gearturner.sparklauncher.core.apps.AppsRepository
import dev.gearturner.sparklauncher.core.data.AddResult
import dev.gearturner.sparklauncher.core.data.SettingsRepository
import dev.gearturner.sparklauncher.core.data.ShortcutPlacement
import dev.gearturner.sparklauncher.core.data.WorkspaceRepository
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.AppShortcut
import dev.gearturner.sparklauncher.core.model.DropPlan
import dev.gearturner.sparklauncher.core.model.DropTarget
import dev.gearturner.sparklauncher.core.model.Dragged
import dev.gearturner.sparklauncher.core.model.ShortcutKey
import dev.gearturner.sparklauncher.core.model.defaultSpan
import dev.gearturner.sparklauncher.core.model.SUBGRID
import dev.gearturner.sparklauncher.core.model.Span
import dev.gearturner.sparklauncher.core.theme.SparkTheme
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

    @Inject lateinit var placement: ShortcutPlacement

    @Inject lateinit var workspace: WorkspaceRepository

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var widgetHost: WidgetHostManager

    @Inject lateinit var widgets: WidgetRepository

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
        showDialog(label, onAdd = { acceptShortcut(request, info, label) }) {
            if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(56.dp))
        }
    }

    private fun showWidget(request: LauncherApps.PinItemRequest, provider: WidgetProvider) {
        showDialog(provider.label, onAdd = { acceptWidget(request, provider) }) {
            Column(modifier = Modifier.heightIn(max = 200.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val (cellWidth, cellHeight) = approximateCellDp()
                WidgetPreview(provider, (cellWidth * 2).dp, (cellHeight * 2).dp, 160.dp)
            }
        }
    }

    private fun showDialog(label: String, onAdd: () -> Unit, preview: @Composable () -> Unit) {
        setContent {
            SparkTheme {
                AlertDialog(
                    onDismissRequest = ::finish,
                    title = { Text(stringResource(R.string.add_shortcut_title)) },
                    text = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            preview()
                            Text(label, textAlign = TextAlign.Center)
                        }
                    },
                    confirmButton = { FilledTonalButton(onClick = onAdd) { Text(stringResource(R.string.add_shortcut_confirm)) } },
                    dismissButton = { TextButton(onClick = ::finish) { Text(stringResource(R.string.add_shortcut_cancel)) } },
                )
            }
        }
    }

    private fun acceptShortcut(request: LauncherApps.PinItemRequest, info: ShortcutInfo, label: String) {
        lifecycleScope.launch {
            val accepted = acceptSafely { request.accept() }
            val serial = userManager.getSerialNumberForUser(info.userHandle)
            val owner = ownerFor(info, serial)
            val message = if (!accepted || owner == null) {
                R.string.add_shortcut_failed
            } else {
                val shortcut = AppShortcut(ShortcutKey(info.`package`, info.id, serial), label, null, info.rank, true)
                val current = settings.current()
                messageFor(placement.addAccepted(shortcut, owner, current.homeColumns, current.homeRows))
            }
            finishWith(message)
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
        const val DEFAULT_COLUMNS = 4
        const val DEFAULT_ROWS = 5
        // status bar, page dots, dock and nav bar together take roughly this much height
        const val RESERVED_HEIGHT_DP = 220f
    }
}
