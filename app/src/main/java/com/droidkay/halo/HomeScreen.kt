@file:OptIn(ExperimentalFoundationApi::class)

package com.droidkay.halo

import android.Manifest
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.round
import kotlinx.coroutines.delay
import kotlin.math.floor
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
    val drag = remember(mode) { DragController() }
    // While dragging to the right of the last page, an empty page appears so the item can start a new one.
    val pageCount = layout.pages.size + (if (drag.extraPage) 1 else 0)
    val pager = rememberPagerState(pageCount = {
        state.layout(mode).pages.size + (if (drag.extraPage) 1 else 0) + (if (BuildConfig.IS_PAD || HaloSettings.appLibrary) 1 else 0)
    })
    val onLibrary = pager.currentPage >= pageCount

    var spotlight by remember { mutableStateOf(false) }
    var librarySearch by remember { mutableStateOf(false) }
    var gallery by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<MenuRequest?>(null) }
    // An open folder: a library category name, or a home folder's key ("f:…").
    var folder by remember { mutableStateOf<String?>(null) }
    var folderPicker by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }

    fun closeOverlays(): Boolean {
        val any = spotlight || librarySearch || gallery || menu != null || folder != null || folderPicker != null || settingsOpen
        settingsOpen = false
        folderPicker = null
        spotlight = false
        librarySearch = false
        gallery = false
        menu = null
        folder = null
        return any
    }

    val launch: (AppInfo, Rect?) -> Unit = { app, bounds -> state.launch(app, view, bounds) }

    // ---------------------------------------------------------------- drag and drop (iOS style)
    // Nova-style gestures (Settings → Gestures).
    fun runGesture(action: String) {
        when (action) {
            "spotlight" -> spotlight = true
            "notifications" -> expandNotifications(context)
            "library" -> if (BuildConfig.IS_PAD || HaloSettings.appLibrary) scope.launch { pager.animateScrollToPage(pageCount) }
            "settings" -> settingsOpen = true
            "edit" -> if (!HaloSettings.lockLayout) state.editMode = true
        }
    }
    val pickUp: () -> Unit = {
        menu = null
        state.editMode = true
    }
    drag.onHover = hover@{
        val item = drag.item ?: return@hover
        val key = item.key
        val p = drag.pointer
        val spec = mode.spec()
        // Over the dock: apps only, and only while it has room (or the app came from it).
        if (item is HomeItem.App && drag.dockRect.contains(p)) {
            val without = state.layout(mode).dock.filter { it != key }
            if (drag.source == DragSpot.Dock || without.size < spec.dockSize) {
                val r = drag.dockRect
                val frac = if (drag.dockVertical) (p.y - r.top) / r.height else (p.x - r.left) / r.width
                val idx = (frac * (without.size + 1)).toInt().coerceIn(0, without.size)
                drag.dockPreview = without.toMutableList().apply { add(idx, key) }
                drag.target = DragSpot.Dock
                return@hover
            }
        }
        if (pager.isScrollInProgress) return@hover
        val page = pager.currentPage
        if (page >= pageCount) return@hover
        val geo = drag.grids[page] ?: return@hover
        if (drag.target != DragSpot.Page(page)) {
            val base = state.layout(mode).pages.getOrNull(page) ?: emptyList()
            drag.pagePreview = if (base.any { it.key == key }) base else base + item
            drag.target = DragSpot.Page(page)
            drag.lastSwap = null
        }
        val col = floor((p.x - geo.origin.x) / geo.cellW).toInt()
        val row = floor((p.y - geo.origin.y) / geo.cellH).toInt()
        if (col !in 0 until geo.cols || row !in 0 until geo.rows) return@hover
        val cur = drag.pagePreview
        val from = cur.indexOfFirst { it.key == key }
        if (from < 0) return@hover
        val hit = pack(cur, geo.cols, geo.rows).first.firstOrNull {
            col in it.col until it.col + it.item.spanW && row in it.row until it.row + it.item.spanH
        }
        val swapKey = hit?.item?.key ?: "end"
        if (swapKey == key || swapKey == drag.lastSwap) return@hover
        if (hit == null && from == cur.lastIndex) return@hover
        val candidate = cur.toMutableList().apply {
            val moving = removeAt(from)
            if (hit == null) add(moving) else add(cur.indexOfFirst { it.key == swapKey }, moving)
        }
        // Only accept arrangements where the dragged item actually lands on this page.
        if (pack(candidate, geo.cols, geo.rows).first.any { it.item.key == key }) {
            drag.pagePreview = candidate
            drag.lastSwap = swapKey
        }
    }
    drag.onDrop = drop@{
        val item = drag.item ?: return@drop
        val key = item.key
        val l = state.layout(mode)
        val pages = l.pages.map { pg -> pg.filter { it.key != key } }.toMutableList()
        var dock = l.dock
        when (val t = drag.target) {
            null -> return@drop
            DragSpot.Dock -> {
                if (item !is HomeItem.App) return@drop
                dock = drag.dockPreview
            }
            is DragSpot.Page -> {
                while (pages.size <= t.index) pages.add(emptyList())
                pages[t.index] = drag.pagePreview
                dock = dock - key
            }
        }
        state.applyDrag(mode, pages, dock)
    }
    // Hold an item at the left/right edge to flip pages; past the last page makes a new one.
    val edgePx = with(LocalDensity.current) { DragEdge.toPx() }
    LaunchedEffect(drag.active) {
        if (!drag.active) return@LaunchedEffect
        var side = 0
        var dwell = 0L
        while (drag.active) {
            delay(60)
            val r = drag.pagerRect
            val pt = drag.pointer
            val s = when {
                drag.dockRect.contains(pt) -> 0
                pt.x < r.left + edgePx -> -1
                pt.x > r.right - edgePx -> 1
                else -> 0
            }
            if (s != side) {
                side = s
                dwell = 0
            } else if (s != 0) dwell += 60
            if (s == 0 || dwell < 520 || pager.isScrollInProgress) continue
            dwell = 0
            val cur = pager.currentPage
            val homeCount = state.layout(mode).pages.size
            val next = cur + s
            if (next < 0) continue
            if (s > 0 && next >= homeCount + (if (drag.extraPage) 1 else 0)) {
                if (drag.extraPage) continue
                drag.extraPage = true
                delay(50)
            }
            pager.animateScrollToPage(next)
            drag.target = null
            drag.onHover()
        }
    }

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
        val edit = if (HaloSettings.lockLayout) MenuEntry("Halo Settings", "⚙") { settingsOpen = true }
        else MenuEntry("Edit Home Screen", "✎") { state.editMode = true }
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
    val dockOn = BuildConfig.IS_PAD || HaloSettings.dockEnabled
    val sideDock = duo && dockOn && (
        (mode == LayoutMode.COVER && HaloSettings.coverSideDock) ||
            (mode == LayoutMode.INNER && landscape && HaloSettings.landscapeSideDock)
        )
    WallpaperBlurDriver(blurSupported) {
        val f = if (mode == LayoutMode.COVER) fold.value else 0f
        maxOf(overlayBlur, f) * 100f
    }

    CompositionLocalProvider(LocalWallpaperBlur provides blurSupported) {
    Box(Modifier.fillMaxSize()) {
        if (HaloSettings.wallpaperDim > 0.01f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = HaloSettings.wallpaperDim)))
        }
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
                    Modifier.statusBarsPadding()
                )
                .navigationBarsPadding()
        ) {
            AnimatedVisibility(visible = state.editMode && !onLibrary) {
                EditBar(
                    onAdd = { gallery = true },
                    onWallpaper = { openWallpaperPicker(context) },
                    onDone = { state.editMode = false },
                    onSettings = { settingsOpen = true },
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
                    .onGloballyPositioned { drag.pagerRect = it.boundsInRoot() }
                    .pointerInput(swipeEnabled) {
                        if (!swipeEnabled) return@pointerInput
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (total > 80.dp.toPx()) runGesture(HaloSettings.swipeDown)
                                else if (total < -80.dp.toPx()) runGesture(HaloSettings.swipeUp)
                            },
                            onVerticalDrag = { change, dy ->
                                total += dy
                                change.consume()
                            },
                        )
                    }
            ) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    // Keep every page alive while dragging so the picked-up item never loses the finger.
                    beyondBoundsPageCount = if (drag.active) pageCount else 0,
                    userScrollEnabled = !drag.active,
                    key = { it },
                ) { page ->
                    val pages = state.layout(mode).pages
                    if (page < pages.size) {
                        HomePage(
                            state, mode, page, pages[page], drag, launch,
                            onMenu = { menu = it }, onOpenFolder = { folder = it }, onPickUp = pickUp,
                            onGesture = { runGesture(it) },
                        )
                    } else if (page < pageCount) {
                        HomePage(
                            state, mode, page, emptyList(), drag, launch,
                            onMenu = { menu = it }, onOpenFolder = { folder = it }, onPickUp = pickUp,
                            onGesture = { runGesture(it) },
                        )
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
            if (sideDock && !onLibrary) {
                SideRail(
                    state, mode, drag, launch,
                    onMenu = { menu = it },
                    onPickUp = pickUp,
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
                        dotsOnly = duo && HaloSettings.pageIndicator == "dots",
                        onSearch = { spotlight = true },
                    )
                    if (!sideDock && dockOn) Dock(state, mode, drag, launch, onMenu = { menu = it }, onPickUp = pickUp)
                }
            }
        }

        DragGhost(state, drag)

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
                mode = mode,
                onDismiss = { gallery = false },
                onAddApp = { app ->
                    val target = pager.currentPage.coerceIn(0, pageCount - 1)
                    state.addToHome(mode, HomeItem.App(app.key), target, prepend = false)
                    Toast.makeText(context, "Added ${app.label}", Toast.LENGTH_SHORT).show()
                },
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
        AnimatedVisibility(
            visible = settingsOpen,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 },
        ) {
            SettingsScreen(state, mode, onDismiss = { settingsOpen = false })
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
    drag: DragController,
    launch: (AppInfo, Rect?) -> Unit,
    onMenu: (MenuRequest) -> Unit,
    onOpenFolder: (String) -> Unit,
    onPickUp: () -> Unit,
    onGesture: (String) -> Unit,
) {
    val spec = mode.spec()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val landscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        // Locked layout: long-press opens Settings instead of jiggle mode.
                        if (HaloSettings.lockLayout) onGesture("settings") else state.editMode = true
                    },
                    onDoubleTap = { if (HaloSettings.doubleTap != "none") onGesture(HaloSettings.doubleTap) },
                )
            }
    ) {
    // Duo inner screen: an iPhone-sized grid centred on the big screen (pushed right in landscape,
    // with extra widgets on the left of page one), instead of stretching icons across it.
    val duoInner = !BuildConfig.IS_PAD && mode == LayoutMode.INNER && HaloSettings.innerCentered
    val wide = landscape
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
    DisposableEffect(pageIndex) { onDispose { drag.grids.remove(pageIndex) } }
    BoxWithConstraints(
        Modifier
            .align(if (duoInner && wide) Alignment.TopEnd else Alignment.TopCenter)
            .padding(end = if (duoInner && wide) 8.dp else 0.dp)
            .width(gridW)
            .fillMaxHeight()
            .onGloballyPositioned { c ->
                drag.grids[pageIndex] = GridGeo(
                    c.positionInRoot(),
                    c.size.width / spec.cols.toFloat(),
                    c.size.height / spec.rows.toFloat(),
                    spec.cols,
                    spec.rows,
                )
            }
    ) {
        val cellWdp = maxWidth / spec.cols
        val cellHdp = maxHeight / spec.rows
        val cellW = with(density) { cellWdp.toPx() }
        val cellH = with(density) { cellHdp.toPx() }
        val iconSize = when {
            BuildConfig.IS_PAD -> min(min(cellWdp * 0.64f, cellHdp * 0.56f), 74.dp)
            mode == LayoutMode.COVER -> min(min(cellWdp * 0.78f, cellHdp * 0.52f), 62.dp)
            else -> min(min(cellWdp * 0.6f, cellHdp * 0.52f), 64.dp)
        }.let { base ->
            if (BuildConfig.IS_PAD) base else min(base * HaloSettings.iconScale, min(cellWdp * 0.94f, cellHdp * 0.8f))
        }
        val labelW = if (BuildConfig.IS_PAD) null else cellWdp - 2.dp
        val widgetUnit = min(cellWdp, cellHdp)
        // iOS shows the widget's name under it.
        val labelSpace = if (BuildConfig.IS_PAD || !HaloSettings.widgetLabels) 0.dp else 16.dp

        // While something is being dragged, show where it would land (iOS: the others make room).
        val dragKey = drag.key
        val display = when {
            dragKey == null -> items
            drag.target == DragSpot.Page(pageIndex) -> drag.pagePreview
            else -> items.filter { it.key != dragKey }
        }
        val placed = pack(display, spec.cols, spec.rows).first
        // The picked-up item stays composed (invisibly) on its own page: it owns the finger's gesture.
        val anchor = if (dragKey != null && drag.source == DragSpot.Page(pageIndex) && placed.none { it.item.key == dragKey }) {
            pack(items, spec.cols, spec.rows).first.firstOrNull { it.item.key == dragKey }
        } else null
        val shown = if (anchor != null) placed + anchor else placed

        shown.forEachIndexed { index, p ->
            key(p.item.key) {
                val item = p.item
                val target = IntOffset((p.col * cellW).roundToInt(), (p.row * cellH).roundToInt())
                val animated by animateIntOffsetAsState(
                    target,
                    spring(stiffness = Spring.StiffnessMediumLow),
                    label = "slot",
                )
                val isDragged = dragKey == item.key
                var iconBounds by remember { mutableStateOf(Rect.Zero) }
                val frameH = widgetUnit * item.spanH - 12.dp - labelSpace
                val frameW = run {
                    // Keep widgets in iOS proportions on wide tablet cells.
                    val ratio = when {
                        item.spanW == item.spanH && item.spanW <= 2 -> 1f
                        item.spanW > item.spanH -> 2.15f
                        else -> 1.05f
                    }
                    min(cellWdp * item.spanW - 12.dp, frameH * ratio)
                }
                val sizePx = IntSize((cellW * item.spanW).roundToInt(), (cellH * item.spanH).roundToInt())
                Box(
                    Modifier
                        .offset { animated }
                        .size(cellWdp * item.spanW, cellHdp * item.spanH)
                        .homeGestures(
                            key = item.key,
                            editMode = state.editMode,
                            pickUpWithoutEdit = !HaloSettings.lockLayout,
                            onTap = {
                                when (item) {
                                    is HomeItem.App -> state.appMap[item.component]?.let { launch(it, iconBounds) }
                                    is HomeItem.Folder -> onOpenFolder(item.key)
                                    else -> {}
                                }
                            },
                            onEditTap = { if (item is HomeItem.Folder) onOpenFolder(item.key) },
                            onLongPress = {
                                when (item) {
                                    is HomeItem.App -> onMenu(MenuRequest(MenuTarget.HomeApp(item.component), iconBounds))
                                    is HomeItem.Folder -> onMenu(MenuRequest(MenuTarget.HomeFolder(item.key), iconBounds))
                                    else -> {} // widgets open their own menu
                                }
                            },
                            onPickUp = { grab, at ->
                                onPickUp()
                                drag.start(item, DragSpot.Page(pageIndex), grab, at, GhostLook(sizePx, iconSize, frameW, frameH, labelW))
                            },
                            onMove = { drag.move(it) },
                            onDrop = { drag.drop() },
                        )
                        .jiggle(state.editMode && !isDragged, index)
                        .graphicsLayer { alpha = if (isDragged) 0f else 1f }
                ) {
                    HomeItemContent(
                        state = state,
                        mode = mode,
                        item = item,
                        iconSize = iconSize,
                        labelWidth = labelW,
                        widgetLabel = labelSpace > 0.dp,
                        frameW = frameW,
                        frameH = frameH,
                        launch = launch,
                        onMenu = onMenu,
                        onOpenFolder = onOpenFolder,
                        onIconBounds = { iconBounds = it },
                    )
                }
            }
        }
    }
    }
}

