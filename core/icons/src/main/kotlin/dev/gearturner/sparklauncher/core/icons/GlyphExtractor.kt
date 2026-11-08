package dev.gearturner.sparklauncher.core.icons

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.sqrt

/**
 * builds a monochrome glyph for apps that ship none. the icon's background color is estimated
 * from a ring just inside its edge, and pixels that clearly differ from it become the glyph:
 * white text on a yellow tile, or bright shapes on a dark backdrop. icons with no distinct logo
 * yield nothing from [extract], and [duotone] recolors them as a whole instead.
 */
internal object GlyphExtractor {

    /** extracted glyphs are drawn at this share of the icon, about where monochrome layers sit */
    const val GLYPH_SCALE = 0.78f

    // color distance on a 0 to 441 scale; below LOW is background, above HIGH is fully glyph
    private const val LOW = 48f
    private const val HIGH = 128f

    // a glyph thinner than this is lost noise, and one wider covers so much it reads as a blob
    private const val MIN_COVERAGE = 0.03f
    private const val MAX_COVERAGE = 0.42f

    private const val RING_INSET = 0.12f
    private const val MIN_SAMPLES = 8

    fun extract(source: Bitmap): Bitmap? {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val background = backgroundColor(pixels, width, height) ?: return null
        val bgR = Color.red(background)
        val bgG = Color.green(background)
        val bgB = Color.blue(background)

        val out = IntArray(pixels.size)
        var coverage = 0f
        var visible = 0
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = Color.alpha(p)
            if (a == 0) continue
            visible++
            val dr = (Color.red(p) - bgR).toFloat()
            val dg = (Color.green(p) - bgG).toFloat()
            val db = (Color.blue(p) - bgB).toFloat()
            val distance = sqrt(dr * dr + dg * dg + db * db)
            val strength = ((distance - LOW) / (HIGH - LOW)).coerceIn(0f, 1f) * (a / 255f)
            if (strength > 0f) {
                out[i] = Color.argb((strength * 255).toInt(), 255, 255, 255)
                coverage += strength
            }
        }
        if (visible == 0) return null
        val ratio = coverage / visible
        if (ratio < MIN_COVERAGE || ratio > MAX_COVERAGE) return null
        return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * redraws a whole picture in the theme's two icon colors, for artwork icons with no single
     * logo to lift out. brightness is stretched across the icon's own range, so bright areas take
     * the glyph color and dark areas the plate color. returns null for icons with too little
     * contrast to read, which then keep their original colors.
     */
    fun duotone(source: Bitmap, plate: Int, glyph: Int): Bitmap? {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        var min = 1f
        var max = 0f
        val luminance = FloatArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            if (Color.alpha(p) < OPAQUE_ENOUGH) {
                luminance[i] = -1f
                continue
            }
            val l = (0.2126f * Color.red(p) + 0.7152f * Color.green(p) + 0.0722f * Color.blue(p)) / 255f
            luminance[i] = l
            if (l < min) min = l
            if (l > max) max = l
        }
        if (max - min < MIN_DUOTONE_RANGE) return null
        val out = IntArray(pixels.size)
        for (i in pixels.indices) {
            val l = luminance[i]
            // transparent corners stay plate colored, since the shape mask trims them anyway
            val t = if (l < 0f) 0f else ((l - min) / (max - min)).let { it * it }
            out[i] = blend(plate, glyph, t)
        }
        return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun blend(from: Int, to: Int, t: Float): Int = Color.rgb(
        (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt(),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt(),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt(),
    )

    /**
     * the median color of opaque pixels on a square ring just inside the edge. the median ignores
     * a logo that happens to touch the ring, where an average would be pulled toward it.
     */
    private fun backgroundColor(pixels: IntArray, width: Int, height: Int): Int? {
        val inset = (minOf(width, height) * RING_INSET).toInt()
        val left = inset
        val top = inset
        val right = width - 1 - inset
        val bottom = height - 1 - inset
        if (right <= left || bottom <= top) return null
        val reds = ArrayList<Int>()
        val greens = ArrayList<Int>()
        val blues = ArrayList<Int>()
        fun sample(x: Int, y: Int) {
            val p = pixels[y * width + x]
            if (Color.alpha(p) < OPAQUE_ENOUGH) return
            reds += Color.red(p)
            greens += Color.green(p)
            blues += Color.blue(p)
        }
        for (x in left..right) {
            sample(x, top)
            sample(x, bottom)
        }
        for (y in top + 1 until bottom) {
            sample(left, y)
            sample(right, y)
        }
        if (reds.size < MIN_SAMPLES) return null
        return Color.rgb(reds.median(), greens.median(), blues.median())
    }

    private fun ArrayList<Int>.median(): Int {
        sort()
        return this[size / 2]
    }

    private const val OPAQUE_ENOUGH = 200

    // below this brightness spread a duotone would be a flat plate, so the original is kept
    private const val MIN_DUOTONE_RANGE = 0.2f
}
