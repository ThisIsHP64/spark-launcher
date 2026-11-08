package dev.gearturner.sparklauncher.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gearturner.sparklauncher.core.model.ColorSource
import dev.gearturner.sparklauncher.core.model.IconShape
import dev.gearturner.sparklauncher.core.model.LauncherSettings
import dev.gearturner.sparklauncher.core.model.PaletteStyleOption
import dev.gearturner.sparklauncher.core.model.ThemeMode
import dev.gearturner.sparklauncher.core.ui.BaseIconSize
import dev.gearturner.sparklauncher.core.ui.LocalIconStyle
import dev.gearturner.sparklauncher.core.ui.rememberAppIcon
import dev.gearturner.sparklauncher.core.ui.rememberIconShape
import kotlin.math.roundToInt

/** hand picked accents that each produce a pleasant palette in every style */
private val SwatchColors = listOf(
    0xFF4A6FA5, 0xFF3E7CB1, 0xFF00796B, 0xFF2E7D32, 0xFF7CB342, 0xFFF9A825,
    0xFFEF6C00, 0xFFD84315, 0xFFC2185B, 0xFF8E24AA, 0xFF5E35B1, 0xFF6D4C41,
).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(settings: LauncherSettings, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            item { SectionHeader(stringResource(R.string.settings_section_colors)) }
            item { ThemeModeRow(settings.themeMode) { mode -> viewModel.update { it.copy(themeMode = mode) } } }
            item { ColorSourceRow(settings.colorSource) { source -> viewModel.update { it.copy(colorSource = source) } } }
            if (settings.colorSource == ColorSource.CUSTOM) {
                item { SeedColorPicker(settings.seedColor) { color -> viewModel.update { it.copy(seedColor = color) } } }
            }
            item { PaletteStyleRow(settings.paletteStyle) { style -> viewModel.update { it.copy(paletteStyle = style) } } }
            item { PalettePreview() }

            item { SectionHeader(stringResource(R.string.settings_section_icons)) }
            item { IconPreviewRow(viewModel) }
            item { IconShapeRow(settings.iconShape) { shape -> viewModel.update { it.copy(iconShape = shape) } } }
            if (settings.iconShape == IconShape.ROUNDED) {
                item {
                    CornerSlider(settings.iconCornerRadiusPercent) { percent ->
                        viewModel.update { it.copy(iconCornerRadiusPercent = percent) }
                    }
                }
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_themed_icons),
                    summary = stringResource(R.string.settings_themed_icons_summary),
                    checked = settings.themedIcons,
                ) { on -> viewModel.update { it.copy(themedIcons = on) } }
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_themed_fallback),
                    summary = stringResource(R.string.settings_themed_fallback_summary),
                    checked = settings.themedIconFallback,
                    enabled = settings.themedIcons,
                ) { on -> viewModel.update { it.copy(themedIconFallback = on) } }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SegmentedSetting(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SettingLabel(label)
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
    ) {
        options.forEachIndexed { index, (value, text) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(text) }
        }
    }
}

@Composable
private fun ThemeModeRow(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) = SegmentedSetting(
    label = stringResource(R.string.settings_theme),
    options = listOf(
        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
        ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
        ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
    ),
    selected = selected,
    onSelect = onSelect,
)

@Composable
private fun ColorSourceRow(selected: ColorSource, onSelect: (ColorSource) -> Unit) = SegmentedSetting(
    label = stringResource(R.string.settings_color_source),
    options = listOf(
        ColorSource.WALLPAPER to stringResource(R.string.settings_color_wallpaper),
        ColorSource.CUSTOM to stringResource(R.string.settings_color_custom),
    ),
    selected = selected,
    onSelect = onSelect,
)

