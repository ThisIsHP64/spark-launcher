package dev.gearturner.sparklauncher.core.data

import androidx.datastore.core.DataStore
import dev.gearturner.sparklauncher.core.model.LauncherSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Singleton
class SettingsRepository @Inject constructor(
    private val store: DataStore<LauncherSettings>,
) {
    val settings: Flow<LauncherSettings> = store.data

    suspend fun current(): LauncherSettings = store.data.first()

    /** sanitizing on every write keeps the stored file valid no matter which screen changed it */
    suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
        store.updateData { transform(it).sanitized() }
    }
}
