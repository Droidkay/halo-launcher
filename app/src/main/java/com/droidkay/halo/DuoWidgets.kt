package com.droidkay.halo

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ================================================================== Google widget

private const val GSA = "com.google.android.googlequicksearchbox"
private val GoogleBlue = Color(0xFF4285F4)
private val GoogleRed = Color(0xFFEA4335)
private val GoogleYellow = Color(0xFFFBBC05)
private val GoogleGreen = Color(0xFF34A853)

@Composable
private fun Dp.dsp(): TextUnit = with(LocalDensity.current) { this@dsp.toSp() }

private data class DuoColors(val card: Color, val tile: Color, val glyph: Color, val text: Color)

@Composable
private fun duoColors(): DuoColors =
    if (widgetDark()) DuoColors(Color(0xFF1C1C1E), Color.White.copy(alpha = 0.09f), Color(0xFFE8EAED), Color.White)
    else DuoColors(Color(0xFFF7F3F4), Color.White.copy(alpha = 0.9f), Color(0xFF3C4043), Color.Black)

@Composable
fun GoogleWidget(size: WidgetSize) {
    val c = duoColors()
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .widgetSurface(RoundedCornerShape(24.dp))
            .padding(10.dp)
    ) {
        val h = maxHeight
        val gap = 8.dp
        if (size == WidgetSize.SMALL) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    GTile(c, Modifier.weight(1f)) { GoogleG(it) }
                    GTile(c, Modifier.weight(1f)) { AiModeGlyph(it, c.glyph) }
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    GTile(c, Modifier.weight(1f)) { MicGlyph(it, c.glyph) }
                    GTile(c, Modifier.weight(1f)) { LensGlyph(it, c.glyph) }
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(c.tile)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(h * 0.2f)) { GoogleG(this.size.minDimension) }
                    Spacer(Modifier.width(10.dp))
                    Text("Search", color = c.glyph.copy(alpha = 0.7f), fontSize = (h * 0.12f).dsp(), fontWeight = FontWeight.Medium)
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    GTile(c, Modifier.weight(1f), pill = true) { MicGlyph(it, c.glyph) }
                    GTile(c, Modifier.weight(1f), pill = true) { LensGlyph(it, c.glyph) }
                    GTile(c, Modifier.weight(1f), pill = true) { AiModeGlyph(it, c.glyph) }
                }
            }
        }
    }
}

@Composable
private fun GTile(c: DuoColors, modifier: Modifier, pill: Boolean = false, glyph: DrawScope.(Float) -> Unit) {
    Box(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(if (pill) 50 else 30))
            .background(c.tile),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize(0.42f)) { glyph(this.size.minDimension) }
    }
}

/** The four-colour Google "G", drawn in a square of side [s] centred in the draw area. */
private fun DrawScope.GoogleG(s: Float) {
    val stroke = s * 0.2f
    val d = s - stroke
    val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
    val arc = Size(d, d)
    val st = Stroke(stroke, cap = StrokeCap.Butt)
    drawArc(GoogleBlue, -8f, 58f, false, tl, arc, style = st)
    drawArc(GoogleGreen, 50f, 92f, false, tl, arc, style = st)
    drawArc(GoogleYellow, 142f, 70f, false, tl, arc, style = st)
    drawArc(GoogleRed, 212f, 105f, false, tl, arc, style = st)
    val cy = size.height / 2f
    drawRect(GoogleBlue, Offset(size.width / 2f, cy - stroke / 2f), Size(d / 2f + stroke / 2f, stroke))
}

