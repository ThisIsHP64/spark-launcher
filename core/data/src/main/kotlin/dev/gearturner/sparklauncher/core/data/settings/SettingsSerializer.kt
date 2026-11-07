package dev.gearturner.sparklauncher.core.data.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import dev.gearturner.sparklauncher.core.model.LauncherSettings
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal object SettingsSerializer : Serializer<LauncherSettings> {

    // unknown keys are tolerated so a downgrade after an update never wipes preferences
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: LauncherSettings = LauncherSettings()

    override suspend fun readFrom(input: InputStream): LauncherSettings = try {
        json.decodeFromString(LauncherSettings.serializer(), input.readBytes().decodeToString()).sanitized()
    } catch (e: SerializationException) {
        throw CorruptionException("settings file could not be decoded", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("settings file has invalid values", e)
    }

    override suspend fun writeTo(t: LauncherSettings, output: OutputStream) {
        output.write(json.encodeToString(LauncherSettings.serializer(), t).encodeToByteArray())
    }
}
