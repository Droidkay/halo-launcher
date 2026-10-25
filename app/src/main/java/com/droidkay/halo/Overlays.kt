package com.droidkay.halo

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ------------------------------------------------------------------ search (Spotlight + App Library list)

@Composable
private fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    focus: FocusRequester,
    placeholder: String,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        modifier = modifier.focusRequester(focus),
        decorationBox = { inner ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MagnifierIcon(Color.White.copy(alpha = 0.7f), 15.dp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = Color.White.copy(alpha = 0.55f), fontSize = 17.sp)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Text(
                        "✕",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable { onChange("") }
                            .padding(start = 8.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp, start = 4.dp),
    )
}

@Composable
private fun AppRow(state: LauncherState, app: AppInfo, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconImage(state.icons[app.key], 42.dp)
        Spacer(Modifier.width(14.dp))
        Text(app.label, color = Color.White, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActionRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { MagnifierIcon(Color.White, 18.dp) }
        Spacer(Modifier.width(14.dp))
        Text(text, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SearchOverlay(
    state: LauncherState,
    mode: LayoutMode,
    isLibrary: Boolean,
    onDismiss: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
    }
    val q = query.trim()
    val results = remember(q, state.apps) {
        if (q.isEmpty()) state.apps
        else state.apps
            .filter { it.label.contains(q, ignoreCase = true) }
            .sortedWith(compareBy<AppInfo>({ !it.label.startsWith(q, ignoreCase = true) }, { it.label.lowercase() }))
    }
    val cols = if (mode == LayoutMode.INNER) 6 else 4

    Box(
        Modifier
            .fillMaxSize()
            .background(glassScrim(0.35f, 0.8f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchField(
                    value = query,
                    onChange = { query = it },
                    focus = focus,
                    placeholder = if (isLibrary) "App Library" else "Search",
                    onSubmit = {
                        val top = results.firstOrNull()
                        if (top != null && q.isNotEmpty()) onLaunch(top)
                        else if (!isLibrary && q.isNotEmpty()) webSearch(context, q)
                    },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Cancel",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
                )
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (!isLibrary && q.isEmpty()) {
                    val suggestions = state.recents.mapNotNull { state.appMap[it] }.take(cols * 2)
                    if (suggestions.isNotEmpty()) {
                        item { SectionTitle("Suggestions") }
                        items(suggestions.chunked(cols)) { rowApps ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                rowApps.forEach { app ->
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        TappableIcon(state.icons[app.key], 56.dp, onTap = { onLaunch(app) }, onLongPress = { })
                                        Text(
                                            app.label,
                                            style = labelStyle(11.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 5.dp),
                                        )
                                    }
                                }
                                repeat(cols - rowApps.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    } else {
                        item { SectionTitle("Type to search your apps and the web") }
                    }
                } else if (isLibrary) {
                    val grouped = results.groupBy { a ->
                        a.label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
                    }.toSortedMap()
                    grouped.forEach { (letter, apps) ->
                        item(key = "h_$letter") { SectionTitle(letter.toString()) }
                        items(apps, key = { it.key }) { app -> AppRow(state, app) { onLaunch(app) } }
                    }
                } else {
                    if (results.isNotEmpty()) {
                        item { SectionTitle("Apps") }
                        items(results.take(12), key = { it.key }) { app -> AppRow(state, app) { onLaunch(app) } }
                    }
                    item { SectionTitle("Search") }
                    item { ActionRow("Search the web for “$q”") { webSearch(context, q) } }
                    item { ActionRow("Search Play Store for “$q”") { playStoreSearch(context, q) } }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ long-press menu

@Composable
fun ContextMenu(req: MenuRequest, entries: List<MenuEntry>, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxSize()
            .background(glassScrim(0.12f, 0.4f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val menuW = 250.dp
            val rowH = 48.dp
            val menuH = rowH * entries.size
            val left: Dp = with(density) { req.bounds.left.toDp() }
            val top: Dp = with(density) { req.bounds.top.toDp() }
            val bottom: Dp = with(density) { req.bounds.bottom.toDp() }
            val x = left.coerceIn(12.dp, (maxWidth - menuW - 12.dp).coerceAtLeast(12.dp))
            val below = bottom + 10.dp
            val y = if (below + menuH < maxHeight - 24.dp) below else (top - menuH - 10.dp).coerceAtLeast(24.dp)
            Column(
                Modifier
                    .offset(x, y)
                    .width(menuW)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xF22C2C2E))
            ) {
                entries.forEachIndexed { i, entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(rowH)
                            .clickable {
                                onDismiss()
                                entry.action()
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            entry.label,
                            color = if (entry.destructive) IosRed else Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(entry.glyph, color = if (entry.destructive) IosRed else Color.White, fontSize = 16.sp)
                    }
                    if (i < entries.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(Color.White.copy(alpha = 0.15f))
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ widget gallery

@Composable
fun WidgetGallery(
    state: LauncherState,
    mode: LayoutMode,
    onDismiss: () -> Unit,
    onAddApp: (AppInfo) -> Unit,
    onAddBuiltIn: (WidgetKind, WidgetSize) -> Unit,
    onAddHosted: (AppWidgetProviderInfo, WidgetSize) -> Unit,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    var tab by remember { mutableStateOf("apps") }
    val providers = remember {
        state.widgets.providers()
            .map { it to runCatching { it.loadLabel(context.packageManager) }.getOrNull().orEmpty() }
            .sortedWith(compareBy<Pair<AppWidgetProviderInfo, String>>({ state.appLabelForPackage(it.first.provider.packageName).lowercase() }, { it.second.lowercase() }))
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color(0xFF1C1C1E))
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Apps | Widgets switch
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(3.dp)
                ) {
                    listOf("apps" to "Apps", "widgets" to "Widgets").forEach { (id, title) ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (tab == id) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                                .clickable { tab = id }
                                .padding(horizontal = 18.dp, vertical = 7.dp)
                        ) {
                            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                GlassPill("Done", bold = true, onClick = onDismiss)
            }
            if (tab == "apps") {
                AddAppsList(state, mode, onAddApp)
            } else
            LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
                WidgetKind.values().forEach { kind ->
                    item(key = "kind_${kind.name}") {
                        Column(Modifier.padding(vertical = 10.dp)) {
                            Text(
                                kind.title,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                            Row(
                                Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                kind.sizes.forEach { size ->
                                    val w = if (size == WidgetSize.SMALL) 150.dp else 316.dp
                                    val h = if (size == WidgetSize.LARGE) 316.dp else 150.dp
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            Modifier
                                                .size(w, h)
                                                .clip(RoundedCornerShape(22.dp))
                                                .clickable { onAddBuiltIn(kind, size) }
                                        ) {
                                            BuiltInWidget(kind, size)
                                        }
                                        Text(size.title, color = IosGray, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "android_header") {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)) {
                        Text("App Widgets", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("Widgets from your installed apps. Tap S, M or L to pick a size.", color = IosGray, fontSize = 13.sp)
                    }
                }
                if (providers.isEmpty()) {
                    item { Text("No app widgets found.", color = IosGray, fontSize = 14.sp, modifier = Modifier.padding(20.dp)) }
                }
                items(providers, key = { it.first.provider.flattenToString() }) { pair ->
                    val info = pair.first
                    val label = pair.second
                    val wDp = info.minWidth / density
                    val hDp = info.minHeight / density
                    val suggested = when {
                        hDp > 220f -> WidgetSize.LARGE
                        wDp > 160f -> WidgetSize.MEDIUM
                        else -> WidgetSize.SMALL
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIconImage(state.iconForPackage(info.provider.packageName), 40.dp)
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 12.dp, end = 8.dp)
                        ) {
                            Text(label, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                state.appLabelForPackage(info.provider.packageName),
                                color = IosGray,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        WidgetSize.values().forEach { s ->
                            Box(
                                Modifier
                                    .padding(start = 6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (s == suggested) IosBlue else Color.White.copy(alpha = 0.14f))
                                    .clickable { onAddHosted(info, s) }
                                    .padding(horizontal = 11.dp, vertical = 6.dp)
                            ) {
                                Text(s.title.take(1), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}


/** "+" sheet, Apps tab: every installed app with a button to put it on the current home page. */
@Composable
private fun AddAppsList(state: LauncherState, mode: LayoutMode, onAddApp: (AppInfo) -> Unit) {
    var query by remember { mutableStateOf("") }
    val apps = state.apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (query.isEmpty()) Text("Search apps", color = IosGray, fontSize = 16.sp)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(IosBlue),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
            items(apps, key = { it.key }) { app ->
                val onHome = state.isOnHome(mode, app.key) || app.key in state.layout(mode).dock
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !onHome) { onAddApp(app) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconImage(state.icons[app.key], 42.dp)
                    Text(
                        app.label,
                        color = Color.White,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp),
                    )
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (onHome) Color.White.copy(alpha = 0.1f) else IosBlue)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            if (onHome) "On Home" else "Add",
                            color = if (onHome) IosGray else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
