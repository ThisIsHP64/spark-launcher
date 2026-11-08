package dev.gearturner.sparklauncher.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** a license spark or one of its libraries is shipped under, with its full text bundled in the app */
internal enum class LicenseKind(@StringRes val title: Int, @RawRes val text: Int) {
    GPL3(R.string.settings_license_gpl3, R.raw.license_gpl3),
    APACHE2(R.string.settings_license_apache2, R.raw.license_apache2),
    MIT_MATERIALKOLOR(R.string.settings_license_mit, R.raw.license_mit_materialkolor),
}

/** a library compiled into the app, credited on the open source licenses page */
internal data class OpenSourceLibrary(val name: String, val owner: String, val license: LicenseKind)

/** everything shipped in the apk; test only libraries are left out because they are not distributed */
internal val OpenSourceLibraries: List<OpenSourceLibrary> = listOf(
    OpenSourceLibrary("AndroidX Activity, Core, Lifecycle, and DataStore", "The Android Open Source Project", LicenseKind.APACHE2),
    OpenSourceLibrary("AndroidX Room", "The Android Open Source Project", LicenseKind.APACHE2),
    OpenSourceLibrary("Dagger and Hilt", "The Dagger Authors", LicenseKind.APACHE2),
    OpenSourceLibrary("Jetpack Compose and Material 3", "The Android Open Source Project", LicenseKind.APACHE2),
    OpenSourceLibrary("Kotlin standard library", "JetBrains", LicenseKind.APACHE2),
    OpenSourceLibrary("kotlinx.coroutines", "JetBrains", LicenseKind.APACHE2),
    OpenSourceLibrary("kotlinx.serialization", "JetBrains", LicenseKind.APACHE2),
    OpenSourceLibrary("Material icons", "Google", LicenseKind.APACHE2),
    OpenSourceLibrary("MaterialKolor", "Jordon de Hoog", LicenseKind.MIT_MATERIALKOLOR),
)

/**
 * license files wrap their lines at a fixed width, which reads badly on a phone. this keeps their
 * paragraphs but lets each one flow to the screen width.
 */
internal fun licenseParagraphs(text: String): List<String> =
    text.replace("\r\n", "\n")
        .split(Regex("\n\\s*\n"))
        .map { paragraph -> paragraph.lines().joinToString(" ") { it.trim() }.replace(Regex(" {2,}"), " ").trim() }
        .filter { it.isNotEmpty() }

