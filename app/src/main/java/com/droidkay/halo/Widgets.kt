package com.droidkay.halo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.SizeF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import java.time.format.TextStyle as JTextStyle

// ------------------------------------------------------------------ shared

private data class WidgetColors(val card: Color, val text: Color, val secondary: Color, val face: Color)

/** Widgets follow Settings → Look: light / dark / auto (phone's dark mode). */
@Composable
fun widgetDark(): Boolean = when (HaloSettings.widgetTheme) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}

/** The widget's card: Liquid Glass (dark smoke or bright frost) or a plain iOS card. */
@Composable
fun Modifier.widgetSurface(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val dark = widgetDark()
    return when {
        HaloSettings.liquidGlass && dark -> this.liquidGlass(shape, dark = true)
        HaloSettings.liquidGlass -> this.clip(shape).background(Color.White.copy(alpha = 0.72f)).liquidGlass(shape)
        dark -> this.clip(shape).background(Color(0xFF1C1C1E))
        else -> this.clip(shape).background(Color.White)
    }
}

@Composable
private fun widgetColors(): WidgetColors {
    val dark = widgetDark()
    return when {
        HaloSettings.liquidGlass && dark -> WidgetColors(Color.Transparent, Color.White, Color(0xFF9A9AA0), Color.White.copy(alpha = 0.07f))
        HaloSettings.liquidGlass -> WidgetColors(Color.Transparent, Color.Black, Color(0xFF6C6C70), Color.Black.copy(alpha = 0.05f))
        dark -> WidgetColors(Color(0xFF1C1C1E), Color.White, IosGray, Color(0xFF2C2C2E))
        else -> WidgetColors(Color.White, Color.Black, IosGray, Color(0xFFF2F2F7))
    }
}

@Composable
private fun WidgetCard(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .widgetSurface(RoundedCornerShape(24.dp))
            .padding(15.dp)
    ) { content() }
}

/** Current time, ticking every second or every minute, paused while the launcher is in the background. */
@Composable
fun rememberNow(everySecond: Boolean): LocalDateTime {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(initialValue = LocalDateTime.now(), everySecond, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val n = LocalDateTime.now()
                value = n
                val wait = if (everySecond) 1000L - (System.currentTimeMillis() % 1000L)
                else (60 - n.second) * 1000L - n.nano / 1_000_000
                delay(wait.coerceAtLeast(50L))
            }
        }
    }
    return now
}

@Composable
private fun Dp.asSp(): TextUnit = with(LocalDensity.current) { this@asSp.toSp() }

@Composable
fun BuiltInWidget(kind: WidgetKind, size: WidgetSize) {
    when (kind) {
        WidgetKind.CLOCK -> ClockWidget(size)
        WidgetKind.CALENDAR -> CalendarWidget(size)
        WidgetKind.BATTERY -> BatteryWidget(size)
        WidgetKind.GOOGLE -> GoogleWidget(size)
        WidgetKind.WEATHER -> WeatherWidget(size)
    }
}

// ------------------------------------------------------------------ clock

private fun polar(angle: Double, length: Float) = Offset((sin(angle) * length).toFloat(), (-cos(angle) * length).toFloat())

@Composable
fun AnalogClock(time: LocalDateTime, modifier: Modifier = Modifier) {
    val colors = widgetColors()
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(colors.face, r, c)
        for (i in 0 until 60) {
            val a = i * 6.0 * PI / 180.0
            val major = i % 5 == 0
            drawLine(
                colors.text.copy(alpha = if (major) 0.85f else 0.3f),
                c + polar(a, if (major) r * 0.86f else r * 0.9f),
                c + polar(a, r * 0.95f),
                strokeWidth = if (major) r * 0.03f else r * 0.012f,
            )
        }
        val numStyle = TextStyle(fontSize = (r * 0.19f).toSp(), fontWeight = FontWeight.SemiBold, color = colors.text)
        for (h in 1..12) {
            val pos = c + polar(h * 30.0 * PI / 180.0, r * 0.68f)
            val layout = measurer.measure(AnnotatedString(h.toString()), numStyle)
            drawText(layout, topLeft = Offset(pos.x - layout.size.width / 2f, pos.y - layout.size.height / 2f))
        }
        val hour = (time.hour % 12) + time.minute / 60.0
        val minute = time.minute + time.second / 60.0
        val hourA = hour * 30.0 * PI / 180.0
        val minA = minute * 6.0 * PI / 180.0
        val secA = time.second * 6.0 * PI / 180.0
        drawLine(colors.text, c, c + polar(hourA, r * 0.5f), strokeWidth = r * 0.075f, cap = StrokeCap.Round)
        drawLine(colors.text, c, c + polar(minA, r * 0.76f), strokeWidth = r * 0.05f, cap = StrokeCap.Round)
        drawLine(IosOrange, c - polar(secA, r * 0.15f), c + polar(secA, r * 0.84f), strokeWidth = r * 0.02f, cap = StrokeCap.Round)
        drawCircle(colors.text, r * 0.06f, c)
        drawCircle(IosOrange, r * 0.035f, c)
    }
}

