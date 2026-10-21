package com.droidkay.halo

import android.graphics.BlendMode
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

/** Hermite smoothstep: soft start, soft finish. */
fun smooth(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** How far into the fold motion the current screen is, 0 (settled) .. 1 (fully frosted). */
private fun foldAmount(mode: LayoutMode, angle: Float): Float =
    if (mode == LayoutMode.COVER) smooth((angle - 6f) / 50f)   // cover: frosts as you start opening
    else smooth((172f - angle) / 112f)                          // inner: swinging half clears as it lands flat

/**
 * A 0..1 fold value that tracks the hinge directly — no springs, no overshoot.
 * It follows the sensor with a frame-rate-independent ease (~60 ms lag), so it's glued to your hand
 * at 120 Hz, and it only runs while something is actually changing.
 */
@Composable
fun rememberFoldAmount(mode: LayoutMode): State<Float> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val hinge = remember { mutableFloatStateOf(0f) }
    val entry = remember { Animatable(1f) }
    val value = remember { mutableFloatStateOf(1f) }

    // The screen you land on starts frosted and clears (the hinge keeps it frosted if you're still opening).
    LaunchedEffect(Unit) {
        withFrameNanos { }
        entry.animateTo(0f, tween(520, delayMillis = 60, easing = FastOutSlowInEasing))
    }

    // Follower: eases `value` toward the target each frame, then sleeps until the target moves again.
    LaunchedEffect(Unit) {
        while (true) {
            snapshotFlow { maxOf(hinge.floatValue, entry.value) }
                .first { abs(it - value.floatValue) > 0.002f }
            var last = withFrameNanos { it }
            while (true) {
                val target = maxOf(hinge.floatValue, entry.value)
                if (abs(target - value.floatValue) <= 0.002f) {
                    value.floatValue = target
                    break
                }
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                val k = 1f - exp(-dt * 16f)
                value.floatValue += (target - value.floatValue) * k
            }
        }
    }

    DisposableEffect(mode, lifecycle) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        if (sm == null || sensor == null) return@DisposableEffect onDispose { }
        var last = -1f
        var settle: Job? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val angle = e.values[0]
                val moving = last >= 0f && abs(angle - last) > 0.4f
                last = angle
                val amount = foldAmount(mode, angle)
                if (amount <= 0f) {
                    settle?.cancel()
                    hinge.floatValue = 0f
                } else if (moving) {
                    hinge.floatValue = amount
                    // Clears once the hinge rests (Flex Mode / propped half-open stays sharp).
                    settle?.cancel()
                    settle = scope.launch {
                        delay(600)
                        hinge.floatValue = 0f
                    }
                }
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    last = -1f
                    sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                }
                Lifecycle.Event.ON_STOP -> {
                    sm.unregisterListener(listener)
                    settle?.cancel()
                    hinge.floatValue = 0f
                }
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            sm.unregisterListener(listener)
            settle?.cancel()
        }
    }
    return value
}

/**
 * Apple "Duo" half-panel blur for the inner screen: the half that's still swinging is frosted,
 * feathering across the crease into the sharp half. One GPU pass; shaders compiled once.
 */
@android.annotation.SuppressLint("NewApi")
class HalfPanelBlur {
    private val sharp: RuntimeShader?
    private val frosted: RuntimeShader?

    init {
        if (Build.VERSION.SDK_INT >= 33) {
            sharp = RuntimeShader(SHADER)
            frosted = RuntimeShader(SHADER)
        } else {
            sharp = null
            frosted = null
        }
    }

    /**
     * @param amount 0..1 fold progress
     * @param horizontalCrease true in landscape (crease runs across; the top half swings)
     */
    fun effect(amount: Float, blurPx: Float, width: Float, height: Float, horizontalCrease: Boolean): RenderEffect? {
        if (amount <= 0.003f || blurPx < 0.5f || width <= 0f || height <= 0f) return null
        val blur = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
        if (Build.VERSION.SDK_INT < 33 || sharp == null || frosted == null) return blur
        val axis = if (horizontalCrease) 1f else 0f
        // Feather moves with the motion: wide and soft early on, tucking into the crease as it lands.
        val featherStart = 0.50f - 0.16f * amount
        for ((shader, inv) in listOf(sharp to 1f, frosted to 0f)) {
            shader.setFloatUniform("size", width, height)
            shader.setFloatUniform("axis", axis)
            shader.setFloatUniform("amount", amount)
            shader.setFloatUniform("edge0", featherStart)
            shader.setFloatUniform("invert", inv)
        }
        val keepSharp = RenderEffect.createRuntimeShaderEffect(sharp, "content")
        val frostedHalf = RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(frosted, "content"),
            blur,
        )
        return RenderEffect.createBlendModeEffect(keepSharp, frostedHalf, BlendMode.PLUS)
    }

    companion object {
        private const val SHADER = """
            uniform shader content;
            uniform float2 size;
            uniform float axis;
            uniform float amount;
            uniform float edge0;
            uniform float invert;
            half4 main(float2 p) {
                float t = axis < 0.5 ? p.x / size.x : p.y / size.y;
                // m = 1 on the swinging half, feathering to 0 just past the crease.
                float m = 1.0 - smoothstep(edge0, 0.56, t);
                half4 c = content.eval(p);
                if (invert > 0.5) {
                    return c * half(1.0 - m);
                }
                // Frosted glass: a touch darker and softer in tone on the moving half.
                c.rgb *= half(1.0 - 0.10 * amount);
                return c * half(m);
            }
        """
    }
}

/**
 * Keeps the system wallpaper blur in step without per-frame window re-layouts:
 * updates only in coarse steps, which is what kept earlier builds smooth.
 */
@Composable
fun WallpaperBlurDriver(enabled: Boolean, radiusPx: () -> Float) {
    if (!enabled) return
    val activity = LocalContext.current.findActivityOrNull() ?: return
    LaunchedEffect(activity) {
        snapshotFlow { (radiusPx() / 18f).toInt() * 18 }
            .distinctUntilChanged()
            .collect { r ->
                val window = activity.window
                if (r > 0) window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val attrs = window.attributes
                attrs.blurBehindRadius = r.coerceAtLeast(0)
                window.attributes = attrs
                if (r <= 0) window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
    }
}
