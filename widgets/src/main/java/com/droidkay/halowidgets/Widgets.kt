package com.droidkay.halowidgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Every widget Halo Widgets offers (one Android widget per size, so any launcher's picker shows them all). */
enum class Kind(val title: String, val size: String, val layout: Int) {
    CLOCK_S("Clock", "Small", R.layout.w_clock_small),
    CLOCK_M("Clock", "Medium", R.layout.w_clock_medium),
    ANALOG_S("Analog Clock", "Small", R.layout.w_analog),
    CALENDAR_S("Calendar", "Small", R.layout.w_calendar_small),
    CALENDAR_M("Calendar", "Medium", R.layout.w_calendar_medium),
    CALENDAR_L("Calendar", "Large", R.layout.w_calendar_large),
    WEATHER_S("Weather", "Small", R.layout.w_weather_small),
    WEATHER_M("Weather", "Medium", R.layout.w_weather_medium),
    BATTERY_S("Batteries", "Small", R.layout.w_battery_small),
    BATTERY_M("Batteries", "Medium", R.layout.w_battery_medium),
    SEARCH_S("Google", "Small", R.layout.w_search_small),
    SEARCH_M("Google", "Medium", R.layout.w_search_medium);

    val provider: Class<out AppWidgetProvider>
        get() = when (this) {
            CLOCK_S -> W_clock_s::class.java
            CLOCK_M -> W_clock_m::class.java
            ANALOG_S -> W_analog_s::class.java
            CALENDAR_S -> W_calendar_s::class.java
            CALENDAR_M -> W_calendar_m::class.java
            CALENDAR_L -> W_calendar_l::class.java
            WEATHER_S -> W_weather_s::class.java
            WEATHER_M -> W_weather_m::class.java
            BATTERY_S -> W_battery_s::class.java
            BATTERY_M -> W_battery_m::class.java
            SEARCH_S -> W_search_s::class.java
            SEARCH_M -> W_search_m::class.java
        }
}

private const val GSA = "com.google.android.googlequicksearchbox"

object Builder {

    fun build(ctx: Context, kind: Kind): RemoteViews {
        val v = RemoteViews(ctx.packageName, kind.layout)
        val pal = Look.palette(ctx)
        val isWeather = kind == Kind.WEATHER_S || kind == Kind.WEATHER_M
        if (!isWeather) v.setInt(R.id.root, "setBackgroundResource", pal.bg)
        when (kind) {
            Kind.CLOCK_S, Kind.CLOCK_M -> clock(ctx, v, pal, kind)
            Kind.ANALOG_S -> { analog(ctx, v); v.setOnClickPendingIntent(R.id.root, open(ctx, kind, clockIntent(ctx))) }
            Kind.CALENDAR_S, Kind.CALENDAR_M, Kind.CALENDAR_L -> calendar(ctx, v, pal, kind)
            Kind.WEATHER_S, Kind.WEATHER_M -> weather(ctx, v, kind)
            Kind.BATTERY_S, Kind.BATTERY_M -> battery(ctx, v, pal, kind)
            Kind.SEARCH_S, Kind.SEARCH_M -> search(ctx, v, pal, kind)
        }
        return v
    }

    // ------------------------------------------------------------------ clock

    private fun clock(ctx: Context, v: RemoteViews, pal: Palette, kind: Kind) {
        v.setTextViewText(R.id.city, TimeZone.getDefault().id.substringAfterLast('/').replace('_', ' ').uppercase())
        v.setTextColor(R.id.time, pal.text)
        v.setTextColor(R.id.date, pal.secondary)
        if (kind == Kind.CLOCK_M) analog(ctx, v)
        v.setOnClickPendingIntent(R.id.root, open(ctx, kind, clockIntent(ctx)))
    }

    private fun analog(ctx: Context, v: RemoteViews) {
        val style = Look.style(ctx)
        val dial = when (style) { "light" -> R.drawable.dial_light; "glass" -> R.drawable.dial_glass; else -> R.drawable.dial_dark }
        val light = style == "light"
        v.setIcon(R.id.analog, "setDial", Icon.createWithResource(ctx, dial))
        v.setIcon(R.id.analog, "setHourHand", Icon.createWithResource(ctx, if (light) R.drawable.hand_hour_light else R.drawable.hand_hour_dark))
        v.setIcon(R.id.analog, "setMinuteHand", Icon.createWithResource(ctx, if (light) R.drawable.hand_minute_light else R.drawable.hand_minute_dark))
    }

    private fun clockIntent(ctx: Context) = firstResolvable(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS))

    // ------------------------------------------------------------------ calendar

    private fun calendar(ctx: Context, v: RemoteViews, pal: Palette, kind: Kind) {
        val today = LocalDate.now()
        if (kind != Kind.CALENDAR_L) {
            v.setTextColor(R.id.day, pal.text)
            v.setTextColor(R.id.month, pal.secondary)
        }
        when (kind) {
            Kind.CALENDAR_M -> v.setImageViewBitmap(R.id.grid, Draw.monthGrid(ctx, 190f, 140f, today, pal, withTitle = true))
            Kind.CALENDAR_L -> v.setImageViewBitmap(R.id.grid, Draw.monthGrid(ctx, 320f, 320f, today, pal, withTitle = true))
            else -> {}
        }
        val b = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
        ContentUris.appendId(b, System.currentTimeMillis())
        v.setOnClickPendingIntent(R.id.root, open(ctx, kind, firstResolvable(ctx, Intent(Intent.ACTION_VIEW).setData(b.build()))))
    }

    // ------------------------------------------------------------------ weather

    private fun weather(ctx: Context, v: RemoteViews, kind: Kind) {
        val w = WeatherStore.cached(ctx)
        v.setInt(R.id.root, "setBackgroundResource", if (w == null) R.drawable.bg_weather_day else weatherBackground(w.code, w.isDay))
        if (w == null) {
            v.setTextViewText(R.id.city, "Weather")
            v.setTextViewText(R.id.temp, "--°")
            v.setTextViewText(R.id.cond, "Updating…")
            v.setTextViewText(R.id.hilo, "")
            v.setImageViewBitmap(R.id.icon, Draw.weatherIcon(ctx, 20f, 2, true))
        } else {
            v.setTextViewText(R.id.city, w.city + " ➤".takeIf { WeatherStore.hasLocation(ctx) }.orEmpty())
            v.setTextViewText(R.id.temp, "${w.temp}°")
            v.setTextViewText(R.id.cond, condition(w.code, w.isDay))
            v.setTextViewText(R.id.hilo, "H:${w.hi}° L:${w.lo}°")
            v.setImageViewBitmap(R.id.icon, Draw.weatherIcon(ctx, 20f, w.code, w.isDay))
            if (kind == Kind.WEATHER_M) {
                val ids = listOf(
                    Triple(R.id.h0_l, R.id.h0_i, R.id.h0_t), Triple(R.id.h1_l, R.id.h1_i, R.id.h1_t),
                    Triple(R.id.h2_l, R.id.h2_i, R.id.h2_t), Triple(R.id.h3_l, R.id.h3_i, R.id.h3_t),
                    Triple(R.id.h4_l, R.id.h4_i, R.id.h4_t), Triple(R.id.h5_l, R.id.h5_i, R.id.h5_t),
                )
                ids.forEachIndexed { i, (l, ic, t) ->
                    val h = w.hours.getOrNull(i)
                    v.setTextViewText(l, h?.label ?: "")
                    v.setTextViewText(t, h?.let { "${it.temp}°" } ?: "")
                    if (h != null) v.setImageViewBitmap(ic, Draw.weatherIcon(ctx, 24f, h.code, h.isDay))
                }
            }
        }
        val app = listOf("com.miui.weather2", "com.sec.android.daemonapp", "com.google.android.apps.weather")
            .firstNotNullOfOrNull { ctx.packageManager.getLaunchIntentForPackage(it) }
        val target = app ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=weather"))
        v.setOnClickPendingIntent(R.id.root, open(ctx, kind, target))
    }

    // ------------------------------------------------------------------ battery

    private fun battery(ctx: Context, v: RemoteViews, pal: Palette, kind: Kind) {
        val b = readBattery(ctx)
        val name = deviceName(ctx)
        v.setImageViewBitmap(R.id.ring, Draw.batteryRing(ctx, if (kind == Kind.BATTERY_S) 110f else 120f, b.percent, b.charging, pal))
        v.setTextViewText(R.id.name, name)
        v.setTextColor(R.id.name, pal.secondary)
        if (kind == Kind.BATTERY_M) {
            v.setTextViewText(R.id.pct, if (b.percent < 0) "--%" else "${b.percent}%")
            v.setTextColor(R.id.pct, pal.text)
            v.setTextViewText(R.id.status, if (b.charging) "Charging" else "On battery")
            v.setTextColor(R.id.status, if (b.charging) Draw.GREEN else pal.secondary)
        }
        v.setOnClickPendingIntent(R.id.root, open(ctx, kind, firstResolvable(ctx, Intent(Intent.ACTION_POWER_USAGE_SUMMARY), Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))))
    }

    // ------------------------------------------------------------------ Google

    private fun search(ctx: Context, v: RemoteViews, pal: Palette, kind: Kind) {
        listOf(R.id.t_search, R.id.t_voice, R.id.t_lens, R.id.t_ai).forEach { v.setInt(it, "setBackgroundResource", pal.tile) }
        val icon = if (kind == Kind.SEARCH_S) 36f else 30f
        v.setImageViewBitmap(R.id.i_g, Draw.googleG(ctx, if (kind == Kind.SEARCH_S) 36f else 28f))
        v.setImageViewBitmap(R.id.i_mic, Draw.mic(ctx, icon, pal.glyph))
        v.setImageViewBitmap(R.id.i_lens, Draw.lens(ctx, icon, pal.glyph))
        v.setImageViewBitmap(R.id.i_ai, Draw.aiSearch(ctx, icon, pal.glyph))
        if (kind == Kind.SEARCH_M) v.setTextColor(R.id.search_text, pal.secondary)

        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
        v.setOnClickPendingIntent(R.id.t_search, open(ctx, kind, firstResolvable(ctx,
            Intent("android.search.action.GLOBAL_SEARCH").setPackage(GSA),
            ctx.packageManager.getLaunchIntentForPackage(GSA),
            web,
        ), 1))
        v.setOnClickPendingIntent(R.id.t_voice, open(ctx, kind, firstResolvable(ctx,
            Intent("android.speech.action.WEB_SEARCH").setPackage(GSA),
            Intent(Intent.ACTION_VOICE_COMMAND),
            web,
        ), 2))
        v.setOnClickPendingIntent(R.id.t_lens, open(ctx, kind, firstResolvable(ctx,
            Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://lens")).setPackage(GSA),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://lens.google.com")),
        ), 3))
        val ai = Uri.parse("https://www.google.com/search?udm=50")
        v.setOnClickPendingIntent(R.id.t_ai, open(ctx, kind, firstResolvable(ctx,
            Intent(Intent.ACTION_VIEW, ai).setPackage(GSA),
            Intent(Intent.ACTION_VIEW, ai),
        ), 4))
    }

    // ------------------------------------------------------------------ helpers

    private fun firstResolvable(ctx: Context, vararg intents: Intent?): Intent {
        val pm = ctx.packageManager
        return intents.filterNotNull().firstOrNull { i ->
            @Suppress("DEPRECATION")
            pm.resolveActivity(i, 0) != null
        } ?: intents.filterNotNull().first()
    }

    private fun open(ctx: Context, kind: Kind, intent: Intent, slot: Int = 0): PendingIntent =
        PendingIntent.getActivity(
            ctx,
            kind.ordinal * 10 + slot,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

// ====================================================================== updating

object Updater {
    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        Kind.values().forEach { k ->
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, k.provider))
            if (ids.isNotEmpty()) runCatching { mgr.updateAppWidget(ids, Builder.build(ctx, k)) }
        }
    }

    /** Every 15 minutes (battery, calendar, weather when it's older than 30 minutes) + one run now. */
    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        wm.enqueueUniquePeriodicWork(
            "halo-widgets",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build(),
        )
        wm.enqueueUniqueWork(
            "halo-widgets-now",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RefreshWorker>()
                .setBackoffCriteria(androidx.work.BackoffPolicy.LINEAR, 20, TimeUnit.SECONDS)
                .build(),
        )
    }
}

class RefreshWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        var weatherFailed = false
        if (WeatherStore.isStale(ctx)) weatherFailed = WeatherStore.refresh(ctx) == null
        Updater.updateAll(ctx)
        // No weather yet (offline, network still starting…): try again shortly instead of in 15 minutes.
        return if (weatherFailed && WeatherStore.cached(ctx) == null && runAttemptCount < 6) Result.retry() else Result.success()
    }
}

/** Base for every widget size: draw it, and keep the background refresh running. */
open class HaloWidget(private val kind: Kind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        runCatching { manager.updateAppWidget(ids, Builder.build(context, kind)) }
        Updater.schedule(context)
    }

    override fun onEnabled(context: Context) {
        Updater.schedule(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_TIMEZONE_CHANGED || intent.action == Intent.ACTION_TIME_CHANGED) Updater.updateAll(context)
    }
}

class W_clock_s : HaloWidget(Kind.CLOCK_S)
class W_clock_m : HaloWidget(Kind.CLOCK_M)
class W_analog_s : HaloWidget(Kind.ANALOG_S)
class W_calendar_s : HaloWidget(Kind.CALENDAR_S)
class W_calendar_m : HaloWidget(Kind.CALENDAR_M)
class W_calendar_l : HaloWidget(Kind.CALENDAR_L)
class W_weather_s : HaloWidget(Kind.WEATHER_S)
class W_weather_m : HaloWidget(Kind.WEATHER_M)
class W_battery_s : HaloWidget(Kind.BATTERY_S)
class W_battery_m : HaloWidget(Kind.BATTERY_M)
class W_search_s : HaloWidget(Kind.SEARCH_S)
class W_search_m : HaloWidget(Kind.SEARCH_M)
