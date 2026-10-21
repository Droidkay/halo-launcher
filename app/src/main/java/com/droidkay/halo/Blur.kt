package com.droidkay.halo

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** True when the system can blur the wallpaper behind Halo (off in battery saver / "Reduce transparency"). */
val LocalWallpaperBlur = compositionLocalOf { false }

/** Overlay tint: light when the wallpaper is blurred (iOS glass), darker when blur isn't available. */
@Composable
fun glassScrim(blurred: Float = 0.18f, fallback: Float = 0.6f): Color =
    Color.Black.copy(alpha = if (LocalWallpaperBlur.current) blurred else fallback)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun rememberWallpaperBlurSupported(): Boolean {
    val activity = LocalContext.current.findActivity()
    return remember(activity) {
        runCatching { activity?.windowManager?.isCrossWindowBlurEnabled == true }.getOrDefault(false)
    }
}

/**
 * Blurs the wallpaper behind the launcher window (Android 12+ cross-window blur),
 * easing the radius in and out like iOS.
 */
@Composable
fun WallpaperBlurEffect(active: Boolean, maxRadiusPx: Int = 90) {
    val activity = LocalContext.current.findActivity() ?: return
    val radius = remember { Animatable(0f) }
    LaunchedEffect(active) {
        val window = activity.window
        if (active) window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        radius.animateTo(if (active) maxRadiusPx.toFloat() else 0f, tween(260)) {
            val attrs = window.attributes
            attrs.blurBehindRadius = value.toInt()
            window.attributes = attrs
        }
        if (!active) window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    }
}

/** Sets the wallpaper blur behind Halo's window directly (0 = off). */
@Composable
fun WindowBlur(radiusPx: Int) {
    val activity = LocalContext.current.findActivity() ?: return
    androidx.compose.runtime.LaunchedEffect(radiusPx) {
        val window = activity.window
        if (radiusPx > 0) window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        val attrs = window.attributes
        attrs.blurBehindRadius = radiusPx
        window.attributes = attrs
        if (radiusPx <= 0) window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    }
}

/**
 * 0..1 blur driven by the Fold's hinge: on the cover screen it grows as you start opening the phone,
 * on the inner screen it grows as you start closing it. Only listens while Halo is on screen.
 */
@Composable
fun rememberHingeBlur(mode: LayoutMode): Float {
    val context = LocalContext.current
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    val fraction = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    androidx.compose.runtime.DisposableEffect(mode, lifecycle) {
        val sm = context.getSystemService(android.hardware.SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_HINGE_ANGLE)
        if (sm == null || sensor == null) return@DisposableEffect onDispose { }
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                val angle = e.values[0]
                fraction.floatValue = if (mode == LayoutMode.COVER) ((angle - 8f) / 50f).coerceIn(0f, 1f)
                else ((70f - angle) / 50f).coerceIn(0f, 1f)
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, accuracy: Int) {}
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START ->
                    sm.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    sm.unregisterListener(listener)
                    fraction.floatValue = 0f
                }
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            sm.unregisterListener(listener)
        }
    }
    return fraction.floatValue
}
