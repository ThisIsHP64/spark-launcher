package dev.gearturner.sparklauncher.core.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.createBitmap
import dev.gearturner.sparklauncher.core.model.IconStyle

/** an icon pack's styling for apps it does not cover */
internal class PackFrame(val back: Drawable?, val mask: Drawable?, val upon: Drawable?, val scale: Float)

/** turns any app drawable into a uniformly shaped bitmap so the grid looks consistent */
internal object IconRenderer {

    // legacy icons sit on a white plate like the pixel launcher does, with some breathing room
    private const val LEGACY_ICON_SCALE = 0.7f

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    fun render(drawable: Drawable, sizePx: Int, style: IconStyle): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val drewThemed = style.themed && drawThemed(canvas, drawable, sizePx, style)
        if (!drewThemed) {
            if (drawable is AdaptiveIconDrawable) drawAdaptive(canvas, drawable, sizePx) else drawLegacy(canvas, drawable, sizePx)
        }
        applyShapeMask(canvas, sizePx, style)
        return bitmap
    }

    /**
     * draws a palette tinted glyph on a palette colored plate. the app's own monochrome layer is
     * used when it has one; otherwise, if allowed, a glyph is extracted from the icon itself.
     * returns false to fall back to the full color icon, which is also what happens when no
     * convincing glyph can be found.
     */
    private fun drawThemed(canvas: Canvas, drawable: Drawable, sizePx: Int, style: IconStyle): Boolean {
        val adaptive = drawable as? AdaptiveIconDrawable
        val monochrome = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) adaptive?.monochrome else null
        val tint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = PorterDuffColorFilter(style.themedForeground, PorterDuff.Mode.SRC_IN)
        }
        if (monochrome != null) {
            canvas.drawColor(style.themedBackground)
            monochrome.bounds = adaptiveBounds(sizePx)
            monochrome.colorFilter = tint.colorFilter
            monochrome.draw(canvas)
            monochrome.colorFilter = null
            return true
        }
        if (!style.themedFallback) return false
        val composite = renderComposite(drawable, sizePx)
        val glyph = GlyphExtractor.extract(composite)
        if (glyph == null) {
            // full picture icons have no logo to lift out, so the whole picture is recolored instead
            val duotone = GlyphExtractor.duotone(composite, style.themedBackground, style.themedForeground) ?: return false
            canvas.drawBitmap(duotone, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            return true
        }
        canvas.drawColor(style.themedBackground)
        // real monochrome layers sit inside the safe zone, so the extracted glyph is shrunk to match
        val inner = sizePx * GlyphExtractor.GLYPH_SCALE
        val offset = (sizePx - inner) / 2f
        canvas.drawBitmap(glyph, null, RectF(offset, offset, offset + inner, offset + inner), tint)
        return true
    }

    /** the icon as the user would normally see it, unmasked, for glyph extraction */
    private fun renderComposite(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        if (drawable is AdaptiveIconDrawable) {
            drawAdaptive(canvas, drawable, sizePx)
        } else {
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
        }
        return bitmap
    }

    /**
     * icon pack art is finished artwork, so only adaptive pack icons are shaped; anything else is
     * drawn as the pack designed it, edge to edge, which is how nova shows pack icons too
     */
    fun renderPackIcon(drawable: Drawable, sizePx: Int, style: IconStyle): Bitmap {
        if (drawable is AdaptiveIconDrawable) return render(drawable, sizePx, style.copy(themed = false))
        val bitmap = createBitmap(sizePx, sizePx)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    /**
     * styles an app the pack has no icon for with the pack's own frame: its background, the
     * shrunken app icon with the pack's mask cut out of it, then the pack's overlay on top
     */
    fun renderWithPackFrame(app: Drawable, sizePx: Int, style: IconStyle, frame: PackFrame): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val full = Rect(0, 0, sizePx, sizePx)
        frame.back?.let {
            it.bounds = full
            it.draw(canvas)
        }
        val inner = (sizePx * frame.scale).toInt().coerceIn(1, sizePx)
        val icon = render(app, inner, style.copy(themed = false))
        val layer = createBitmap(sizePx, sizePx)
        val layerCanvas = Canvas(layer)
        val offset = (sizePx - inner) / 2f
        layerCanvas.drawBitmap(icon, offset, offset, null)
        frame.mask?.let { mask ->
            val masked = createBitmap(sizePx, sizePx)
            mask.bounds = full
            mask.draw(Canvas(masked))
            layerCanvas.drawBitmap(masked, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
        }
        canvas.drawBitmap(layer, 0f, 0f, null)
        frame.upon?.let {
            it.bounds = full
            it.draw(canvas)
        }
        return bitmap
    }

    private fun adaptiveBounds(sizePx: Int): Rect {
        // adaptive layers are larger than the visible area; expanding the bounds makes the safe zone fill the icon
        val inset = (sizePx * AdaptiveIconDrawable.getExtraInsetFraction()).toInt()
        return Rect(-inset, -inset, sizePx + inset, sizePx + inset)
    }

    private fun drawAdaptive(canvas: Canvas, icon: AdaptiveIconDrawable, sizePx: Int) {
        val bounds = adaptiveBounds(sizePx)
        listOfNotNull(icon.background, icon.foreground).forEach { layer ->
            layer.bounds = bounds
            layer.draw(canvas)
        }
    }

    private fun drawLegacy(canvas: Canvas, icon: Drawable, sizePx: Int) {
        canvas.drawColor(Color.WHITE)
        val inner = (sizePx * LEGACY_ICON_SCALE).toInt()
        val offset = (sizePx - inner) / 2
        icon.setBounds(offset, offset, offset + inner, offset + inner)
        icon.draw(canvas)
    }

    private fun applyShapeMask(canvas: Canvas, sizePx: Int, style: IconStyle) {
        // clearing the inverse of the shape with an anti-aliased paint gives smooth edges, unlike clipPath
        val outside = IconShapes.path(style.shape, sizePx.toFloat(), style.cornerPercent)
            .apply { fillType = Path.FillType.INVERSE_WINDING }
        canvas.drawPath(outside, maskPaint)
    }
}
