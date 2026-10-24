package com.droidkay.kitafold

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/**
 * A quiet foreground service. Android stops sending hinge readings to apps it considers idle,
 * which is why the transition never fired; while this runs, Kita Fold counts as active.
 */
class KeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Kita Fold running", NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                }
            )
        }
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setContentTitle("Kita Fold is active")
            .setContentText("Watching the hinge for fold transitions")
            .setOngoing(true)
            .build()
        try {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            running = true
            Diag.log("Keep-alive running")
        } catch (t: Throwable) {
            Diag.log("Keep-alive failed: ${t.javaClass.simpleName}")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "kitafold"
        @Volatile var running = false

        fun start(c: Context) {
            if (running) return
            try {
                c.startForegroundService(Intent(c, KeepAliveService::class.java))
            } catch (t: Throwable) {
                Diag.log("Couldn't start keep-alive: ${t.javaClass.simpleName}")
            }
        }
    }
}