/** preset swatches for a quick pick, plus a hue slider for anything in between */
@Composable
private fun SeedColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    SettingLabel(stringResource(R.string.settings_custom_color))
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SwatchColors.forEach { color ->
            val description = stringResource(R.string.settings_color_swatch, "#%06X".format(color and 0xFFFFFF))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(color), CircleShape)
                    .border(
                        width = if (color == selected) 3.dp else 0.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(color) }
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                if (color == selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
            }
        }
    }
    var hue by remember(selected) { mutableFloatStateOf(hueOf(selected)) }
    Text(
        stringResource(R.string.settings_hue),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = 24.dp, top = 8.dp),
    )
    Slider(
        value = hue,
        onValueChange = { hue = it },
        // the palette is rebuilt once the finger lifts, not on every frame of the drag
        onValueChangeFinished = { onSelect(Color.hsv(hue, 0.55f, 0.7f).toArgb()) },
        valueRange = 0f..359f,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}

private fun hueOf(argb: Int): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    return hsv[0]
}

@Composable
private fun PaletteStyleRow(selected: PaletteStyleOption, onSelect: (PaletteStyleOption) -> Unit) {
    SettingLabel(stringResource(R.string.settings_palette_style))
    val labels = mapOf(
        PaletteStyleOption.TONAL_SPOT to stringResource(R.string.settings_style_tonal_spot),
        PaletteStyleOption.VIBRANT to stringResource(R.string.settings_style_vibrant),
        PaletteStyleOption.EXPRESSIVE to stringResource(R.string.settings_style_expressive),
        PaletteStyleOption.FIDELITY to stringResource(R.string.settings_style_fidelity),
        PaletteStyleOption.MONOCHROME to stringResource(R.string.settings_style_monochrome),
    )
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PaletteStyleOption.entries.forEach { style ->
            FilterChip(selected = style == selected, onClick = { onSelect(style) }, label = { Text(labels.getValue(style)) })
        }
    }
}

/** the main palette roles as swatches, so a style change is visible before leaving settings */
@Composable
private fun PalettePreview() {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primaryContainer, scheme.surfaceContainerHighest)
            .forEach { color -> Box(modifier = Modifier.size(32.dp).background(color, CircleShape)) }
    }
}

@Composable
private fun IconPreviewRow(viewModel: SettingsViewModel) {
    val apps by viewModel.previewApps.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        apps.forEach { key ->
            val icon = rememberAppIcon(key, BaseIconSize)
            Box(modifier = Modifier.size(BaseIconSize)) {
                if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(BaseIconSize))
            }
        }
    }
}

@Composable
private fun IconShapeRow(selected: IconShape, onSelect: (IconShape) -> Unit) {
    SettingLabel(stringResource(R.string.settings_icon_shape))
    val labels = mapOf(
        IconShape.CIRCLE to stringResource(R.string.settings_shape_circle),
        IconShape.SQUIRCLE to stringResource(R.string.settings_shape_squircle),
        IconShape.ROUNDED to stringResource(R.string.settings_shape_rounded),
        IconShape.SQUARE to stringResource(R.string.settings_shape_square),
        IconShape.TEARDROP to stringResource(R.string.settings_shape_teardrop),
    )
    val base = LocalIconStyle.current
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconShape.entries.forEach { shape ->
            val outline = rememberIconShape(base.copy(shape = shape, cornerPercent = if (shape == IconShape.ROUNDED) 30 else base.cornerPercent))
            val isSelected = shape == selected
            Column(
                modifier = Modifier
                    .clickable { onSelect(shape) }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            outline,
                        ),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    labels.getValue(shape),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CornerSlider(percent: Int, onChange: (Int) -> Unit) {
    var value by remember(percent) { mutableFloatStateOf(percent.toFloat()) }
    SettingLabel(stringResource(R.string.settings_corner_radius, value.roundToInt()))
    Slider(
        value = value,
        onValueChange = { value = it },
        // icons re-render once per release instead of once per frame of the drag
        onValueChangeFinished = { onChange(value.roundToInt()) },
        valueRange = 0f..50f,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.size(16.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