private fun DrawScope.MicGlyph(s: Float, color: Color) {
    val cx = size.width / 2f
    val top = (size.height - s) / 2f
    val w = s * 0.36f
    drawRoundRect(color, Offset(cx - w / 2f, top), Size(w, s * 0.6f), CornerRadius(w / 2f))
    val stroke = s * 0.09f
    val r = s * 0.32f
    drawArc(
        color, 0f, 180f, false,
        Offset(cx - r, top + s * 0.6f - r - s * 0.02f), Size(r * 2, r * 2),
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
    drawLine(color, Offset(cx, top + s * 0.88f), Offset(cx, top + s), strokeWidth = stroke, cap = StrokeCap.Round)
}

private fun DrawScope.LensGlyph(s: Float, color: Color) {
    val l = (size.width - s) / 2f
    val t = (size.height - s) / 2f
    val stroke = s * 0.11f
    val st = Stroke(stroke, cap = StrokeCap.Round)
    val k = s * 0.32f
    // Three rounded corners of the Lens frame; the fourth is the solid dot.
    drawArc(color, 180f, 90f, false, Offset(l, t), Size(k * 2, k * 2), style = st)
    drawLine(color, Offset(l, t + k), Offset(l, t + s * 0.55f), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, Offset(l + k, t), Offset(l + s * 0.55f, t), strokeWidth = stroke, cap = StrokeCap.Round)
    drawArc(color, 270f, 90f, false, Offset(l + s - k * 2, t), Size(k * 2, k * 2), style = st)
    drawArc(color, 90f, 90f, false, Offset(l, t + s - k * 2), Size(k * 2, k * 2), style = st)
    drawLine(color, Offset(l + s, t + k), Offset(l + s, t + s * 0.45f), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, Offset(l + k, t + s), Offset(l + s * 0.45f, t + s), strokeWidth = stroke, cap = StrokeCap.Round)
    drawCircle(color, s * 0.17f, Offset(l + s / 2f, t + s / 2f), style = Stroke(stroke))
    drawCircle(color, s * 0.1f, Offset(l + s * 0.84f, t + s * 0.84f))
}

private fun DrawScope.AiModeGlyph(s: Float, color: Color) {
    val l = (size.width - s) / 2f
    val t = (size.height - s) / 2f
    val stroke = s * 0.11f
    drawCircle(color, s * 0.28f, Offset(l + s * 0.4f, t + s * 0.45f), style = Stroke(stroke))
    drawLine(color, Offset(l + s * 0.6f, t + s * 0.66f), Offset(l + s * 0.9f, t + s * 0.96f), strokeWidth = stroke, cap = StrokeCap.Round)
    sparkle(Offset(l + s * 0.86f, t + s * 0.14f), s * 0.17f, GoogleBlue)
}

private fun DrawScope.sparkle(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticBezierTo(c.x, c.y, c.x + r, c.y)
        quadraticBezierTo(c.x, c.y, c.x, c.y + r)
        quadraticBezierTo(c.x, c.y, c.x - r, c.y)
        quadraticBezierTo(c.x, c.y, c.x, c.y - r)
        close()
    }
    drawPath(p, color)
}

/** Tap on the Google widget, [fx]/[fy] being the tap position as a fraction of the widget's size. */
fun onGoogleWidgetTap(context: Context, size: WidgetSize, fx: Float, fy: Float) {
    val zone = if (size == WidgetSize.SMALL) {
        when {
            fy < 0.5f && fx < 0.5f -> "search"
            fy < 0.5f -> "ai"
            fx < 0.5f -> "voice"
            else -> "lens"
        }
    } else {
        when {
            fy < 0.5f -> "search"
            fx < 0.34f -> "voice"
            fx < 0.67f -> "lens"
            else -> "ai"
        }
    }
    when (zone) {
        "search" -> {
            val ok = context.safeStart(Intent("android.search.action.GLOBAL_SEARCH").setPackage(GSA)) ||
                context.safeStart(context.packageManager.getLaunchIntentForPackage(GSA) ?: Intent())
            if (!ok) context.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
        }
        "voice" -> {
            val ok = context.safeStart(Intent("android.speech.action.WEB_SEARCH").setPackage(GSA)) ||
                context.safeStart(Intent(Intent.ACTION_VOICE_COMMAND))
            if (!ok) context.safeStart(Intent("android.speech.action.WEB_SEARCH"))
        }
        "lens" -> {
            val ok = context.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://lens")).setPackage(GSA))
            if (!ok) context.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://lens.google.com")))
        }
        else -> {
            val uri = Uri.parse("https://www.google.com/search?udm=50")
            if (!context.safeStart(Intent(Intent.ACTION_VIEW, uri).setPackage(GSA))) {
                context.safeStart(Intent(Intent.ACTION_VIEW, uri))
            }
        }
    }
}

// ================================================================== Weather data

data class HourCast(val label: String, val code: Int, val isDay: Boolean, val temp: Int)

data class WeatherData(
    val city: String,
    val temp: Int,
    val code: Int,
    val isDay: Boolean,
    val hi: Int,
    val lo: Int,
    val hours: List<HourCast>,
    val fetchedAt: Long,
)

/** Current weather from Open-Meteo (no API key), for the phone's coarse location. Cached for 30 minutes. */
object WeatherRepo {
    var data by mutableStateOf<WeatherData?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    private var loading = false
    private var cacheRead = false

    /** Location is asked for once (first tap); after that a tap opens the weather app. */
    fun shouldAskLocation(context: Context): Boolean =
        !hasPermission(context) && !prefs(context).getBoolean("asked", false)

