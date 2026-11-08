package dev.gearturner.sparklauncher.feature.search

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gearturner.sparklauncher.core.apps.AppsRepository
import dev.gearturner.sparklauncher.core.apps.LaunchRequest
import dev.gearturner.sparklauncher.core.apps.LaunchResult
import dev.gearturner.sparklauncher.core.apps.ShortcutsRepository
import dev.gearturner.sparklauncher.core.data.SettingsRepository
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.AppShortcut
import dev.gearturner.sparklauncher.core.model.LauncherSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

/** a shortcut result with the app it belongs to, so the row can name and badge its owner */
data class ShortcutHit(val shortcut: AppShortcut, val owner: AppKey?, val appLabel: String?)

/** everything search found beyond apps, which the drawer ranks itself */
data class SearchResults(
    val query: String = "",
    val calculation: String? = null,
    val shortcuts: List<ShortcutHit> = emptyList(),
    val contacts: List<ContactHit> = emptyList(),
    val settings: List<SettingHit> = emptyList(),
) {
    val isEmpty: Boolean get() = calculation == null && shortcuts.isEmpty() && contacts.isEmpty() && settings.isEmpty()
}

/**
 * every source runs on the device. each one is isolated so a failure in one, such as revoked
 * contacts access, leaves the others showing.
 */
@Singleton
class SearchRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shortcuts: ShortcutsRepository,
    private val apps: AppsRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val contacts = ContactSearch(context)

    // all shortcuts are read once per search session; a blank query ends the session and drops them
    @Volatile
    private var shortcutCache: List<AppShortcut>? = null

    fun hasContactsPermission(): Boolean = contacts.hasPermission()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun results(query: Flow<String>): Flow<SearchResults> =
        combine(
            // typing fast should not query contacts for every letter, but clearing must be instant
            query.distinctUntilChanged().debounce { if (it.isBlank()) 0L else DEBOUNCE_MS },
            settingsRepository.settings,
        ) { q, s -> q to s }
            .mapLatest { (q, s) -> search(q, s) }
            .flowOn(Dispatchers.IO)

    private suspend fun search(query: String, settings: LauncherSettings): SearchResults {
        if (query.isBlank()) {
            shortcutCache = null
            return SearchResults()
        }
        return SearchResults(
            query = query,
            calculation = guarded("calculator") { Calculator.evaluate(query) },
            shortcuts = if (settings.searchShortcuts) guarded("shortcuts") { searchShortcuts(query) }.orEmpty() else emptyList(),
            contacts = if (settings.searchContacts) guarded("contacts") { contacts.search(query) }.orEmpty() else emptyList(),
            settings = if (settings.searchSettings) {
                guarded("settings") { SettingsSearch.search(SettingsSearch.resolve(context.resources), query) }.orEmpty()
            } else {
                emptyList()
            },
        )
    }

    private suspend fun searchShortcuts(query: String): List<ShortcutHit> {
        val all = shortcutCache ?: shortcuts.allLaunchable().also { shortcutCache = it }
        val installed = apps.apps.value.orEmpty()
        val owners = installed.associateBy { it.key.packageName to it.key.userSerial }
        val hits = all.map { shortcut ->
            val owner = owners[shortcut.key.packageName to shortcut.key.userSerial]
            ShortcutHit(shortcut, owner?.key, owner?.label)
        }
        // the app name counts as a keyword, so searching "maps" offers that app's shortcuts too
        return rankMatches(
            hits,
            query,
            label = { it.shortcut.shortLabel },
            keywords = { listOfNotNull(it.shortcut.longLabel, it.appLabel) },
        ).take(MAX_SHORTCUTS)
    }

    private inline fun <T> guarded(source: String, block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        Log.w(TAG, "$source search failed", e)
        null
    }

    fun openShortcut(hit: ShortcutHit, request: LaunchRequest = LaunchRequest()): Boolean =
        shortcuts.launch(hit.shortcut.key, request) is LaunchResult.Started

    fun openContact(hit: ContactHit): Boolean = start(Intent(Intent.ACTION_VIEW, hit.lookupUri))

    fun openSetting(hit: SettingHit): Boolean = start(Intent(hit.action))

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private companion object {
        const val TAG = "SearchRepository"
        const val DEBOUNCE_MS = 100L
        const val MAX_SHORTCUTS = 4
    }
}
