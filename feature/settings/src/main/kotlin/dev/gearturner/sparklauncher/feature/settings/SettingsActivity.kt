package dev.gearturner.sparklauncher.feature.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.gearturner.sparklauncher.core.icons.IconRepository
import dev.gearturner.sparklauncher.core.theme.SparkTheme
import dev.gearturner.sparklauncher.core.ui.LocalIconRepository
import dev.gearturner.sparklauncher.core.ui.ProvideIconStyle
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    @Inject lateinit var iconRepository: IconRepository

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val loaded = settings ?: return@setContent
            // settings are themed by themselves, so every change previews right here
            SparkTheme(loaded) {
                CompositionLocalProvider(LocalIconRepository provides iconRepository) {
                    ProvideIconStyle(loaded) {
                        SettingsScreen(loaded, viewModel, onBack = ::finish)
                    }
                }
            }
        }
    }
}
