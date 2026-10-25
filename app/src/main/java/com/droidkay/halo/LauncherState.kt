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
        // Halo Pad v2 layout (no category-folder page, iPadOS grid): start fresh once.
        val resetPad = BuildConfig.IS_PAD && store.layoutVersion() < PAD_LAYOUT_VERSION
        if (!resetPad) LayoutMode.values().forEach { m -> store.load(m)?.let { layouts[m] = it } }
        if (resetPad) store.setLayoutVersion(PAD_LAYOUT_VERSION)
        // Halo (Fold) moved to the iPhone "Duo" layout: re-flow saved screens into the 4-column grid once.
        if (!BuildConfig.IS_PAD && store.duoVersion() < DUO_LAYOUT_VERSION) {
            LayoutMode.values().forEach { m -> layouts[m]?.let { commit(m, migrateToDuo(m, it)) } }
            store.setDuoVersion(DUO_LAYOUT_VERSION)
        }
        // Grid / dock sizes come from Settings and may have changed since the layout was saved.
        if (!BuildConfig.IS_PAD) reflowAll()
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
                            app.key to renderAppIcon(context, app, iconPx).asImageBitmap()
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
        android.util.Log.d(
            "HaloLayout",
            "$mode dock=${n.dock.map { it.substringBefore('/') }} pages=" +
                n.pages.mapIndexed { i, p -> "$i:${p.map { it.key.substringBefore('/').substringAfterLast('.') }}" },
        )
    }

    fun setPage(mode: LayoutMode, index: Int, items: List<HomeItem>) {
        val l = layout(mode)
        if (index !in l.pages.indices) return
        commit(mode, l.copy(pages = l.pages.toMutableList().also { it[index] = items }))
    }

    /**
     * Saves the result of a drag: [pages] may hold a page that's too full now, so whatever doesn't
     * fit is pushed onto the start of the next page (making a new page at the end if needed), like iOS.
     */
    fun applyDrag(mode: LayoutMode, pages: List<List<HomeItem>>, dock: List<String>) {
        val spec = mode.spec()
        val out = pages.map { it.toMutableList() }.toMutableList()
        var i = 0
        while (i < out.size) {
            val (_, overflow) = pack(out[i], spec.cols, spec.rows)
            if (overflow.isNotEmpty()) {
                out[i].removeAll { o -> overflow.any { it.key == o.key } }
                if (i + 1 >= out.size) out.add(mutableListOf())
                out[i + 1].addAll(0, overflow)
            }
            i++
        }
        commit(mode, layout(mode).copy(pages = out, dock = dock.distinct().take(spec.dockSize)))
    }

    /** Re-fits a screen after its grid or dock size changed: overflow moves to later pages, extra dock apps to the home screen. */
    fun reflow(mode: LayoutMode) {
        val l = layouts[mode] ?: return
        val spec = mode.spec()
        val extra = l.dock.drop(spec.dockSize).filter { k -> l.pages.none { p -> p.any { it.key == k } } }.map { HomeItem.App(it) }
        val pages = l.pages.toMutableList()
        if (extra.isNotEmpty()) pages[pages.lastIndex] = pages.last() + extra
        applyDrag(mode, pages, l.dock.take(spec.dockSize))
    }

    fun reflowAll() = LayoutMode.values().forEach { reflow(it) }

    /** Back to Halo's starting layout for this screen. */
    fun resetLayout(mode: LayoutMode) {
        layout(mode).pages.flatten().filterIsInstance<HomeItem.Hosted>().forEach { widgets.delete(it.appWidgetId) }
        commit(mode, createDefault(mode))
    }

    /** Re-renders every icon (after the icon shape or icon pack changed). */
    fun reloadIcons() {
        icons.clear()
        loadApps()
    }

    fun isOnHome(mode: LayoutMode, key: String): Boolean = layout(mode).pages.any { p ->
        p.any { it.key == key || (it is HomeItem.Folder && key in it.apps) }
    }

    // ---------------------------------------------------------------- folders

    fun folders(mode: LayoutMode): List<HomeItem.Folder> =
        layout(mode).pages.flatten().filterIsInstance<HomeItem.Folder>()

    fun folder(mode: LayoutMode, key: String): HomeItem.Folder? = folders(mode).firstOrNull { it.key == key }

    private fun mapItems(mode: LayoutMode, f: (HomeItem) -> HomeItem?) {
        val l = layout(mode)
        commit(mode, l.copy(pages = l.pages.map { p -> p.mapNotNull(f) }))
    }

    fun renameFolder(mode: LayoutMode, key: String, name: String) {
        val clean = name.trim().ifEmpty { "Folder" }
        mapItems(mode) { if (it.key == key && it is HomeItem.Folder) it.copy(name = clean) else it }
    }

    /** Moves an app into a folder (taking it off the home grid and out of any other folder, like iOS). */
    fun moveToFolder(mode: LayoutMode, folderKey: String, appKey: String) {
        mapItems(mode) { item ->
            when {
                item is HomeItem.App && item.component == appKey -> null
                item is HomeItem.Folder && item.key == folderKey ->
                    if (appKey in item.apps) item else item.copy(apps = item.apps + appKey)
                item is HomeItem.Folder && appKey in item.apps -> item.copy(apps = item.apps - appKey)
                else -> item
            }
        }
    }

    /** New folder holding [appKey], placed where the app was (or on the current page). Returns its key. */
    fun createFolder(mode: LayoutMode, name: String, appKey: String, page: Int): String {
        val folder = HomeItem.Folder(System.currentTimeMillis(), name, listOf(appKey))
        val l = layout(mode)
        val onGrid = l.pages.any { p -> p.any { it is HomeItem.App && it.component == appKey } }
        if (onGrid) {
            mapItems(mode) { item ->
                when {
                    item is HomeItem.App && item.component == appKey -> folder
                    item is HomeItem.Folder && appKey in item.apps -> item.copy(apps = item.apps - appKey)
                    else -> item
                }
            }
        } else {
            mapItems(mode) { if (it is HomeItem.Folder && appKey in it.apps) it.copy(apps = it.apps - appKey) else it }
            addToHome(mode, folder, page, prepend = false)
        }
        return folder.key
    }

    fun removeFromFolder(mode: LayoutMode, folderKey: String, appKey: String) {
        mapItems(mode) { if (it is HomeItem.Folder && it.key == folderKey) it.copy(apps = it.apps - appKey) else it }
    }

    /** Adds a folder per App Library category (Social, Entertainment…) for apps not already in a folder. */
    fun addCategoryFolders(mode: LayoutMode): Int {
        val taken = folders(mode).flatMap { it.apps }.toSet()
        val existing = folders(mode).associateBy { it.name }
        val grouped = apps.filter { it.key !in taken }.groupBy { LibraryCategories.of(it) }
        var added = 0
        var lastPage = layout(mode).pages.lastIndex
        LibraryCategories.order.forEachIndexed { i, cat ->
            val list = grouped[cat]?.map { it.key } ?: return@forEachIndexed
            if (list.isEmpty()) return@forEachIndexed
            val current = existing[cat]
            if (current != null) {
                mapItems(mode) { if (it.key == current.key && it is HomeItem.Folder) it.copy(apps = it.apps + list) else it }
            } else {
                lastPage = addToHome(mode, HomeItem.Folder(System.currentTimeMillis() + i, cat, list), lastPage, prepend = false)
                added++
            }
        }
        return added
    }

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
                pages = l.pages.map { p ->
                    p.filter { it !is HomeItem.App || it.component in keys }.mapNotNull {
                        if (it is HomeItem.Folder) {
                            val kept = it.apps.filter { a -> a in keys }
                            if (kept.isEmpty()) null else if (kept.size == it.apps.size) it else it.copy(apps = kept)
                        } else it
                    }
                },
                dock = l.dock.filter { it in keys },
            )
            if (cleaned != l) commit(m, cleaned)
        }
    }

    /**
     * Keeps everything the user had, but re-packs each page into the 4-column grid (spilling onto new
     * pages after it when needed), trims the dock to 4 (extra dock apps go to the home screen) and puts
     * the Google and Weather widgets at the top of page one.
     */
    private fun migrateToDuo(mode: LayoutMode, old: ScreenLayout): ScreenLayout {
        val spec = mode.spec()
        val dock = old.dock.take(spec.dockSize)
        val all = old.pages.flatten()
        val lead = buildList<HomeItem> {
            if (all.none { it is HomeItem.BuiltIn && it.kind == WidgetKind.GOOGLE }) {
                add(HomeItem.BuiltIn(System.currentTimeMillis(), WidgetKind.GOOGLE, WidgetSize.SMALL))
            }
            if (all.none { it is HomeItem.BuiltIn && it.kind == WidgetKind.WEATHER }) {
                add(HomeItem.BuiltIn(System.currentTimeMillis() + 1, WidgetKind.WEATHER, WidgetSize.SMALL))
            }
        }
        val moved = old.dock.drop(spec.dockSize).filter { k -> all.none { it.key == k } }.map { HomeItem.App(it) }
        val sources = old.pages.mapIndexed { i, p ->
            (if (i == 0) lead + p else p) + (if (i == old.pages.lastIndex) moved else emptyList())
        }
        val pages = mutableListOf<List<HomeItem>>()
        sources.forEach { items ->
            var cur = mutableListOf<HomeItem>()
            items.forEach { item ->
                if (fits(cur + item, spec)) {
                    cur.add(item)
                } else {
                    if (cur.isNotEmpty()) pages += cur
                    cur = mutableListOf(item)
                }
            }
            if (cur.isNotEmpty()) pages += cur
        }
        return ScreenLayout(pages, dock)
    }

    private fun createDefault(mode: LayoutMode): ScreenLayout =
        if (BuildConfig.IS_PAD) createPadDefault() else createPhoneDefault(mode)

    /** iPadOS 26-style start: a row of dark glass widgets on top, apps underneath. */
    private fun createPadDefault(): ScreenLayout {
        val spec = LayoutMode.INNER.spec()
        val byPkg = apps.groupBy { it.pkg }
        fun keyFor(pkg: String): String? = byPkg[pkg]?.firstOrNull()?.key
        val dock = (PAD_DOCK.mapNotNull { keyFor(it) } + FAVORITES.mapNotNull { keyFor(it) })
            .distinct().take(spec.dockSize).toMutableList()
        val favorites = FAVORITES.mapNotNull { keyFor(it) }.distinct().filter { it !in dock }.toMutableList()
        val first = mutableListOf<HomeItem>(
            HomeItem.BuiltIn(1L, WidgetKind.CLOCK, WidgetSize.MEDIUM),
            HomeItem.BuiltIn(2L, WidgetKind.CALENDAR, WidgetSize.SMALL),
            HomeItem.BuiltIn(3L, WidgetKind.BATTERY, WidgetSize.SMALL),
        )
        while (favorites.isNotEmpty() && fits(first + HomeItem.App(favorites.first()), spec)) {
            first.add(HomeItem.App(favorites.removeAt(0)))
        }
        val pages = mutableListOf<List<HomeItem>>(first)
        if (favorites.isNotEmpty()) {
            val second = mutableListOf<HomeItem>(HomeItem.BuiltIn(4L, WidgetKind.CALENDAR, WidgetSize.LARGE))
            while (favorites.isNotEmpty() && fits(second + HomeItem.App(favorites.first()), spec)) {
                second.add(HomeItem.App(favorites.removeAt(0)))
            }
            pages += second
        }
        return ScreenLayout(pages, dock)
    }

    private fun createPhoneDefault(mode: LayoutMode): ScreenLayout {
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

        val dockPkgs = listOfNotNull(phone, messages, browser, camera)
        val dock = dockPkgs.mapNotNull { keyFor(it) }.distinct().take(spec.dockSize).toMutableList()

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

        // iPhone Duo start: Google + Weather widgets on top, apps underneath.
        val first = mutableListOf<HomeItem>(
            HomeItem.BuiltIn(1L, WidgetKind.GOOGLE, WidgetSize.SMALL),
            HomeItem.BuiltIn(2L, WidgetKind.WEATHER, WidgetSize.SMALL),
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
        private const val PAD_LAYOUT_VERSION = 2
        private const val DUO_LAYOUT_VERSION = 1
        private val PAD_DOCK = listOf(
            "com.android.chrome", "com.google.android.youtube", "com.whatsapp", "com.google.android.gm",
            "com.miui.gallery", "com.google.android.apps.photos", "com.android.camera", "com.android.settings",
            "com.miui.notes", "com.google.android.apps.docs",
        )
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
