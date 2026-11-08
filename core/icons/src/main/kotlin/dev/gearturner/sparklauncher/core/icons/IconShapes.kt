package dev.gearturner.sparklauncher.core.icons

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import dev.gearturner.sparklauncher.core.model.IconShape

/** icon outlines on a 100 unit square, matching the shapes aosp ships for the pixel's style picker */
object IconShapes {

    fun path(shape: IconShape, sizePx: Float, cornerPercent: Int): Path {
        val unit = when (shape) {
            IconShape.CIRCLE -> Path().apply { addOval(RectF(0f, 0f, 100f, 100f), Path.Direction.CW) }
            IconShape.SQUARE -> Path().apply { addRect(0f, 0f, 100f, 100f, Path.Direction.CW) }
            IconShape.ROUNDED -> {
                val r = cornerPercent.coerceIn(0, 50).toFloat()
                Path().apply { addRoundRect(0f, 0f, 100f, 100f, r, r, Path.Direction.CW) }
            }
            IconShape.SQUIRCLE -> Path().apply {
                moveTo(50f, 0f)
                cubicTo(10f, 0f, 0f, 10f, 0f, 50f)
                cubicTo(0f, 90f, 10f, 100f, 50f, 100f)
                cubicTo(90f, 100f, 100f, 90f, 100f, 50f)
                cubicTo(100f, 10f, 90f, 0f, 50f, 0f)
                close()
            }
            // round everywhere except a small corner at the bottom right
            IconShape.TEARDROP -> Path().apply {
                moveTo(50f, 0f)
                arcTo(RectF(0f, 0f, 100f, 100f), 270f, 90f)
                lineTo(100f, 85f)
                arcTo(RectF(70f, 70f, 100f, 100f), 0f, 90f)
                lineTo(50f, 100f)
                arcTo(RectF(0f, 0f, 100f, 100f), 90f, 180f)
                close()
            }
        }
        return Path().apply { addPath(unit, Matrix().apply { setScale(sizePx / 100f, sizePx / 100f) }) }
    }
}
