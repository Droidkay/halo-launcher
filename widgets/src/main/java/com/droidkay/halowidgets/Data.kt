package com.droidkay.halowidgets

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

// ====================================================================== look

/** Colours for one widget style. [bg] is the background drawable. */
data class Palette(
    val bg: Int,
    val text: Int,
    val secondary: Int,
    val track: Int,
    val tile: Int,
    val glyph: Int,
)

object Look {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("halo_widgets", Context.MODE_PRIVATE)

    /** dark, light or glass */
    fun style(ctx: Context): String = prefs(ctx).getString("style", "dark") ?: "dark"
    fun setStyle(ctx: Context, v: String) = prefs(ctx).edit().putString("style", v).apply()

    fun palette(ctx: Context): Palette = when (style(ctx)) {
        "light" -> Palette(R.drawable.bg_light, 0xFF000000.toInt(), 0xFF8E8E93.toInt(), 0x1F000000, R.drawable.tile_light, 0xFF3C4043.toInt())
        "glass" -> Palette(R.drawable.bg_glass, 0xFFFFFFFF.toInt(), 0xFFB0B4BE.toInt(), 0x33FFFFFF, R.drawable.tile_glass, 0xFFE8EAED.toInt())
        else -> Palette(R.drawable.bg_dark, 0xFFFFFFFF.toInt(), 0xFF8E8E93.toInt(), 0x26FFFFFF, R.drawable.tile_dark, 0xFFE8EAED.toInt())
    }
}

// ====================================================================== battery

data class BatteryInfo(val percent: Int, val charging: Boolean)

fun readBattery(ctx: Context): BatteryInfo {
    val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return BatteryInfo(-1, false)
    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return BatteryInfo(if (level < 0) -1 else level * 100 / scale, charging)
}

fun deviceName(ctx: Context): String =
    if (ctx.resources.configuration.smallestScreenWidthDp >= 600) "This Tablet" else "This Phone"

// ====================================================================== weather (Open-Meteo, no key)

data class Hour(val label: String, val code: Int, val isDay: Boolean, val temp: Int)

data class Weather(
    val city: String,
    val temp: Int,
    val code: Int,
    val isDay: Boolean,
    val hi: Int,
    val lo: Int,
    val hours: List<Hour>,
    val at: Long,
)

object WeatherStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("halo_weather", Context.MODE_PRIVATE)

    fun hasLocation(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun cached(ctx: Context): Weather? =
        prefs(ctx).getString("data", null)?.let { runCatching { decode(JSONObject(it)) }.getOrNull() }

    fun isStale(ctx: Context): Boolean {
        val w = cached(ctx) ?: return true
        return System.currentTimeMillis() - w.at > 30 * 60_000L
    }

    /** Blocking; call from a worker. */
    fun refresh(ctx: Context): Weather? {
        val p = prefs(ctx)
        val loc = if (hasLocation(ctx)) location(ctx) else null
        var place: String? = null
        val lat: Double
        val lon: Double
        if (loc != null) {
            lat = loc.latitude
            lon = loc.longitude
            p.edit().putFloat("lat", lat.toFloat()).putFloat("lon", lon.toFloat()).apply()
        } else if (p.contains("lat")) {
            lat = p.getFloat("lat", 0f).toDouble()
            lon = p.getFloat("lon", 0f).toDouble()
        } else {
            // No location: the city of the time zone (Africa/Lagos → Lagos).
            val city = TimeZone.getDefault().id.substringAfterLast('/').replace('_', ' ')
            val geo = get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(city, "UTF-8"))
                ?.optJSONArray("results")?.optJSONObject(0) ?: return null
            lat = geo.getDouble("latitude")
            lon = geo.getDouble("longitude")
            place = geo.optString("name", city)
        }
        val f = Locale.getDefault().country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")
        val json = get(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=2" +
                (if (f) "&temperature_unit=fahrenheit" else ""),
        ) ?: return null
        val w = runCatching {
            val cur = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            val hourly = json.getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val curTime = cur.getString("time").take(13)
            var start = 0
            for (i in 0 until times.length()) if (times.getString(i).take(13) >= curTime) { start = i; break }
            val fmt = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(ctx)) "HH" else "ha", Locale.getDefault())
            val hours = (start until minOf(start + 6, times.length())).map { i ->
                Hour(
                    if (i == start) "Now" else LocalDateTime.parse(times.getString(i)).format(fmt),
                    hourly.getJSONArray("weather_code").getInt(i),
                    hourly.getJSONArray("is_day").getInt(i) == 1,
                    hourly.getJSONArray("temperature_2m").getDouble(i).roundToInt(),
                )
            }
            Weather(
                place ?: cityName(ctx, lat, lon),
                cur.getDouble("temperature_2m").roundToInt(),
                cur.getInt("weather_code"),
                cur.getInt("is_day") == 1,
                daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
                hours,
                System.currentTimeMillis(),
            )
        }.getOrNull() ?: return null
        p.edit().putString("data", encode(w).toString()).apply()
        return w
    }

    private fun get(url: String): JSONObject? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        try {
            JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun cityName(ctx: Context, lat: Double, lon: Double): String {
        val fallback = TimeZone.getDefault().id.substringAfterLast('/').replace('_', ' ')
        return runCatching {
            val a = Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
            a?.locality ?: a?.subAdminArea ?: a?.adminArea
        }.getOrNull() ?: fallback
    }

    @SuppressLint("MissingPermission")
    private fun location(ctx: Context): Location? {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        return runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    private fun encode(w: Weather) = JSONObject().apply {
        put("city", w.city); put("temp", w.temp); put("code", w.code); put("day", w.isDay)
        put("hi", w.hi); put("lo", w.lo); put("at", w.at)
        put("hours", JSONArray().apply {
            w.hours.forEach { h -> put(JSONObject().apply { put("l", h.label); put("c", h.code); put("d", h.isDay); put("t", h.temp) }) }
        })
    }

    private fun decode(o: JSONObject): Weather {
        val a = o.optJSONArray("hours") ?: JSONArray()
        return Weather(
            o.getString("city"), o.getInt("temp"), o.getInt("code"), o.getBoolean("day"),
            o.getInt("hi"), o.getInt("lo"),
            (0 until a.length()).map { i -> a.getJSONObject(i).let { Hour(it.getString("l"), it.getInt("c"), it.getBoolean("d"), it.getInt("t")) } },
            o.getLong("at"),
        )
    }
}

fun condition(code: Int, isDay: Boolean): String = when (code) {
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

fun weatherBackground(code: Int, isDay: Boolean): Int = when {
    !isDay -> R.drawable.bg_weather_night
    code >= 45 -> R.drawable.bg_weather_rain
    code == 3 -> R.drawable.bg_weather_cloudy
    else -> R.drawable.bg_weather_day
}
