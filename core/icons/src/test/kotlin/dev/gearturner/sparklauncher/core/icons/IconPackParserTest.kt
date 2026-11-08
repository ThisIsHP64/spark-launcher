package dev.gearturner.sparklauncher.core.icons

import android.util.Xml
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IconPackParserTest {

    private fun parser(xml: String) = Xml.newPullParser().apply { setInput(StringReader(xml)) }

    @Test
    fun readsComponentsAndStylingTags() {
        val filter = IconPackParser.parseAppFilter(
            parser(
                """
                <resources>
                    <iconback img1="back_a" img2="back_b" />
                    <iconupon img1="upon" />
                    <iconmask img1="mask" />
                    <scale factor="0.8" />
                    <item component="ComponentInfo{com.maps/com.maps.Main}" drawable="maps" />
                    <item component="ComponentInfo{com.mail/.Inbox}" drawable="mail" />
                    <item component="ComponentInfo{com.maps/com.maps.Main}" drawable="maps_duplicate" />
                </resources>
                """.trimIndent(),
            ),
        )
        assertEquals("maps", filter.components["com.maps/com.maps.Main"])
        assertEquals("mail", filter.components["com.mail/com.mail.Inbox"])
        assertEquals(listOf("back_a", "back_b"), filter.iconBacks)
        assertEquals("upon", filter.iconUpon)
        assertEquals("mask", filter.iconMask)
        assertEquals(0.8f, filter.scale)
    }

    @Test
    fun skipsMalformedAndHostileEntries() {
        val filter = IconPackParser.parseAppFilter(
            parser(
                """
                <resources>
                    <item component="not a component" drawable="x" />
                    <item component="ComponentInfo{com.a/com.a.Main}" drawable="../escape" />
                    <item component="ComponentInfo{com.b/com.b.Main}" />
                    <scale factor="900" />
                    <item component="ComponentInfo{com.ok/com.ok.Main}" drawable="ok_icon" />
                </resources>
                """.trimIndent(),
            ),
        )
        assertEquals(mapOf("com.ok/com.ok.Main" to "ok_icon"), filter.components)
        assertEquals(1f, filter.scale)
    }

    @Test
    fun drawableListKeepsOrderAndDropsDuplicates() {
        val names = IconPackParser.parseDrawableList(
            parser("""<resources><category title="A"/><item drawable="b"/><item drawable="a"/><item drawable="b"/></resources>"""),
        )
        assertEquals(listOf("b", "a"), names)
    }

    @Test
    fun componentParsingRejectsJunk() {
        assertNull(IconPackParser.parseComponent("ComponentInfo{/x}"))
        assertNull(IconPackParser.parseComponent("ComponentInfo{com.a/}"))
        assertEquals("com.a/com.a.B", IconPackParser.parseComponent(" ComponentInfo{com.a/.B} "))
    }
}
