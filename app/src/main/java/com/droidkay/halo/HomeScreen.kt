@file:OptIn(ExperimentalFoundationApi::class)

package com.droidkay.halo

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed class MenuTarget {
    data class HomeApp(val key: String) : MenuTarget()
    data class DockApp(val key: String) : MenuTarget()
    data class LibraryApp(val key: String) : MenuTarget()
    data class Widget(val key: String) : MenuTarget()
    data class HomeFolder(val key: String) : MenuTarget()
    data class FolderApp(val folderKey: String, val key: String) : MenuTarget()
}

data class MenuRequest(val target: MenuTarget, val bounds: Rect)

data class MenuEntry(val label: String, val glyph: String, val destructive: Boolean = false, val action: () -> Unit)

@Composable
fun HaloApp(state: LauncherState) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val config = LocalConfiguration.current
        val smallest = minOf(config.screenWidthDp, config.screenHeightDp)
        // Fold 5: cover screen ≈ 340–410dp wide, inner screen ≈ 670dp+.
        // Halo Pad always uses the iPadOS layout.
        val mode = if (BuildConfig.IS_PAD || smallest >= 500) LayoutMode.INNER else LayoutMode.COVER
        FoldMorphHost(mode, enabled = !BuildConfig.IS_PAD) {
            key(mode) { HomeRoot(state, mode) }
        }
    }
}

