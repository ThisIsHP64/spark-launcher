package dev.gearturner.sparklauncher.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import dev.gearturner.sparklauncher.core.ui.Choice
import dev.gearturner.sparklauncher.core.ui.ChoiceDialog
import dev.gearturner.sparklauncher.core.ui.rememberIconShape
import kotlin.math.roundToInt

/** hand picked accents that each produce a pleasant palette in every style */
private val SwatchColors = listOf(
    0xFF4A6FA5, 0xFF3E7CB1, 0xFF00796B, 0xFF2E7D32, 0xFF7CB342, 0xFFF9A825,
    0xFFEF6C00, 0xFFD84315, 0xFFC2185B, 0xFF8E24AA, 0xFF5E35B1, 0xFF6D4C41,
).map { it.toInt() }

private enum class SettingsPage { MAIN, LOOK, HOME, DRAWER, GESTURES, SEARCH, ABOUT, LIBRARIES, LICENSE }

/** a short list of sections, each its own page, so no single page grows into a wall of options */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsScreen(settings: LauncherSettings, viewModel: SettingsViewModel) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    var license by rememberSaveable { mutableStateOf(LicenseKind.GPL3) }
    val parent = page.parent(license)
    val openLicense: (LicenseKind) -> Unit = {
        license = it
        page = SettingsPage.LICENSE
    }
    BackHandler(enabled = parent != null) { parent?.let { page = it } }
    // only the main list outlives its page, so coming back keeps its scroll position
    val mainListState = rememberLazyListState()
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        // the fading pages are partly see through, so the themed background sits under them
        // instead of the white window behind the activity
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        targetState = page,
        transitionSpec = {
            // deeper pages enter from the end and shallower ones from the start
            val direction = if (targetState.depth(license) >= initialState.depth(license)) 1 else -1
            val enter = slideInHorizontally(motion.defaultSpatialSpec()) { direction * it / 4 } +
                fadeIn(motion.defaultEffectsSpec())
            val exit = slideOutHorizontally(motion.defaultSpatialSpec()) { -direction * it / 4 } +
                fadeOut(motion.fastEffectsSpec())
            (enter togetherWith exit).using(SizeTransform(clip = false))
        },
        label = "settings page",
    ) { shown ->
        // each page owns its bar state and list state, so a page always opens at the top and expanded
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        val listState = if (shown == SettingsPage.MAIN) mainListState else rememberLazyListState()
        val shownParent = shown.parent(license)
        val licenseParagraphs = if (shown == SettingsPage.LICENSE) rememberLicenseParagraphs(license) else emptyList()
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            // the main page opens on spark's centered branding in the list itself, so it has no bar
            topBar = {
                if (shown != SettingsPage.MAIN) LargeFlexibleTopAppBar(
                    title = { Text(title(shown, license)) },
                    // the main page leaves with the system back gesture; only sub-pages show a way up
                    navigationIcon = {
                        shownParent?.let { up ->
                            IconButton(onClick = { page = up }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            LazyColumn(
                state = listState,
                contentPadding = padding,
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            ) {
                when (shown) {
                    SettingsPage.MAIN -> mainPage { page = it }
                    SettingsPage.LOOK -> lookPage(settings, viewModel)
                    SettingsPage.HOME -> homePage(settings, viewModel)
                    SettingsPage.DRAWER -> drawerPage(settings, viewModel)
                    SettingsPage.GESTURES -> gesturesPage(settings, viewModel)
                    SettingsPage.SEARCH -> searchPage(settings, viewModel)
                    SettingsPage.ABOUT -> aboutPage(onOpenLicense = openLicense, onOpenLibraries = { page = SettingsPage.LIBRARIES })
                    SettingsPage.LIBRARIES -> librariesPage(openLicense)
                    SettingsPage.LICENSE -> licenseTextPage(licenseParagraphs)
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

/** how many steps from the main page, which decides the slide direction */
private fun SettingsPage.depth(license: LicenseKind): Int = when (this) {
    SettingsPage.MAIN -> 0
    SettingsPage.LIBRARIES -> 2
    SettingsPage.LICENSE -> if (license == LicenseKind.GPL3) 2 else 3
    else -> 1
}

/** each page goes back to the page that opened it; spark's own license opens from about */
private fun SettingsPage.parent(license: LicenseKind): SettingsPage? = when (this) {
    SettingsPage.MAIN -> null
    SettingsPage.LIBRARIES -> SettingsPage.ABOUT
    SettingsPage.LICENSE -> if (license == LicenseKind.GPL3) SettingsPage.ABOUT else SettingsPage.LIBRARIES
    else -> SettingsPage.MAIN
}

@Composable
private fun title(page: SettingsPage, license: LicenseKind): String = when (page) {
    SettingsPage.MAIN -> stringResource(R.string.settings_title)
    SettingsPage.LOOK -> stringResource(R.string.settings_page_look)
    SettingsPage.HOME -> stringResource(R.string.settings_page_home)
    SettingsPage.DRAWER -> stringResource(R.string.settings_page_drawer)
    SettingsPage.GESTURES -> stringResource(R.string.settings_page_gestures)
    SettingsPage.SEARCH -> stringResource(R.string.settings_page_search)
    SettingsPage.ABOUT -> stringResource(R.string.settings_page_about)
    SettingsPage.LIBRARIES -> stringResource(R.string.settings_about_libraries)
    SettingsPage.LICENSE -> stringResource(license.title)
}

private fun LazyListScope.mainPage(open: (SettingsPage) -> Unit) {
    item { BrandHeader(showDetails = false) }
    item {
        SettingsCard {
            PageEntry(Icons.Outlined.Palette, stringResource(R.string.settings_page_look), stringResource(R.string.settings_page_look_summary)) {
                open(SettingsPage.LOOK)
            }
            PageEntry(Icons.Outlined.GridView, stringResource(R.string.settings_page_home), stringResource(R.string.settings_page_home_summary)) {
                open(SettingsPage.HOME)
            }
            PageEntry(Icons.Outlined.Apps, stringResource(R.string.settings_page_drawer), stringResource(R.string.settings_page_drawer_summary)) {
                open(SettingsPage.DRAWER)
            }
            PageEntry(Icons.Outlined.Gesture, stringResource(R.string.settings_page_gestures), stringResource(R.string.settings_page_gestures_summary)) {
                open(SettingsPage.GESTURES)
            }
            PageEntry(Icons.Outlined.Search, stringResource(R.string.settings_page_search), stringResource(R.string.settings_page_search_summary)) {
                open(SettingsPage.SEARCH)
            }
            PageEntry(Icons.Outlined.Info, stringResource(R.string.settings_page_about), stringResource(R.string.settings_page_about_summary)) {
                open(SettingsPage.ABOUT)
            }
        }
    }
}

private fun LazyListScope.lookPage(settings: LauncherSettings, viewModel: SettingsViewModel) {
    item { SectionHeader(stringResource(R.string.settings_section_appearance)) }
    item {
        SettingsCard {
            ThemeModeRow(settings.themeMode) { mode -> viewModel.update { it.copy(themeMode = mode) } }
        }
    }

    item { SectionHeader(stringResource(R.string.settings_section_colors)) }
    item {
        SettingsCard {
            // the preview leads the card so every control below visibly changes it
            PalettePreview()
            CardDivider()
            ColorSourceRow(settings.colorSource) { source -> viewModel.update { it.copy(colorSource = source) } }
            // the accent picker slides in under the source toggle that controls it
            AnimatedVisibility(visible = settings.colorSource == ColorSource.CUSTOM) {
                Column {
                    SeedColorPicker(
                        selected = settings.seedColor,
                        onSelect = { color -> viewModel.update { it.copy(seedColor = color) } },
                        onPreview = viewModel::previewSeed,
                        onCommit = viewModel::commitSeed,
                    )
                }
            }
            CardDivider()
            PaletteStyleRow(settings.paletteStyle) { style -> viewModel.update { it.copy(paletteStyle = style) } }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    item { SectionHeader(stringResource(R.string.settings_section_icons)) }
    item {
        SettingsCard {
            IconPreviewRow(viewModel)
            CardDivider()
            IconShapeRow(settings.iconShape) { shape -> viewModel.update { it.copy(iconShape = shape) } }
            AnimatedVisibility(visible = settings.iconShape == IconShape.ROUNDED) {
                Column {
                    CornerSlider(settings.iconCornerRadiusPercent) { percent ->
                        viewModel.update { it.copy(iconCornerRadiusPercent = percent) }
                    }
                }
            }
        }
    }
    item { Spacer(modifier = Modifier.height(12.dp)) }
    item { SettingsCard { IconPackRow(settings.iconPack, viewModel) } }

    item { SectionHeader(stringResource(R.string.settings_section_icon_colors)) }
    item {
        SettingsCard {
            SwitchRow(
                title = stringResource(R.string.settings_themed_icons),
                summary = stringResource(R.string.settings_themed_icons_summary),
                checked = settings.themedIcons,
            ) { on -> viewModel.update { it.copy(themedIcons = on) } }
            // indented because it only applies while themed icons are on
            SwitchRow(
                title = stringResource(R.string.settings_themed_fallback),
                summary = stringResource(R.string.settings_themed_fallback_summary),
                checked = settings.themedIconFallback,
                enabled = settings.themedIcons,
                indent = 16.dp,
            ) { on -> viewModel.update { it.copy(themedIconFallback = on) } }
        }
    }
}

/** groups related controls on a rounded surface, inset from the screen edge */
@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 8.dp),
        content = content,
    )
}

@Composable
internal fun CardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

private fun LazyListScope.homePage(settings: LauncherSettings, viewModel: SettingsViewModel) {
    item { SectionHeader(stringResource(R.string.settings_section_layout)) }
    item {
        SettingsCard {
            HomeModeRow(settings.homeMode) { mode -> viewModel.update { it.copy(homeMode = mode) } }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    item { SectionHeader(stringResource(R.string.settings_section_grid)) }
    item { HomeGridCard(settings, viewModel) }

    item { SectionHeader(stringResource(R.string.settings_section_labels)) }
    item {
        SettingsCard {
            SwitchRow(
                title = stringResource(R.string.settings_home_labels),
                summary = stringResource(R.string.settings_home_labels_summary),
                checked = settings.showHomeLabels,
            ) { on -> viewModel.update { it.copy(showHomeLabels = on) } }
            SegmentedSetting(
                label = stringResource(R.string.settings_label_lines),
                options = listOf(1 to stringResource(R.string.settings_label_lines_one), 2 to stringResource(R.string.settings_label_lines_two)),
                selected = settings.labelLines,
            ) { lines -> viewModel.update { it.copy(labelLines = lines) } }
            CardDivider()
            SwitchRow(
                title = stringResource(R.string.settings_dots),
                summary = stringResource(R.string.settings_dots_summary),
                checked = settings.notificationDots,
            ) { on -> viewModel.update { it.copy(notificationDots = on) } }
        }
    }
    item { NotificationAccessCard(settings, viewModel) }
}

/** the grid controls under the preview they change, so a drag or a tap is seen as it happens */
@Composable
private fun HomeGridCard(settings: LauncherSettings, viewModel: SettingsViewModel) {
    var liveScale by remember(settings.iconScale) { mutableFloatStateOf(settings.iconScale) }
    SettingsCard {
        GridPreview(settings, liveScale)
        Stepper(stringResource(R.string.settings_columns), settings.homeColumns, GridLimits.HOME_COLUMNS) { value ->
            viewModel.updateGrid { it.copy(homeColumns = value) }
        }
        Stepper(stringResource(R.string.settings_rows), settings.homeRows, GridLimits.HOME_ROWS) { value ->
            viewModel.updateGrid { it.copy(homeRows = value) }
        }
        Stepper(
            stringResource(R.string.settings_dock),
            settings.dockSlots,
            GridLimits.DOCK_SLOTS,
            display = { if (it == 0) stringResource(R.string.settings_dock_hidden) else it.toString() },
        ) { value -> viewModel.updateGrid { it.copy(dockSlots = value) } }
        IconScaleSlider(settings.iconScale, onLiveChange = { liveScale = it }) { scale ->
            viewModel.update { it.copy(iconScale = scale) }
        }
        CardDivider()
        SwitchRow(
            title = stringResource(R.string.settings_subgrid),
            summary = stringResource(R.string.settings_subgrid_summary),
            checked = settings.subgridPositioning,
        ) { on -> viewModel.update { it.copy(subgridPositioning = on) } }
    }
}

private fun LazyListScope.drawerPage(settings: LauncherSettings, viewModel: SettingsViewModel) {
    item { SectionHeader(stringResource(R.string.settings_section_grid)) }
    item { DrawerGridCard(settings, viewModel) }
}

/** the same icon size setting as on the home page, shown here beside the drawer preview it changes */
@Composable
private fun DrawerGridCard(settings: LauncherSettings, viewModel: SettingsViewModel) {
    var liveScale by remember(settings.iconScale) { mutableFloatStateOf(settings.iconScale) }
    SettingsCard {
        DrawerPreview(settings.drawerColumns, liveScale, settings.showDrawerLabels)
        Stepper(stringResource(R.string.settings_columns), settings.drawerColumns, GridLimits.DRAWER_COLUMNS) { value ->
            viewModel.update { it.copy(drawerColumns = value) }
        }
        IconScaleSlider(settings.iconScale, onLiveChange = { liveScale = it }) { scale ->
            viewModel.update { it.copy(iconScale = scale) }
        }
        CardDivider()
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
private fun IconScaleSlider(scale: Float, onLiveChange: (Float) -> Unit, onChange: (Float) -> Unit) {
    var value by remember(scale) { mutableFloatStateOf(scale * 100f) }
    SettingLabel(stringResource(R.string.settings_icon_size, value.roundToInt()))
    Slider(
        value = value,
        onValueChange = {
            value = it
            // only the preview follows the drag; the setting itself is written on release
            onLiveChange(it.roundToInt() / 100f)
        },
        // icons re-render once per release instead of once per frame of the drag
        onValueChangeFinished = { onChange(value.roundToInt() / 100f) },
        valueRange = GridLimits.ICON_SCALE_MIN * 100f..GridLimits.ICON_SCALE_MAX * 100f,
        steps = 11,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}

@Composable
internal fun SectionHeader(title: String) {
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
private fun SeedColorPicker(selected: Int, onSelect: (Int) -> Unit, onPreview: (Int) -> Unit, onCommit: (Int) -> Unit) {
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
        onValueChange = {
            hue = it
            // the theme follows the slider live; only the final hue is written to storage
            onPreview(seedFromHue(it))
        },
        onValueChangeFinished = { onCommit(seedFromHue(hue)) },
        valueRange = 0f..359f,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}

/** the same saturation and value for every hue, so the slider only ever changes the color family */
private fun seedFromHue(hue: Float): Int = Color.hsv(hue, 0.55f, 0.7f).toArgb()

private fun hueOf(argb: Int): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    return hsv[0]
}

@OptIn(ExperimentalLayoutApi::class)
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
    // chips wrap instead of scrolling so every style is visible without discovering a swipe
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
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
        val choices = listOf(Choice<String?>(null, none)) + packs.orEmpty().map { Choice<String?>(it.packageName, it.label) }
        ChoiceDialog(
            title = stringResource(R.string.settings_icon_pack),
            choices = choices,
            selected = active?.packageName,
            onSelect = { pkg ->
                choosing = false
                viewModel.update { it.copy(iconPack = pkg) }
            },
            onDismiss = { choosing = false },
            // with no packs the only choice is app icons, so the dialog also says where packs come from
            footer = if (packs?.isEmpty() == true) {
                {
                    Text(
                        stringResource(R.string.settings_icon_pack_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            } else {
                null
            },
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
internal fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    indent: Dp = 0.dp,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(start = 24.dp + indent, end = 24.dp, top = 12.dp, bottom = 12.dp),
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