internal fun LazyListScope.aboutPage(onOpenLicense: (LicenseKind) -> Unit, onOpenLibraries: () -> Unit) {
    item { BrandHeader(showDetails = true) }
    item { SectionHeader(stringResource(R.string.settings_about_updates)) }
    item {
        val context = LocalContext.current
        SettingsCard {
            AboutRow(
                Icons.Outlined.SystemUpdate,
                stringResource(R.string.settings_about_check_updates),
                stringResource(R.string.settings_about_check_updates_summary),
            ) { openReleasesPage(context) }
        }
    }
    item { SectionHeader(stringResource(R.string.settings_about_credits)) }
    item {
        SettingsCard {
            AboutRow(Icons.Outlined.Person, stringResource(R.string.settings_about_author), stringResource(R.string.settings_about_author_role))
        }
    }
    item { SectionHeader(stringResource(R.string.settings_about_legal)) }
    item {
        SettingsCard {
            AboutRow(Icons.Outlined.Shield, stringResource(R.string.settings_about_privacy), stringResource(R.string.settings_about_privacy_summary))
            AboutRow(Icons.Outlined.Description, stringResource(R.string.settings_about_license), stringResource(R.string.settings_license_gpl3)) {
                onOpenLicense(LicenseKind.GPL3)
            }
            AboutRow(Icons.Outlined.Code, stringResource(R.string.settings_about_libraries), stringResource(R.string.settings_about_libraries_summary)) {
                onOpenLibraries()
            }
        }
    }
    item {
        Text(
            stringResource(R.string.settings_about_copyright),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}

internal fun LazyListScope.librariesPage(onOpenLicense: (LicenseKind) -> Unit) {
    item {
        SettingsCard {
            OpenSourceLibraries.forEach { library ->
                AboutRow(icon = null, title = library.name, summary = "${library.owner} · ${stringResource(library.license.title)}") {
                    onOpenLicense(library.license)
                }
            }
        }
    }
}

internal fun LazyListScope.licenseTextPage(paragraphs: List<String>) {
    items(paragraphs.size) { index ->
        Text(
            paragraphs[index],
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
        )
    }
}

/** the full text of a license, read off the main thread because the gpl alone is about 35 kilobytes */
@Composable
internal fun rememberLicenseParagraphs(kind: LicenseKind): List<String> {
    val resources = LocalResources.current
    val paragraphs by produceState(emptyList(), kind, resources) {
        value = withContext(Dispatchers.IO) { readLicense(resources, kind) }
    }
    return paragraphs
}

private fun readLicense(resources: Resources, kind: LicenseKind): List<String> = try {
    licenseParagraphs(resources.openRawResource(kind.text).bufferedReader().use { it.readText() })
} catch (e: IOException) {
    Log.w("AboutSettings", "could not read ${kind.name}", e)
    emptyList()
} catch (e: Resources.NotFoundException) {
    Log.w("AboutSettings", "missing ${kind.name}", e)
    emptyList()
}

/**
 * spark's glyph on its own, the ring and the spark leaving it, filled with the fire gradient from the
 * launcher icon. it is drawn from the same coordinates as art/spark-launcher-icon.svg, cropped to the
 * glyph's 50 unit square, so the two never drift apart in shape.
 */
@Composable
internal fun SparkGlyph(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val unit = this.size.minDimension / GLYPH_SPAN
        val fire = Brush.linearGradient(
            0f to Color(0xFFFFC233),
            0.5f to Color(0xFFFF6B1A),
            1f to Color(0xFFD3300C),
            start = Offset.Zero,
            end = Offset(this.size.width, this.size.height),
        )
        fun at(x: Float, y: Float) = Offset((x - GLYPH_LEFT) * unit, (y - GLYPH_TOP) * unit)
        val radius = RING_RADIUS * unit
        // the ring opens between 80 and 10 degrees above the right, where the spark escapes
        drawArc(
            brush = fire,
            startAngle = -10f,
            sweepAngle = 290f,
            useCenter = false,
            topLeft = at(RING_X, RING_Y) - Offset(radius, radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = RING_STROKE * unit, cap = StrokeCap.Round),
        )
        drawCircle(brush = fire, radius = SPARK_RADIUS * unit, center = at(SPARK_X, SPARK_Y))
    }
}

// glyph geometry on the 108 unit adaptive icon canvas, matching the svg
private const val GLYPH_LEFT = 27.5f
private const val GLYPH_TOP = 31.5f
private const val GLYPH_SPAN = 50f
private const val RING_X = 51.5f
private const val RING_Y = 57.5f
private const val RING_RADIUS = 20f
private const val RING_STROKE = 8f
private const val SPARK_X = 72f
private const val SPARK_Y = 37f
private const val SPARK_RADIUS = 5.5f

/** spark's icon and name; the about page ([showDetails]) centers them above the version and tagline, the main page stacks a left aligned logo over a large title */
@Composable
internal fun BrandHeader(showDetails: Boolean) {
    val context = LocalContext.current
    val version = remember(context) { appVersion(context) }
    if (!showDetails) {
        // the main page leads with a left aligned logo and a large title under it
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            SparkGlyph(ROW_ICON)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge)
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SparkGlyph(HEADER_ICON)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_about_app_name), style = MaterialTheme.typography.headlineSmall)
        if (showDetails && version != null) {
            Text(
                stringResource(R.string.settings_about_version, version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showDetails) {
            Text(
                stringResource(R.string.settings_about_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AboutRow(icon: ImageVector?, title: String, summary: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.size(20.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** the app has no internet access of its own, so updates are checked by handing the page to the browser */
private fun openReleasesPage(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w("AboutSettings", "no browser to open the releases page", e)
        Toast.makeText(context, R.string.settings_about_no_browser, Toast.LENGTH_SHORT).show()
    }
}

private const val RELEASES_URL = "https://github.com/ThisIsHP64/spark-launcher/releases"

private fun appVersion(context: Context): String? = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
} catch (e: PackageManager.NameNotFoundException) {
    null
}

private val HEADER_ICON = 72.dp
private val ROW_ICON = 56.dp