@Composable
private fun HomeRoot(state: LauncherState, mode: LayoutMode) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val layout = state.layout(mode)
    val pageCount = layout.pages.size
    val pager = rememberPagerState(pageCount = { state.layout(mode).pages.size + 1 })
    val onLibrary = pager.currentPage >= pageCount

    var spotlight by remember { mutableStateOf(false) }
    var librarySearch by remember { mutableStateOf(false) }
    var gallery by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<MenuRequest?>(null) }
    // An open folder: a library category name, or a home folder's key ("f:…").
    var folder by remember { mutableStateOf<String?>(null) }
    var folderPicker by remember { mutableStateOf<String?>(null) }

    fun closeOverlays(): Boolean {
        val any = spotlight || librarySearch || gallery || menu != null || folder != null || folderPicker != null
        folderPicker = null
        spotlight = false
        librarySearch = false
        gallery = false
        menu = null
        folder = null
        return any
    }

    val launch: (AppInfo, Rect?) -> Unit = { app, bounds -> state.launch(app, view, bounds) }

    var lastSignal by remember { mutableIntStateOf(state.homeSignal) }
    LaunchedEffect(state.homeSignal) {
        if (state.homeSignal != lastSignal) {
            lastSignal = state.homeSignal
            when {
                closeOverlays() -> {}
                state.editMode -> state.editMode = false
                else -> pager.animateScrollToPage(0)
            }
        }
    }

    BackHandler {
        when {
            closeOverlays() -> {}
            state.editMode -> state.editMode = false
            pager.currentPage != 0 -> scope.launch { pager.animateScrollToPage(0) }
            else -> {}
        }
    }

    fun entriesFor(req: MenuRequest): List<MenuEntry> {
        val edit = MenuEntry("Edit Home Screen", "✎") { state.editMode = true }
        return when (val t = req.target) {
            is MenuTarget.HomeApp -> {
                val app = state.appMap[t.key]
                listOfNotNull(
                    edit,
                    app?.let { MenuEntry("App Info", "ⓘ") { state.openAppInfo(it) } },
                    MenuEntry("Move to Folder", "▢") { folderPicker = t.key },
                    MenuEntry("Remove from Home Screen", "−") { state.removeFromHome(mode, t.key) },
                    app?.let { MenuEntry("Delete App", "✕", destructive = true) { state.uninstall(it) } },
                )
            }
            is MenuTarget.DockApp -> {
                val app = state.appMap[t.key]
                listOfNotNull(
                    edit,
                    app?.let { MenuEntry("App Info", "ⓘ") { state.openAppInfo(it) } },
                    MenuEntry("Remove from Dock", "−") { state.removeFromDock(mode, t.key) },
                    app?.let { MenuEntry("Delete App", "✕", destructive = true) { state.uninstall(it) } },
                )
            }
            is MenuTarget.LibraryApp -> {
                val app = state.appMap[t.key]
                buildList {
                    if (!state.isOnHome(mode, t.key)) {
                        add(MenuEntry("Add to Home Screen", "+") {
                            state.addToHome(mode, HomeItem.App(t.key), 0, prepend = false)
                            Toast.makeText(context, "Added to Home Screen", Toast.LENGTH_SHORT).show()
                        })
                    }
                    if (state.canAddToDock(mode, t.key)) {
                        add(MenuEntry("Add to Dock", "+") { state.addToDock(mode, t.key) })
                    }
                    add(MenuEntry("Move to Folder", "▢") { folderPicker = t.key })
                    if (app != null) {
                        add(MenuEntry("App Info", "ⓘ") { state.openAppInfo(app) })
                        add(MenuEntry("Delete App", "✕", destructive = true) { state.uninstall(app) })
                    }
                }
            }
            is MenuTarget.Widget -> listOf(
                edit,
                MenuEntry("Remove Widget", "−", destructive = true) { state.removeFromHome(mode, t.key) },
            )
            is MenuTarget.HomeFolder -> listOf(
                MenuEntry("Rename", "✎") { folder = t.key },
                edit,
                MenuEntry("Remove Folder", "−", destructive = true) { state.removeFromHome(mode, t.key) },
            )
            is MenuTarget.FolderApp -> {
                val app = state.appMap[t.key]
                listOfNotNull(
                    MenuEntry("Move to Another Folder", "▢") {
                        folder = null
                        folderPicker = t.key
                    },
                    MenuEntry("Remove from Folder", "−") { state.removeFromFolder(mode, t.folderKey, t.key) },
                    app?.let { MenuEntry("App Info", "ⓘ") { state.openAppInfo(it) } },
                    app?.let { MenuEntry("Delete App", "✕", destructive = true) { state.uninstall(it) } },
                )
            }
        }
    }

    // iOS-style glass: when a folder, search or menu is open, blur the wallpaper and the icons behind it.
    val blurOn = folder != null || spotlight || librarySearch || menu != null || folderPicker != null
    val blurSupported = rememberWallpaperBlurSupported()
    val overlayBlur by animateFloatAsState(if (blurOn) 1f else 0f, tween(260), label = "overlayBlur")
    // Fold transition (Apple "Duo" style):
    //  - cover screen: the whole screen frosts as the hinge starts to open (and clears as it closes)
    //  - inner screen: only the half that's still swinging is frosted, sharpening as it lands flat
    val fold = rememberFoldAmount(mode, enabled = !BuildConfig.IS_PAD)
    val halfBlur = remember { HalfPanelBlur() }
    val landscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    // iPhone "Duo" layout (Halo on the Fold): side status rail, and the dock moves to the right
    // edge on the cover screen and when the inner screen is held sideways.
    val duo = !BuildConfig.IS_PAD
    val sideDock = duo && (mode == LayoutMode.COVER || landscape)
    WallpaperBlurDriver(blurSupported) {
        val f = if (mode == LayoutMode.COVER) fold.value else 0f
        maxOf(overlayBlur, f) * 100f
    }

    CompositionLocalProvider(LocalWallpaperBlur provides blurSupported) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Read on the GPU layer only: no recomposition per frame.
                    val f = fold.value
                    val overlayPx = overlayBlur * 24.dp.toPx()
                    if (mode == LayoutMode.COVER) {
                        val r = maxOf(overlayPx, f * 30.dp.toPx())
                        alpha = 1f - 0.22f * f
                        scaleX = 1f + 0.03f * f
                        scaleY = 1f + 0.03f * f
                        renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
                    } else {
                        val half = halfBlur.effect(f, f * 36.dp.toPx(), size.width, size.height, landscape)
                        renderEffect = when {
                            overlayPx > 0.5f -> BlurEffect(overlayPx, overlayPx, TileMode.Decal)
                            half != null -> half.asComposeRenderEffect()
                            else -> null
                        }
                    }
                }
                .then(
                    // Duo: Android's status bar is hidden (Halo draws its own side rail), so only
                    // keep clear of the camera cutout.
                    if (duo) Modifier.windowInsetsPadding(WindowInsets.displayCutout).padding(top = 6.dp)
                    else Modifier.statusBarsPadding()
                )
                .navigationBarsPadding()
        ) {
            AnimatedVisibility(visible = state.editMode && !onLibrary) {
                EditBar(
                    onAdd = { gallery = true },
                    onWallpaper = { openWallpaperPicker(context) },
                    onDone = { state.editMode = false },
                    onAutoFolders = ({
                        val n = state.addCategoryFolders(mode)
                        Toast.makeText(
                            context,
                            if (n > 0) "Added $n category folders" else "Apps sorted into your folders",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }).takeUnless { BuildConfig.IS_PAD },
                )
            }
            if (!state.isDefaultHome && !state.bannerDismissed && !state.editMode && !onLibrary) {
                DefaultHomeBanner(onSet = { openHomeSettings(context) }, onDismiss = { state.dismissBanner() })
            }
            val swipeEnabled = !onLibrary && !state.editMode
            Row(Modifier.weight(1f).fillMaxWidth()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(swipeEnabled) {
                        if (!swipeEnabled) return@pointerInput
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (total > 80.dp.toPx()) spotlight = true },
                            onVerticalDrag = { change, dy ->
                                total += dy
                                change.consume()
                            },
                        )
                    }
            ) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { it }) { page ->
                    val pages = state.layout(mode).pages
                    if (page < pages.size) {
                        HomePage(state, mode, page, pages[page], launch, onMenu = { menu = it }, onOpenFolder = { folder = it })
                    } else {
                        AppLibraryPage(
                            state, mode, launch,
                            onSearch = { librarySearch = true },
                            onFolder = { folder = it },
                            onMenu = { menu = it },
                        )
                    }
                }
            }
            if (duo) {
                SideRail(
                    state, mode, launch,
                    showDock = sideDock && !onLibrary,
                    onMenu = { menu = it },
                )
            }
            }
            AnimatedVisibility(
                visible = !onLibrary,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
            ) {
                Column {
                    SearchOrDots(
                        scrolling = pager.isScrollInProgress,
                        count = pageCount,
                        current = pager.currentPage,
                        dotsOnly = duo,
                        onSearch = { spotlight = true },
                    )
                    if (!sideDock) Dock(state, mode, launch, onMenu = { menu = it })
                }
            }
        }

        AnimatedVisibility(visible = spotlight, enter = fadeIn(), exit = fadeOut()) {
            SearchOverlay(
                state, mode, isLibrary = false,
                onDismiss = { spotlight = false },
                onLaunch = { app ->
                    spotlight = false
                    launch(app, null)
                },
            )
        }
        AnimatedVisibility(visible = librarySearch, enter = fadeIn(), exit = fadeOut()) {
            SearchOverlay(
                state, mode, isLibrary = true,
                onDismiss = { librarySearch = false },
                onLaunch = { app ->
                    librarySearch = false
                    launch(app, null)
                },
            )
        }
        var shownFolder by remember { mutableStateOf<String?>(null) }
        if (folder != null) shownFolder = folder
        AnimatedVisibility(
            visible = folder != null,
            enter = fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.86f),
            exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.86f),
        ) {
            shownFolder?.let { open ->
                if (open.startsWith("f:")) {
                    val f = state.folder(mode, open)
                    FolderPanel(
                        state, mode,
                        title = f?.name ?: "Folder",
                        list = f?.apps?.mapNotNull { state.appMap[it] } ?: emptyList(),
                        launch = launch,
                        onDismiss = { folder = null },
                        appMenu = { app, r -> menu = MenuRequest(MenuTarget.FolderApp(open, app.key), r) },
                        onRename = { state.renameFolder(mode, open, it) },
                    )
                } else {
                    val list = remember(open, state.apps, state.recents) {
                        state.libraryGroups().firstOrNull { it.first == open }?.second ?: emptyList()
                    }
                    FolderPanel(
                        state, mode,
                        title = open,
                        list = list,
                        launch = launch,
                        onDismiss = { folder = null },
                        appMenu = { app, r -> menu = MenuRequest(MenuTarget.LibraryApp(app.key), r) },
                        onRename = null,
                    )
                }
            }
        }
        folderPicker?.let { appKey ->
            FolderPicker(
                state, mode, appKey,
                onDismiss = { folderPicker = null },
                onPick = { key ->
                    state.moveToFolder(mode, key, appKey)
                    folderPicker = null
                },
                onNew = { name ->
                    val target = pager.currentPage.coerceIn(0, pageCount - 1)
                    val key = state.createFolder(mode, name, appKey, target)
                    folderPicker = null
                    folder = key
                },
            )
        }
        AnimatedVisibility(
            visible = gallery,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
        ) {
            WidgetGallery(
                state = state,
                onDismiss = { gallery = false },
                onAddBuiltIn = { kind, size ->
                    gallery = false
                    val target = pager.currentPage.coerceIn(0, pageCount - 1)
                    val page = state.addToHome(mode, HomeItem.BuiltIn(System.currentTimeMillis(), kind, size), target, prepend = true)
                    scope.launch { pager.animateScrollToPage(page) }
                },
                onAddHosted = { info, size ->
                    gallery = false
                    val target = pager.currentPage.coerceIn(0, pageCount - 1)
                    state.widgets.requestAdd(info) { id ->
                        val page = state.addToHome(mode, HomeItem.Hosted(id, size), target, prepend = true)
                        scope.launch { pager.animateScrollToPage(page) }
                    }
                },
            )
        }
        menu?.let { req ->
            ContextMenu(req, entriesFor(req), onDismiss = { menu = null })
        }
    }
    }
}

