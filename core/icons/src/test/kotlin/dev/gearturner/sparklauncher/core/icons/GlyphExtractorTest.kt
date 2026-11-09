package dev.gearturner.sparklauncher.core.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlyphExtractorTest {

    private val size = 100

    private fun icon(background: Int, draw: Canvas.() -> Unit = {}): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).apply {
                drawColor(background)
                draw()
            }
        }

    private fun Canvas.block(color: Int, l: Float, t: Float, r: Float, b: Float) =
        drawRect(l, t, r, b, Paint().apply { this.color = color })

    @Test
    fun lightTextOnALightTileBecomesAGlyph() {
        // like 2048: white marks on a yellow tile, which a darkness rule would erase
        val tile = icon(Color.rgb(237, 194, 46)) { block(Color.WHITE, 30f, 40f, 70f, 60f) }
        val glyph = GlyphExtractor.extract(tile)
        assertNotNull(glyph)
        assertTrue(Color.alpha(glyph!!.getPixel(50, 50)) > 200)
        assertTrue(Color.alpha(glyph.getPixel(20, 20)) < 10)
    }

    @Test
    fun brightShapesOnADarkBackdropBecomeAGlyph() {
        val art = icon(Color.rgb(16, 20, 40)) { block(Color.rgb(60, 140, 255), 35f, 35f, 65f, 65f) }
        assertNotNull(GlyphExtractor.extract(art))
    }

    @Test
    fun aPlainTileHasNoGlyph() {
        assertNull(GlyphExtractor.extract(icon(Color.rgb(90, 90, 200))))
    }

    @Test
    fun artworkThatDiffersFromItsEdgeAlmostEverywhereIsRejected() {
        // a thin frame around a full bleed picture, as busy game icons often are
        val busy = icon(Color.rgb(20, 120, 60)) { block(Color.rgb(250, 240, 230), 14f, 14f, 86f, 86f) }
        assertNull(GlyphExtractor.extract(busy))
    }

    @Test
    fun duotoneMapsBrightAreasToTheGlyphColorAndDarkAreasToThePlate() {
        val art = icon(Color.rgb(20, 60, 140)) { block(Color.WHITE, 30f, 30f, 70f, 70f) }
        val plate = Color.rgb(30, 30, 40)
        val glyph = Color.rgb(200, 220, 255)
        val result = GlyphExtractor.duotone(art, plate, glyph)
        assertNotNull(result)
        assertTrue(result!!.getPixel(50, 50) == glyph)
        assertTrue(result.getPixel(5, 5) == plate)
    }

    @Test
    fun flatIconsAreNotDuotoned() {
        assertNull(GlyphExtractor.duotone(icon(Color.rgb(90, 90, 200)), Color.BLACK, Color.WHITE))
    }

    private fun Canvas.stroke(color: Int, width: Float, x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(
            x1, y1, x2, y2,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                strokeWidth = width
                strokeCap = Paint.Cap.ROUND
            },
        )

    @Test
    fun aLoneShapeOnATransparentCanvasIsItsOwnGlyph() {
        // like a legacy phone icon: a shaded blue handset with no tile, whose bands sit close to its own fill
        val handset = icon(Color.TRANSPARENT) {
            stroke(Color.rgb(14, 171, 244), 24f, 15f, 85f, 85f, 15f)
            stroke(Color.rgb(10, 120, 200), 8f, 30f, 70f, 70f, 30f)
        }
        val glyph = GlyphExtractor.extract(handset)
        assertNotNull(glyph)
        assertTrue(Color.alpha(glyph!!.getPixel(50, 50)) > 200)
        // the lighter and darker bands stay solid instead of leaving only an outline
        assertTrue(Color.alpha(glyph.getPixel(40, 60)) > 200)
        assertTrue(Color.alpha(glyph.getPixel(10, 10)) < 10)
    }

    @Test
    fun aMarkInsideATransparentCutoutStillWinsOverItsShape() {
        // a round logo with a white mark on a transparent canvas: only the mark is lifted out
        val logo = icon(Color.TRANSPARENT) {
            drawCircle(50f, 50f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 100, 220) })
            block(Color.WHITE, 44f, 25f, 56f, 75f)
        }
        val glyph = GlyphExtractor.extract(logo)
        assertNotNull(glyph)
        assertTrue(Color.alpha(glyph!!.getPixel(50, 50)) > 200)
        assertTrue(Color.alpha(glyph.getPixel(25, 50)) < 10)
    }

    @Test
    fun anEmptyCanvasHasNoGlyph() {
        assertNull(GlyphExtractor.extract(icon(Color.TRANSPARENT)))
    }

    @Test
    fun anOpaqueTileKeepsUsingItsEdgeColor() {
        // a dark mark on an opaque tile is judged against the tile, not treated as a cutout shape
        val tile = icon(Color.rgb(240, 240, 240)) { block(Color.rgb(20, 20, 20), 35f, 35f, 65f, 65f) }
        val glyph = GlyphExtractor.extract(tile)
        assertNotNull(glyph)
        assertTrue(Color.alpha(glyph!!.getPixel(50, 50)) > 200)
        assertTrue(Color.alpha(glyph.getPixel(10, 10)) < 10)
    }
}
