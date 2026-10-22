package com.droidkay.halo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class LayoutMode { COVER, INNER }

/** Grid size per screen: the cover screen behaves like an iPhone, the inner screen like an iPad. */
data class GridSpec(val cols: Int, val rows: Int, val dockSize: Int)

fun LayoutMode.spec(): GridSpec =
    if (this == LayoutMode.INNER) GridSpec(cols = 6, rows = 6, dockSize = if (BuildConfig.IS_PAD) 8 else 6)
    else GridSpec(cols = 4, rows = 6, dockSize = 4)

enum class WidgetSize(val w: Int, val h: Int, val title: String) {
    SMALL(2, 2, "Small"),
    MEDIUM(4, 2, "Medium"),
    LARGE(4, 4, "Large"),
}

enum class WidgetKind(val title: String) {
    CLOCK("Clock"),
    CALENDAR("Calendar"),
    BATTERY("Batteries");

    val sizes: List<WidgetSize>
        get() = if (this == CALENDAR) listOf(WidgetSize.SMALL, WidgetSize.MEDIUM, WidgetSize.LARGE)
        else listOf(WidgetSize.SMALL, WidgetSize.MEDIUM)
}

sealed class HomeItem {
    abstract val key: String
    abstract val spanW: Int
    abstract val spanH: Int

    /** An app shortcut. [component] is the flattened ComponentName, same as AppInfo.key. */
    data class App(val component: String) : HomeItem() {
        override val key: String get() = component
        override val spanW: Int get() = 1
        override val spanH: Int get() = 1
    }

    /** An iOS-style folder of apps. [apps] are flattened ComponentNames. */
    data class Folder(val id: Long, val name: String, val apps: List<String>) : HomeItem() {
        override val key: String get() = "f:$id"
        override val spanW: Int get() = 1
        override val spanH: Int get() = 1
    }

    /** One of Halo's own widgets (clock, calendar, battery). */
    data class BuiltIn(val id: Long, val kind: WidgetKind, val size: WidgetSize) : HomeItem() {
        override val key: String get() = "w:$id"
        override val spanW: Int get() = size.w
        override val spanH: Int get() = size.h
    }

    /** A real Android widget from another app. */
    data class Hosted(val appWidgetId: Int, val size: WidgetSize) : HomeItem() {
        override val key: String get() = "h:$appWidgetId"
        override val spanW: Int get() = size.w
        override val spanH: Int get() = size.h
    }
}

data class ScreenLayout(val pages: List<List<HomeItem>>, val dock: List<String>) {
    fun normalized(): ScreenLayout {
        val kept = pages.filter { it.isNotEmpty() }
        return copy(pages = if (kept.isEmpty()) listOf(emptyList()) else kept)
    }

    companion object {
        val EMPTY = ScreenLayout(listOf(emptyList()), emptyList())
    }
}

data class Placed(val item: HomeItem, val col: Int, val row: Int)

/**
 * Flows items into the grid in order, like iOS: each item takes the first free spot
 * that fits it. Widgets wider than one cell snap to even columns.
 * Returns what was placed and what didn't fit.
 */
fun pack(items: List<HomeItem>, cols: Int, rows: Int): Pair<List<Placed>, List<HomeItem>> {
    val grid = Array(rows) { BooleanArray(cols) }
    val placed = ArrayList<Placed>()
    val overflow = ArrayList<HomeItem>()
    for (item in items) {
        val w = item.spanW.coerceAtMost(cols)
        val h = item.spanH.coerceAtMost(rows)
        var spotRow = -1
        var spotCol = -1
        search@ for (r in 0..rows - h) {
            for (c in 0..cols - w) {
                if (w > 1 && c % 2 != 0) continue
                var free = true
                check@ for (rr in r until r + h) {
                    for (cc in c until c + w) {
                        if (grid[rr][cc]) {
                            free = false
                            break@check
                        }
                    }
                }
                if (free) {
                    spotRow = r
                    spotCol = c
                    break@search
                }
            }
        }
        if (spotRow < 0) {
            overflow.add(item)
        } else {
            for (rr in spotRow until spotRow + h) for (cc in spotCol until spotCol + w) grid[rr][cc] = true
            placed.add(Placed(item, spotCol, spotRow))
        }
    }
    return placed to overflow
}

fun fits(items: List<HomeItem>, spec: GridSpec): Boolean = pack(items, spec.cols, spec.rows).second.isEmpty()

class LayoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("halo_layout", Context.MODE_PRIVATE)

    fun load(mode: LayoutMode): ScreenLayout? {
        val raw = prefs.getString("layout_${mode.name}", null) ?: return null
        return try {
            parse(raw)
        } catch (e: Exception) {
            null
        }
    }

    fun save(mode: LayoutMode, layout: ScreenLayout) {
        val root = JSONObject()
        root.put("dock", JSONArray(layout.dock))
        val pages = JSONArray()
        layout.pages.forEach { page ->
            val arr = JSONArray()
            page.forEach { arr.put(toJson(it)) }
            pages.put(arr)
        }
        root.put("pages", pages)
        prefs.edit().putString("layout_${mode.name}", root.toString()).apply()
    }

    fun loadRecents(): List<String> =
        prefs.getString("recents", null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    fun saveRecents(list: List<String>) {
        prefs.edit().putString("recents", list.joinToString("\n")).apply()
    }

    fun bannerDismissed(): Boolean = prefs.getBoolean("banner_dismissed", false)

    fun setBannerDismissed() {
        prefs.edit().putBoolean("banner_dismissed", true).apply()
    }

    private fun toJson(item: HomeItem): JSONObject = JSONObject().apply {
        when (item) {
            is HomeItem.App -> {
                put("t", "a"); put("c", item.component)
            }
            is HomeItem.BuiltIn -> {
                put("t", "w"); put("id", item.id); put("k", item.kind.name); put("s", item.size.name)
            }
            is HomeItem.Hosted -> {
                put("t", "h"); put("id", item.appWidgetId); put("s", item.size.name)
            }
            is HomeItem.Folder -> {
                put("t", "f"); put("id", item.id); put("n", item.name); put("a", JSONArray(item.apps))
            }
        }
    }

    private fun fromJson(o: JSONObject): HomeItem? = try {
        when (o.getString("t")) {
            "a" -> HomeItem.App(o.getString("c"))
            "w" -> HomeItem.BuiltIn(o.getLong("id"), WidgetKind.valueOf(o.getString("k")), WidgetSize.valueOf(o.getString("s")))
            "h" -> HomeItem.Hosted(o.getInt("id"), WidgetSize.valueOf(o.getString("s")))
            "f" -> {
                val a = o.optJSONArray("a") ?: JSONArray()
                HomeItem.Folder(o.getLong("id"), o.optString("n", "Folder"), (0 until a.length()).map { a.getString(it) })
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    private fun parse(raw: String): ScreenLayout {
        val root = JSONObject(raw)
        val dockArr = root.optJSONArray("dock") ?: JSONArray()
        val dock = (0 until dockArr.length()).map { dockArr.getString(it) }
        val pagesArr = root.getJSONArray("pages")
        val pages = (0 until pagesArr.length()).map { i ->
            val page = pagesArr.getJSONArray(i)
            (0 until page.length()).mapNotNull { j -> fromJson(page.getJSONObject(j)) }
        }
        return ScreenLayout(pages, dock).normalized()
    }
}