/** The item floating under the finger while it is being moved. */
@Composable
private fun DragGhost(state: LauncherState, drag: DragController) {
    val item = drag.item ?: return
    val look = drag.look ?: return
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { (drag.pointer - drag.grab).round() }
            .size(with(density) { look.size.width.toDp() }, with(density) { look.size.height.toDp() })
            .graphicsLayer {
                scaleX = 1.1f
                scaleY = 1.1f
                alpha = 0.95f
            },
        contentAlignment = Alignment.Center,
    ) {
        when (item) {
            is HomeItem.App -> AppIconImage(state.icons[item.component], look.iconSize)
            is HomeItem.Folder -> FolderCell(
                state = state, folder = item, iconSize = look.iconSize, edit = false,
                onOpen = {}, onLongPress = {}, onRemove = {}, gestures = false,
            )
            is HomeItem.BuiltIn -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.padding(top = 6.dp).size(look.frameW, look.frameH)) { BuiltInWidget(item.kind, item.size) }
            }
            is HomeItem.Hosted -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .size(look.frameW, look.frameH)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.35f))
                )
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
    onIconBounds: (Rect) -> Unit = {},
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
                showLabel = HaloSettings.showLabels,
                handleGestures = false,
                onIconBounds = onIconBounds,
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
                gestures = false,
                onBounds = onIconBounds,
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
    handleGestures: Boolean = true,
    onIconBounds: ((Rect) -> Unit)? = null,
    onTap: (Rect) -> Unit,
    onLongPress: (Rect) -> Unit,
    onRemove: (() -> Unit)?,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box {
            TappableIcon(icon, iconSize, enabled = handleGestures && !edit, onBounds = onIconBounds, onTap = onTap, onLongPress = onLongPress)
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

/** Dock apps with the live drag preview, laid out by the caller's Row (bottom dock) or Column (side dock). */
@Composable
private fun DockIcons(
    state: LauncherState,
    mode: LayoutMode,
    drag: DragController,
    iconSize: Dp,
    launch: (AppInfo, Rect?) -> Unit,
    onMenu: (MenuRequest) -> Unit,
    onPickUp: () -> Unit,
    showSpacerWhenEmpty: Boolean = true,
) {
    val layout = state.layout(mode)
    val edit = state.editMode
    val density = LocalDensity.current
    val dragKey = drag.key
    val keys = when {
        dragKey == null -> layout.dock
        drag.target == DragSpot.Dock -> drag.dockPreview
        else -> layout.dock.filter { it != dragKey }
    }
    // An app dragged out of the dock stays composed (zero-sized) because it owns the finger's gesture.
    val anchor = if (dragKey != null && drag.source == DragSpot.Dock && dragKey !in keys) dragKey else null
    if (keys.isEmpty() && showSpacerWhenEmpty) Spacer(Modifier.size(iconSize))
    (keys + listOfNotNull(anchor)).forEachIndexed { i, k ->
        key(k) {
            val app = state.appMap[k]
            if (app != null) {
                val isDragged = k == dragKey
                var iconBounds by remember { mutableStateOf(Rect.Zero) }
                val px = with(density) { iconSize.roundToPx() }
                Box(
                    Modifier
                        .size(if (k == anchor) 0.dp else iconSize)
                        .homeGestures(
                            key = k,
                            editMode = edit,
                            pickUpWithoutEdit = !HaloSettings.lockLayout,
                            onTap = { launch(app, iconBounds) },
                            onEditTap = {},
                            onLongPress = { onMenu(MenuRequest(MenuTarget.DockApp(k), iconBounds)) },
                            onPickUp = { grab, at ->
                                onPickUp()
                                drag.start(HomeItem.App(k), DragSpot.Dock, grab, at, GhostLook(IntSize(px, px), iconSize, iconSize, iconSize, null))
                            },
                            onMove = { drag.move(it) },
                            onDrop = { drag.drop() },
                        )
                        .jiggle(edit && !isDragged, i + 7)
                        .graphicsLayer { alpha = if (isDragged) 0f else 1f }
                ) {
                    AppCell(
                        app = app,
                        icon = state.icons[app.key],
                        iconSize = iconSize,
                        edit = edit,
                        modifier = Modifier,
                        showLabel = false,
                        handleGestures = false,
                        onIconBounds = { iconBounds = it },
                        onTap = {},
                        onLongPress = {},
                        onRemove = { state.removeFromDock(mode, k) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Dock(
    state: LauncherState,
    mode: LayoutMode,
    drag: DragController,
    launch: (AppInfo, Rect?) -> Unit,
    onMenu: (MenuRequest) -> Unit,
    onPickUp: () -> Unit,
) {
    val layout = state.layout(mode)
    val recentExtras = if (BuildConfig.IS_PAD) {
        // Pad in portrait: 8 dock apps already fill the width, so recents only show in landscape.
        val portrait = LocalConfiguration.current.let { it.screenHeightDp > it.screenWidthDp }
        state.recents.filter { it !in layout.dock }.mapNotNull { state.appMap[it] }.take(if (portrait) 0 else 3)
    } else emptyList()
    val iconSize = if (mode == LayoutMode.INNER) 60.dp else 56.dp
    val edit = state.editMode
    val grow = with(LocalDensity.current) { 16.dp.toPx() }
    DisposableEffect(Unit) { onDispose { drag.dockRect = Rect.Zero } }
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
                    else Modifier.clip(RoundedCornerShape(32.dp)).background(Color.White.copy(alpha = if (HaloSettings.dockBackground) 0.22f else 0f))
                )
                .onGloballyPositioned {
                    drag.dockRect = it.boundsInRoot().inflate(grow)
                    drag.dockVertical = false
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = if (mode == LayoutMode.COVER) Arrangement.SpaceEvenly else Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockIcons(state, mode, drag, iconSize, launch, onMenu, onPickUp, showSpacerWhenEmpty = recentExtras.isEmpty())
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
 * Right-edge dock of the iPhone Duo layout (cover screen, and the inner screen in landscape):
 * the dock as a tall glass pill.
 */
@Composable
private fun SideRail(
    state: LauncherState,
    mode: LayoutMode,
    drag: DragController,
    launch: (AppInfo, Rect?) -> Unit,
    onMenu: (MenuRequest) -> Unit,
    onPickUp: () -> Unit,
) {
    val iconSize = if (mode == LayoutMode.COVER) 46.dp else 52.dp
    val grow = with(LocalDensity.current) { 16.dp.toPx() }
    Column(
        Modifier
            .fillMaxHeight()
            .width(iconSize + 28.dp)
            .padding(end = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(if (mode == LayoutMode.COVER) 1.25f else 0.85f))
        run {
            DisposableEffect(Unit) { onDispose { drag.dockRect = Rect.Zero } }
            Column(
                Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color.White.copy(alpha = if (HaloSettings.dockBackground) 0.22f else 0f))
                    .onGloballyPositioned {
                        drag.dockRect = it.boundsInRoot().inflate(grow)
                        drag.dockVertical = true
                    }
                    .padding(horizontal = 7.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DockIcons(state, mode, drag, iconSize, launch, onMenu, onPickUp)
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
private fun EditBar(onAdd: () -> Unit, onWallpaper: () -> Unit, onDone: () -> Unit, onSettings: () -> Unit, onAutoFolders: (() -> Unit)?) {
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
        Spacer(Modifier.width(8.dp))
        // Halo Settings
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.28f))
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Text("⚙", color = Color.White, fontSize = 18.sp)
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
