package dev.gearturner.sparklauncher.core.data.db

import dev.gearturner.sparklauncher.core.model.SwipeTarget
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

// unknown keys are ignored so a target written by a newer version never breaks an older one
private val targetJson = Json { ignoreUnknownKeys = true }

internal fun SwipeTarget.encode(): String = targetJson.encodeToString(SwipeTarget.serializer(), this)

/** a corrupt or hand edited cell reads as no target, so one bad row never breaks every icon */
internal fun decodeSwipeTarget(raw: String?): SwipeTarget? {
    if (raw.isNullOrBlank()) return null
    return try {
        targetJson.decodeFromString(SwipeTarget.serializer(), raw).sanitized()
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
