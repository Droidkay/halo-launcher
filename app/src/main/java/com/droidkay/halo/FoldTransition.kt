package com.droidkay.halo

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The fold transition's two signals, both 0 (sharp, settled) .. 1 (fully in the frosted-glass blur).
 *  - [progress]: drives the interface itself — blur, scale, opacity, parallax. Slightly springy.
 *  - [depth]: drives the wallpaper blur and dimming. Critically damped and a touch slower, so the
 *    background trails the foreground like a camera's depth of field.
 * Values can briefly overshoot below 0 at the end of a spring; callers clamp what must stay in range.
 */
class FoldMotion(val progress: State<Float>, val depth: State<Float>)

/** Hermite smoothstep: soft start, soft finish, no sudden edges. */
fun smooth(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** How "mid-fold" the hinge angle is for the screen currently showing. */
private fun foldAmount(mode: LayoutMode, angle: Float): Float =
    if (mode == LayoutMode.COVER) smooth((angle - 4f) / 56f)   // cover: 4°→60° opening
    else smooth((168f - angle) / 108f)                          // inner: 168°→60° closing

@Composable
fun rememberFoldMotion(mode: LayoutMode): FoldMotion {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()

    // Targets. The entry target starts at 1 so a freshly-shown screen begins inside the blur.
    val hingeTarget = remember { mutableFloatStateOf(0f) }
    val entryTarget = remember { mutableFloatStateOf(1f) }
    val progress = remember { Animatable(1f) }
    val depth = remember { Animatable(1f) }

    // Release the entry blur once the first frame is on screen, so the reveal is never cut short.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        delay(60)
        entryTarget.floatValue = 0f
    }

    // Springs chase the target and are retargeted mid-flight, keeping their velocity:
    // motion stays continuous however fast or slowly the hinge moves.
    LaunchedEffect(Unit) {
        snapshotFlow { maxOf(hingeTarget.floatValue, entryTarget.floatValue) }
            .distinctUntilChanged()
            .collectLatest { target ->
                coroutineScope {
                    launch { depth.animateTo(target, spring(dampingRatio = 1f, stiffness = 120f)) }
                    progress.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 210f))
                }
            }
    }

    // Hinge sensor: blur follows the angle while the phone is moving, and settles to perfect
    // clarity once it stops (so Flex Mode and resting half-open never stay blurred).
    DisposableEffect(mode, lifecycle) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        if (sm == null || sensor == null) return@DisposableEffect onDispose { }
        var last = -1f
        var settle: Job? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val angle = e.values[0]
                val moving = last >= 0f && abs(angle - last) > 0.5f
                last = angle
                val amount = foldAmount(mode, angle)
                if (amount <= 0f) {
                    settle?.cancel()
                    hingeTarget.floatValue = 0f
                } else if (moving) {
                    hingeTarget.floatValue = amount
                    settle?.cancel()
                    settle = scope.launch {
                        delay(450)
                        hingeTarget.floatValue = 0f
                    }
                }
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    last = -1f
                    sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                }
                Lifecycle.Event.ON_STOP -> {
                    sm.unregisterListener(listener)
                    settle?.cancel()
                    hingeTarget.floatValue = 0f
                }
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            sm.unregisterListener(listener)
            settle?.cancel()
        }
    }

    return remember(progress, depth) { FoldMotion(progress.asState(), depth.asState()) }
}

/**
 * Keeps the system wallpaper blur in step with [radiusPx] without recomposing the UI.
 * Updates are stepped so the window isn't re-laid-out on every frame (keeps motion at full refresh rate).
 */
@Composable
fun WallpaperBlurDriver(enabled: Boolean, radiusPx: () -> Float) {
    if (!enabled) return
    val activity = LocalContext.current.findActivityOrNull() ?: return
    LaunchedEffect(activity) {
        snapshotFlow { (radiusPx() / 6f).toInt() * 6 }
            .distinctUntilChanged()
            .collect { r ->
                val window = activity.window
                if (r > 0) window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val attrs = window.attributes
                attrs.blurBehindRadius = r.coerceAtLeast(0)
                window.attributes = attrs
                if (r <= 0) window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
    }
}
