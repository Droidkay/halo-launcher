package com.droidkay.halo

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Shape

/**
 * Halo's settings. Every value is Compose state (the home screen updates live) and is saved to
 * SharedPreferences as soon as it changes.
 *
 * Halo has two layouts and picks one from the screen it's on:
 *  - phone layout ([LayoutMode.COVER]): phones and the Fold's cover screen — iPhone style
 *  - tablet layout ([LayoutMode.INNER]): tablets and the Fold's inner screen — iPad style
 * Grid sizes are the portrait numbers; in landscape the grid turns sideways (5×6 becomes 6×5),
 * so pages keep the same apps when you rotate.
 */
object HaloSettings {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("halo_settings", Context.MODE_PRIVATE)
        prefs = p
        coverCols = p.getInt("coverCols", 4)
        coverRows = p.getInt("coverRows", 6)
        innerCols = p.getInt("tabletCols", 5)
        innerRows = p.getInt("tabletRows", 6)
        phoneDock = p.getInt("dockCount", 4)
        tabletDock = p.getInt("tabletDock", 6)
        dockRecents = p.getBoolean("dockRecents", true)
        iconScale = p.getFloat("iconScale", 1f)
        showLabels = p.getBoolean("showLabels", true)
        labelScale = p.getFloat("labelScale", 1f)
        widgetLabels = p.getBoolean("widgetLabels", true)
        iconShape = p.getString("iconShape", "squircle") ?: "squircle"
        iconPack = p.getString("iconPack", "") ?: ""
        dockEnabled = p.getBoolean("dockEnabled", true)
        dockBackground = p.getBoolean("dockBackground", true)
        liquidGlass = p.getBoolean("liquidGlass", true)
        widgetTheme = p.getString("widgetTheme", "auto") ?: "auto"
        pageIndicator = p.getString("pageIndicator2", "auto") ?: "auto"
        swipeDown = p.getString("swipeDown2", "notifications") ?: "notifications"
        swipeUp = p.getString("swipeUp", "none") ?: "none"
        doubleTap = p.getString("doubleTap", "none") ?: "none"
        appLibrary = p.getBoolean("appLibrary", true)
        lockLayout = p.getBoolean("lockLayout", false)
        wallpaperDim = p.getFloat("wallpaperDim", 0f)
    }

    private fun save(block: SharedPreferences.Editor.() -> Unit) {
        prefs?.edit()?.apply(block)?.apply()
    }

    // ---- grids (portrait numbers)
    var coverCols by mutableIntStateOf(4)
        private set
    var coverRows by mutableIntStateOf(6)
        private set
    var innerCols by mutableIntStateOf(5)
        private set
    var innerRows by mutableIntStateOf(6)
        private set

    fun cols(mode: LayoutMode) = if (mode == LayoutMode.COVER) coverCols else innerCols
    fun rows(mode: LayoutMode) = if (mode == LayoutMode.COVER) coverRows else innerRows

    fun setGrid(mode: LayoutMode, cols: Int, rows: Int) {
        if (mode == LayoutMode.COVER) {
            coverCols = cols
            coverRows = rows
            save { putInt("coverCols", cols); putInt("coverRows", rows) }
        } else {
            innerCols = cols
            innerRows = rows
            save { putInt("tabletCols", cols); putInt("tabletRows", rows) }
        }
    }

    // ---- dock
    var dockEnabled by mutableStateOf(true)
        private set
    var phoneDock by mutableIntStateOf(4)
        private set
    var tabletDock by mutableIntStateOf(6)
        private set
    /** Tablet layout: the iPad-style recent apps at the end of the dock. */
    var dockRecents by mutableStateOf(true)
        private set
    var dockBackground by mutableStateOf(true)
        private set

    fun dockSize(mode: LayoutMode) = if (mode == LayoutMode.COVER) phoneDock else tabletDock
    fun updateDockEnabled(v: Boolean) { dockEnabled = v; save { putBoolean("dockEnabled", v) } }
    fun updateDockSize(mode: LayoutMode, v: Int) {
        if (mode == LayoutMode.COVER) {
            phoneDock = v
            save { putInt("dockCount", v) }
        } else {
            tabletDock = v
            save { putInt("tabletDock", v) }
        }
    }
    fun updateDockRecents(v: Boolean) { dockRecents = v; save { putBoolean("dockRecents", v) } }
    fun updateDockBackground(v: Boolean) { dockBackground = v; save { putBoolean("dockBackground", v) } }

    // ---- icons
    var iconScale by mutableFloatStateOf(1f)
        private set
    var showLabels by mutableStateOf(true)
        private set
    var labelScale by mutableFloatStateOf(1f)
        private set
    var widgetLabels by mutableStateOf(true)
        private set
    /** squircle, circle, rounded, square, teardrop */
    var iconShape by mutableStateOf("squircle")
        private set
    /** Package name of the icon pack, or "" for the apps' own icons. */
    var iconPack by mutableStateOf("")
        private set

    fun updateIconScale(v: Float) { iconScale = v; save { putFloat("iconScale", v) } }
    fun updateShowLabels(v: Boolean) { showLabels = v; save { putBoolean("showLabels", v) } }
    fun updateLabelScale(v: Float) { labelScale = v; save { putFloat("labelScale", v) } }
    fun updateWidgetLabels(v: Boolean) { widgetLabels = v; save { putBoolean("widgetLabels", v) } }
    fun updateIconShape(v: String) { iconShape = v; save { putString("iconShape", v) } }
    fun updateIconPack(v: String) { iconPack = v; save { putString("iconPack", v) } }

    // ---- look
    /** iOS 26 Liquid Glass dock, folders, widgets and buttons (off = classic frosted look). */
    var liquidGlass by mutableStateOf(true)
        private set
    /** auto (follow the phone's dark mode), light or dark */
    var widgetTheme by mutableStateOf("auto")
        private set
    /** auto (phone: Search pill, tablet: dots), dots or search */
    var pageIndicator by mutableStateOf("auto")
        private set
    var wallpaperDim by mutableFloatStateOf(0f)
        private set

    fun updateLiquidGlass(v: Boolean) { liquidGlass = v; save { putBoolean("liquidGlass", v) } }
    fun updateWidgetTheme(v: String) { widgetTheme = v; save { putString("widgetTheme", v) } }
    fun updatePageIndicator(v: String) { pageIndicator = v; save { putString("pageIndicator2", v) } }
    fun updateWallpaperDim(v: Float) { wallpaperDim = v; save { putFloat("wallpaperDim", v) } }

    // ---- gestures (swipe down defaults to the notification panel, like the phone's own home screen)
    /** spotlight, notifications, library, none */
    var swipeDown by mutableStateOf("notifications")
        private set
    var swipeUp by mutableStateOf("none")
        private set
    var doubleTap by mutableStateOf("none")
        private set

    fun updateSwipeDown(v: String) { swipeDown = v; save { putString("swipeDown2", v) } }
    fun updateSwipeUp(v: String) { swipeUp = v; save { putString("swipeUp", v) } }
    fun updateDoubleTap(v: String) { doubleTap = v; save { putString("doubleTap", v) } }

    // ---- layout
    var appLibrary by mutableStateOf(true)
        private set
    var lockLayout by mutableStateOf(false)
        private set

    fun updateAppLibrary(v: Boolean) { appLibrary = v; save { putBoolean("appLibrary", v) } }
    fun updateLockLayout(v: Boolean) { lockLayout = v; save { putBoolean("lockLayout", v) } }
}

/** The mask Halo cuts app icons and folders to. */
fun iconShapeFor(name: String): Shape = when (name) {
    "circle" -> CircleShape
    "rounded" -> RoundedCornerShape(30)
    "square" -> RoundedCornerShape(8)
    "teardrop" -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 14, bottomStartPercent = 50)
    else -> SquircleShape
}
