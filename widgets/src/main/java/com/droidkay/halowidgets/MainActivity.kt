package com.droidkay.halowidgets

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Halo Widgets: choose a style, see every widget live, and add them to the home screen
 * (or long-press the home screen → Widgets → Halo Widgets).
 */
class MainActivity : Activity() {

    private lateinit var previews: LinearLayout
    private lateinit var styleRow: LinearLayout
    private lateinit var locationText: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF0D1B3A.toInt(), 0xFF2A1B4A.toInt(), 0xFF071022.toInt()))
            fitsSystemWindows = true
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(32))
        }
        scroll.addView(col)

        col.addView(text("Halo Widgets", 32f, Color.WHITE, bold = true))
        col.addView(text(
            "iPad-style widgets for any home screen. To add one: long-press an empty spot on your home screen → Widgets → Halo Widgets, or tap Add below.",
            15f, 0xFFB0B4BE.toInt(),
        ).apply { setPadding(0, dp(6), 0, dp(18)) })

        // Style
        col.addView(section("STYLE"))
        styleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        col.addView(styleRow)
        refreshStyleRow()

        // Weather location
        col.addView(section("WEATHER"))
        val locRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        locationText = text("", 14f, 0xFFB0B4BE.toInt())
        locRow.addView(locationText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        locRow.addView(pill("Use my location", 0xFF0A84FF.toInt()) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 7)
        })
        col.addView(locRow)
        refreshLocationText()

        // Previews
        col.addView(section("WIDGETS"))
        previews = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(previews)
        buildPreviews()

        setContentView(scroll)
        Updater.schedule(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshLocationText()
        Updater.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        // Weather may have arrived in the background since the previews were drawn.
        if (::previews.isInitialized) buildPreviews()
    }

    private fun refreshLocationText() {
        locationText.text = if (WeatherStore.hasLocation(this)) "Weather uses your location."
        else "Weather uses your time zone's city. Allow location for your exact area."
    }

    private fun refreshStyleRow() {
        styleRow.removeAllViews()
        listOf("dark" to "Dark", "light" to "Light", "glass" to "Glass").forEach { (id, label) ->
            val on = Look.style(this) == id
            styleRow.addView(pill(label, if (on) 0xFF0A84FF.toInt() else 0x26FFFFFF) {
                Look.setStyle(this, id)
                refreshStyleRow()
                buildPreviews()
                Updater.updateAll(this)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(10) })
        }
    }

    private fun buildPreviews() {
        previews.removeAllViews()
        val groups = Kind.values().groupBy { it.title }
        groups.forEach { (title, kinds) ->
            previews.addView(text(title, 18f, Color.WHITE, bold = true).apply { setPadding(0, dp(14), 0, dp(8)) })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val h = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }
            previews.addView(h)
            kinds.forEach { k -> row.addView(previewCard(k)) }
        }
    }

    private fun previewCard(k: Kind): View {
        val (wDp, hDp) = when (k.size) {
            "Small" -> 170 to 170
            "Medium" -> 360 to 170
            else -> 360 to 360
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, dp(18), 0)
        }
        val frame = FrameLayout(this)
        runCatching {
            val v = Builder.build(this, k).apply(this, frame)
            frame.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        card.addView(frame, LinearLayout.LayoutParams(dp(wDp), dp(hDp)))
        val under = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        under.addView(text(k.size, 14f, 0xFFB0B4BE.toInt()), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        under.addView(pill("Add", 0x33FFFFFF) { pin(k) })
        card.addView(under, LinearLayout.LayoutParams(dp(wDp), ViewGroup.LayoutParams.WRAP_CONTENT))
        return card
    }

    private fun pin(k: Kind) {
        val mgr = AppWidgetManager.getInstance(this)
        val ok = mgr.isRequestPinAppWidgetSupported && runCatching {
            mgr.requestPinAppWidget(ComponentName(this, k.provider), null, null)
        }.getOrDefault(false)
        if (!ok) {
            Toast.makeText(this, "Long-press your home screen → Widgets → Halo Widgets → ${k.title}", Toast.LENGTH_LONG).show()
        }
    }

    private fun section(label: String) = text(label, 13f, 0xFF8E93A0.toInt(), bold = true).apply { setPadding(0, dp(14), 0, dp(8)) }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun pill(label: String, color: Int, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = GradientDrawable().apply { setColor(color); cornerRadius = dp(20).toFloat() }
        setPadding(dp(18), dp(9), dp(18), dp(9))
        setOnClickListener { onClick() }
    }

    @Suppress("unused")
    private fun granted() = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