@Composable
private fun ClockWidget(size: WidgetSize) {
    val context = LocalContext.current
    val colors = widgetColors()
    val now = rememberNow(everySecond = true)
    if (size == WidgetSize.SMALL) {
        Box(
            Modifier
                .fillMaxSize()
                .widgetSurface(RoundedCornerShape(24.dp))
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnalogClock(now, Modifier.fillMaxSize())
        }
        return
    }
    val is24 = DateFormat.is24HourFormat(context)
    val city = TimeZone.getDefault().id.substringAfterLast('/').replace('_', ' ')
    WidgetCard {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val h = maxHeight
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(city.uppercase(), color = IosOrange, fontSize = (h * 0.1f).asSp(), fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm")),
                            color = colors.text,
                            fontSize = (h * 0.36f).asSp(),
                            fontWeight = FontWeight.Light,
                            maxLines = 1,
                        )
                        if (!is24) {
                            Text(
                                now.format(DateTimeFormatter.ofPattern("a", Locale.getDefault())),
                                color = colors.secondary,
                                fontSize = (h * 0.12f).asSp(),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 4.dp, bottom = h * 0.07f),
                            )
                        }
                    }
                    Text(
                        now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                        color = colors.secondary,
                        fontSize = (h * 0.1f).asSp(),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
                AnalogClock(now, Modifier.fillMaxHeight().aspectRatio(1f))
            }
        }
    }
}

// ------------------------------------------------------------------ calendar

@Composable
private fun CalendarWidget(size: WidgetSize) {
    val colors = widgetColors()
    val today = rememberNow(everySecond = false).toLocalDate()
    WidgetCard {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val h = maxHeight
            when (size) {
                WidgetSize.SMALL -> DayFace(today, colors, h)
                WidgetSize.MEDIUM -> Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(0.42f).fillMaxHeight()) { DayFace(today, colors, h) }
                    MonthGrid(today, colors, Modifier.weight(0.58f).fillMaxHeight(), header = false, fontSize = (h * 0.085f).asSp(), circle = h * 0.13f)
                }
                WidgetSize.LARGE -> Column(Modifier.fillMaxSize()) {
                    Text(
                        today.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())).uppercase(),
                        color = IosRed,
                        fontSize = (h * 0.05f).asSp(),
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(h * 0.02f))
                    MonthGrid(today, colors, Modifier.weight(1f).fillMaxWidth(), header = true, fontSize = (h * 0.05f).asSp(), circle = h * 0.085f)
                }
            }
        }
    }
}

