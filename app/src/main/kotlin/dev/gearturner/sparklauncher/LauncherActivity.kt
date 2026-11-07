package dev.gearturner.sparklauncher

import android.content.Intent
import android.os.Bundle
import android.os.UserManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.util.Consumer
import dagger.hilt.android.AndroidEntryPoint
import dev.gearturner.sparklauncher.core.icons.IconRepository
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.theme.SparkTheme
import dev.gearturner.sparklauncher.core.ui.HomeIconRegistry
import dev.gearturner.sparklauncher.core.ui.LocalHomeIconRegistry
import dev.gearturner.sparklauncher.core.ui.LocalIconRepository
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@AndroidEntryPoint
class LauncherActivity : ComponentActivity() {

    @Inject
    lateinit var iconRepository: IconRepository

    @Inject
    lateinit var userManager: UserManager

    // owned by the activity so the swipe home handshake can be answered before compose recomposes
    private val iconRegistry = HomeIconRegistry()
    private var floatingIcon: FloatingIconSurface? = null

    // only the latest press matters; older ones are dropped rather than queued up
    private val homePresses = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val homePressEvents = homePresses.asSharedFlow()

    private val homeIntentListener = Consumer<Intent> { intent ->
        answerGestureContract(intent)
        // home intents also arrive when returning from another app; only presses made while
        // already looking at home should close the drawer or scroll, matching aosp's check
        val broughtToFront = intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT != 0
        val alreadyOnHome = hasWindowFocus() && !broughtToFront
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME) && alreadyOnHome) {
            homePresses.tryEmit(Unit)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        addOnNewIntentListener(homeIntentListener)
        answerGestureContract(intent)
        setContent {
            SparkTheme {
                CompositionLocalProvider(
                    LocalIconRepository provides iconRepository,
                    LocalHomeIconRegistry provides iconRegistry,
                ) {
                    LauncherRoot(homePresses = homePressEvents)
                }
            }
        }
    }

    /**
     * replies with where the closing app's icon sits so the system animates the window into it.
     * when the icon is not on the visible page or dock nothing is sent and the system uses its
     * default animation, which is the right outcome for an app with no visible icon.
     */
    private fun answerGestureContract(intent: Intent?) {
        val contract = GestureNavContract.consume(intent) ?: return
        val key = AppKey(
            packageName = contract.component.packageName,
            className = contract.component.className,
            userSerial = userManager.getSerialNumberForUser(contract.user),
        )
        val target = iconRegistry.findOnScreen(key) ?: return
        val bitmap = target.anchor.icon?.asAndroidBitmap()
        floatingIcon?.dismiss()
        if (bitmap == null) {
            // without a rendered icon the window still lands in the right place, just without the crossfade
            contract.sendEndPosition(target.bounds, iconSurface = null, onFinished = null)
            return
        }
        target.anchor.hidden = true
        floatingIcon = FloatingIconSurface(
            activity = this,
            bounds = target.bounds,
            icon = bitmap,
            onReady = { surface, onFinished -> contract.sendEndPosition(target.bounds, surface, onFinished) },
            onDismissed = { target.anchor.hidden = false },
        ).also { it.show() }
    }

    override fun onPause() {
        // a new interaction replaces any stand in icon left from an interrupted animation
        floatingIcon?.dismiss()
        floatingIcon = null
        super.onPause()
    }

    override fun onDestroy() {
        floatingIcon?.dismiss()
        removeOnNewIntentListener(homeIntentListener)
        super.onDestroy()
    }
}
