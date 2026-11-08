package dev.gearturner.sparklauncher.core.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlin.math.max
import kotlin.math.min

/**
 * the icon's own color for its notification dot, like the pixel launcher: the most common vibrant
 * hue, averaged so stray pixels do not decide it. null for gray or themed icons, which have none.
 */
fun ImageBitmap.accentColor(): Color? = try {
    val source = asAndroidBitmap()
    // hardware bitmaps cannot be read pixel by pixel, so those are copied first
    val readable = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
    val small = Bitmap.createScaledBitmap(readable, SAMPLE_SIZE, SAMPLE_SIZE, true)
    val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
    small.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
    accentOf(pixels)?.let(::Color)
} catch (e: IllegalArgumentException) {
    null
} catch (e: IllegalStateException) {
    // a recycled bitmap; the dot falls back to the theme color
    null
}

/** works on plain argb ints so the choice can be tested without android */
internal fun accentOf(pixels: IntArray): Int? {
    val buckets = Array(HUE_BUCKETS) { mutableListOf<Int>() }
    for (pixel in pixels) {
        if (pixel ushr 24 < OPAQUE_ENOUGH) continue
        val r = (pixel shr 16 and 0xFF) / 255f
        val g = (pixel shr 8 and 0xFF) / 255f
        val b = (pixel and 0xFF) / 255f
        val high = max(r, max(g, b))
        val low = min(r, min(g, b))
        val saturation = if (high == 0f) 0f else (high - low) / high
        if (saturation < MIN_SATURATION || high < MIN_BRIGHTNESS) continue
        buckets[(hueOf(r, g, b, high, low) / 360f * HUE_BUCKETS).toInt().coerceIn(0, HUE_BUCKETS - 1)] += pixel
    }
    val winner = buckets.maxBy { it.size }
    if (winner.isEmpty()) return null
    var r = 0L
    var g = 0L
    var b = 0L
    winner.forEach { pixel ->
        r += pixel shr 16 and 0xFF
        g += pixel shr 8 and 0xFF
        b += pixel and 0xFF
    }
    val n = winner.size
    return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
}

private fun hueOf(r: Float, g: Float, b: Float, high: Float, low: Float): Float {
    val delta = high - low
    if (delta == 0f) return 0f
    val hue = when (high) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }
    return if (hue < 0f) hue + 360f else hue
}

private const val SAMPLE_SIZE = 16
private const val HUE_BUCKETS = 12
private const val OPAQUE_ENOUGH = 200
private const val MIN_SATURATION = 0.3f
private const val MIN_BRIGHTNESS = 0.3f
