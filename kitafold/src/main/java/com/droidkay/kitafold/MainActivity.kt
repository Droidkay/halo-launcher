package com.droidkay.kitafold

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity(), SensorEventListener {
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var angle: TextView
    private lateinit var log: TextView
    private var sm: SensorManager? = null

    private val tick = object : Runnable {
        override fun run() {
            val on = FoldService.instance != null
            status.text = if (on) "Service: ON. Folding the phone now plays the transition."
            else "Service: OFF. Tap the first button, find \"Kita Fold\" under Installed apps and switch it on."
            status.setTextColor(if (on) Color.rgb(22, 160, 100) else Color.rgb(220, 80, 80))
            angle.text = "Hinge sensor: " + (if (!Diag.hasSensor && FoldService.instance != null) "not found" else
                if (Diag.angle >= 0f) "%.0f°".format(Diag.angle) else "move the hinge…")
            log.text = Diag.text()
            ui.postDelayed(this, 300)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(t: String, bold: Boolean = false, size: Float = 15f): TextView =
        TextView(this).apply {
            text = t; textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(4))
        }

    private fun slider(name: String, max: Int, get: () -> Int, set: (Int) -> Unit, unit: String): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = label("$name: ${get()}$unit")
        val bar = SeekBar(this).apply {
            this.max = max; progress = get()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    set(p); head.text = "$name: $p$unit"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        box.addView(head); box.addView(bar)
        return box
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(30))
        }
        root.addView(label("Kita Fold", true, 28f))
        root.addView(label("A smooth frosted fold / unfold transition for any app on your Fold.", false, 14f))
        status = label("", true, 14f); root.addView(status)
        angle = label("", false, 14f); root.addView(angle)

        root.addView(Button(this).apply {
            text = "1. Open Accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(Button(this).apply {
            text = "2. Preview the transition now"
            setOnClickListener {
                val s = FoldService.instance
                if (s == null) Diag.log("Turn the service on first") else s.preview()
            }
        })

        root.addView(slider("Duration", 1500, { Prefs.duration(this) }, { Prefs.setDuration(this, it.coerceAtLeast(250)) }, " ms"))
        root.addView(slider("Frost strength", 100, { Prefs.blur(this) }, { Prefs.setBlur(this, it) }, ""))
        root.addView(slider("Stretch", 100, { Prefs.stretch(this) }, { Prefs.setStretch(this, it) }, ""))
        root.addView(CheckBox(this).apply {
            text = "Frost the swinging half while folding"; isChecked = Prefs.frost(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setFrost(this@MainActivity, v) }
        })
        root.addView(CheckBox(this).apply {
            text = "Swinging half is the right side"; isChecked = Prefs.frostRight(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setFrostRight(this@MainActivity, v) }
        })
        root.addView(CheckBox(this).apply {
            text = "Haptic tick"; isChecked = Prefs.haptic(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setHaptic(this@MainActivity, v) }
        })

        root.addView(label("What the service is doing", true, 15f))
        log = TextView(this).apply { textSize = 12f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }
        root.addView(log)
        root.addView(label("Samsung may stop background apps. In Settings > Apps > Kita Fold > Battery choose Unrestricted.", false, 12f))

        setContentView(ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        sm = getSystemService(SensorManager::class.java)
        val h = sm?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        if (h != null) { Diag.hasSensor = true; sm?.registerListener(this, h, SensorManager.SENSOR_DELAY_NORMAL) }
        ui.post(tick)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(tick)
        sm?.unregisterListener(this)
    }

    override fun onSensorChanged(e: SensorEvent) { Diag.angle = e.values[0] }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}
