package com.droidkay.kitafold

import android.content.Context

/** Settings plus a tiny in-memory log the main screen shows, so you can see what the service is doing. */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("kitafold", Context.MODE_PRIVATE)

    fun duration(c: Context): Int = sp(c).getInt("duration", 700)
    fun setDuration(c: Context, v: Int) = sp(c).edit().putInt("duration", v).apply()

    fun blur(c: Context): Int = sp(c).getInt("blur", 60)
    fun setBlur(c: Context, v: Int) = sp(c).edit().putInt("blur", v).apply()

    fun stretch(c: Context): Int = sp(c).getInt("stretch", 50)
    fun setStretch(c: Context, v: Int) = sp(c).edit().putInt("stretch", v).apply()

    fun frost(c: Context): Boolean = sp(c).getBoolean("frost", true)
    fun setFrost(c: Context, v: Boolean) = sp(c).edit().putBoolean("frost", v).apply()

    fun frostRight(c: Context): Boolean = sp(c).getBoolean("frostRight", false)
    fun setFrostRight(c: Context, v: Boolean) = sp(c).edit().putBoolean("frostRight", v).apply()

    fun haptic(c: Context): Boolean = sp(c).getBoolean("haptic", true)
    fun setHaptic(c: Context, v: Boolean) = sp(c).edit().putBoolean("haptic", v).apply()
}

object Diag {
    @Volatile var angle: Float = -1f
    @Volatile var hasSensor: Boolean = false
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun log(s: String) {
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        lines.addFirst("$t  $s")
        while (lines.size > 14) lines.removeLast()
    }

    @Synchronized
    fun text(): String = if (lines.isEmpty()) "(nothing yet)" else lines.joinToString("\n")
}
