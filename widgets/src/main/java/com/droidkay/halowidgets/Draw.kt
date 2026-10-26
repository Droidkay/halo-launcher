package com.droidkay.halowidgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Pixel-perfect pieces of the widgets that RemoteViews can't draw itself (rings, month grid, symbols). */
object Draw {
    const val RED = 0xFFFF3B30.toInt()
    const val GREEN = 0xFF34C759.toInt()
    const val ORANGE = 0xFFFF9500.toInt()

    fun px(ctx: Context, dp: Float) = (dp * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun bitmap(ctx: Context, wDp: Float, hDp: Float): Pair<Bitmap, Canvas> {
        val b = Bitmap.createBitmap(px(ctx, wDp), px(ctx, hDp), Bitmap.Config.ARGB_8888)
        return b to Canvas(b)
    }

    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    // ------------------------------------------------------------------ battery ring

    fun batteryRing(ctx: Context, sizeDp: Float, percent: Int, charging: Boolean, pal: Palette): Bitmap {
        val (b, c) = bitmap(ctx, sizeDp, sizeDp)
        val s = b.width.toFloat()
        val stroke = s * 0.1f
        val r = RectF(stroke / 2, stroke / 2, s - stroke / 2, s - stroke / 2)
        val track = paint(pal.track).apply { style = Paint.Style.STROKE; strokeWidth = stroke }
        c.drawArc(r, 0f, 360f, false, track)
        val ringColor = if (!charging && percent in 0..20) RED else GREEN
        val arc = paint(ringColor).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        c.drawArc(r, -90f, 360f * (percent.coerceIn(0, 100) / 100f), false, arc)
        val text = paint(pal.text).apply {
            textAlign = Paint.Align.CENTER
            textSize = s * 0.2f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val label = if (percent < 0) "--" else "$percent%"
        val cy = s / 2 - (text.descent() + text.ascent()) / 2
        c.drawText(label, s / 2, cy + (if (charging) s * 0.06f else 0f), text)
        if (charging) bolt(c, s / 2, s * 0.3f, s * 0.11f, ringColor)
        return b
    }

    private fun bolt(c: Canvas, cx: Float, cy: Float, h: Float, color: Int) {
        val p = Path().apply {
            moveTo(cx + h * 0.15f, cy - h / 2)
            lineTo(cx - h * 0.35f, cy + h * 0.08f)
            lineTo(cx - h * 0.02f, cy + h * 0.08f)
            lineTo(cx - h * 0.15f, cy + h / 2)
            lineTo(cx + h * 0.35f, cy - h * 0.08f)
            lineTo(cx + h * 0.02f, cy - h * 0.08f)
            close()
        }
        c.drawPath(p, paint(color))
    }

    // ------------------------------------------------------------------ month grid

    fun monthGrid(ctx: Context, wDp: Float, hDp: Float, today: LocalDate, pal: Palette, withTitle: Boolean): Bitmap {
        val (b, c) = bitmap(ctx, wDp, hDp)
        val w = b.width.toFloat()
        val h = b.height.toFloat()
        val locale = Locale.getDefault()
        val ym = YearMonth.from(today)
        val first = WeekFields.of(locale).firstDayOfWeek
        val offset = (ym.atDay(1).dayOfWeek.value - first.value + 7) % 7
        val days = ym.lengthOfMonth()
        val rows = (offset + days + 6) / 7
        var top = 0f
        val font = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        if (withTitle) {
            val t = paint(RED).apply { textSize = h * 0.085f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
            c.drawText(today.month.getDisplayName(TextStyle.FULL, locale).uppercase(locale), w * 0.02f, -t.ascent(), t)
            top = t.descent() - t.ascent() + h * 0.02f
        }
        val headerH = (h - top) / (rows + 1)
        val cellW = w / 7f
        val head = paint(pal.secondary).apply { textAlign = Paint.Align.CENTER; textSize = min(headerH * 0.48f, cellW * 0.42f); typeface = font }
        for (i in 0 until 7) {
            val label = first.plus(i.toLong()).getDisplayName(TextStyle.NARROW, locale)
            c.drawText(label, cellW * (i + 0.5f), top + headerH / 2 - (head.descent() + head.ascent()) / 2, head)
        }
        val num = paint(pal.text).apply { textAlign = Paint.Align.CENTER; textSize = min(headerH * 0.5f, cellW * 0.44f); typeface = font }
        val todayPaint = paint(Color.WHITE).apply { textAlign = Paint.Align.CENTER; textSize = num.textSize; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        for (d in 1..days) {
            val idx = offset + d - 1
            val col = idx % 7
            val row = idx / 7
            val cx = cellW * (col + 0.5f)
            val cy = top + headerH * (row + 1.5f)
            if (d == today.dayOfMonth) {
                c.drawCircle(cx, cy, min(headerH, cellW) * 0.42f, paint(RED))
                c.drawText(d.toString(), cx, cy - (todayPaint.descent() + todayPaint.ascent()) / 2, todayPaint)
            } else {
                c.drawText(d.toString(), cx, cy - (num.descent() + num.ascent()) / 2, num)
            }
        }
        return b
    }

    // ------------------------------------------------------------------ weather symbols

    fun weatherIcon(ctx: Context, sizeDp: Float, code: Int, isDay: Boolean): Bitmap {
        val (b, c) = bitmap(ctx, sizeDp, sizeDp)
        val s = b.width.toFloat()
        val sun = 0xFFFFD60A.toInt()
        fun drawSun(cx: Float, cy: Float, r: Float) {
            c.drawCircle(cx, cy, r, paint(sun))
            val ray = paint(sun).apply { strokeWidth = r * 0.28f; strokeCap = Paint.Cap.ROUND }
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                c.drawLine(
                    cx + (cos(a) * r * 1.4f).toFloat(), cy + (sin(a) * r * 1.4f).toFloat(),
                    cx + (cos(a) * r * 1.8f).toFloat(), cy + (sin(a) * r * 1.8f).toFloat(), ray,
                )
            }
        }
        fun drawMoon(cx: Float, cy: Float, r: Float) {
            val disc = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
            val bite = Path().apply { addCircle(cx + r * 0.65f, cy - r * 0.25f, r, Path.Direction.CW) }
            disc.op(bite, Path.Op.DIFFERENCE)
            c.drawPath(disc, paint(Color.WHITE))
        }
        fun drawCloud(top: Float) {
            val p = paint(Color.WHITE)
            val base = top + s * 0.5f
            c.drawCircle(s * 0.3f, base - s * 0.02f, s * 0.17f, p)
            c.drawCircle(s * 0.52f, base - s * 0.12f, s * 0.23f, p)
            c.drawCircle(s * 0.74f, base, s * 0.15f, p)
            c.drawRoundRect(RectF(s * 0.13f, base - s * 0.02f, s * 0.89f, base + s * 0.15f), s * 0.085f, s * 0.085f, p)
        }
        when (code) {
            0 -> if (isDay) drawSun(s / 2, s / 2, s * 0.22f) else drawMoon(s / 2, s / 2, s * 0.34f)
            1, 2 -> {
                if (isDay) drawSun(s * 0.62f, s * 0.32f, s * 0.15f) else drawMoon(s * 0.62f, s * 0.32f, s * 0.22f)
                drawCloud(s * 0.2f)
            }
            3, 45, 48 -> drawCloud(s * 0.08f)
            in 51..67, in 80..82 -> {
                drawCloud(-s * 0.05f)
                val drop = paint(0xFF64D2FF.toInt()).apply { strokeWidth = s * 0.07f; strokeCap = Paint.Cap.ROUND }
                for (i in 0 until 3) {
                    val x = s * (0.32f + i * 0.18f)
                    c.drawLine(x, s * 0.72f, x - s * 0.06f, s * 0.92f, drop)
                }
            }
            in 71..77, 85, 86 -> {
                drawCloud(-s * 0.05f)
                for (i in 0 until 3) c.drawCircle(s * (0.32f + i * 0.18f), s * 0.82f, s * 0.05f, paint(Color.WHITE))
            }
            in 95..99 -> {
                drawCloud(-s * 0.05f)
                bolt(c, s * 0.52f, s * 0.8f, s * 0.38f, sun)
            }
            else -> drawCloud(s * 0.08f)
        }
        return b
    }

    // ------------------------------------------------------------------ Google buttons

    fun googleG(ctx: Context, sizeDp: Float): Bitmap {
        val (b, c) = bitmap(ctx, sizeDp, sizeDp)
        val s = b.width.toFloat()
        val stroke = s * 0.2f
        val r = RectF(stroke / 2, stroke / 2, s - stroke / 2, s - stroke / 2)
        fun arc(color: Int, start: Float, sweep: Float) =
            c.drawArc(r, start, sweep, false, paint(color).apply { style = Paint.Style.STROKE; strokeWidth = stroke })
        arc(0xFF4285F4.toInt(), -8f, 58f)
        arc(0xFF34A853.toInt(), 50f, 92f)
        arc(0xFFFBBC05.toInt(), 142f, 70f)
        arc(0xFFEA4335.toInt(), 212f, 105f)
        c.drawRect(s / 2, s / 2 - stroke / 2, s - stroke / 2 + stroke / 2, s / 2 + stroke / 2, paint(0xFF4285F4.toInt()))
        return b
    }

    fun mic(ctx: Context, sizeDp: Float, color: Int): Bitmap {
        val (b, c) = bitmap(ctx, sizeDp, sizeDp)
        val s = b.width.toFloat()
        val cx = s / 2
        val w = s * 0.34f
        c.drawRoundRect(RectF(cx - w / 2, 0f, cx + w / 2, s * 0.6f), w / 2, w / 2, paint(color))
        val st = paint(color).apply { style = Paint.Style.STROKE; strokeWidth = s * 0.09f; strokeCap = Paint.Cap.ROUND }
        val r = s * 0.3f
        c.drawArc(RectF(cx - r, s * 0.58f - r - s * 0.02f, cx + r, s * 0.58f + r - s * 0.02f), 0f, 180f, false, st)
        c.drawLine(cx, s * 0.87f, cx, s * 0.97f, st)
        return b
    }

    fun lens(ctx: Context, sizeDp: Float, color: Int): Bitmap {
        val (b, c) = bitmap(ctx, sizeDp, sizeDp)
        val s = b.width.toFloat()
        val sw = s * 0.1f
        val st = paint(color).apply { style = Paint.Style.STROKE; strokeWidth = sw; strokeCap = Paint.Cap.ROUND }
        val i = sw / 2
        val k = s * 0.3f
        // Lens frame: three rounded corners, the fourth is the dot.
        c.drawArc(RectF(i, i, i + 2 * k, i + 2 * k), 180f, 90f, false, st)
        c.drawLine(i, i + k, i, s * 0.55f, st)
        c.drawLine(i + k, i, s * 0.55f, i, st)
        c.drawArc(RectF(s - i - 2 * k, i, s - i, i + 2 * k), 270f, 90f, false, st)
        c.drawLine(s - i, i + k, s - i, s * 0.45f, st)
        c.drawArc(RectF(i, s - i - 2 * k, i + 2 * k, s - i), 90f, 90f, false, st)
        c.drawLine(i + k, s - i, s * 0.45f, s - i, st)
        c.drawCircle(s / 2, s / 2, s * 0.16f, st)
        c.drawCircle(s * 0.84f, s * 0.84f, s * 0.1f, paint(color))
        return b
    }

    fun aiSearch(ctx: Context, sizeDp: Float, color: Int): Bitmap {
        val (b, c) = bitmap(ctx, sizeDp, sizeDp)
        val s = b.width.toFloat()
        val st = paint(color).apply { style = Paint.Style.STROKE; strokeWidth = s * 0.1f; strokeCap = Paint.Cap.ROUND }
        c.drawCircle(s * 0.4f, s * 0.46f, s * 0.27f, st)
        c.drawLine(s * 0.6f, s * 0.66f, s * 0.9f, s * 0.95f, st)
        val cx = s * 0.85f
        val cy = s * 0.14f
        val r = s * 0.15f
        val p = Path().apply {
            moveTo(cx, cy - r)
            quadTo(cx, cy, cx + r, cy)
            quadTo(cx, cy, cx, cy + r)
            quadTo(cx, cy, cx - r, cy)
            quadTo(cx, cy, cx, cy - r)
            close()
        }
        c.drawPath(p, paint(0xFF4285F4.toInt()))
        return b
    }
}
