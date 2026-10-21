package com.droidkay.halo

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle

data class AppInfo(
    val component: ComponentName,
    val label: String,
    val category: Int,
    val installTime: Long,
    val user: UserHandle,
    val info: LauncherActivityInfo,
) {
    val key: String = component.flattenToString()
    val pkg: String get() = component.packageName
}

/**
 * Draws an app icon as a full-bleed square so it can be clipped to the iOS-style squircle.
 * Adaptive icons are drawn edge to edge; old-style icons sit on a white tile.
 */
fun renderIcon(d: Drawable, size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    if (d is AdaptiveIconDrawable) {
        val inset = size / 4
        val bg = d.background
        if (bg == null) canvas.drawColor(Color.WHITE)
        bg?.let {
            it.setBounds(-inset, -inset, size + inset, size + inset)
            it.draw(canvas)
        }
        d.foreground?.let {
            it.setBounds(-inset, -inset, size + inset, size + inset)
            it.draw(canvas)
        }
    } else {
        canvas.drawColor(Color.WHITE)
        val pad = (size * 0.12f).toInt()
        d.setBounds(pad, pad, size - pad, size - pad)
        d.draw(canvas)
    }
    return bmp
}

/** App Library folders, sorted the way iOS sorts them. */
object LibraryCategories {
    val order = listOf(
        "Social",
        "Entertainment",
        "Creativity",
        "Productivity & Finance",
        "Utilities",
        "Information & Reading",
        "Travel",
        "Games",
        "Other",
    )

    private val social = listOf("whatsapp", "telegram", "facebook", "instagram", "twitter", "musically", "trill", "snapchat", "linkedin", "threads", "messenger", "discord", "signal", "wechat", "truecaller", "messaging", "dialer", "contacts")
    private val entertainment = listOf("youtube", "netflix", "spotify", "boomplay", "audiomack", "primevideo", "showmax", "music", "podcast", "twitch", "dstv", "disney")
    private val creativity = listOf("capcut", "lemon.lv", "camera", "gallery", "photo", "canva", "lightroom", "picsart", "inshot", "snapseed", "vsco", "video.editor", "elevenlabs")
    private val finance = listOf("bank", "opay", "palmpay", "moniepoint", "kuda", "gtbank", "accessbank", "zenith", "firstbank", "uba", "wallet", "binance", "chipper", "paystack", "pay", "office", "docs", "drive", "notion", "outlook", "calendar", "keep", "notes", "gm", "email", "claude", "chatgpt", "slack", "zoom", "teams", "sheets", "slides")
    private val utilities = listOf("chrome", "browser", "sbrowser", "firefox", "opera", "settings", "calculator", "clock", "files", "myfiles", "vending", "galaxystore", "smartthings", "weather", "vpn", "authenticator", "samsung", "sec.android", "google.android")
    private val reading = listOf("news", "reader", "kindle", "bible", "medium", "quora", "reddit", "wikipedia", "books")
    private val travel = listOf("maps", "uber", "bolt", "indrive", "booking", "airbnb", "airline", "waze")

    fun of(app: AppInfo): String {
        when (app.category) {
            ApplicationInfo.CATEGORY_SOCIAL -> return "Social"
            ApplicationInfo.CATEGORY_GAME -> return "Games"
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> return "Entertainment"
            ApplicationInfo.CATEGORY_IMAGE -> return "Creativity"
            ApplicationInfo.CATEGORY_NEWS -> return "Information & Reading"
            ApplicationInfo.CATEGORY_MAPS -> return "Travel"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> return "Productivity & Finance"
        }
        val p = app.pkg.lowercase()
        val parts = p.split('.')
        fun hit(words: List<String>) = words.any { w -> if (w.contains('.')) p.contains(w) else parts.any { it.contains(w) } }
        return when {
            hit(social) -> "Social"
            hit(entertainment) -> "Entertainment"
            hit(creativity) -> "Creativity"
            hit(finance) -> "Productivity & Finance"
            hit(reading) -> "Information & Reading"
            hit(travel) -> "Travel"
            hit(utilities) -> "Utilities"
            else -> "Other"
        }
    }
}
