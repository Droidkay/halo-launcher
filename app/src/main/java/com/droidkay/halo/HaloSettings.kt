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
 * Halo's Nova-style settings. Every value is Compose state (screens update live) and is saved
 * to SharedPreferences as soon as it changes.
 */
object HaloSettings {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("halo_settings", Context.MODE_PRIVATE)
        prefs = p
        coverCols = p.getInt("coverCols", 4)
        coverRows = p.getInt("coverRows", 6)
        innerCols = p.getInt("innerCols", 4)
        innerRows = p.getInt("innerRows", 6)
        innerCentered = p.getBoolean("innerCentered", true)
        iconScale = p.getFloat("iconScale", 1f)
        showLabels = p.getBoolean("showLabels", true)
        labelScale = p.getFloat("labelScale", 1f)
        widgetLabels = p.getBoolean("widgetLabels", true)
        iconShape = p.getString("iconShape", "squircle") ?: "squircle"
        iconPack = p.getString("iconPack", "") ?: ""
        dockEnabled = p.getBoolean("dockEnabled", true)
        dockCount = p.getInt("dockCount", 4)
        dockBackground = p.getBoolean("dockBackground", true)
        coverSideDock = p.getBoolean("coverSideDock", false)
        landscapeSideDock = p.getBoolean("landscapeSideDock", true)
        pageIndicator = p.getString("pageIndicator", "dots") ?: "dots"
        swipeDown = p.getString("swipeDown", "spotlight") ?: "spotlight"
        swipeUp = p.getString("swipeUp", "none") ?: "none"
        doubleTap = p.getString("doubleTap", "none") ?: "none"
        appLibrary = p.getBoolean("appLibrary", true)
        lockLayout = p.getBoolean("lockLayout", false)
        wallpaperDim = p.getFloat("wallpaperDim", 0f)
    }

    private fun save(block: SharedPreferences.Editor.() -> Unit) {
        prefs?.edit()?.apply(block)?.apply()
    }

    // ---- home screen grid
    var coverCols by mutableIntStateOf(4)
        private set
    var coverRows by mutableIntStateOf(6)
        private set
    var innerCols by mutableIntStateOf(4)
        private set
    var innerRows by mutableIntStateOf(6)
        private set
    /** Inner screen: iPhone-sized grid in the middle (true) or icons spread across the whole screen. */
    var innerCentered by mutableStateOf(true)
        private set

    fun setGrid(mode: LayoutMode, cols: Int, rows: Int) {
        if (mode == LayoutMode.COVER) {
            coverCols = cols; coverRows = rows
            save { putInt("coverCols", cols); putInt("coverRows", rows) }
        } else {
            innerCols = cols; innerRows = rows
            save { putInt("innerCols", cols); putInt("innerRows", rows) }
        }
    }

    fun updateInnerCentered(v: Boolean) { innerCentered = v; save { putBoolean("innerCentered", v) } }

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

    // ---- dock
    var dockEnabled by mutableStateOf(true)
        private set
    var dockCount by mutableIntStateOf(4)
        private set
    var dockBackground by mutableStateOf(true)
        private set
    var coverSideDock by mutableStateOf(false)
        private set
    var landscapeSideDock by mutableStateOf(true)
        private set

    fun updateDockEnabled(v: Boolean) { dockEnabled = v; save { putBoolean("dockEnabled", v) } }
    fun updateDockCount(v: Int) { dockCount = v; save { putInt("dockCount", v) } }
    fun updateDockBackground(v: Boolean) { dockBackground = v; save { putBoolean("dockBackground", v) } }
    fun updateCoverSideDock(v: Boolean) { coverSideDock = v; save { putBoolean("coverSideDock", v) } }
    fun updateLandscapeSideDock(v: Boolean) { landscapeSideDock = v; save { putBoolean("landscapeSideDock", v) } }

    // ---- look & gestures
    /** dots or search */
    var pageIndicator by mutableStateOf("dots")
        private set
    /** spotlight, notifications, library, none */
    var swipeDown by mutableStateOf("spotlight")
        private set
    var swipeUp by mutableStateOf("none")
        private set
    var doubleTap by mutableStateOf("none")
        private set
    var appLibrary by mutableStateOf(true)
        private set
    var lockLayout by mutableStateOf(false)
        private set
    var wallpaperDim by mutableFloatStateOf(0f)
        private set

    fun updatePageIndicator(v: String) { pageIndicator = v; save { putString("pageIndicator", v) } }
    fun updateSwipeDown(v: String) { swipeDown = v; save { putString("swipeDown", v) } }
    fun updateSwipeUp(v: String) { swipeUp = v; save { putString("swipeUp", v) } }
    fun updateDoubleTap(v: String) { doubleTap = v; save { putString("doubleTap", v) } }
    fun updateAppLibrary(v: Boolean) { appLibrary = v; save { putBoolean("appLibrary", v) } }
    fun updateLockLayout(v: Boolean) { lockLayout = v; save { putBoolean("lockLayout", v) } }
    fun updateWallpaperDim(v: Float) { wallpaperDim = v; save { putFloat("wallpaperDim", v) } }
}

/** The mask Halo cuts app icons and folders to. */
fun iconShapeFor(name: String): Shape = when (name) {
    "circle" -> CircleShape
    "rounded" -> RoundedCornerShape(30)
    "square" -> RoundedCornerShape(8)
    "teardrop" -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 14, bottomStartPercent = 50)
    else -> SquircleShape
}
