package com.droidkay.halo

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** Where a dragged item came from / where it would land. */
sealed class DragSpot {
    data class Page(val index: Int) : DragSpot()
    object Dock : DragSpot()
}

/** Grid geometry of a home page in root coordinates, so a drag can work out which cell it is over. */
data class GridGeo(val origin: Offset, val cellW: Float, val cellH: Float, val cols: Int, val rows: Int)

/** How the dragged item looked where it was picked up, so the floating copy matches it. */
data class GhostLook(
    val size: IntSize,
    val iconSize: Dp,
    val frameW: Dp,
    val frameH: Dp,
    val labelWidth: Dp?,
)

/**
 * iOS-style drag and drop for the home screen. One item at a time floats under the finger
 * (drawn by HomeRoot); pages and the dock show a live preview of where it would land, and the
 * real layout only changes when it is dropped.
 */
class DragController {
    var item by mutableStateOf<HomeItem?>(null)
        private set
    var source by mutableStateOf<DragSpot?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
    var grab = Offset.Zero
        private set
    var look by mutableStateOf<GhostLook?>(null)
        private set

    var target by mutableStateOf<DragSpot?>(null)
    var pagePreview by mutableStateOf<List<HomeItem>>(emptyList())
    var dockPreview by mutableStateOf<List<String>>(emptyList())
    var lastSwap: String? = null

    /** Shown past the last page while dragging there, iOS-style, so an item can start a new page. */
    var extraPage by mutableStateOf(false)

    val grids = HashMap<Int, GridGeo>()
    var dockRect = Rect.Zero
    var dockVertical = false
    var pagerRect = Rect.Zero

    /** Set by HomeRoot each composition: works out the drop target for the current pointer. */
    var onHover: () -> Unit = {}
    var onDrop: () -> Unit = {}

    val active: Boolean get() = item != null
    val key: String? get() = item?.key

    fun start(item: HomeItem, from: DragSpot, grab: Offset, at: Offset, look: GhostLook) {
        this.item = item
        this.source = from
        this.grab = grab
        this.look = look
        this.pointer = at
        target = null
        lastSwap = null
        onHover()
    }

    fun move(at: Offset) {
        if (!active) return
        pointer = at
        onHover()
    }

    fun drop() {
        if (!active) return
        try {
            onDrop()
        } finally {
            item = null
            source = null
            target = null
            look = null
            pagePreview = emptyList()
            dockPreview = emptyList()
            extraPage = false
            lastSwap = null
        }
    }
}

/**
 * One gesture handler per home item, replacing the separate tap / long-press / drag detectors so
 * they can't fight each other:
 *  - normal mode: tap opens, long-press opens the menu, long-press then move picks the item up
 *    (and switches on jiggle mode), a plain swipe is left to the pager.
 *  - jiggle mode: tap = [onEditTap], any drag picks the item up straight away.
 * [pickUpWithoutEdit] = false leaves normal-mode touches alone (widgets handle their own taps).
 */
@Composable
fun Modifier.homeGestures(
    key: Any,
    editMode: Boolean,
    pickUpWithoutEdit: Boolean,
    onTap: () -> Unit,
    onEditTap: () -> Unit,
    onLongPress: () -> Unit,
    onPickUp: (grab: Offset, at: Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onDrop: () -> Unit,
): Modifier {
    val haptic = LocalHapticFeedback.current
    val edit by rememberUpdatedState(editMode)
    val pickNormal by rememberUpdatedState(pickUpWithoutEdit)
    val tap by rememberUpdatedState(onTap)
    val editTap by rememberUpdatedState(onEditTap)
    val longPress by rememberUpdatedState(onLongPress)
    val pickUp by rememberUpdatedState(onPickUp)
    val move by rememberUpdatedState(onMove)
    val drop by rememberUpdatedState(onDrop)
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun toRoot(p: Offset): Offset = coords[0]?.takeIf { it.isAttached }?.localToRoot(p) ?: p

    return this
        .onGloballyPositioned { coords[0] = it }
        .pointerInput(key) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val inEdit = edit
                if (!inEdit && !pickNormal) return@awaitEachGesture
                // Claim the touch so the page's own long-press (which switches on jiggle mode) stays out of it.
                down.consume()
                val slop = viewConfiguration.touchSlop

                // Phase 1: tap, swipe or (outside jiggle mode) long-press.
                // 0 = long-press, 1 = tap, 2 = moved, 3 = cancelled
                var outcome = 3
                var last = down.position
                val done = withTimeoutOrNull(if (inEdit) Long.MAX_VALUE / 4 else viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id }
                        if (c == null) {
                            outcome = 3
                            break
                        }
                        if (c.changedToUp() || !c.pressed) {
                            outcome = if (c.isConsumed) 3 else 1
                            if (outcome == 1) c.consume()
                            break
                        }
                        if (c.isConsumed) {
                            outcome = 3
                            break
                        }
                        if ((c.position - down.position).getDistance() > slop) {
                            last = c.position
                            outcome = 2
                            if (inEdit) c.consume()
                            break
                        }
                    }
                    true
                }
                if (done == null) outcome = 0

                var picked = false
                when {
                    outcome == 1 -> if (inEdit) editTap() else tap()
                    inEdit && outcome == 2 -> picked = true
                    !inEdit && outcome == 0 -> {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        longPress()
                        // Phase 2: keep holding and move = pick it up (closes the menu).
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (c.changedToUp() || !c.pressed) {
                                c.consume()
                                break
                            }
                            c.consume()
                            if ((c.position - down.position).getDistance() > slop * 1.5f) {
                                last = c.position
                                picked = true
                                break
                            }
                        }
                    }
                }
                if (!picked) return@awaitEachGesture

                // Phase 3: dragging.
                pickUp(down.position, toRoot(last))
                try {
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (c.changedToUp() || !c.pressed) {
                            c.consume()
                            break
                        }
                        c.consume()
                        move(toRoot(c.position))
                    }
                } finally {
                    drop()
                }
            }
        }
}

/** Default spacing used to decide that the finger is "at the edge" of the pager. */
val DragEdge: Dp = 36.dp
