package com.droidkay.halo

import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.PixelCopy
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.abs

/**
 * Keeps one picture of each layout (cover / inner), taken the moment the hinge starts to move.
 * When the screen then switches, that picture is the "outgoing" layout of the morph.
 */
object FoldSnapshots {
    private class Snap(val bitmap: Bitmap, val at: Long)

    private val slots = HashMap<LayoutMode, Snap>()

    /** True while a morph is playing, so nothing photographs the overlay itself. */
    @Volatile
    var morphing = false

    fun capture(view: View, mode: LayoutMode) {
        if (morphing) return
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0) return
        val window = view.context.findActivityOrNull()?.window ?: return
        try {
            val loc = IntArray(2)
            view.getLocationInWindow(loc)
            val bmp = Bitmap.createBitmap((w / 2).coerceAtLeast(1), (h / 2).coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val src = Rect(loc[0], loc[1], loc[0] + w, loc[1] + h)
            PixelCopy.request(window, src, bmp, { result ->
                if (result == PixelCopy.SUCCESS) {
                    slots[mode] = Snap(bmp, SystemClock.elapsedRealtime())
                }
            }, Handler(Looper.getMainLooper()))
        } catch (_: Throwable) {
            // No snapshot: the screen simply switches with the normal frost transition.
        }
    }

    /** The picture of the layout we're leaving, if it's fresh. */
    fun take(newMode: LayoutMode): ImageBitmap? {
        val other = if (newMode == LayoutMode.COVER) LayoutMode.INNER else LayoutMode.COVER
        val snap = slots[other] ?: return null
        val fresh = SystemClock.elapsedRealtime() - snap.at < 20_000L
        return if (fresh) snap.bitmap.asImageBitmap() else null
    }
}

/** Photographs the current layout when the hinge starts to move (not on every tiny change). */
@Composable
private fun FoldSnapshotter(mode: LayoutMode) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentMode by rememberUpdatedState(mode)
    DisposableEffect(lifecycle, view) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        if (sm == null || sensor == null) return@DisposableEffect onDispose { }
        var lastAngle = -1f
        var lastMoveAt = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val angle = e.values[0]
                val moved = lastAngle >= 0f && abs(angle - lastAngle) > 1.0f
                lastAngle = angle
                if (!moved) return
                val now = SystemClock.elapsedRealtime()
                // A new gesture = the hinge was still for a moment before this movement.
                if (now - lastMoveAt > 1200L) FoldSnapshots.capture(view, currentMode)
                lastMoveAt = now
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    lastAngle = -1f
                    sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                }
                Lifecycle.Event.ON_STOP -> sm.unregisterListener(listener)
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            sm.unregisterListener(listener)
        }
    }
}

/**
 * Apple "Duo" fold morph. When the Fold opens or closes, the layout you were looking at
 * stretches toward the new screen shape while melting into frosted glass, and the new layout
 * resolves out of the frost underneath it. The hinge-driven half-panel frost in HomeScreen keeps
 * running on top of this.
 */
@Composable
fun FoldMorphHost(mode: LayoutMode, enabled: Boolean = true, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    val view = LocalView.current
    FoldSnapshotter(mode)

    // Evaluated once per mode change, during that very composition, so the new layout never flashes un-frosted.
    val outgoing = remember(mode) { FoldSnapshots.take(mode) }
    val progress = remember(mode) { Animatable(if (outgoing != null) 0f else 1f) }
    var showOutgoing by remember(mode) { mutableStateOf(outgoing != null) }

    LaunchedEffect(mode) {
        if (outgoing == null) return@LaunchedEffect
        FoldSnapshots.morphing = true
        try {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            progress.animateTo(1f, tween(760, easing = CubicBezierEasing(0.22f, 0.85f, 0.2f, 1f)))
        } finally {
            FoldSnapshots.morphing = false
            showOutgoing = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value
                    val e = smooth(p)
                    alpha = if (outgoing == null) 1f else 0.1f + 0.9f * smooth((p - 0.12f) / 0.7f)
                    val s = 0.93f + 0.07f * e
                    scaleX = s
                    scaleY = s
                    val r = (1f - e) * 26.dp.toPx()
                    renderEffect = if (outgoing != null && r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
                }
        ) { content() }

        val snap = outgoing
        if (snap != null && showOutgoing) {
            Image(
                bitmap = snap,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progress.value
                        val e = smooth(p)
                        alpha = 1f - smooth(p * 1.6f)
                        val sc = 1f + 0.10f * e
                        scaleX = sc
                        scaleY = sc
                        val r = e * 30.dp.toPx()
                        renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
                    },
            )
        }
    }
}