// ------------------------------------------------------------------ home page grid

@Composable
private fun HomePage(
    state: LauncherState,
    mode: LayoutMode,
    pageIndex: Int,
    items: List<HomeItem>,
    launch: (AppInfo, Rect?) -> Unit,
    onMenu: (MenuRequest) -> Unit,
    onOpenFolder: (String) -> Unit,
) {
    val spec = mode.spec()
    val density = LocalDensity.current
    var working by remember(items) { mutableStateOf(items) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var dragGrab by remember { mutableStateOf(Offset.Zero) }
    var lastSwap by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.editMode = true
                })
            }
    ) {
    // Duo inner screen: an iPhone-sized grid centred on the big screen (pushed right in landscape,
    // with extra widgets on the left of page one), instead of stretching icons across it.
    val duoInner = !BuildConfig.IS_PAD && mode == LayoutMode.INNER
    val wide = maxWidth > maxHeight
    val rowUnit = maxHeight / spec.rows
    val gridW = if (duoInner) min(maxWidth - 16.dp, rowUnit * spec.cols * 1.04f) else maxWidth - 16.dp
    if (duoInner && wide && pageIndex == 0) {
        SideWidgets(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp)
                .width(maxWidth - gridW - 40.dp)
                .fillMaxHeight(),
            unit = rowUnit,
            edit = state.editMode,
        )
    }
    BoxWithConstraints(
        Modifier
            .align(if (duoInner && wide) Alignment.TopEnd else Alignment.TopCenter)
            .padding(end = if (duoInner && wide) 8.dp else 0.dp)
            .width(gridW)
            .fillMaxHeight()
    ) {
        val cellWdp = maxWidth / spec.cols
        val cellHdp = maxHeight / spec.rows
        val cellW = with(density) { cellWdp.toPx() }
        val cellH = with(density) { cellHdp.toPx() }
        val iconSize = when {
            BuildConfig.IS_PAD -> min(min(cellWdp * 0.64f, cellHdp * 0.56f), 74.dp)
            mode == LayoutMode.COVER -> min(min(cellWdp * 0.78f, cellHdp * 0.52f), 62.dp)
            else -> min(min(cellWdp * 0.6f, cellHdp * 0.52f), 64.dp)
        }
        val labelW = if (BuildConfig.IS_PAD) null else cellWdp - 2.dp
        val widgetUnit = min(cellWdp, cellHdp)
        // iOS shows the widget's name under it.
        val labelSpace = if (BuildConfig.IS_PAD) 0.dp else 16.dp
        val placed = remember(working, spec) { pack(working, spec.cols, spec.rows).first }

        fun reorder() {
            val key = dragKey ?: return
            val col = (dragPos.x / cellW).toInt().coerceIn(0, spec.cols - 1)
            val row = (dragPos.y / cellH).toInt().coerceIn(0, spec.rows - 1)
            val current = working
            val layoutNow = pack(current, spec.cols, spec.rows).first
            val hit = layoutNow.firstOrNull {
                col in it.col until it.col + it.item.spanW && row in it.row until it.row + it.item.spanH
            }
            val from = current.indexOfFirst { it.key == key }
            if (from < 0) return
            if (hit == null) {
                if (from != current.lastIndex && lastSwap != "end") {
                    val candidate = current.toMutableList().apply { add(removeAt(from)) }
                    if (fits(candidate, spec)) {
                        working = candidate
                        lastSwap = "end"
                    }
                }
                return
            }
            if (hit.item.key == key || hit.item.key == lastSwap) return
            val to = current.indexOfFirst { it.key == hit.item.key }
            val candidate = current.toMutableList().apply { add(to, removeAt(from)) }
            if (fits(candidate, spec)) {
                working = candidate
                lastSwap = hit.item.key
            }
        }

        fun finishDrag() {
            if (dragKey != null && working != items) state.setPage(mode, pageIndex, working)
            dragKey = null
            lastSwap = null
        }

        placed.forEachIndexed { index, p ->
            key(p.item.key) {
                val target = IntOffset((p.col * cellW).roundToInt(), (p.row * cellH).roundToInt())
                val animated by animateIntOffsetAsState(
                    target,
                    spring(stiffness = Spring.StiffnessMediumLow),
                    label = "slot",
                )
                val currentOffset by rememberUpdatedState(animated)
                val dragging = dragKey == p.item.key
                val itemOffset = if (dragging) {
                    IntOffset((dragPos.x - dragGrab.x).roundToInt(), (dragPos.y - dragGrab.y).roundToInt())
                } else animated
                val itemKey = p.item.key
                Box(
                    Modifier
                        .offset { itemOffset }
                        .size(cellWdp * p.item.spanW, cellHdp * p.item.spanH)
                        .zIndex(if (dragging) 1f else 0f)
                        .jiggle(state.editMode && !dragging, index)
                        .pointerInput(state.editMode, itemKey) {
                            if (!state.editMode) return@pointerInput
                            detectDragGestures(
                                onDragStart = { local ->
                                    dragKey = itemKey
                                    dragGrab = local
                                    dragPos = Offset(currentOffset.x + local.x, currentOffset.y + local.y)
                                    lastSwap = null
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragPos += amount
                                    reorder()
                                },
                                onDragEnd = { finishDrag() },
                                onDragCancel = { finishDrag() },
                            )
                        }
                ) {
                    HomeItemContent(
                        state = state,
                        mode = mode,
                        item = p.item,
                        iconSize = iconSize,
                        labelWidth = labelW,
                        widgetLabel = labelSpace > 0.dp,
                        frameW = run {
                            // Keep widgets in iOS proportions on wide tablet cells.
                            val h = widgetUnit * p.item.spanH - 12.dp - labelSpace
                            val ratio = when {
                                p.item.spanW == p.item.spanH && p.item.spanW <= 2 -> 1f
                                p.item.spanW > p.item.spanH -> 2.15f
                                else -> 1.05f
                            }
                            min(cellWdp * p.item.spanW - 12.dp, h * ratio)
                        },
                        frameH = widgetUnit * p.item.spanH - 12.dp - labelSpace,
                        launch = launch,
                        onMenu = onMenu,
                        onOpenFolder = onOpenFolder,
                    )
                }
            }
        }
    }
    }
}

