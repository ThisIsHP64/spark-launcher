package dev.gearturner.sparklauncher.core.icons

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.util.DisplayMetrics
import android.util.TypedValue
import java.io.IOException
import java.io.InputStream
import kotlin.math.max

/**
 * icon packs are untrusted, and android decodes a pack image in full, scaled to the screen density,
 * before spark shrinks it to icon size. one enormous image would exhaust memory and crash home on
 * every start, so only the image header is read first and oversized images are refused.
 */
internal object PackImageLimits {

    /** ten times the 192 px a launcher icon needs at the highest density, so no real pack is refused */
    const val MAX_SIDE = 2048

    /**
     * false when the resource is an image that would decode larger than [MAX_SIDE] at
     * [targetDensity], or cannot be read at all. vectors and other xml drawables pass, since they
     * draw at whatever size they are given; one that wraps a huge image is caught when it decodes.
     */
    fun fits(resources: Resources, id: Int, targetDensity: Int): Boolean {
        val value = TypedValue()
        val (width, height) = try {
            resources.openRawResource(id, value).use(::imageSize)
        } catch (e: IOException) {
            return false
        }
        return fits(width, height, value.density, targetDensity)
    }

    /** the stored size of an image, or zero or less for anything that is not one; no pixels are read */
    fun imageSize(stream: InputStream): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(stream, null, options)
        return options.outWidth to options.outHeight
    }

    /** sizes of zero or less mean the resource is not an image, which is always allowed */
    fun fits(width: Int, height: Int, sourceDensity: Int, targetDensity: Int): Boolean {
        if (width <= 0 || height <= 0) return true
        // android scales an image from the density folder it sits in to the screen's, so a small
        // file in a low density folder can still decode several times larger
        val scale = when {
            sourceDensity == TypedValue.DENSITY_NONE || targetDensity <= 0 -> 1.0
            sourceDensity <= 0 -> targetDensity.toDouble() / DisplayMetrics.DENSITY_DEFAULT
            else -> targetDensity.toDouble() / sourceDensity
        }
        // shrinking still decodes the full file, so a downscaled image is judged at its stored size
        return max(width, height) * max(scale, 1.0) <= MAX_SIDE
    }
}
