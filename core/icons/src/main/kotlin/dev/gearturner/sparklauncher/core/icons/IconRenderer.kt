package dev.gearturner.sparklauncher.core.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap

/** turns any app drawable into a uniformly shaped bitmap so the grid looks consistent */
internal object IconRenderer {

    // legacy icons sit on a white plate like the pixel launcher does, with some breathing room
    private const val LEGACY_ICON_SCALE = 0.7f

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    fun render(drawable: Drawable, sizePx: Int, cornerRadiusPercent: Int): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        if (drawable is AdaptiveIconDrawable) {
            drawAdaptive(canvas, drawable, sizePx)
        } else {
            drawLegacy(canvas, drawable, sizePx)
        }
        applyShapeMask(canvas, sizePx, cornerRadiusPercent)
        return bitmap
    }

    fun shapePath(sizePx: Int, cornerRadiusPercent: Int): Path {
        val radius = sizePx * cornerRadiusPercent.coerceIn(0, 50) / 100f
        return Path().apply {
            addRoundRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), radius, radius, Path.Direction.CW)
        }
    }

    private fun drawAdaptive(canvas: Canvas, icon: AdaptiveIconDrawable, sizePx: Int) {
        // adaptive layers are larger than the visible area; expand bounds so the safe zone fills the bitmap
        val inset = (sizePx * AdaptiveIconDrawable.getExtraInsetFraction()).toInt()
        val bounds = Rect(-inset, -inset, sizePx + inset, sizePx + inset)
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

    private fun applyShapeMask(canvas: Canvas, sizePx: Int, cornerRadiusPercent: Int) {
        // clearing the inverse of the shape with an anti-aliased paint gives smooth edges, unlike clipPath
        val outside = shapePath(sizePx, cornerRadiusPercent).apply { fillType = Path.FillType.INVERSE_WINDING }
        canvas.drawPath(outside, maskPaint)
    }
}