@Composable
private fun DayFace(today: LocalDate, colors: WidgetColors, h: Dp) {
    Column(Modifier.fillMaxSize()) {
        Text(
            today.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())).uppercase(),
            color = IosRed,
            fontSize = (h * 0.1f).asSp(),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            today.dayOfMonth.toString(),
            color = colors.text,
            fontSize = (h * 0.42f).asSp(),
            fontWeight = FontWeight.Light,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Text(
            today.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
            color = colors.secondary,
            fontSize = (h * 0.09f).asSp(),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun MonthGrid(today: LocalDate, colors: WidgetColors, modifier: Modifier, header: Boolean, fontSize: TextUnit, circle: Dp) {
    val locale = Locale.getDefault()
    val ym = YearMonth.from(today)
    val firstDow = WeekFields.of(locale).firstDayOfWeek
    val offset = (ym.atDay(1).dayOfWeek.value - firstDow.value + 7) % 7
    val days = ym.lengthOfMonth()
    val rows = (offset + days + 6) / 7
    Column(modifier) {
        if (!header) {
            Text(
                today.format(DateTimeFormatter.ofPattern("MMMM", locale)).uppercase(),
                color = IosRed,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                Text(
                    firstDow.plus(i.toLong()).getDisplayName(JTextStyle.NARROW, locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = colors.secondary,
                    fontSize = fontSize,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                for (c in 0 until 7) {
                    val day = r * 7 + c - offset + 1
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (day in 1..days) {
                            val isToday = day == today.dayOfMonth
                            Box(
                                Modifier
                                    .size(circle)
                                    .clip(CircleShape)
                                    .background(if (isToday) IosRed else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    day.toString(),
                                    color = if (isToday) Color.White else colors.text,
                                    fontSize = fontSize,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ battery

data class BatteryInfo(val percent: Int, val charging: Boolean)

@Composable
fun rememberBattery(): State<BatteryInfo> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(BatteryInfo(-1, false)) }
    DisposableEffect(Unit) {
        fun parse(i: Intent): BatteryInfo {
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            return BatteryInfo(if (level < 0) -1 else level * 100 / scale, charging)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                state.value = parse(intent)
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        sticky?.let { state.value = parse(it) }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return state
}

@Composable
private fun BatteryRing(info: BatteryInfo, modifier: Modifier, textSize: TextUnit) {
    val colors = widgetColors()
    val progress = (info.percent.coerceIn(0, 100)) / 100f
    val ringColor = if (!info.charging && info.percent in 0..20) IosRed else IosGreen
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.minDimension * 0.1f
            val d = size.minDimension - w
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            drawArc(colors.text.copy(alpha = 0.12f), 0f, 360f, false, topLeft, Size(d, d), style = Stroke(w))
            drawArc(ringColor, -90f, 360f * progress, false, topLeft, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (info.charging) Text("⚡", fontSize = textSize * 0.7f, color = ringColor)
            Text(
                if (info.percent < 0) "--" else "${info.percent}%",
                color = colors.text,
                fontSize = textSize,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun BatteryWidget(size: WidgetSize) {
    val colors = widgetColors()
    val info by rememberBattery()
    WidgetCard {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val h = maxHeight
            if (size == WidgetSize.SMALL) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    BatteryRing(info, Modifier.weight(1f).aspectRatio(1f), (h * 0.13f).asSp())
                    Text("This Phone", color = colors.secondary, fontSize = (h * 0.09f).asSp(), fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                }
            } else {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    BatteryRing(info, Modifier.fillMaxHeight().aspectRatio(1f), (h * 0.13f).asSp())
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        Text("This Phone", color = colors.secondary, fontSize = (h * 0.1f).asSp(), fontWeight = FontWeight.SemiBold)
                        Text(
                            if (info.percent < 0) "--" else "${info.percent}%",
                            color = colors.text,
                            fontSize = (h * 0.3f).asSp(),
                            fontWeight = FontWeight.Light,
                        )
                        Text(
                            if (info.charging) "Charging" else "On battery",
                            color = if (info.charging) IosGreen else colors.secondary,
                            fontSize = (h * 0.1f).asSp(),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ hosted Android widgets

private fun forwardTap(view: View, x: Float, y: Float) {
    val t = SystemClock.uptimeMillis()
    val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)
    view.dispatchTouchEvent(down)
    down.recycle()
    val up = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0)
    view.dispatchTouchEvent(up)
    up.recycle()
}

/**
 * Shows a real Android widget. A transparent layer on top passes taps through to the widget,
 * catches long-presses for the menu, and lets horizontal swipes turn the page.
 */
@Composable
fun HostedWidget(state: LauncherState, appWidgetId: Int, width: Dp, height: Dp, editMode: Boolean, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val hostView = remember(appWidgetId) { state.widgets.createView(context, appWidgetId) }
    val colors = widgetColors()
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
    ) {
        if (hostView == null) {
            Box(Modifier.fillMaxSize().background(colors.card), contentAlignment = Alignment.Center) {
                Text("Widget unavailable", color = colors.secondary, fontSize = 13.sp)
            }
        } else {
            AndroidView(
                factory = {
                    (hostView.parent as? ViewGroup)?.removeView(hostView)
                    hostView
                },
                update = { v ->
                    runCatching { v.updateAppWidgetSize(Bundle(), listOf(SizeF(width.value, height.value))) }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(editMode, hostView) {
                    if (editMode) return@pointerInput
                    detectTapGestures(
                        onTap = { pos -> hostView?.let { forwardTap(it, pos.x, pos.y) } },
                        onLongPress = { onLongPress() },
                    )
                }
        )
    }
}
