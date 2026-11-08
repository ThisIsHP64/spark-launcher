package dev.gearturner.sparklauncher.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import dev.gearturner.sparklauncher.core.model.GridLimits
import dev.gearturner.sparklauncher.core.model.HomeMode
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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

private enum class SettingsPage { MAIN, LOOK, HOME, DRAWER }

/** a short list of sections, each its own page, so no single page grows into a wall of options */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(settings: LauncherSettings, viewModel: SettingsViewModel, onBack: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    BackHandler(enabled = page != SettingsPage.MAIN) { page = SettingsPage.MAIN }
    val title = when (page) {
        SettingsPage.MAIN -> stringResource(R.string.settings_title)
        SettingsPage.LOOK -> stringResource(R.string.settings_page_look)
        SettingsPage.HOME -> stringResource(R.string.settings_page_home)
        SettingsPage.DRAWER -> stringResource(R.string.settings_page_drawer)
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { if (page == SettingsPage.MAIN) onBack() else page = SettingsPage.MAIN }) {
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
            when (page) {
                SettingsPage.MAIN -> mainPage { page = it }
                SettingsPage.LOOK -> lookPage(settings, viewModel)
                SettingsPage.HOME -> homePage(settings, viewModel)
                SettingsPage.DRAWER -> drawerPage(settings, viewModel)
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

private fun LazyListScope.mainPage(open: (SettingsPage) -> Unit) {
    item {
        PageEntry(Icons.Outlined.Palette, stringResource(R.string.settings_page_look), stringResource(R.string.settings_page_look_summary)) {
            open(SettingsPage.LOOK)
        }
    }
    item {
        PageEntry(Icons.Outlined.GridView, stringResource(R.string.settings_page_home), stringResource(R.string.settings_page_home_summary)) {
            open(SettingsPage.HOME)
        }
    }
    item {
        PageEntry(Icons.Outlined.Apps, stringResource(R.string.settings_page_drawer), stringResource(R.string.settings_page_drawer_summary)) {
            open(SettingsPage.DRAWER)
        }
    }
}

private fun LazyListScope.lookPage(settings: LauncherSettings, viewModel: SettingsViewModel) {
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
    item { IconPackRow(settings.iconPack, viewModel) }
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
}

private fun LazyListScope.homePage(settings: LauncherSettings, viewModel: SettingsViewModel) {
    item { HomeModeRow(settings.homeMode) { mode -> viewModel.update { it.copy(homeMode = mode) } } }
    item { SectionHeader(stringResource(R.string.settings_section_grid)) }
    item { GridPreview(settings) }
    item {
        Stepper(stringResource(R.string.settings_columns), settings.homeColumns, GridLimits.HOME_COLUMNS) { value ->
            viewModel.updateGrid { it.copy(homeColumns = value) }
        }
    }
    item {
        Stepper(stringResource(R.string.settings_rows), settings.homeRows, GridLimits.HOME_ROWS) { value ->
            viewModel.updateGrid { it.copy(homeRows = value) }
        }
    }
    item {
        Stepper(
            stringResource(R.string.settings_dock),
            settings.dockSlots,
            GridLimits.DOCK_SLOTS,
            display = { if (it == 0) stringResource(R.string.settings_dock_hidden) else it.toString() },
        ) { value -> viewModel.updateGrid { it.copy(dockSlots = value) } }
    }
    item { SectionHeader(stringResource(R.string.settings_section_icons)) }
    item { IconScaleSlider(settings.iconScale) { scale -> viewModel.update { it.copy(iconScale = scale) } } }
    item {
        SwitchRow(
            title = stringResource(R.string.settings_home_labels),
            summary = stringResource(R.string.settings_home_labels_summary),
            checked = settings.showHomeLabels,
        ) { on -> viewModel.update { it.copy(showHomeLabels = on) } }
    }
    item {
        SegmentedSetting(
            label = stringResource(R.string.settings_label_lines),
            options = listOf(1 to stringResource(R.string.settings_label_lines_one), 2 to stringResource(R.string.settings_label_lines_two)),
            selected = settings.labelLines,
        ) { lines -> viewModel.update { it.copy(labelLines = lines) } }
    }
}

private fun LazyListScope.drawerPage(settings: LauncherSettings, viewModel: SettingsViewModel) {
    item {
        Stepper(stringResource(R.string.settings_columns), settings.drawerColumns, GridLimits.DRAWER_COLUMNS) { value ->
            viewModel.update { it.copy(drawerColumns = value) }
        }
    }
    item {
        SwitchRow(
            title = stringResource(R.string.settings_drawer_labels),
            summary = stringResource(R.string.settings_drawer_labels_summary),
            checked = settings.showDrawerLabels,
        ) { on -> viewModel.update { it.copy(showDrawerLabels = on) } }
    }
}

@Composable
private fun PageEntry(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.size(20.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeModeRow(selected: HomeMode, onSelect: (HomeMode) -> Unit) {
    SegmentedSetting(
        label = stringResource(R.string.settings_home_mode),
        options = listOf(
            HomeMode.DRAWER to stringResource(R.string.settings_home_mode_drawer),
            HomeMode.HOME_ONLY to stringResource(R.string.settings_home_mode_home_only),
        ),
        selected = selected,
        onSelect = onSelect,
    )
    Text(
        stringResource(
            if (selected == HomeMode.DRAWER) R.string.settings_home_mode_drawer_summary else R.string.settings_home_mode_home_only_summary,
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

/** a phone shaped sketch of the grid and dock, at the screen's own proportions */
@Composable
private fun GridPreview(settings: LauncherSettings) {
    val config = LocalConfiguration.current
    val aspect = config.screenWidthDp.toFloat() / config.screenHeightDp.coerceAtLeast(1)
    val dot = MaterialTheme.colorScheme.primary
    val dockDot = MaterialTheme.colorScheme.tertiary
    val frame = MaterialTheme.colorScheme.surfaceContainerHighest
    val description = stringResource(R.string.settings_grid_preview, settings.homeColumns, settings.homeRows, settings.dockSlots)
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .height(220.dp)
                .aspectRatio(aspect)
                .semantics { contentDescription = description },
        ) {
            drawRoundRect(frame, cornerRadius = CornerRadius(24.dp.toPx()))
            val pad = 12.dp.toPx()
            val dockHeight = if (settings.dockSlots > 0) size.height * 0.12f else 0f
            val gridHeight = size.height - 2 * pad - dockHeight - pad
            val cellW = (size.width - 2 * pad) / settings.homeColumns
            val cellH = gridHeight / settings.homeRows
            val radius = minOf(cellW, cellH) * 0.28f
            for (row in 0 until settings.homeRows) {
                for (col in 0 until settings.homeColumns) {
                    drawCircle(dot, radius, Offset(pad + cellW * (col + 0.5f), pad + cellH * (row + 0.5f)))
                }
            }
            if (settings.dockSlots > 0) {
                val slotW = (size.width - 2 * pad) / settings.dockSlots
                val y = size.height - pad - dockHeight / 2f
                for (slot in 0 until settings.dockSlots) drawCircle(dockDot, radius, Offset(pad + slotW * (slot + 0.5f), y))
            }
        }
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    range: IntRange,
    display: @Composable (Int) -> String = { it.toString() },
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.settings_decrease, label))
        }
        Text(display(value), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.settings_increase, label))
        }
    }
}

@Composable
private fun IconScaleSlider(scale: Float, onChange: (Float) -> Unit) {
    var value by remember(scale) { mutableFloatStateOf(scale * 100f) }
    SettingLabel(stringResource(R.string.settings_icon_size, value.roundToInt()))
    Slider(
        value = value,
        onValueChange = { value = it },
        // icons re-render once per release instead of once per frame of the drag
        onValueChangeFinished = { onChange(value.roundToInt() / 100f) },
        valueRange = GridLimits.ICON_SCALE_MIN * 100f..GridLimits.ICON_SCALE_MAX * 100f,
        steps = 11,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
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
private fun IconPackRow(selected: String?, viewModel: SettingsViewModel) {
    val packs by viewModel.packs.collectAsStateWithLifecycle()
    var choosing by remember { mutableStateOf(false) }
    val none = stringResource(R.string.settings_icon_pack_none)
    // a chosen pack that was uninstalled reads as app icons, which is what is showing; the choice
    // itself is kept so reinstalling the pack brings its icons back
    val active = packs?.firstOrNull { it.packageName == selected }
    val current = active?.label ?: none
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { choosing = true }
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(stringResource(R.string.settings_icon_pack), style = MaterialTheme.typography.bodyLarge)
        Text(current, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (choosing) {
        val options = listOf<Pair<String?, String>>(null to none) + packs.orEmpty().map { it.packageName to it.label }
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.settings_icon_pack)) },
            text = {
                LazyColumn {
                    items(options.size) { index ->
                        val (pkg, label) = options[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    choosing = false
                                    viewModel.update { it.copy(iconPack = pkg) }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = pkg == active?.packageName, onClick = null)
                            Spacer(modifier = Modifier.size(12.dp))
                            Text(label)
                        }
                    }
                    if (packs?.isEmpty() == true) {
                        item {
                            Text(
                                stringResource(R.string.settings_icon_pack_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.settings_close)) } },
        )
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