    fun markAsked(context: Context) {
        prefs(context).edit().putBoolean("asked", true).apply()
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun prefs(context: Context) = context.getSharedPreferences("halo_weather", Context.MODE_PRIVATE)

    private fun readCache(context: Context) {
        if (cacheRead) return
        cacheRead = true
        prefs(context).getString("data", null)?.let { raw -> data = runCatching { decode(JSONObject(raw)) }.getOrNull() }
    }

    suspend fun refresh(context: Context, force: Boolean = false) {
        readCache(context)
        if (loading) return
        val d = data
        if (!force && d != null && System.currentTimeMillis() - d.fetchedAt < 30 * 60_000L) return
        loading = true
        try {
            val fresh = withContext(Dispatchers.IO) { fetch(context.applicationContext) }
            if (fresh != null) {
                data = fresh
                failed = false
                prefs(context).edit().putString("data", encode(fresh).toString()).apply()
            } else {
                failed = data == null
            }
        } finally {
            loading = false
        }
    }

    private fun useFahrenheit(): Boolean = Locale.getDefault().country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")

    private suspend fun fetch(context: Context): WeatherData? {
        val p = prefs(context)
        val loc = if (hasPermission(context)) location(context) else null
        val lat: Double
        val lon: Double
        var place: String? = null
        if (loc != null) {
            lat = loc.latitude
            lon = loc.longitude
            p.edit().putFloat("lat", lat.toFloat()).putFloat("lon", lon.toFloat()).apply()
        } else if (p.contains("lat")) {
            lat = p.getFloat("lat", 0f).toDouble()
            lon = p.getFloat("lon", 0f).toDouble()
        } else {
            // No location (yet): use the city of the phone's time zone, e.g. Africa/Lagos → Lagos.
            val city = TimeZone.getDefault().id.substringAfterLast('/').replace('_', ' ')
            val geo = runCatching {
                val conn = URL("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + java.net.URLEncoder.encode(city, "UTF-8"))
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                try {
                    JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getJSONArray("results").getJSONObject(0)
                } finally {
                    conn.disconnect()
                }
            }.getOrNull() ?: return null
            lat = geo.getDouble("latitude")
            lon = geo.getDouble("longitude")
            place = geo.optString("name", city)
        }

        val f = useFahrenheit()
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,is_day" +
            "&hourly=temperature_2m,weather_code,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=2" + (if (f) "&temperature_unit=fahrenheit" else "")
        val json = runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            try {
                JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            } finally {
                conn.disconnect()
            }
        }.getOrNull() ?: return null

        return runCatching {
            val cur = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            val hourly = json.getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val curTime = cur.getString("time").take(13) // yyyy-MM-ddTHH
            var start = 0
            for (i in 0 until times.length()) {
                if (times.getString(i).take(13) >= curTime) { start = i; break }
            }
            val is24 = DateFormat.is24HourFormat(context)
            val fmt = DateTimeFormatter.ofPattern(if (is24) "HH" else "ha", Locale.getDefault())
            val temps = hourly.getJSONArray("temperature_2m")
            val codes = hourly.getJSONArray("weather_code")
            val days = hourly.getJSONArray("is_day")
            val hours = (start until minOf(start + 6, times.length())).map { i ->
                HourCast(
                    label = if (i == start) "Now" else LocalDateTime.parse(times.getString(i)).format(fmt),
                    code = codes.getInt(i),
                    isDay = days.getInt(i) == 1,
                    temp = temps.getDouble(i).roundToInt(),
                )
            }
            WeatherData(
                city = place ?: cityName(context, lat, lon),
                temp = cur.getDouble("temperature_2m").roundToInt(),
                code = cur.getInt("weather_code"),
                isDay = cur.getInt("is_day") == 1,
                hi = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                lo = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
                hours = hours,
                fetchedAt = System.currentTimeMillis(),
            )
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun cityName(context: Context, lat: Double, lon: Double): String {
        val fallback = TimeZone.getDefault().id.substringAfterLast('/').replace('_', ' ')
        return runCatching {
            val a = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
            a?.locality ?: a?.subAdminArea ?: a?.adminArea
        }.getOrNull() ?: fallback
    }

    @SuppressLint("MissingPermission")
    private suspend fun location(context: Context): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 3 * 3600_000L) return last
        val provider = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { it in providers }
            ?: return last
        val current = withTimeoutOrNull(15_000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                runCatching {
                    lm.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(context)) { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
        return current ?: last
    }

    private fun encode(d: WeatherData) = JSONObject().apply {
        put("city", d.city); put("temp", d.temp); put("code", d.code); put("day", d.isDay)
        put("hi", d.hi); put("lo", d.lo); put("at", d.fetchedAt)
        put("hours", JSONArray().apply {
            d.hours.forEach { h -> put(JSONObject().apply { put("l", h.label); put("c", h.code); put("d", h.isDay); put("t", h.temp) }) }
        })
    }

    private fun decode(o: JSONObject): WeatherData {
        val arr = o.optJSONArray("hours") ?: JSONArray()
        return WeatherData(
            city = o.getString("city"),
            temp = o.getInt("temp"),
            code = o.getInt("code"),
            isDay = o.getBoolean("day"),
            hi = o.getInt("hi"),
            lo = o.getInt("lo"),
            hours = (0 until arr.length()).map { i ->
                val h = arr.getJSONObject(i)
                HourCast(h.getString("l"), h.getInt("c"), h.getBoolean("d"), h.getInt("t"))
            },
            fetchedAt = o.getLong("at"),
        )
    }
}

fun weatherCondition(code: Int, isDay: Boolean): String = when (code) {
    0 -> if (isDay) "Sunny" else "Clear"
    1 -> if (isDay) "Mostly Sunny" else "Mostly Clear"
    2 -> "Partly Cloudy"
    3 -> "Cloudy"
    45, 48 -> "Foggy"
    51, 53, 55, 56, 57 -> "Drizzle"
    61, 63, 66 -> "Rain"
    65, 67 -> "Heavy Rain"
    71, 73, 75, 77 -> "Snow"
    80, 81 -> "Showers"
    82 -> "Heavy Showers"
    85, 86 -> "Snow Showers"
    95, 96, 99 -> "Thunderstorms"
    else -> "—"
}

fun openWeatherApp(context: Context) {
    val pm = context.packageManager
    val pkgs = listOf("com.sec.android.daemonapp", "com.google.android.apps.weather", "com.miui.weather2")
    for (p in pkgs) {
        val i = pm.getLaunchIntentForPackage(p) ?: continue
        if (context.safeStart(i)) return
    }
    webSearch(context, "weather")
}

// ================================================================== Weather widget

private fun weatherBrush(code: Int, isDay: Boolean): Brush = when {
    !isDay -> Brush.verticalGradient(listOf(Color(0xFF0E1C3D), Color(0xFF2B3F6E)))
    code >= 51 || code == 45 || code == 48 -> Brush.verticalGradient(listOf(Color(0xFF4D5D72), Color(0xFF7A8BA0)))
    code == 3 -> Brush.verticalGradient(listOf(Color(0xFF5A7598), Color(0xFF8BA3BF)))
    else -> Brush.verticalGradient(listOf(Color(0xFF2E7BD6), Color(0xFF63A9EE)))
}

private val WShadow = Shadow(Color.Black.copy(alpha = 0.18f), Offset(0f, 1f), 3f)

@Composable
fun WeatherWidget(size: WidgetSize) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                runCatching { WeatherRepo.refresh(context) }
                delay(10 * 60_000L)
            }
        }
    }
    val density = LocalDensity.current
    val d = WeatherRepo.data
    val code = d?.code ?: 0
    val isDay = d?.isDay ?: true
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(weatherBrush(code, isDay))
            .padding(14.dp)
    ) {
        val h = maxHeight
        val st = { s: Dp, w: FontWeight -> TextStyle(color = Color.White, fontSize = with(density) { s.toSp() }, fontWeight = w, shadow = WShadow) }
        if (d == null) {
            Column(Modifier.fillMaxSize()) {
                Text("Weather", style = st(h * 0.11f, FontWeight.SemiBold))
                Spacer(Modifier.weight(1f))
                WeatherGlyph(2, true, Modifier.size(h * 0.24f))
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        WeatherRepo.failed -> "Couldn't load weather. Tap to retry."
                        else -> "Updating…"
                    },
                    style = st(h * 0.085f, FontWeight.Medium),
                )
            }
            return@BoxWithConstraints
        }
        val top = @Composable { m: Modifier ->
            Column(m) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.city, style = st(h * 0.105f, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(3.dp))
                    Canvas(Modifier.size(h * 0.07f)) { locationArrow() }
                }
                Text("${d.temp}°", style = st(h * (if (size == WidgetSize.SMALL) 0.3f else 0.27f), FontWeight.Light), maxLines = 1)
            }
        }
        val summary = @Composable { align: Alignment.Horizontal ->
            Column(horizontalAlignment = align) {
                WeatherGlyph(d.code, d.isDay, Modifier.size(h * 0.11f))
                Text(weatherCondition(d.code, d.isDay), style = st(h * 0.085f, FontWeight.SemiBold), maxLines = 1)
                Text("H:${d.hi}° L:${d.lo}°", style = st(h * 0.085f, FontWeight.SemiBold), maxLines = 1)
            }
        }
        if (size == WidgetSize.SMALL) {
            Column(Modifier.fillMaxSize()) {
                top(Modifier)
                Spacer(Modifier.weight(1f))
                summary(Alignment.Start)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth()) {
                    top(Modifier.weight(1f))
                    summary(Alignment.End)
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    d.hours.forEach { hc ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(hc.label, style = st(h * 0.075f, FontWeight.SemiBold), maxLines = 1)
                            Spacer(Modifier.height(3.dp))
                            WeatherGlyph(hc.code, hc.isDay, Modifier.size(h * 0.12f))
                            Spacer(Modifier.height(3.dp))
                            Text("${hc.temp}°", style = st(h * 0.085f, FontWeight.SemiBold), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.locationArrow() {
    val s = size.minDimension
    val p = Path().apply {
        moveTo(s, 0f)
        lineTo(0f, s * 0.42f)
        lineTo(s * 0.5f, s * 0.5f)
        lineTo(s * 0.58f, s)
        close()
    }
    drawPath(p, Color.White)
}

/** Small iOS-style condition symbol: sun, moon, clouds, rain, snow or thunder. */
@Composable
fun WeatherGlyph(code: Int, isDay: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val sunColor = Color(0xFFFFD60A)
        fun sun(c: Offset, r: Float) {
            drawCircle(sunColor, r, c)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val o1 = Offset(c.x + (cos(a) * r * 1.35f).toFloat(), c.y + (sin(a) * r * 1.35f).toFloat())
                val o2 = Offset(c.x + (cos(a) * r * 1.75f).toFloat(), c.y + (sin(a) * r * 1.75f).toFloat())
                drawLine(sunColor, o1, o2, strokeWidth = r * 0.28f, cap = StrokeCap.Round)
            }
        }
        fun moon(c: Offset, r: Float) {
            val p = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r))
            }
            val cut = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(c.x - r * 0.35f, c.y - r * 1.25f, c.x + r * 1.65f, c.y + r * 0.75f))
            }
            val m = Path().apply { op(p, cut, androidx.compose.ui.graphics.PathOperation.Difference) }
            drawPath(m, Color.White)
        }
        fun cloud(top: Float, color: Color = Color.White) {
            val baseY = top + s * 0.5f
            drawCircle(color, s * 0.17f, Offset(s * 0.3f, baseY - s * 0.02f))
            drawCircle(color, s * 0.23f, Offset(s * 0.52f, baseY - s * 0.12f))
            drawCircle(color, s * 0.15f, Offset(s * 0.74f, baseY + s * 0.0f))
            drawRoundRect(color, Offset(s * 0.13f, baseY - s * 0.02f), Size(s * 0.76f, s * 0.17f), CornerRadius(s * 0.085f))
        }
        when (code) {
            0 -> if (isDay) sun(center, s * 0.24f) else moon(center, s * 0.36f)
            1, 2 -> {
                if (isDay) sun(Offset(s * 0.62f, s * 0.32f), s * 0.16f) else moon(Offset(s * 0.62f, s * 0.32f), s * 0.24f)
                cloud(s * 0.2f)
            }
            3, 45, 48 -> cloud(s * 0.08f)
            in 51..67, in 80..82 -> {
                cloud(-s * 0.05f)
                for (i in 0 until 3) {
                    val x = s * (0.32f + i * 0.18f)
                    drawLine(Color(0xFF64D2FF), Offset(x, s * 0.72f), Offset(x - s * 0.06f, s * 0.92f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
                }
            }
            in 71..77, 85, 86 -> {
                cloud(-s * 0.05f)
                for (i in 0 until 3) drawCircle(Color.White, s * 0.05f, Offset(s * (0.32f + i * 0.18f), s * 0.82f))
            }
            in 95..99 -> {
                cloud(-s * 0.05f)
                val bolt = Path().apply {
                    moveTo(s * 0.55f, s * 0.6f); lineTo(s * 0.4f, s * 0.82f); lineTo(s * 0.52f, s * 0.82f)
                    lineTo(s * 0.45f, s); lineTo(s * 0.65f, s * 0.74f); lineTo(s * 0.53f, s * 0.74f); close()
                }
                drawPath(bolt, sunColor)
            }
            else -> cloud(s * 0.08f)
        }
    }
}
