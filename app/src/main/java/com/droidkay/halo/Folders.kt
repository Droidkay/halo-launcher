package com.droidkay.halo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A folder on the home grid: frosted tile with a 3×3 preview of its apps, name underneath. */
@Composable
fun FolderCell(
    state: LauncherState,
    folder: HomeItem.Folder,
    iconSize: Dp,
    edit: Boolean,
    onOpen: () -> Unit,
    onLongPress: (Rect) -> Unit,
    onRemove: () -> Unit,
    gestures: Boolean = true,
    onBounds: ((Rect) -> Unit)? = null,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val haptic = LocalHapticFeedback.current
    val open by rememberUpdatedState(onOpen)
    val long by rememberUpdatedState(onLongPress)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box {
            Box(
                Modifier
                    .size(iconSize)
                    .onGloballyPositioned {
                        bounds = it.boundsInRoot()
                        onBounds?.invoke(bounds)
                    }
                    .then(
                        if (BuildConfig.IS_PAD) Modifier.liquidGlass(SquircleShape, rim = 1.dp)
                        else Modifier.clip(iconShapeFor(HaloSettings.iconShape)).background(Color.White.copy(alpha = 0.30f))
                    )
                    .pointerInput(edit, gestures) {
                        // On the home grid, taps and long-presses are handled by the item's drag handler.
                        if (!gestures) return@pointerInput
                        detectTapGestures(
                            onTap = { open() },
                            onLongPress = {
                                if (!edit) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    long(bounds)
                                }
                            },
                        )
                    }
                    .padding(iconSize * 0.12f),
            ) {
                val gap = iconSize * 0.06f
                val mini = (iconSize * 0.76f - gap * 2) / 3
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (r in 0..2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            for (c in 0..2) {
                                val key = folder.apps.getOrNull(r * 3 + c)
                                if (key != null) AppIconImage(state.icons[key], mini) else Spacer(Modifier.size(mini))
                            }
                        }
                    }
                }
            }
            if (edit) MinusBadge(onRemove, Modifier.align(Alignment.TopStart).offset((-7).dp, (-7).dp))
        }
        if (HaloSettings.showLabels) {
            Spacer(Modifier.height(6.dp))
            Text(
                folder.name,
                style = labelStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = iconSize + 24.dp),
            )
        }
    }
}

/**
 * Opened folder, iPadOS style: big frosted panel over the blurred home screen.
 * [onRename] makes the title tappable to rename (home folders); library folders pass null.
 */
@Composable
fun FolderPanel(
    state: LauncherState,
    mode: LayoutMode,
    title: String,
    list: List<AppInfo>,
    launch: (AppInfo, Rect?) -> Unit,
    onDismiss: () -> Unit,
    appMenu: (AppInfo, Rect) -> Unit,
    onRename: ((String) -> Unit)?,
) {
    val cols = if (BuildConfig.IS_PAD) 5 else if (mode == LayoutMode.INNER) 6 else 4
    val focus = LocalFocusManager.current
    var name by remember(title) { mutableStateOf(title) }
    var editing by remember { mutableStateOf(false) }
    fun commit() {
        if (onRename != null && name.trim() != title) onRename(name)
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(glassScrim(0.15f, 0.6f))
            .pointerInput(Unit) {
                detectTapGestures {
                    commit()
                    onDismiss()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 22.dp, vertical = 40.dp)
                .widthIn(max = 620.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (onRename != null) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        commit()
                        focus.clearFocus()
                    }),
                    modifier = Modifier
                        .padding(bottom = 18.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (editing) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .onFocusChanged {
                            if (editing && !it.isFocused) commit()
                            editing = it.isFocused
                        },
                )
            } else {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 18.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                modifier = Modifier
                    .heightIn(max = 600.dp)
                    .then(
                        if (BuildConfig.IS_PAD) Modifier.liquidGlass(RoundedCornerShape(40.dp))
                        else Modifier.clip(RoundedCornerShape(40.dp))
                            .background(Color.White.copy(alpha = if (LocalWallpaperBlur.current) 0.22f else 0.14f))
                    )
                    .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } },
                contentPadding = PaddingValues(22.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(list, key = { it.key }) { app ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TappableIcon(
                            state.icons[app.key], if (BuildConfig.IS_PAD) 66.dp else 56.dp,
                            onTap = { r ->
                                commit()
                                onDismiss()
                                launch(app, r)
                            },
                            onLongPress = { r -> appMenu(app, r) },
                        )
                        Text(
                            app.label,
                            style = labelStyle(12.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** "Move to Folder" sheet: pick an existing folder or make a new one. */
@Composable
fun FolderPicker(
    state: LauncherState,
    mode: LayoutMode,
    appKey: String,
    onDismiss: () -> Unit,
    onPick: (folderKey: String) -> Unit,
    onNew: (name: String) -> Unit,
) {
    val app = state.appMap[appKey]
    val folders = state.folders(mode)
    val suggested = app?.let { LibraryCategories.of(it) } ?: "Folder"
    Box(
        Modifier
            .fillMaxSize()
            .background(glassScrim(0.2f, 0.6f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF22C2C2E))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(vertical = 10.dp)
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (app != null) AppIconImage(state.icons[app.key], 34.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Move ${app?.label ?: "app"} to…",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    PickerRow("New Folder “$suggested”", null, accent = true) { onNew(suggested) }
                }
                items(folders, key = { it.key }) { f ->
                    PickerRow(f.name, "${f.apps.size} apps", accent = false) { onPick(f.key) }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(title: String, subtitle: String?, accent: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = if (accent) IosBlue else Color.White,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) Text(subtitle, color = IosGray, fontSize = 13.sp)
    }
}
