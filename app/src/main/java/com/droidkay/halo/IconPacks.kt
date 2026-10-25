package com.droidkay.halo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Nova / ADW-style icon packs: any app that answers the standard icon-pack intents and ships an
 * appfilter.xml mapping "ComponentInfo{package/activity}" to a drawable.
 */
object IconPacks {
    private val INTENTS = listOf(
        "com.novalauncher.THEME",
        "org.adw.launcher.THEMES",
        "org.adw.launcher.icons.ACTION_PICK_ICON",
        "com.gau.go.launcherex.theme",
        "com.teslacoilsw.launcher.THEME",
    )

    data class PackInfo(val pkg: String, val label: String)

    class Pack(val pkg: String, val res: Resources, val map: Map<String, String>) {
        fun drawable(component: ComponentName): Drawable? {
            val name = map[component.flattenToString()] ?: map[component.packageName] ?: return null
            @Suppress("DiscouragedApi")
            val id = res.getIdentifier(name, "drawable", pkg)
            if (id == 0) return null
            return runCatching { res.getDrawableForDensity(id, DisplayMetrics.DENSITY_XXXHIGH, null) }.getOrNull()
        }
    }

    fun installed(context: Context): List<PackInfo> {
        val pm = context.packageManager
        return INTENTS.flatMap { action ->
            runCatching {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA)
            }.getOrDefault(emptyList())
        }
            .map { it.activityInfo.packageName }
            .distinct()
            .map { pkg -> PackInfo(pkg, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)) }
            .sortedBy { it.label.lowercase() }
    }

    private var cached: Pack? = null

    fun load(context: Context, pkg: String): Pack? {
        if (pkg.isEmpty()) return null
        cached?.let { if (it.pkg == pkg) return it }
        val pack = runCatching {
            val res = context.packageManager.getResourcesForApplication(pkg)
            val map = HashMap<String, String>()
            @Suppress("DiscouragedApi")
            val xmlId = res.getIdentifier("appfilter", "xml", pkg)
            val parser: XmlPullParser = if (xmlId != 0) {
                res.getXml(xmlId)
            } else {
                val stream = context.createPackageContext(pkg, 0).assets.open("appfilter.xml")
                XmlPullParserFactory.newInstance().newPullParser().apply { setInput(stream, "utf-8") }
            }
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val draw = parser.getAttributeValue(null, "drawable")
                    if (comp != null && draw != null && comp.startsWith("ComponentInfo{") && comp.endsWith("}")) {
                        val inner = comp.removePrefix("ComponentInfo{").removeSuffix("}")
                        val cn = ComponentName.unflattenFromString(inner)
                        if (cn != null) {
                            map.putIfAbsent(cn.flattenToString(), draw)
                            map.putIfAbsent(cn.packageName, draw)
                        }
                    }
                }
                ev = parser.next()
            }
            Pack(pkg, res, map)
        }.getOrNull()
        cached = pack
        return pack
    }
}

/**
 * Renders the icon Halo shows for [app]: the icon pack's version (drawn as-is) when there is one,
 * otherwise the app's own icon cut to the chosen shape.
 */
fun renderAppIcon(context: Context, app: AppInfo, size: Int): Bitmap {
    val pack = IconPacks.load(context, HaloSettings.iconPack)
    pack?.drawable(app.component)?.let { d ->
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(bmp))
        return bmp
    }
    val square = renderIcon(app.info.getIcon(DisplayMetrics.DENSITY_XXXHIGH), size)
    return maskIcon(square, HaloSettings.iconShape)
}

private fun maskIcon(src: Bitmap, shapeName: String): Bitmap {
    val size = src.width
    val outline = iconShapeFor(shapeName).createOutline(Size(size.toFloat(), size.toFloat()), LayoutDirection.Ltr, Density(1f))
    val path = Path().apply { addOutline(outline) }.asAndroidPath()
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawPath(path, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(src, 0f, 0f, paint)
    src.recycle()
    return out
}