/** Landscape inner screen, page one: the iPhone Duo's extra widgets to the left of the app grid. */
@Composable
private fun SideWidgets(modifier: Modifier, unit: Dp, edit: Boolean) {
    val context = LocalContext.current
    BoxWithConstraints(modifier) {
        val side = min(unit * 2 - 28.dp, (maxWidth - 14.dp) / 2)
        if (side < 90.dp) return@BoxWithConstraints
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(WidgetKind.CALENDAR, WidgetKind.BATTERY).forEach { kind ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(side)
                            .clip(RoundedCornerShape(22.dp))
                            .clickable(enabled = !edit) { openBuiltInWidget(context, kind) }
                    ) {
                        BuiltInWidget(kind, WidgetSize.SMALL)
                    }
                    Text(kind.title, style = labelStyle(11.sp), modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeItemContent(
    state: LauncherState,
    mode: LayoutMode,
    item: HomeItem,
    iconSize: Dp,
    labelWidth: Dp?,
    widgetLabel: Boolean,
    frameW: Dp,
    frameH: Dp,
    launch: (AppInfo, Rect?) -> Unit,
    onMenu: (MenuRequest) -> Unit,
    onOpenFolder: (String) -> Unit,
) {
    val edit = state.editMode
    when (item) {
        is HomeItem.App -> {
            val app = state.appMap[item.component] ?: return
            AppCell(
                app = app,
                icon = state.icons[app.key],
                iconSize = iconSize,
                edit = edit,
                labelWidth = labelWidth,
                onTap = { r -> launch(app, r) },
                onLongPress = { r -> onMenu(MenuRequest(MenuTarget.HomeApp(app.key), r)) },
                onRemove = { state.removeFromHome(mode, app.key) },
            )
        }
        is HomeItem.Folder -> {
            FolderCell(
                state = state,
                folder = item,
                iconSize = iconSize,
                edit = edit,
                onOpen = { onOpenFolder(item.key) },
                onLongPress = { r -> onMenu(MenuRequest(MenuTarget.HomeFolder(item.key), r)) },
                onRemove = { state.removeFromHome(mode, item.key) },
            )
        }
        is HomeItem.BuiltIn -> {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) scope.launch { WeatherRepo.refresh(context, force = true) }
            }
            WidgetFrame(
                frameW, frameH, edit,
                label = if (widgetLabel) item.kind.title else null,
                onRemove = { state.removeFromHome(mode, item.key) },
                onTap = { at ->
                    when (item.kind) {
                        WidgetKind.GOOGLE -> onGoogleWidgetTap(context, item.size, at.x, at.y)
                        WidgetKind.WEATHER -> when {
                            !WeatherRepo.hasPermission(context) -> askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            WeatherRepo.data == null -> scope.launch { WeatherRepo.refresh(context, force = true) }
                            else -> openWeatherApp(context)
                        }
                        else -> openBuiltInWidget(context, item.kind)
                    }
                },
                onLongPress = { r -> onMenu(MenuRequest(MenuTarget.Widget(item.key), r)) },
            ) {
                BuiltInWidget(item.kind, item.size)
            }
        }
        is HomeItem.Hosted -> {
            var bounds by remember { mutableStateOf(Rect.Zero) }
            WidgetFrame(
                frameW, frameH, edit,
                onRemove = { state.removeFromHome(mode, item.key) },
                onTap = null,
                onLongPress = null,
                onBounds = { bounds = it },
            ) {
                HostedWidget(state, item.appWidgetId, frameW, frameH, edit) {
                    onMenu(MenuRequest(MenuTarget.Widget(item.key), bounds))
                }
            }
        }
    }
}

@Composable
fun AppCell(
    app: AppInfo,
    icon: androidx.compose.ui.graphics.ImageBitmap?,
    iconSize: Dp,
    edit: Boolean,
    modifier: Modifier = Modifier.fillMaxSize(),
    showLabel: Boolean = true,
    labelWidth: Dp? = null,
    onTap: (Rect) -> Unit,
    onLongPress: (Rect) -> Unit,
    onRemove: (() -> Unit)?,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box {
            TappableIcon(icon, iconSize, enabled = !edit, onTap = onTap, onLongPress = onLongPress)
            if (edit && onRemove != null) {
                MinusBadge(onRemove, Modifier.align(Alignment.TopStart).offset((-7).dp, (-7).dp))
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                app.label,
                style = labelStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = labelWidth ?: (iconSize + 24.dp)),
            )
        }
    }
}

