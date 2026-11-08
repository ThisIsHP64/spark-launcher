package dev.gearturner.sparklauncher.core.icons

import android.graphics.Bitmap
import android.util.TypedValue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PackImageLimitsTest {

    private val xxxhdpi = 640

    private fun png(width: Int, height: Int): ByteArray = ByteArrayOutputStream().also { out ->
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, out)
    }.toByteArray()

    @Test
    fun headerIsReadWithoutDecodingPixels() {
        assertEquals(300 to 120, PackImageLimits.imageSize(ByteArrayInputStream(png(300, 120))))
    }

    @Test
    fun nonImagesReportNoSize() {
        val (width, height) = PackImageLimits.imageSize(ByteArrayInputStream("<vector />".toByteArray()))
        assertTrue(width <= 0 && height <= 0)
    }

    @Test
    fun ordinaryPackIconsFit() {
        assertTrue(PackImageLimits.fits(192, 192, xxxhdpi, xxxhdpi))
        assertTrue(PackImageLimits.fits(512, 512, TypedValue.DENSITY_NONE, xxxhdpi))
        assertTrue(PackImageLimits.fits(PackImageLimits.MAX_SIDE, 10, xxxhdpi, xxxhdpi))
    }

    @Test
    fun hugeImagesAreRefused() {
        assertFalse(PackImageLimits.fits(20_000, 20_000, xxxhdpi, xxxhdpi))
        assertFalse(PackImageLimits.fits(10, PackImageLimits.MAX_SIDE + 1, TypedValue.DENSITY_NONE, xxxhdpi))
    }

    @Test
    fun upscalingFromALowDensityFolderCounts() {
        // 1000 px in drawable-mdpi becomes 4000 px on an xxxhdpi screen
        assertFalse(PackImageLimits.fits(1000, 1000, 160, xxxhdpi))
        // the default density folder is treated as mdpi, as android does
        assertFalse(PackImageLimits.fits(1000, 1000, TypedValue.DENSITY_DEFAULT, xxxhdpi))
    }

    @Test
    fun downscalingDoesNotEarnExtraRoom() {
        assertFalse(PackImageLimits.fits(3000, 3000, xxxhdpi, 160))
    }

    @Test
    fun vectorsAndOtherXmlAlwaysFit() {
        assertTrue(PackImageLimits.fits(-1, -1, TypedValue.DENSITY_DEFAULT, xxxhdpi))
    }
}
