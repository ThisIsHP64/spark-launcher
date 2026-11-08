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
}
