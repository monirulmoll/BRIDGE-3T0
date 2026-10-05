package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.AutoBridgeApplication
import com.example.MainActivity
import com.example.R

class BridgeBackgroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var clipboardManager: ClipboardManager? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("AutoBridge Active (Background & Screen-Off Ready)"))

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AutoBridge::BackgroundCpuWakeLock"
        )?.apply {
            setReferenceCounted(false)
        }

        // Apply wake lock if screen off execution is enabled
        updateWakeLockState()

        // Register screen on/off receiver to maintain wakefulness during screen-off
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(TAG, "Screen went OFF. Maintaining CPU awake state for AutoBridge.")
                        updateWakeLockState()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d(TAG, "Screen came ON.")
                        updateWakeLockState()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)

        isRunning = true
        Log.i(TAG, "BridgeBackgroundService started successfully")
    }

    private fun updateWakeLockState() {
        val settings = AutoBridgeApplication.instance.repository.currentSettings()
        if (settings.isBridgeEnabled && settings.screenOffExecution) {
            if (wakeLock?.isHeld != true) {
                try {
                    wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours max safeguard
                    Log.i(TAG, "Partial WakeLock acquired. CPU will stay active when screen is off.")
                } catch (e: Exception) {
                    Log.e(TAG, "Could not acquire wake lock: ${e.message}")
                }
            }
        } else {
            if (wakeLock?.isHeld == true) {
                try {
                    wakeLock?.release()
                    Log.i(TAG, "WakeLock released.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing wake lock: ${e.message}")
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AutoBridge Background Execution",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps AutoBridge active and responsive in background even when screen is off."
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoBridge Running")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        if (wakeLock?.isHeld == true) {
            try {
                wakeLock?.release()
            } catch (_: Exception) {}
        }
        Log.i(TAG, "BridgeBackgroundService destroyed")
    }

    companion object {
        private const val TAG = "AutoBridgeBgService"
        const val CHANNEL_ID = "autobridge_bg_channel"
        const val NOTIFICATION_ID = 2024

        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, BridgeBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BridgeBackgroundService::class.java)
            context.stopService(intent)
        }
    }
}
