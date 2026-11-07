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
}
