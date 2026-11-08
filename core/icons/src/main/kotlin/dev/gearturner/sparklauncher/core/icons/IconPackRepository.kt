package dev.gearturner.sparklauncher.core.icons

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import android.util.Xml
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import dev.gearturner.sparklauncher.core.apps.AppsRepository
import dev.gearturner.sparklauncher.core.apps.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

data class IconPackInfo(val packageName: String, val label: String)

/**
 * finds and reads icon packs. a pack's resources are read through the package manager, which
 * never loads or runs the pack's code; parsed packs are kept for the two most recently used.
 */
@Singleton
class IconPackRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    apps: AppsRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    private val parsed = LruCache<String, LoadedPack>(2)
    private val parseLock = Mutex()

    /** installed packs, kept current as apps come and go so menus can check it without waiting */
    val packs: StateFlow<List<IconPackInfo>> = apps.packageChanges
        .map { }
        .onStart { emit(Unit) }
        .conflate()
        .map { installedPacks() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private class LoadedPack(val resources: Resources, val filter: AppFilter)

    /** every installed app that declares itself an icon pack for any of the common launchers */
    suspend fun installedPacks(): List<IconPackInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        PACK_ACTIONS.flatMap { action ->
            try {
                pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA)
            } catch (e: RuntimeException) {
                emptyList()
            }
        }
            .map { it.activityInfo.packageName }
            .distinct()
            .mapNotNull { pkg ->
                try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    IconPackInfo(pkg, pm.getApplicationLabel(info).toString())
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
            }
            .sortedBy { it.label.lowercase() }
    }

    /** the pack's drawable for an app, or null when the pack has none for it */
    suspend fun iconFor(packPackage: String, component: String, density: Int): Drawable? {
        val pack = load(packPackage) ?: return null
        val name = pack.filter.components[component] ?: return null
        return drawable(pack.resources, packPackage, name, density)
    }

    suspend fun drawable(packPackage: String, name: String, density: Int): Drawable? {
        val pack = load(packPackage) ?: return null
        return drawable(pack.resources, packPackage, name, density)
    }

    /** backgrounds, overlay, mask and scale for styling apps the pack does not cover */
    suspend fun styling(packPackage: String): AppFilter? = load(packPackage)?.filter

    /** names for the manual icon picker: the pack's own list when it has one, else its mapped icons */
    suspend fun drawableNames(packPackage: String): List<String> = withContext(Dispatchers.IO) {
        val pack = load(packPackage) ?: return@withContext emptyList()
        val listed = readXml(pack.resources, packPackage, "drawable") { IconPackParser.parseDrawableList(it) }
        listed?.takeIf { it.isNotEmpty() } ?: pack.filter.components.values.distinct().sorted()
    }

    /** drops a pack whose app was updated or removed, so its next use reads fresh files */
    fun forget(packPackage: String) {
        parsed.remove(packPackage)
    }

    private suspend fun load(packPackage: String): LoadedPack? = parseLock.withLock {
        parsed.get(packPackage)?.let { return@withLock it }
        withContext(Dispatchers.IO) {
            val resources = try {
                context.packageManager.getResourcesForApplication(packPackage)
            } catch (e: PackageManager.NameNotFoundException) {
                return@withContext null
            }
            val filter = readXml(resources, packPackage, "appfilter") { IconPackParser.parseAppFilter(it) }
                ?: AppFilter.EMPTY
            LoadedPack(resources, filter).also { parsed.put(packPackage, it) }
        }
    }

    /** packs ship these files either as compiled xml resources or as raw assets */
    private fun <T> readXml(resources: Resources, packPackage: String, name: String, read: (XmlPullParser) -> T): T? {
        try {
            @Suppress("DiscouragedApi") // packs are only addressable by name, never by a compiled id
            val id = resources.getIdentifier(name, "xml", packPackage)
            if (id != 0) return resources.getXml(id).use(read)
            return resources.assets.open("$name.xml").use { stream ->
                val parser = Xml.newPullParser().apply {
                    // external entities stay off; a pack's xml has no business reaching outside itself
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    setInput(stream, null)
                }
                read(parser)
            }
        } catch (e: IOException) {
            return null
        } catch (e: XmlPullParserException) {
            Log.w(TAG, "malformed $name in $packPackage", e)
            return null
        } catch (e: RuntimeException) {
            Log.w(TAG, "could not read $name in $packPackage", e)
            return null
        }
    }

    private fun drawable(resources: Resources, packPackage: String, name: String, density: Int): Drawable? = try {
        @Suppress("DiscouragedApi") // packs are only addressable by name, never by a compiled id
        val id = resources.getIdentifier(name, "drawable", packPackage)
        if (id == 0) null else resources.getDrawableForDensity(id, density, null)
    } catch (e: Resources.NotFoundException) {
        null
    } catch (e: RuntimeException) {
        Log.w(TAG, "could not load $name from $packPackage", e)
        null
    }

    companion object {
        private const val TAG = "IconPackRepository"

        /** the intents icon packs register so launchers can find them; mirrored in the manifest queries */
        val PACK_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.gau.go.launcherex.theme",
            "com.anddoes.launcher.THEME",
        )
    }
}

private inline fun <T> XmlResourceParser.use(block: (XmlPullParser) -> T): T = try {
    block(this)
} finally {
    close()
}
