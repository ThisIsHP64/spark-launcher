package dev.gearturner.sparklauncher

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * guards the core privacy promise: the shipped apk must never be able to reach the network.
 * it inspects the merged manifest so permissions pulled in by any dependency are caught too.
 */
class ManifestPrivacyTest {

    private val forbiddenPermissions = setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "com.google.android.gms.permission.AD_ID",
        "android.permission.QUERY_ALL_PACKAGES",
    )

    @Test
    fun mergedManifestsRequestNoNetworkOrTrackingPermissions() {
        val manifests = findMergedManifests()
        assertTrue(
            "no merged manifest found; run this test through gradle so the manifest is generated first",
            manifests.isNotEmpty(),
        )
        manifests.forEach { manifest ->
            val requested = readRequestedPermissions(manifest)
            forbiddenPermissions.forEach { permission ->
                assertFalse("$permission found in ${manifest.path}", permission in requested)
            }
        }
    }

    @Test
    fun accessibilityServiceCanOnlyBeBoundByTheSystem() {
        findMergedManifests().forEach { manifest ->
            val document = parse(manifest)
            val services = document.getElementsByTagName("service")
            val ours = (0 until services.length).map { services.item(it) }
                .filter { it.attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue?.endsWith("SparkAccessibilityService") == true }
            assertTrue("accessibility service missing from ${manifest.path}", ours.size == 1)
            val permission = ours.single().attributes.getNamedItemNS(ANDROID_NS, "permission")?.nodeValue
            assertTrue("service must require the system bind permission", permission == "android.permission.BIND_ACCESSIBILITY_SERVICE")
        }
    }

    @Test
    fun notificationListenerCanOnlyBeBoundByTheSystem() {
        findMergedManifests().forEach { manifest ->
            val document = parse(manifest)
            val services = document.getElementsByTagName("service")
            val ours = (0 until services.length).map { services.item(it) }
                .filter { it.attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue?.endsWith("SparkNotificationListener") == true }
            assertTrue("notification listener missing from ${manifest.path}", ours.size == 1)
            val permission = ours.single().attributes.getNamedItemNS(ANDROID_NS, "permission")?.nodeValue
            assertTrue("listener must require the system bind permission", permission == "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE")
        }
    }

    @Test
    fun noPermissionToPostOrReadNotificationsIsRequested() {
        findMergedManifests().forEach { manifest ->
            val requested = readRequestedPermissions(manifest)
            assertFalse("android.permission.POST_NOTIFICATIONS" in requested)
            assertFalse("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" in requested)
        }
    }

    @Test
    fun accessibilityServiceCannotReadTheScreen() {
        val config = File("src/main/res/xml/accessibility_service.xml")
        assertTrue("accessibility config not found", config.isFile)
        val root = parse(config).documentElement
        assertTrue(root.getAttributeNS(ANDROID_NS, "canRetrieveWindowContent") == "false")
        assertFalse(root.hasAttributeNS(ANDROID_NS, "accessibilityEventTypes"))
        assertFalse(root.hasAttributeNS(ANDROID_NS, "canRequestFilterKeyEvents"))
        assertFalse(root.hasAttributeNS(ANDROID_NS, "canPerformGestures"))
    }

    private fun parse(file: File) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder().parse(file)

    private fun findMergedManifests(): List<File> {
        // unit tests run with the module directory as the working directory; agp writes to both folders
        return listOf("merged_manifests", "merged_manifest")
            .map { File("build/intermediates/$it") }
            .filter { it.isDirectory }
            .flatMap { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.name == "AndroidManifest.xml" && !it.path.contains("UnitTest") }
                    .toList()
            }
    }

    private fun readRequestedPermissions(manifest: File): Set<String> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // the manifest is local build output, but external entities stay disabled out of habit
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val document = factory.newDocumentBuilder().parse(manifest)
        val androidNs = "http://schemas.android.com/apk/res/android"
        val tags = listOf("uses-permission", "uses-permission-sdk-23")
        return tags.flatMap { tag ->
            val nodes = document.getElementsByTagName(tag)
            (0 until nodes.length).map { index ->
                nodes.item(index).attributes.getNamedItemNS(androidNs, "name").nodeValue
            }
        }.toSet()
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
