package com.droidkay.halo

import android.app.SearchManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** iOS-style continuous-corner icon shape (a superellipse). */
object SquircleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val a = size.width / 2f
        val b = size.height / 2f
        val n = 5.0
        val steps = 120
        for (i in 0..steps) {
            val t = 2.0 * PI * i / steps
            val c = cos(t)
            val s = sin(t)
            val x = a + a * sign(c).toFloat() * abs(c).pow(2.0 / n).toFloat()
            val y = b + b * sign(s).toFloat() * abs(s).pow(2.0 / n).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

val IosRed = Color(0xFFFF3B30)
val IosGreen = Color(0xFF34C759)
val IosOrange = Color(0xFFFF9500)
val IosBlue = Color(0xFF0A84FF)
val IosGray = Color(0xFF8E8E93)

private val LabelShadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 1.5f), 6f)

fun labelStyle(size: TextUnit = 12.sp): TextStyle =
    TextStyle(color = Color.White, fontSize = size, fontWeight = FontWeight.Medium, shadow = LabelShadow)

@Composable
fun AppIconImage(icon: ImageBitmap?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(SquircleShape)
            .background(Color.White.copy(alpha = 0.18f))
    ) {
        if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

/** An icon that reports its on-screen bounds on tap and long-press (used for the open animation and menus). */
@Composable
fun TappableIcon(
    icon: ImageBitmap?,
    size: Dp,
    enabled: Boolean = true,
    onBounds: ((Rect) -> Unit)? = null,
    onTap: (Rect) -> Unit,
    onLongPress: (Rect) -> Unit,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val haptic = LocalHapticFeedback.current
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    AppIconImage(
        icon,
        size,
        Modifier
            .onGloballyPositioned {
                bounds = it.boundsInRoot()
                onBounds?.invoke(bounds)
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onTap = { tap(bounds) },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        longPress(bounds)
                    },
                )
            }
    )
}

/** The wobble icons do in edit mode. */
@Composable
fun Modifier.jiggle(enabled: Boolean, seed: Int): Modifier {
    if (!enabled) return this
    val transition = rememberInfiniteTransition(label = "jiggle")
    val duration = 120 + (abs(seed) % 5) * 14
    val rotation by transition.animateFloat(
        initialValue = -1.8f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(tween(duration, easing = LinearEasing), RepeatMode.Reverse),
        label = "rotation",
    )
    return this.graphicsLayer { rotationZ = rotation }
}

@Composable
fun MinusBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color(0xEED1D1D6))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(10.dp)
                .height(2.dp)
                .background(Color.Black)
        )
    }
}

@Composable
fun MagnifierIcon(color: Color = Color.White, size: Dp = 14.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * 0.14f
        drawCircle(color, radius = s * 0.32f, center = Offset(s * 0.42f, s * 0.42f), style = Stroke(stroke))
        drawLine(color, Offset(s * 0.66f, s * 0.66f), Offset(s * 0.92f, s * 0.92f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun GlassPill(text: String, bold: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .then(
                if (BuildConfig.IS_PAD) Modifier.liquidGlass(RoundedCornerShape(50))
                else Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.28f))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

// ------------------------------------------------------------------ intents

fun Context.safeStart(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: Exception) {
    false
}

fun webSearch(context: Context, query: String) {
    val ok = context.safeStart(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query))
    if (!ok) context.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
}

fun playStoreSearch(context: Context, query: String) {
    val ok = context.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(query))))
    if (!ok) context.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=" + Uri.encode(query))))
}

fun openWallpaperPicker(context: Context) {
    context.safeStart(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Choose wallpaper"))
}

fun openHomeSettings(context: Context) {
    context.safeStart(Intent(Settings.ACTION_HOME_SETTINGS))
}

fun openBuiltInWidget(context: Context, kind: WidgetKind) {
    when (kind) {
        WidgetKind.CLOCK -> context.safeStart(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        WidgetKind.CALENDAR -> {
            val builder = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
            ContentUris.appendId(builder, System.currentTimeMillis())
            context.safeStart(Intent(Intent.ACTION_VIEW).setData(builder.build()))
        }
        WidgetKind.BATTERY -> context.safeStart(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
        WidgetKind.GOOGLE -> onGoogleWidgetTap(context, WidgetSize.SMALL, 0f, 0f)
        WidgetKind.WEATHER -> openWeatherApp(context)
    }
}
