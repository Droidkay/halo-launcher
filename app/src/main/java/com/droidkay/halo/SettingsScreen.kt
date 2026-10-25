package com.droidkay.halo

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val Panel = Color(0xFF1C1C1E)
private val Divider = Color.White.copy(alpha = 0.08f)

/** Nova-style settings for Halo, opened from the ⚙ button in jiggle mode. */
@Composable
fun SettingsScreen(state: LauncherState, current: LayoutMode, onDismiss: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onDismiss)
    var screen by remember { mutableStateOf(current) }
    var resetArmed by remember { mutableStateOf(false) }
    val packs = remember { IconPacks.installed(context) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Halo Settings", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            GlassPill("Done", bold = true, onClick = onDismiss)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                // ------------------------------------------------ home screen
                Section("HOME SCREEN") {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Screen", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Segmented(
                            listOf(LayoutMode.COVER to "Cover", LayoutMode.INNER to "Inner"),
                            screen,
                        ) { screen = it }
                    }
                    Line()
                    val cols = if (screen == LayoutMode.COVER) HaloSettings.coverCols else HaloSettings.innerCols
                    val rows = if (screen == LayoutMode.COVER) HaloSettings.coverRows else HaloSettings.innerRows
                    Stepper("Columns", cols, 3..(if (screen == LayoutMode.COVER) 6 else 8)) {
                        HaloSettings.setGrid(screen, it, rows)
                        state.reflow(screen)
                    }
                    Line()
                    Stepper("Rows", rows, 4..9) {
                        HaloSettings.setGrid(screen, cols, it)
                        state.reflow(screen)
                    }
                    if (screen == LayoutMode.INNER) {
                        Line()
                        Toggle(
                            "iPhone-style grid",
                            "Keeps the grid phone-sized in the middle of the inner screen. Off spreads it across the screen.",
                            HaloSettings.innerCentered,
                        ) { HaloSettings.updateInnerCentered(it) }
                    }
                    Line()
                    Choice(
                        "Page indicator (inner screen)",
                        listOf("dots" to "Dots", "search" to "Search pill"),
                        HaloSettings.pageIndicator,
                    ) { HaloSettings.updatePageIndicator(it) }
                    Line()
                    Toggle("App Library page", "The last page sorts every app into folders.", HaloSettings.appLibrary) {
                        HaloSettings.updateAppLibrary(it)
                    }
                    Line()
                    SliderRow("Wallpaper dim", HaloSettings.wallpaperDim, 0f..0.6f, "${(HaloSettings.wallpaperDim * 100).roundToInt()}%") {
                        HaloSettings.updateWallpaperDim(it)
                    }
                }

                // ------------------------------------------------ icons
                Section("ICONS") {
                    SliderRow("Icon size", HaloSettings.iconScale, 0.7f..1.4f, "${(HaloSettings.iconScale * 100).roundToInt()}%") {
                        HaloSettings.updateIconScale(it)
                    }
                    Line()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Icon shape", color = Color.White, fontSize = 16.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            listOf("squircle" to "iOS", "circle" to "Circle", "rounded" to "Rounded", "square" to "Square", "teardrop" to "Teardrop").forEach { (id, title) ->
                                val selected = HaloSettings.iconShape == id
                                Column(
                                    Modifier.clickable {
                                        if (!selected) {
                                            HaloSettings.updateIconShape(id)
                                            state.reloadIcons()
                                        }
                                    },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(
                                        Modifier
                                            .size(46.dp)
                                            .clip(iconShapeFor(id))
                                            .background(if (selected) IosBlue else Color.White.copy(alpha = 0.25f))
                                    )
                                    Text(title, color = if (selected) Color.White else IosGray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                    Line()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Icon pack", color = Color.White, fontSize = 16.sp)
                        Text(
                            if (packs.isEmpty()) "No icon packs installed. Install any Nova-compatible icon pack from the Play Store and it will show up here."
                            else "Icon packs made for Nova, ADW and similar launchers.",
                            color = IosGray,
                            fontSize = 13.sp,
                        )
                    }
                    (listOf(IconPacks.PackInfo("", "System icons")) + packs).forEach { p ->
                        Radio(p.label, HaloSettings.iconPack == p.pkg) {
                            if (HaloSettings.iconPack != p.pkg) {
                                HaloSettings.updateIconPack(p.pkg)
                                state.reloadIcons()
                            }
                        }
                    }
                    Line()
                    Toggle("App names", null, HaloSettings.showLabels) { HaloSettings.updateShowLabels(it) }
                    if (HaloSettings.showLabels) {
                        Line()
                        SliderRow("Name size", HaloSettings.labelScale, 0.8f..1.3f, "${(HaloSettings.labelScale * 100).roundToInt()}%") {
                            HaloSettings.updateLabelScale(it)
                        }
                    }
                    Line()
                    Toggle("Widget names", "Show the widget's name under it, like iOS.", HaloSettings.widgetLabels) {
                        HaloSettings.updateWidgetLabels(it)
                    }
                }

                // ------------------------------------------------ dock
                Section("DOCK") {
                    Toggle("Show dock", null, HaloSettings.dockEnabled) { HaloSettings.updateDockEnabled(it) }
                    if (HaloSettings.dockEnabled) {
                        Line()
                        Stepper("Dock apps", HaloSettings.dockCount, 3..7) {
                            HaloSettings.updateDockCount(it)
                            state.reflowAll()
                        }
                        Line()
                        Toggle("Dock background", "The frosted pill behind the dock icons.", HaloSettings.dockBackground) {
                            HaloSettings.updateDockBackground(it)
                        }
                        Line()
                        Toggle("Side dock on cover screen", "iPhone Duo style: dock down the right edge.", HaloSettings.coverSideDock) {
                            HaloSettings.updateCoverSideDock(it)
                        }
                        Line()
                        Toggle("Side dock in landscape", "Inner screen held sideways.", HaloSettings.landscapeSideDock) {
                            HaloSettings.updateLandscapeSideDock(it)
                        }
                    }
                }

                // ------------------------------------------------ gestures
                val actions = listOf("spotlight" to "Search", "notifications" to "Notifications", "library" to "App Library", "none" to "Nothing")
                Section("GESTURES") {
                    Choice("Swipe down", actions, HaloSettings.swipeDown) { HaloSettings.updateSwipeDown(it) }
                    Line()
                    Choice("Swipe up", actions, HaloSettings.swipeUp) { HaloSettings.updateSwipeUp(it) }
                    Line()
                    Choice(
                        "Double-tap",
                        listOf("none" to "Nothing", "spotlight" to "Search", "notifications" to "Notifications", "edit" to "Edit Home Screen"),
                        HaloSettings.doubleTap,
                    ) { HaloSettings.updateDoubleTap(it) }
                }

                // ------------------------------------------------ layout
                Section("LAYOUT") {
                    Toggle(
                        "Lock home screen",
                        "Stops icons being moved or removed by accident. Long-press empty space to get back here.",
                        HaloSettings.lockLayout,
                    ) {
                        HaloSettings.updateLockLayout(it)
                        if (it) state.editMode = false
                    }
                    if (!BuildConfig.IS_PAD) {
                        Line()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val n = state.addCategoryFolders(screen)
                                    Toast.makeText(
                                        context,
                                        if (n > 0) "Added $n category folders" else "Apps sorted into your folders",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Column {
                                Text("Auto Folders", color = IosBlue, fontSize = 16.sp)
                                Text("Sort every app into category folders (Social, Entertainment…) on the ${if (screen == LayoutMode.COVER) "cover" else "inner"} screen.", color = IosGray, fontSize = 13.sp)
                            }
                        }
                    }
                    Line()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (resetArmed) {
                                    state.resetLayout(screen)
                                    resetArmed = false
                                    Toast.makeText(context, "Home screen reset", Toast.LENGTH_SHORT).show()
                                } else resetArmed = true
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            if (resetArmed) "Tap again to reset the ${if (screen == LayoutMode.COVER) "cover" else "inner"} screen"
                            else "Reset ${if (screen == LayoutMode.COVER) "cover" else "inner"} screen layout",
                            color = IosRed,
                            fontSize = 16.sp,
                        )
                    }
                }
                Text(
                    "Halo ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""}",
                    color = IosGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ building blocks

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        color = IosGray,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 32.dp, top = 22.dp, bottom = 6.dp),
    )
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Panel),
        content = content,
    )
}

@Composable
private fun Line() {
    Box(
        Modifier
            .padding(start = 16.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Divider)
    )
}

@Composable
private fun Toggle(title: String, subtitle: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!value) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = IosGray, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = IosGreen,
                checkedThumbColor = Color.White,
                checkedBorderColor = IosGreen,
                uncheckedTrackColor = Color(0xFF39393D),
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = Color(0xFF39393D),
            ),
        )
    }
}

@Composable
private fun Stepper(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        StepButton("−", value > range.first) { onChange(value - 1) }
        Text(
            value.toString(),
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(40.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepButton("+", value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.14f else 0.05f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) Color.White else IosGray, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = IosGray, fontSize = 15.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = IosBlue,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f),
            ),
        )
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(2.dp)
    ) {
        options.forEach { (id, title) ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (id == selected) Color.White.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable { onSelect(id) }
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** A setting with a few named options, shown as chips. */
@Composable
private fun Choice(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (id, label) ->
                val on = id == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) IosBlue else Color.White.copy(alpha = 0.1f))
                        .clickable { onSelect(id) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(label, color = Color.White, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun Radio(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) IosBlue else IosGray, CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(if (selected) IosBlue else Color.Transparent)
        )
    }
}