@Composable
private fun WidgetFrame(
    frameW: Dp,
    frameH: Dp,
    edit: Boolean,
    label: String? = null,
    onRemove: () -> Unit,
    onTap: ((Offset) -> Unit)?,
    onLongPress: ((Rect) -> Unit)?,
    onBounds: ((Rect) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val haptic = LocalHapticFeedback.current
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(frameW, frameH)
                .onGloballyPositioned {
                    bounds = it.boundsInRoot()
                    onBounds?.invoke(bounds)
                }
                .pointerInput(edit, onTap != null) {
                    if (edit || (tap == null && longPress == null)) return@pointerInput
                    detectTapGestures(
                        onTap = { pos ->
                            // Hand over where the tap landed, as a fraction of the widget's size.
                            tap?.invoke(Offset(pos.x / size.width.coerceAtLeast(1), pos.y / size.height.coerceAtLeast(1)))
                        },
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            longPress?.invoke(bounds)
                        },
                    )
                }
        ) {
            content()
            if (edit) MinusBadge(onRemove, Modifier.align(Alignment.TopStart).offset((-7).dp, (-7).dp))
        }
        if (label != null) {
            Text(label, style = labelStyle(11.sp), maxLines = 1, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

// ------------------------------------------------------------------ dock, search pill, edit bar

@Composable
private fun Dock(state: LauncherState, mode: LayoutMode, launch: (AppInfo, Rect?) -> Unit, onMenu: (MenuRequest) -> Unit) {
    val layout = state.layout(mode)
    val dockApps = layout.dock.mapNotNull { state.appMap[it] }
    val recentExtras = if (mode == LayoutMode.INNER) {
        // Pad in portrait: 8 dock apps already fill the width, so recents only show in landscape.
        val portrait = LocalConfiguration.current.let { it.screenHeightDp > it.screenWidthDp }
        state.recents.filter { it !in layout.dock }.mapNotNull { state.appMap[it] }
            .take(if (BuildConfig.IS_PAD && portrait) 0 else 3)
    } else emptyList()
    val iconSize = if (mode == LayoutMode.INNER) 60.dp else 56.dp
    val edit = state.editMode
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .then(if (mode == LayoutMode.COVER) Modifier.fillMaxWidth() else Modifier)
                .then(
                    if (BuildConfig.IS_PAD) Modifier.liquidGlass(RoundedCornerShape(34.dp))
                    else Modifier.clip(RoundedCornerShape(32.dp)).background(Color.White.copy(alpha = 0.22f))
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = if (mode == LayoutMode.COVER) Arrangement.SpaceEvenly else Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dockApps.isEmpty() && recentExtras.isEmpty()) Spacer(Modifier.height(iconSize))
            dockApps.forEachIndexed { i, app ->
                Box(Modifier.jiggle(edit, i + 7)) {
                    AppCell(
                        app = app,
                        icon = state.icons[app.key],
                        iconSize = iconSize,
                        edit = edit,
                        modifier = Modifier,
                        showLabel = false,
                        onTap = { r -> launch(app, r) },
                        onLongPress = { r -> onMenu(MenuRequest(MenuTarget.DockApp(app.key), r)) },
                        onRemove = { state.removeFromDock(mode, app.key) },
                    )
                }
            }
            if (recentExtras.isNotEmpty()) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(iconSize * 0.8f)
                        .background(Color.White.copy(alpha = 0.35f))
                )
                recentExtras.forEach { app ->
                    AppCell(
                        app = app,
                        icon = state.icons[app.key],
                        iconSize = iconSize,
                        edit = edit,
                        modifier = Modifier,
                        showLabel = false,
                        onTap = { r -> launch(app, r) },
                        onLongPress = { r -> onMenu(MenuRequest(MenuTarget.LibraryApp(app.key), r)) },
                        onRemove = null,
                    )
                }
            }
        }
    }
}

