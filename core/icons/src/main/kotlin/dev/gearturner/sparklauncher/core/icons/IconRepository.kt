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
import dev.gearturner.sparklauncher.core.apps.di.ApplicationScope
import dev.gearturner.sparklauncher.core.model.AppKey
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
    val key: AppKey,
    val sizePx: Int,
    val cornerRadiusPercent: Int,
)

@Singleton
class IconRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apps: AppsRepository,
    @ApplicationScope scope: CoroutineScope,
) {
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
                cache.snapshot().keys.filter { it.key.packageName in packages }.forEach { cache.remove(it) }
                _generation.update { it + 1 }
            }
        }
    }

    fun cached(request: IconRequest): Bitmap? = cache.get(request)

    /** returns null when the app vanished, letting callers show a fallback instead of crashing */
    suspend fun load(request: IconRequest): Bitmap? {
        cache.get(request)?.let { return it }
        return withContext(Dispatchers.IO) {
            val info = apps.activityInfo(request.key) ?: return@withContext null
            try {
                val density = context.resources.displayMetrics.densityDpi
                val drawable = info.getIcon(density) ?: return@withContext null
                var bitmap = IconRenderer.render(drawable, request.sizePx, request.cornerRadiusPercent)
                if (info.user != Process.myUserHandle()) {
                    bitmap = applyProfileBadge(bitmap, info.user)
                }
                cache.put(request, bitmap)
                bitmap
            } catch (e: RuntimeException) {
                // a malformed icon resource in one app should only cost that app its icon
                Log.w(TAG, "icon render failed for ${request.key.packageName}", e)
                null
            }
        }
    }

    private fun applyProfileBadge(bitmap: Bitmap, user: android.os.UserHandle): Bitmap {
        val badged = context.packageManager.getUserBadgedIcon(BitmapDrawable(context.resources, bitmap), user)
        val out = createBitmap(bitmap.width, bitmap.height)
        badged.setBounds(0, 0, out.width, out.height)
        badged.draw(Canvas(out))
        return out
    }

    private companion object {
        const val TAG = "IconRepository"
    }
}
