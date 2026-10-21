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