/**
 * Right-edge column of the iPhone Duo layout: the vertical status rail at the top and, on the cover
 * screen or in landscape, the dock as a tall glass pill.
 */
@Composable
private fun SideRail(
    state: LauncherState,
    mode: LayoutMode,
    launch: (AppInfo, Rect?) -> Unit,
    showDock: Boolean,
    onMenu: (MenuRequest) -> Unit,
) {
    val iconSize = if (mode == LayoutMode.COVER) 46.dp else 52.dp
    Column(
        Modifier
            .fillMaxHeight()
            .width(if (showDock) iconSize + 28.dp else 40.dp)
            .padding(end = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DuoStatusRail(Modifier.padding(top = 4.dp))
        Spacer(Modifier.weight(if (mode == LayoutMode.COVER) 1.25f else 0.85f))
        if (showDock) {
            val dockApps = state.layout(mode).dock.mapNotNull { state.appMap[it] }
            val edit = state.editMode
            Column(
                Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color.White.copy(alpha = 0.22f))
                    .padding(horizontal = 7.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (dockApps.isEmpty()) Spacer(Modifier.size(iconSize))
                dockApps.forEachIndexed { i, app ->
                    Box(Modifier.jiggle(edit, i + 7)) {
                        AppCell(
                            app = app,
                            icon = state.icons[app.key],
                            iconSize = iconSize,
                            edit = edit,
                            modifier = Modifier,
                            showLabel = false,
                            onTap = { r -> launch(app, r) },
                            onLongPress = { r -> onMenu(MenuRequest(MenuTarget.DockApp(app.key), r)) },
                            onRemove = { state.removeFromDock(mode, app.key) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun SearchOrDots(scrolling: Boolean, count: Int, current: Int, dotsOnly: Boolean = false, onSearch: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (dotsOnly) 26.dp else 34.dp),
        contentAlignment = Alignment.Center,
    ) {
        // iPadOS shows page dots only; the phone keeps the iOS Search pill.
        Crossfade(targetState = scrolling || BuildConfig.IS_PAD || dotsOnly, label = "pill") { isScrolling ->
            if (isScrolling) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (i in 0 until count) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (i == current) Color.White else Color.White.copy(alpha = 0.4f))
                        )
                    }
                }
            } else {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.22f))
                        .clickable(onClick = onSearch)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MagnifierIcon(Color.White, 12.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Search", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun EditBar(onAdd: () -> Unit, onWallpaper: () -> Unit, onDone: () -> Unit, onAutoFolders: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.28f))
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.weight(1f))
        if (onAutoFolders != null) {
            GlassPill("Auto Folders", onClick = onAutoFolders)
            Spacer(Modifier.width(8.dp))
        }
        GlassPill("Wallpaper", onClick = onWallpaper)
        Spacer(Modifier.width(8.dp))
        GlassPill("Done", bold = true, onClick = onDone)
    }
}

@Composable
private fun DefaultHomeBanner(onSet: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Make Halo your Home app", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("So the Home button always opens this screen.", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
        }
        GlassPill("Set", bold = true, onClick = onSet)
        Spacer(Modifier.width(4.dp))
        Text(
            "✕",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 16.sp,
            modifier = Modifier
                .clickable(onClick = onDismiss)
                .padding(8.dp),
        )
    }
}
