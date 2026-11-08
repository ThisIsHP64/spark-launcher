package dev.gearturner.sparklauncher.core.icons

import org.xmlpull.v1.XmlPullParser

/** what an icon pack's appfilter declares, in the adw and nova format nearly every pack uses */
data class AppFilter(
    /** "package/class" to drawable name */
    val components: Map<String, String>,
    /** backgrounds, overlays and masks used to style apps the pack has no icon for */
    val iconBacks: List<String> = emptyList(),
    val iconUpon: String? = null,
    val iconMask: String? = null,
    val scale: Float = 1f,
) {
    companion object {
        val EMPTY = AppFilter(emptyMap())
    }
}

/**
 * reads appfilter xml from an untrusted pack. entries are capped, names are validated as
 * resource names, and anything malformed is skipped rather than failing the whole pack.
 */
object IconPackParser {

    private const val MAX_ENTRIES = 40_000
    private val DRAWABLE_NAME = Regex("[A-Za-z0-9_]{1,128}")

    fun parseAppFilter(parser: XmlPullParser): AppFilter {
        val components = HashMap<String, String>()
        val backs = ArrayList<String>()
        var upon: String? = null
        var mask: String? = null
        var scale = 1f
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && components.size < MAX_ENTRIES) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "item" -> {
                        val component = parser.getAttributeValue(null, "component")?.let(::parseComponent)
                        val drawable = parser.getAttributeValue(null, "drawable")?.takeIf(DRAWABLE_NAME::matches)
                        // the first mapping wins, matching how other launchers read duplicate entries
                        if (component != null && drawable != null) components.putIfAbsent(component, drawable)
                    }
                    "iconback" -> (0 until parser.attributeCount)
                        .map { parser.getAttributeValue(it) }
                        .filter(DRAWABLE_NAME::matches)
                        .take(MAX_BACKS)
                        .let(backs::addAll)
                    "iconupon" -> upon = parser.getAttributeValue(null, "img1")?.takeIf(DRAWABLE_NAME::matches)
                    "iconmask" -> mask = parser.getAttributeValue(null, "img1")?.takeIf(DRAWABLE_NAME::matches)
                    "scale" -> scale = parser.getAttributeValue(null, "factor")?.toFloatOrNull()
                        ?.takeIf { it in MIN_SCALE..MAX_SCALE } ?: scale
                }
            }
            event = parser.next()
        }
        return AppFilter(components, backs, upon, mask, scale)
    }

    /** the drawable names a pack lists for manual picking, from its drawable.xml */
    fun parseDrawableList(parser: XmlPullParser): List<String> {
        val names = LinkedHashSet<String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && names.size < MAX_ENTRIES) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                parser.getAttributeValue(null, "drawable")?.takeIf(DRAWABLE_NAME::matches)?.let(names::add)
            }
            event = parser.next()
        }
        return names.toList()
    }

    /**
     * turns "ComponentInfo{com.app/com.app.Main}" into "com.app/com.app.Main", expanding the
     * short ".Main" form some packs use. returns null for anything that is not a component.
     */
    internal fun parseComponent(raw: String): String? {
        val inner = raw.trim().removePrefix("ComponentInfo{").removeSuffix("}")
        val slash = inner.indexOf('/')
        if (slash <= 0 || slash == inner.lastIndex) return null
        val pkg = inner.substring(0, slash)
        val cls = inner.substring(slash + 1).let { if (it.startsWith(".")) pkg + it else it }
        if (pkg.any { it.isWhitespace() } || cls.any { it.isWhitespace() }) return null
        return "$pkg/$cls"
    }

    private const val MAX_BACKS = 16
    private const val MIN_SCALE = 0.3f
    private const val MAX_SCALE = 1.5f
}
