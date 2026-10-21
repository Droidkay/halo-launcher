package com.droidkay.halo

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.View
import android.widget.Toast
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherState(private val context: Context, val widgets: WidgetHostManager) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val store = LayoutStore(context)
    private val iconPx = (context.resources.displayMetrics.density * 76).toInt().coerceAtLeast(128)

    var apps by mutableStateOf<List<AppInfo>>(emptyList())
        private set
    var appsLoaded by mutableStateOf(false)
        private set
    val icons = mutableStateMapOf<String, ImageBitmap>()
    private val layouts = mutableStateMapOf<LayoutMode, ScreenLayout>()

    var editMode by mutableStateOf(false)
    var recents by mutableStateOf(store.loadRecents())
        private set
    var homeSignal by mutableIntStateOf(0)
        private set
    var bannerDismissed by mutableStateOf(store.bannerDismissed())
        private set
    var isDefaultHome by mutableStateOf(true)
        private set

    private val appMapState = derivedStateOf { apps.associateBy { it.key } }
    val appMap: Map<String, AppInfo> get() = appMapState.value

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh(packageName)
        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) {
            packageNames.forEach { refresh(it) }
        }
        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) {
            packageNames.forEach { refresh(it) }
        }
    }

    init {
        LayoutMode.values().forEach { m -> store.load(m)?.let { layouts[m] = it } }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        loadApps()
    }

    fun dispose() {
        runCatching { launcherApps.unregisterCallback(callback) }
        scope.cancel()
    }

    // ---------------------------------------------------------------- apps

    private fun refresh(pkg: String) {
        icons.keys.filter { it.startsWith("$pkg/") }.forEach { icons.remove(it) }
        loadApps()
    }

    private fun queryApps(): List<AppInfo> =
        launcherApps.getActivityList(null, Process.myUserHandle())
            .filter { it.componentName.packageName != context.packageName }
            .map {
                AppInfo(
                    component = it.componentName,
                    label = it.label?.toString() ?: it.componentName.packageName,
                    category = it.applicationInfo.category,
                    installTime = it.firstInstallTime,
                    user = it.user,
                    info = it,
                )
            }
            .sortedBy { it.label.lowercase() }

    fun loadApps() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { runCatching { queryApps() }.getOrDefault(emptyList()) }
            apps = list
            appsLoaded = true
            LayoutMode.values().forEach { m -> if (layouts[m] == null) commit(m, createDefault(m)) }
            purgeMissing()
            // Icons render in chunks so the home screen fills in quickly.
            list.filter { it.key !in icons }.chunked(16).forEach { chunk ->
                val rendered = withContext(Dispatchers.Default) {
                    chunk.mapNotNull { app ->
                        runCatching {
                            app.key to renderIcon(app.info.getIcon(DisplayMetrics.DENSITY_XXXHIGH), iconPx).asImageBitmap()
                        }.getOrNull()
                    }
                }
                rendered.forEach { (k, v) -> icons[k] = v }
            }
        }
    }

    fun iconForPackage(pkg: String): ImageBitmap? = apps.firstOrNull { it.pkg == pkg }?.let { icons[it.key] }

    fun appLabelForPackage(pkg: String): String = apps.firstOrNull { it.pkg == pkg }?.label ?: pkg

    fun launch(app: AppInfo, view: View?, bounds: Rect?) {
        val r = bounds?.takeIf { it.width > 0f && it.height > 0f }?.let {
            android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt())
        }
        val opts = if (view != null && r != null) {
            ActivityOptions.makeScaleUpAnimation(view, r.left, r.top, r.width(), r.height()).toBundle()
        } else null
        try {
            launcherApps.startMainActivity(app.component, app.user, r, opts)
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't open ${app.label}", Toast.LENGTH_SHORT).show()
            return
        }
        recents = (listOf(app.key) + recents.filter { it != app.key }).take(12)
        store.saveRecents(recents)
    }

    fun openAppInfo(app: AppInfo) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun uninstall(app: AppInfo) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun homePressed() {
        homeSignal++
    }

    fun refreshDefaultHome() {
        isDefaultHome = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY,
            )?.activityInfo?.packageName == context.packageName
        }.getOrDefault(true)
    }

    fun dismissBanner() {
        bannerDismissed = true
        store.setBannerDismissed()
    }

    // ---------------------------------------------------------------- layout

    fun layout(mode: LayoutMode): ScreenLayout = layouts[mode] ?: ScreenLayout.EMPTY

    private fun commit(mode: LayoutMode, layout: ScreenLayout) {
        val n = layout.normalized()
        layouts[mode] = n
        store.save(mode, n)
    }

    fun setPage(mode: LayoutMode, index: Int, items: List<HomeItem>) {
        val l = layout(mode)
        if (index !in l.pages.indices) return
        commit(mode, l.copy(pages = l.pages.toMutableList().also { it[index] = items }))
    }

    fun isOnHome(mode: LayoutMode, key: String): Boolean = layout(mode).pages.any { p -> p.any { it.key == key } }

    fun canAddToDock(mode: LayoutMode, key: String): Boolean {
        val l = layout(mode)
        return key !in l.dock && l.dock.size < mode.spec().dockSize
    }

    fun addToDock(mode: LayoutMode, key: String): Boolean {
        if (!canAddToDock(mode, key)) return false
        val l = layout(mode)
        commit(mode, l.copy(dock = l.dock + key))
        return true
    }

    fun removeFromDock(mode: LayoutMode, key: String) {
        val l = layout(mode)
        commit(mode, l.copy(dock = l.dock - key))
    }

    fun removeFromHome(mode: LayoutMode, key: String) {
        val l = layout(mode)
        val item = l.pages.flatten().firstOrNull { it.key == key }
        if (item is HomeItem.Hosted) widgets.delete(item.appWidgetId)
        commit(mode, l.copy(pages = l.pages.map { p -> p.filter { it.key != key } }))
    }

    /** Adds to the preferred page if there's room, otherwise the next page with room, otherwise a new page. */
    fun addToHome(mode: LayoutMode, item: HomeItem, preferredPage: Int, prepend: Boolean): Int {
        val spec = mode.spec()
        val l = layout(mode)
        val pages = l.pages.toMutableList()
        val order = (listOf(preferredPage) + pages.indices.filter { it != preferredPage }).filter { it in pages.indices }
        for (i in order) {
            val options = if (prepend) listOf(listOf(item) + pages[i], pages[i] + item) else listOf(pages[i] + item)
            for (candidate in options) {
                if (fits(candidate, spec)) {
                    pages[i] = candidate
                    commit(mode, l.copy(pages = pages))
                    return i
                }
            }
        }
        pages.add(listOf(item))
        commit(mode, l.copy(pages = pages))
        return pages.lastIndex
    }

    fun libraryGroups(): List<Pair<String, List<AppInfo>>> {
        val out = mutableListOf<Pair<String, List<AppInfo>>>()
        if (apps.isEmpty()) return out
        val map = appMap
        val suggestions = recents.mapNotNull { map[it] }.take(8)
        if (suggestions.isNotEmpty()) out += "Suggestions" to suggestions
        out += "Recently Added" to apps.sortedByDescending { it.installTime }.take(8)
        val grouped = apps.groupBy { LibraryCategories.of(it) }
        LibraryCategories.order.forEach { name ->
            grouped[name]?.takeIf { it.isNotEmpty() }?.let { out += name to it }
        }
        return out
    }

    private fun purgeMissing() {
        val keys = appMap.keys
        LayoutMode.values().forEach { m ->
            val l = layouts[m] ?: return@forEach
            val cleaned = l.copy(
                pages = l.pages.map { p -> p.filter { it !is HomeItem.App || it.component in keys } },
                dock = l.dock.filter { it in keys },
            )
            if (cleaned != l) commit(m, cleaned)
        }
    }

    private fun createDefault(mode: LayoutMode): ScreenLayout {
        val spec = mode.spec()
        val byPkg = apps.groupBy { it.pkg }
        fun keyFor(pkg: String?): String? = pkg?.let { byPkg[it]?.firstOrNull()?.key }
        fun firstInstalled(vararg pkgs: String): String? = pkgs.firstOrNull { byPkg.containsKey(it) }
        val pm = context.packageManager
        fun resolve(intent: Intent): String? = runCatching {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.getOrNull()?.takeIf { it != "android" && byPkg.containsKey(it) }

        val phone = resolve(Intent(Intent.ACTION_DIAL))
            ?: firstInstalled("com.samsung.android.dialer", "com.google.android.dialer")
        val messages = resolve(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING))
            ?: firstInstalled("com.samsung.android.messaging", "com.google.android.apps.messaging")
        val browser = resolve(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
            ?: firstInstalled("com.android.chrome", "com.sec.android.app.sbrowser")
        val camera = resolve(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            ?: firstInstalled("com.sec.android.app.camera")

        val dockPkgs = listOfNotNull(phone, messages, browser, camera) +
            if (mode == LayoutMode.INNER) listOf("com.whatsapp", "com.google.android.youtube") else emptyList()
        val dock = dockPkgs.mapNotNull { keyFor(it) }.distinct().toMutableList()

        val favorites = FAVORITES.mapNotNull { keyFor(it) }.distinct().filter { it !in dock }.toMutableList()
        val others = apps.map { it.key }.filter { it !in dock && it !in favorites }.toMutableList()
        while (dock.size < spec.dockSize && (favorites.isNotEmpty() || others.isNotEmpty())) {
            dock += if (favorites.isNotEmpty()) favorites.removeAt(0) else others.removeAt(0)
        }

        fun fill(page: MutableList<HomeItem>, source: MutableList<String>) {
            while (source.isNotEmpty()) {
                val candidate = page + HomeItem.App(source.first())
                if (!fits(candidate, spec)) break
                page.add(HomeItem.App(source.removeAt(0)))
            }
        }

        val first = mutableListOf<HomeItem>(
            HomeItem.BuiltIn(1L, WidgetKind.CLOCK, WidgetSize.MEDIUM),
            HomeItem.BuiltIn(2L, WidgetKind.CALENDAR, WidgetSize.SMALL),
            HomeItem.BuiltIn(3L, WidgetKind.BATTERY, WidgetSize.SMALL),
        )
        fill(first, favorites)
        fill(first, others)
        val pages = mutableListOf<List<HomeItem>>(first)
        if (favorites.isNotEmpty()) {
            val second = mutableListOf<HomeItem>()
            fill(second, favorites)
            pages += second
        }
        return ScreenLayout(pages, dock)
    }

    companion object {
        private val FAVORITES = listOf(
            "com.whatsapp", "com.whatsapp.w4b", "com.google.android.youtube", "com.google.android.apps.youtube.creator",
            "com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.facebook.katana",
            "com.twitter.android", "org.telegram.messenger", "com.google.android.gm", "com.google.android.apps.maps",
            "com.sec.android.gallery3d", "com.google.android.apps.photos", "com.samsung.android.calendar",
            "com.google.android.calendar", "com.sec.android.app.clockpackage", "com.google.android.deskclock",
            "com.android.settings", "com.android.vending", "com.spotify.music", "com.lemon.lvoverseas",
            "com.google.android.apps.docs", "com.anthropic.claude", "com.openai.chatgpt", "com.sec.android.app.myfiles",
            "com.samsung.android.app.notes", "com.google.android.keep", "com.sec.android.app.popupcalculator",
            "com.sec.android.daemonapp", "com.samsung.android.weather",
        )
    }
}
