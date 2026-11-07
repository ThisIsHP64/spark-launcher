package dev.gearturner.sparklauncher.core.data

import dev.gearturner.sparklauncher.core.data.db.AppOverrideDao
import dev.gearturner.sparklauncher.core.data.db.AppOverrideEntity
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.AppOverride
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class AppOverridesRepository @Inject constructor(
    private val dao: AppOverrideDao,
) {
    // read modify write per app, serialized so hiding and renaming at once cannot drop either change
    private val lock = Mutex()

    val overrides: Flow<Map<AppKey, AppOverride>> = dao.observeAll().map { rows ->
        rows.associate { row ->
            val key = AppKey(row.packageName, row.className, row.userSerial)
            key to AppOverride(key, row.hidden, row.customLabel)
        }
    }

    suspend fun setHidden(key: AppKey, hidden: Boolean) = update(key) { it.copy(hidden = hidden) }

    /** a blank label clears the override so the app's own name comes back */
    suspend fun setLabel(key: AppKey, label: String?) = update(key) {
        it.copy(customLabel = label?.trim()?.take(MAX_LABEL_LENGTH)?.ifEmpty { null })
    }

    private suspend fun update(key: AppKey, transform: (AppOverrideEntity) -> AppOverrideEntity) = lock.withLock {
        val current = dao.get(key.packageName, key.className, key.userSerial)
            ?: AppOverrideEntity(key.packageName, key.className, key.userSerial)
        val next = transform(current)
        // rows that override nothing are deleted so the table only holds real choices
        if (!next.hidden && next.customLabel == null) {
            dao.delete(key.packageName, key.className, key.userSerial)
        } else {
            dao.upsert(next)
        }
    }

    companion object {
        const val MAX_LABEL_LENGTH = 48
    }
}

/** applies a label override to a display name, used by every surface that shows app names */
fun Map<AppKey, AppOverride>.labelFor(key: AppKey, fallback: String): String = this[key]?.customLabel ?: fallback
