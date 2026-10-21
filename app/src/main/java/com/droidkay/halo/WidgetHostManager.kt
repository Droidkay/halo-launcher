package com.droidkay.halo

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent

/** Hosts real Android widgets (Weather, Google, Calendar, etc.) on the home screen. */
class WidgetHostManager(private val activity: Activity) {
    private val host = AppWidgetHost(activity.applicationContext, HOST_ID)
    val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)

    private class Pending(val id: Int, val info: AppWidgetProviderInfo, val onAdded: (Int) -> Unit)

    private var pending: Pending? = null

    fun start() {
        runCatching { host.startListening() }
    }

    fun stop() {
        runCatching { host.stopListening() }
    }

    fun providers(): List<AppWidgetProviderInfo> = runCatching { manager.installedProviders }.getOrDefault(emptyList())

    fun requestAdd(info: AppWidgetProviderInfo, onAdded: (Int) -> Unit) {
        val id = host.allocateAppWidgetId()
        pending = Pending(id, info, onAdded)
        val bound = runCatching { manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }.getOrDefault(false)
        if (bound) {
            configureOrFinish()
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            try {
                @Suppress("DEPRECATION")
                activity.startActivityForResult(intent, REQ_BIND)
            } catch (e: Exception) {
                finish(false)
            }
        }
    }

    private fun configureOrFinish() {
        val p = pending ?: return
        val needsConfig = p.info.configure != null &&
            (p.info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) == 0
        if (needsConfig) {
            try {
                host.startAppWidgetConfigureActivityForResult(activity, p.id, 0, REQ_CONFIGURE, null)
            } catch (e: Exception) {
                finish(true)
            }
        } else {
            finish(true)
        }
    }

    /** Returns true when the result belonged to the widget flow. */
    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        when (requestCode) {
            REQ_BIND -> if (resultCode == Activity.RESULT_OK) configureOrFinish() else finish(false)
            REQ_CONFIGURE -> finish(resultCode == Activity.RESULT_OK)
            else -> return false
        }
        return true
    }

    private fun finish(ok: Boolean) {
        val p = pending ?: return
        pending = null
        if (ok) p.onAdded(p.id) else runCatching { host.deleteAppWidgetId(p.id) }
    }

    fun delete(id: Int) {
        runCatching { host.deleteAppWidgetId(id) }
    }

    fun createView(context: Context, id: Int): AppWidgetHostView? {
        val info = runCatching { manager.getAppWidgetInfo(id) }.getOrNull() ?: return null
        return runCatching { host.createView(context, id, info) }.getOrNull()
    }

    companion object {
        const val HOST_ID = 2026
        const val REQ_BIND = 41
        const val REQ_CONFIGURE = 42
    }
}
