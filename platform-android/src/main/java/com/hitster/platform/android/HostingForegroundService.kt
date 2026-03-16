package com.hitster.platform.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class HostingForegroundService : Service() {
    private var wifiLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                releaseNetworkLocks()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_START,
            null -> {
                ensureNotificationChannel()
                acquireNetworkLocks()
                startForeground(notificationId, createNotification())
            }

            else -> Unit
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseNetworkLocks()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, notificationChannelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle(getString(R.string.hosting_notification_title))
            .setContentText(getString(R.string.hosting_notification_body))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            notificationChannelId,
            getString(R.string.hosting_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.hosting_notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun acquireNetworkLocks() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        if (wifiLock == null) {
            wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "${packageName}:hosting-wifi",
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (multicastLock == null) {
            multicastLock = wifiManager.createMulticastLock("${packageName}:hosting-multicast").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseNetworkLocks() {
        multicastLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        multicastLock = null
        wifiLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wifiLock = null
    }

    companion object {
        private const val ACTION_START = "com.hitster.platform.android.action.START_HOSTING_SERVICE"
        private const val ACTION_STOP = "com.hitster.platform.android.action.STOP_HOSTING_SERVICE"
        private const val notificationChannelId = "hosting_session"
        private const val notificationId = 2001

        fun start(context: Context) {
            val intent = Intent(context, HostingForegroundService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, HostingForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
