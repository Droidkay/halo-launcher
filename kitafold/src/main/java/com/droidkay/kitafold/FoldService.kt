package com.droidkay.kitafold

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Display
import android.view.Gravity
import android.view.View
import android.graphics.Color
import android.graphics.Point
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.view.animation.PathInterpolator
import kotlin.math.abs

/**
 * Plays the fold morph over whatever is on screen:
 *  1. when the hinge starts to move, take a picture of the screen that is on;
 *  2. when the other screen lights up, put that picture on top of it, then stretch, frost and fade it away
 *     while the live screen underneath resolves out of a blur.
 */
class FoldService : AccessibilityService(), SensorEventListener, DisplayManager.DisplayListener {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var sm: SensorManager
    private lateinit var dm: DisplayManager

    private var lastAngle = -1f
    private var lastMoveAt = 0L

    private var snap: Bitmap? = null
    private var snapAt = 0L
    private var snapDisplay = -1
    private var capturing = false
    private var playing = false

    private val lastState = HashMap<Int, Int>()

    override fun onServiceConnected() {
        instance = this
        sm = getSystemService(SensorManager::class.java)
        dm = getSystemService(DisplayManager::class.java)
        val hinge = sm.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        Diag.hasSensor = hinge != null
        if (hinge != null) sm.registerListener(this, hinge, SensorManager.SENSOR_DELAY_GAME)
        dm.registerDisplayListener(this, main)
        for (d in dm.displays) lastState[d.displayId] = d.state
        Diag.log("Service on. Hinge sensor: ${if (hinge != null) "yes" else "NO"}")
        KeepAliveService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        try { sm.unregisterListener(this) } catch (_: Throwable) {}
        try { dm.unregisterDisplayListener(this) } catch (_: Throwable) {}
        if (instance === this) instance = null
        super.onDestroy()
    }

    // ---- hinge: frost the swinging half while it moves, and take the picture when a fold/unfold begins
    override fun onSensorChanged(e: SensorEvent) {
        val a = e.values[0]
        Diag.angle = a
        Diag.events++
        val first = lastAngle < 0f
        val now = SystemClock.elapsedRealtime()
        // Treat a big jump, or the first reading after waking mid-fold, as movement.
        val moved = (!first && abs(a - lastAngle) > 0.4f) || (first && a in 8f..165f)
        lastAngle = a
        if (!moved) return
        if (now - lastMoveAt > 1200L) {
            Diag.log("Hinge moving at %.0f°".format(a))
            capture(null)
        }
        lastMoveAt = now
        if (Prefs.frost(this) && !playing) {
            val d = activeDisplay()
            val inner = d != null && isInner(d)
            val amount = if (inner) smooth((176f - a) / 60f) else smooth((a - 6f) / 50f)
            setFrost(amount, d, half = inner)
            main.removeCallbacks(clearFrost)
            main.postDelayed(clearFrost, 650L)
        }
    }

