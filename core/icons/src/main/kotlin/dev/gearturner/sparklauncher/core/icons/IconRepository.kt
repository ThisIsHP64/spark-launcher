package dev.gearturner.sparklauncher.core.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Process
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.createBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gearturner.sparklauncher.core.apps.AppsRepository
import dev.gearturner.sparklauncher.core.apps.ShortcutsRepository
import dev.gearturner.sparklauncher.core.apps.di.ApplicationScope
import dev.gearturner.sparklauncher.core.data.AppOverridesRepository
import dev.gearturner.sparklauncher.core.model.AppKey
import dev.gearturner.sparklauncher.core.model.AppOverride
import dev.gearturner.sparklauncher.core.model.IconSource
import dev.gearturner.sparklauncher.core.model.IconStyle
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class IconRequest(
    val source: IconSource,
    val sizePx: Int,
    val style: IconStyle,
) {
    constructor(key: AppKey, sizePx: Int, style: IconStyle) : this(IconSource.App(key), sizePx, style)

    val packageName: String
        get() = when (source) {
            is IconSource.App -> source.key.packageName
            is IconSource.Shortcut -> source.key.packageName
        }
}

@Singleton
class IconRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apps: AppsRepository,
    private val shortcuts: ShortcutsRepository,
    private val iconPacks: IconPackRepository,
    overridesRepository: AppOverridesRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    @Volatile
    private var overrides: Map<AppKey, AppOverride> = emptyMap()

    // an eighth of the heap comfortably holds a few hundred icons at xxhdpi
    private val cache = object : LruCache<IconRequest, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: IconRequest, value: Bitmap): Int = value.allocationByteCount
    }

    private val _generation = MutableStateFlow(0)

    /** bumps whenever cached icons were dropped, so visible icons know to reload */
    val generation: StateFlow<Int> = _generation.asStateFlow()

    init {
        scope.launch {
            apps.packageChanges.collect { packages ->
                // an updated icon pack changes every icon drawn from it, not just its own
                packages.forEach(iconPacks::forget)
                cache.snapshot().keys
                    .filter { it.packageName in packages || it.style.iconPack in packages }
                    .forEach { cache.remove(it) }
                _generation.update { it + 1 }
            }
        }
        scope.launch {
            overridesRepository.overrides.collect { next ->
                val changed = (next.keys + overrides.keys).filter { next[it]?.customIcon != overrides[it]?.customIcon }.toSet()
                overrides = next
                if (changed.isNotEmpty()) {
                    cache.snapshot().keys.filter { (it.source as? IconSource.App)?.key in changed }.forEach { cache.remove(it) }
                    _generation.update { it + 1 }
                }
            }
        }
        // apps can swap a shortcut's icon without any package change, so shortcut bitmaps refresh too
        scope.launch {
            apps.shortcutChanges.collect {
                cache.snapshot().keys.filter { it.source is IconSource.Shortcut }.forEach { cache.remove(it) }
                _generation.update { it + 1 }
            }
        }
    }

    fun cached(request: IconRequest): Bitmap? = cache.get(request)

    /** returns null when the app or shortcut vanished, letting callers show a fallback instead of crashing */
    suspend fun load(request: IconRequest): Bitmap? {
        cache.get(request)?.let { return it }
        val bitmap = when (val source = request.source) {
            is IconSource.App -> renderApp(source.key, request)
            is IconSource.Shortcut -> renderShortcut(source, request)
        } ?: return null
        cache.put(request, bitmap)
        return bitmap
    }

    /**
     * picks the icon in order: the user's pick for this app, then the chosen pack's icon, then
     * the pack's frame around the app's own icon, then the app's icon, themed if enabled
     */
    private suspend fun renderApp(key: AppKey, request: IconRequest): Bitmap? {
        val info = apps.activityInfo(key) ?: return null
        val density = density()
        val style = request.style
        val component = "${key.packageName}/${key.className}"
        val packIcon = overrides[key]?.customIcon?.let { iconPacks.drawable(it.packPackage, it.drawableName, density) }
            ?: style.iconPack?.let { iconPacks.iconFor(it, component, density) }
        val frame = if (packIcon == null && !style.themed) style.iconPack?.let { packFrame(it, key, density) } else null
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = when {
                    packIcon != null -> IconRenderer.renderPackIcon(packIcon, request.sizePx, style)
                    else -> {
                        val drawable = info.getIcon(density) ?: return@withContext null
                        if (frame != null) {
                            IconRenderer.renderWithPackFrame(drawable, request.sizePx, style, frame)
                        } else {
                            IconRenderer.render(drawable, request.sizePx, style)
                        }
                    }
                }
                if (info.user != Process.myUserHandle()) applyProfileBadge(bitmap, info.user) else bitmap
            } catch (e: RuntimeException) {
                // a malformed icon resource in one app should only cost that app its icon
                Log.w(TAG, "icon render failed for ${key.packageName}", e)
                null
            }
        }
    }

    /** null when the pack has no styling, in which case uncovered apps keep their normal icon */
    private suspend fun packFrame(pack: String, key: AppKey, density: Int): PackFrame? {
        val filter = iconPacks.styling(pack) ?: return null
        if (filter.iconBacks.isEmpty() && filter.iconUpon == null && filter.iconMask == null) return null
        // packs with several backgrounds spread them across apps, stably per app
        val back = filter.iconBacks.takeIf { it.isNotEmpty() }
            ?.let { it[Math.floorMod(key.packageName.hashCode(), it.size)] }
            ?.let { iconPacks.drawable(pack, it, density) }
        return PackFrame(
            back = back,
            mask = filter.iconMask?.let { iconPacks.drawable(pack, it, density) },
            upon = filter.iconUpon?.let { iconPacks.drawable(pack, it, density) },
            scale = filter.scale,
        )
    }

    /** one icon from a pack, for the manual icon picker */
    suspend fun loadPackDrawable(pack: String, drawableName: String, sizePx: Int, style: IconStyle): Bitmap? {
        val drawable = iconPacks.drawable(pack, drawableName, density()) ?: return null
        return withContext(Dispatchers.Default) {
            try {
                IconRenderer.renderPackIcon(drawable, sizePx, style)
            } catch (e: RuntimeException) {
                null
            }
        }
    }

    /** shortcut icons carry a small copy of their app's icon so it is clear where they lead */
    private suspend fun renderShortcut(source: IconSource.Shortcut, request: IconRequest): Bitmap? {
        val drawable = shortcuts.iconDrawable(source.key, density())
        val owner = source.owner
        val base = withContext(Dispatchers.IO) {
            try {
                drawable?.let { IconRenderer.render(it, request.sizePx, request.style) }
            } catch (e: RuntimeException) {
                Log.w(TAG, "shortcut icon render failed for ${source.key.packageName}", e)
                null
            }
        }
        // a shortcut without its own icon falls back to the app icon, which is what the app would show
        if (base == null) return owner?.let { load(IconRequest(it, request.sizePx, request.style)) }
        val badgeSize = (request.sizePx * SHORTCUT_BADGE_FRACTION).toInt().coerceAtLeast(1)
        val badge = owner?.let { load(IconRequest(it, badgeSize, request.style)) } ?: return base
        // the base bitmap is freshly rendered and not cached yet, so drawing onto it is safe
        val offset = (request.sizePx - badgeSize).toFloat()
        Canvas(base).drawBitmap(badge, offset, offset, null)
        return base
    }

    private fun density(): Int = context.resources.displayMetrics.densityDpi

    private fun applyProfileBadge(bitmap: Bitmap, user: android.os.UserHandle): Bitmap {
        val badged = context.packageManager.getUserBadgedIcon(BitmapDrawable(context.resources, bitmap), user)
        val out = createBitmap(bitmap.width, bitmap.height)
        badged.setBounds(0, 0, out.width, out.height)
        badged.draw(Canvas(out))
        return out
    }

    private companion object {
        const val TAG = "IconRepository"
        const val SHORTCUT_BADGE_FRACTION = 0.4f
    }
}
