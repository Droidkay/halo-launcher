package com.droidkay.halo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp

/** The last page: apps auto-sorted into folders, like the iOS App Library. */
@Composable
fun AppLibraryPage(
    state: LauncherState,
    mode: LayoutMode,
    launch: (AppInfo, Rect?) -> Unit,
    onSearch: () -> Unit,
    onFolder: (String) -> Unit,
    onMenu: (MenuRequest) -> Unit,
) {
    val groups = remember(state.apps, state.recents) { state.libraryGroups() }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    // iPad-sized folder tiles (≈140dp) on tablets — more columns on wider screens instead of
    // bigger tiles — and the iPhone's two big columns on phones. The grid is centred.
    val gap = 18.dp
    val tile = if (mode == LayoutMode.INNER) 140.dp else 150.dp
    val cols = if (mode == LayoutMode.INNER) {
        ((maxWidth - 40.dp + gap) / (tile + gap)).toInt().coerceIn(3, 6)
    } else 2
    val gridW = if (mode == LayoutMode.INNER) min(maxWidth, tile * cols + gap * (cols - 1) + 40.dp) else maxWidth
    Column(Modifier.width(gridW).fillMaxHeight()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .then(
                    if (HaloSettings.liquidGlass) Modifier.liquidGlass(RoundedCornerShape(50))
                    else Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.2f))
                )
                .clickable(onClick = onSearch)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MagnifierIcon(Color.White.copy(alpha = 0.8f), 15.dp)
            Spacer(Modifier.width(6.dp))
            Text("App Library", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(groups, key = { it.first }) { group ->
                CategoryTile(state, group.first, group.second, launch, onFolder, onMenu)
            }
        }
    }
    }
}

@Composable
private fun CategoryTile(
    state: LauncherState,
    name: String,
    list: List<AppInfo>,
    launch: (AppInfo, Rect?) -> Unit,
    onFolder: (String) -> Unit,
    onMenu: (MenuRequest) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(
                    if (HaloSettings.liquidGlass) Modifier.liquidGlass(RoundedCornerShape(28.dp))
                    else Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.16f))
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (row in 0..1) {
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (col in 0..1) {
                        val i = row * 2 + col
                        BoxWithConstraints(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val size = min(maxWidth, maxHeight)
                            if (i < 3 || list.size <= 4) {
                                list.getOrNull(i)?.let { app ->
                                    TappableIcon(
                                        state.icons[app.key], size,
                                        onTap = { r -> launch(app, r) },
                                        onLongPress = { r -> onMenu(MenuRequest(MenuTarget.LibraryApp(app.key), r)) },
                                    )
                                }
                            } else {
                                MiniGrid(state, list.drop(3).take(4), onClick = { onFolder(name) })
                            }
                        }
                    }
                }
            }
        }
        Text(
            name,
            style = labelStyle(12.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun MiniGrid(state: LauncherState, apps: List<AppInfo>, onClick: () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val gap = 5.dp
        val cell = (min(maxWidth, maxHeight) - gap) / 2
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (r in 0..1) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (c in 0..1) {
                        val app = apps.getOrNull(r * 2 + c)
                        if (app != null) AppIconImage(state.icons[app.key], cell)
                        else Spacer(Modifier.width(cell))
                    }
                }
            }
        }
    }
}