    /** Ramps the frost up and down once on the current screen, without needing the hinge. */
    fun testFrost() {
        val d = activeDisplay() ?: return
        val inner = isInner(d)
        Diag.log("Frost test on display ${d.displayId}")
        val anim = ValueAnimator.ofFloat(0f, 1f, 1f, 0f)
        anim.duration = 1600
        anim.addUpdateListener { setFrost(it.animatedValue as Float, d, half = inner) }
        anim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) { removeFrost() }
        })
        anim.start()
    }

    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}

    private fun smooth(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun realSize(d: Display): Point {
        val p = Point()
        @Suppress("DEPRECATION")
        d.getRealSize(p)
        return p
    }

    /** The inner panel is the big, nearly square one. */
    private fun isInner(d: Display): Boolean {
        val p = realSize(d)
        val short = minOf(p.x, p.y).toFloat()
        val long = maxOf(p.x, p.y).toFloat()
        return long > 0f && short / long > 0.6f
    }

    // ---- live frost window (blurs whatever app is behind it)
    private var frostView: View? = null
    private var frostWm: WindowManager? = null
    private var frostLp: WindowManager.LayoutParams? = null
    private var frostDisplayId = -1
    private var frostHalf = false
    private var warnedBlur = false
    private val clearFrost = Runnable { removeFrost() }

    private fun removeFrost() {
        val v = frostView ?: return
        try { frostWm?.removeView(v) } catch (_: Throwable) {}
        frostView = null
        frostWm = null
        frostLp = null
        frostDisplayId = -1
    }

    private fun setFrost(amount: Float, d: Display?, half: Boolean) {
        if (d == null || amount <= 0.01f) { removeFrost(); return }
        if (frostView == null || frostDisplayId != d.displayId || frostHalf != half) {
            removeFrost()
            val ctx = createDisplayContext(d)
            val wm = ctx.getSystemService(WindowManager::class.java)
            if (!warnedBlur && !wm.isCrossWindowBlurEnabled) {
                warnedBlur = true
                Diag.log("System blur is off (battery saver / reduce transparency): tint only")
            }
            val size = realSize(d)
            val landscape = size.x > size.y
            val w = if (half && !landscape) size.x / 2 else WindowManager.LayoutParams.MATCH_PARENT
            val h = if (half && landscape) size.y / 2 else WindowManager.LayoutParams.MATCH_PARENT
            val lp = WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                PixelFormat.TRANSLUCENT,
            )
            lp.gravity = Gravity.TOP or (if (Prefs.frostRight(this) && !landscape) Gravity.END else Gravity.START)
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            val v = View(ctx)
            try {
                wm.addView(v, lp)
            } catch (t: Throwable) {
                Diag.log("Frost failed: ${t.javaClass.simpleName}")
                return
            }
            frostView = v; frostWm = wm; frostLp = lp; frostDisplayId = d.displayId; frostHalf = half
            Diag.log("Frosting ${if (half) "half" else "whole"} screen on display ${d.displayId}")
        }
        val v = frostView ?: return
        val lp = frostLp ?: return
        val strength = Prefs.blur(this) / 100f
        val blurOn = frostWm?.isCrossWindowBlurEnabled == true
        lp.blurBehindRadius = (amount * (20f + 100f * strength)).toInt()
        v.setBackgroundColor(Color.argb(((if (blurOn) 70 else 150) * amount).toInt(), 10, 12, 20))
        try { frostWm?.updateViewLayout(v, lp) } catch (_: Throwable) {}
    }

    private fun activeDisplay(): Display? =
        dm.displays.firstOrNull { it.state == Display.STATE_ON } ?: dm.getDisplay(Display.DEFAULT_DISPLAY)

    /** @param thenPlay when set (preview), play the morph on this display straight after the picture is taken. */
    fun capture(thenPlay: Display?) {
        if (capturing || playing) return
        val d = activeDisplay() ?: return
        capturing = true
        try {
            takeScreenshot(d.displayId, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    try {
                        val hw = result.hardwareBuffer
                        val wrapped = Bitmap.wrapHardwareBuffer(hw, result.colorSpace)
                        val soft = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                        hw.close()
                        if (soft != null) {
                            snap = soft
                            snapAt = SystemClock.elapsedRealtime()
                            snapDisplay = d.displayId
                            Diag.log("Picture of display ${d.displayId} ok (${soft.width}x${soft.height})")
                            if (thenPlay != null) play(thenPlay)
                        } else Diag.log("Picture failed: empty")
                    } catch (t: Throwable) {
                        Diag.log("Picture failed: ${t.javaClass.simpleName}")
                    }
                    capturing = false
                }

                override fun onFailure(errorCode: Int) {
                    capturing = false
                    Diag.log("Picture failed, code $errorCode")
                }
            })
        } catch (t: Throwable) {
            capturing = false
            Diag.log("Picture failed: ${t.javaClass.simpleName}")
        }
    }

    // ---- the other screen lights up: play the morph there
    override fun onDisplayAdded(displayId: Int) {}
    override fun onDisplayRemoved(displayId: Int) { lastState.remove(displayId) }

    override fun onDisplayChanged(displayId: Int) {
        val d = dm.getDisplay(displayId) ?: return
        val before = lastState[displayId]
        lastState[displayId] = d.state
        val size = realSize(d)
        Diag.log("Display $displayId changed: ${stateName(d.state)} ${size.x}x${size.y}")
        if (d.state != Display.STATE_ON) return
        val s = snap ?: return
        if (playing) return
        val fresh = SystemClock.elapsedRealtime() - snapAt < 15_000L
        if (!fresh) return
        // Samsung Folds use ONE logical display that changes size between the cover and inner panels,
        // so a fold shows up as display 0 resizing, not as another display turning on.
        val snapShort = minOf(s.width, s.height).toFloat()
        val nowShort = minOf(size.x, size.y).toFloat()
        val resized = snapShort > 0f && abs(nowShort - snapShort) / snapShort > 0.15f
        val otherScreen = displayId != snapDisplay && before != Display.STATE_ON
        if (resized || otherScreen) {
            Diag.log("Screen switched, playing morph")
            play(d)
        }
    }

    private fun stateName(st: Int) = when (st) {
        Display.STATE_ON -> "on"
        Display.STATE_OFF -> "off"
        Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND -> "doze"
        else -> "state $st"
    }

    fun preview() {
        val d = activeDisplay() ?: return
        Diag.log("Preview requested")
        capture(d)
    }

    private fun play(display: Display) {
        val bmp = snap ?: return
        if (playing) return
        playing = true
        main.removeCallbacks(clearFrost)
        removeFrost()
        val ctx: Context = createDisplayContext(display)
        val wm = ctx.getSystemService(WindowManager::class.java)
        val dur = Prefs.duration(this).toLong()
        val blurMax = 8f + Prefs.blur(this) * 0.5f          // px
        val behindMax = (Prefs.blur(this) * 1.2f).toInt()    // px, blur of the live screen underneath
        val stretch = Prefs.stretch(this) / 500f             // 0..0.2 extra scale

        val view = ImageView(ctx)
        view.scaleType = ImageView.ScaleType.FIT_XY
        view.setImageBitmap(bmp)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
            PixelFormat.TRANSLUCENT,
        )
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        lp.blurBehindRadius = behindMax

        try {
            wm.addView(view, lp)
        } catch (t: Throwable) {
            Diag.log("Overlay failed: ${t.javaClass.simpleName}")
            playing = false
            return
        }
        if (Prefs.haptic(this)) {
            try {
                getSystemService(Vibrator::class.java)
                    ?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } catch (_: Throwable) {}
        }

        val ease = PathInterpolator(0.22f, 0.85f, 0.2f, 1f)
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = dur
        anim.addUpdateListener { a ->
            val p = a.animatedValue as Float
            val e = ease.getInterpolation(p)
            val fade = (p * 1.6f).coerceIn(0f, 1f)
            view.alpha = 1f - fade * fade * (3f - 2f * fade)
            val sc = 1f + stretch * e
            view.scaleX = sc
            view.scaleY = sc
            val r = (e * blurMax).coerceAtLeast(0.1f)
            try { view.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)) } catch (_: Throwable) {}
            lp.blurBehindRadius = (behindMax * (1f - e)).toInt()
            try { wm.updateViewLayout(view, lp) } catch (_: Throwable) {}
        }
        anim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                try { wm.removeView(view) } catch (_: Throwable) {}
                snap = null
                playing = false
            }
        })
        anim.start()
    }

    companion object {
        @Volatile
        var instance: FoldService? = null
    }
}
