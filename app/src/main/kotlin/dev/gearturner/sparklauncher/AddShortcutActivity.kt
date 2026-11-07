package dev.gearturner.sparklauncher

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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.AppShortcut
import dev.gearturner.sparklauncher.core.model.ShortcutKey
import dev.gearturner.sparklauncher.core.theme.SparkTheme
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * confirms a shortcut another app asked to pin, such as a website from a browser. the system sends
 * these only to the default home app, and nothing is placed until the user taps add.
 */
@AndroidEntryPoint
class AddShortcutActivity : ComponentActivity() {

    @Inject lateinit var launcherApps: LauncherApps

    @Inject lateinit var userManager: UserManager

    @Inject lateinit var apps: AppsRepository

    @Inject lateinit var placement: ShortcutPlacement

    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // the request object comes from outside the app, so anything unexpected simply closes the dialog
        val request = try {
            launcherApps.getPinItemRequest(intent)
        } catch (e: RuntimeException) {
            Log.w(TAG, "unreadable pin request", e)
            null
        }
        val info = request?.takeIf {
            it.isValid && it.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT
        }?.shortcutInfo
        if (request == null || info == null) {
            finish()
            return
        }
        val label = info.shortLabel?.toString().orEmpty().ifEmpty { info.id }
        val icon = loadIcon(info)
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
                            if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(56.dp))
                            Text(label, textAlign = TextAlign.Center)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { accept(request, info, label) }) {
                            Text(stringResource(R.string.add_shortcut_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = ::finish) { Text(stringResource(R.string.add_shortcut_cancel)) }
                    },
                )
            }
        }
    }

    private fun accept(request: LauncherApps.PinItemRequest, info: ShortcutInfo, label: String) {
        lifecycleScope.launch {
            val accepted = try {
                request.accept()
            } catch (e: RuntimeException) {
                Log.w(TAG, "pin request could not be accepted", e)
                false
            }
            val serial = userManager.getSerialNumberForUser(info.userHandle)
            val owner = ownerFor(info, serial)
            val message = if (!accepted || owner == null) {
                R.string.add_shortcut_failed
            } else {
                val shortcut = AppShortcut(ShortcutKey(info.`package`, info.id, serial), label, null, info.rank, true)
                val current = settings.current()
                when (placement.addAccepted(shortcut, owner, current.homeColumns, current.homeRows)) {
                    is AddResult.Added -> R.string.add_shortcut_added
                    AddResult.AlreadyOnHome -> R.string.add_shortcut_already
                    AddResult.NoSpace -> R.string.add_shortcut_no_space
                }
            }
            Toast.makeText(this@AddShortcutActivity, message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /** a shortcut is shown on behalf of a launchable activity of its app, which also supplies its badge */
    private suspend fun ownerFor(info: ShortcutInfo, serial: Long): AppKey? {
        info.activity?.let { activity -> return AppKey(activity.packageName, activity.className, serial) }
        return apps.awaitApps().firstOrNull { it.key.packageName == info.`package` && it.key.userSerial == serial }?.key
    }

    private fun loadIcon(info: ShortcutInfo): ImageBitmap? = try {
        launcherApps.getShortcutIconDrawable(info, resources.displayMetrics.densityDpi)
            ?.toBitmap()
            ?.asImageBitmap()
    } catch (e: RuntimeException) {
        Log.w(TAG, "pin request icon failed", e)
        null
    }

    private companion object {
        const val TAG = "AddShortcutActivity"
    }
}
