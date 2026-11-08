package dev.gearturner.sparklauncher.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.gearturner.sparklauncher.core.icons.IconShapes
import dev.gearturner.sparklauncher.core.model.IconShape
import dev.gearturner.sparklauncher.core.model.IconStyle
import dev.gearturner.sparklauncher.core.model.LauncherSettings
import dev.gearturner.sparklauncher.core.theme.LocalSparkDarkTheme
import dev.gearturner.sparklauncher.core.theme.themedIconColors

/** the icon look chosen in settings, provided once at the root so every icon agrees */
val LocalIconStyle = staticCompositionLocalOf { IconStyle() }

/** the icon outline as a compose shape, so plates and placeholders match the icons exactly */
@Composable
fun rememberIconShape(style: IconStyle = LocalIconStyle.current): Shape =
    remember(style.shape, style.cornerPercent) { IconOutline(style.shape, style.cornerPercent) }

private class IconOutline(private val shape: IconShape, private val cornerPercent: Int) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val side = size.minDimension
        val path = IconShapes.path(shape, side, cornerPercent).asComposePath().apply {
            // centers the square outline when the box is not square
            translate(Offset((size.width - side) / 2f, (size.height - side) / 2f))
        }
        return Outline.Generic(path)
    }
}

/** provides the icon style for [settings] from the active theme, shared by the launcher and settings */
@Composable
fun ProvideIconStyle(settings: LauncherSettings, content: @Composable () -> Unit) {
    val (plate, glyph) = MaterialTheme.colorScheme.themedIconColors(LocalSparkDarkTheme.current)
    val style = remember(settings, plate, glyph) { IconStyle.from(settings, plate, glyph) }
    CompositionLocalProvider(LocalIconStyle provides style, content = content)
}
