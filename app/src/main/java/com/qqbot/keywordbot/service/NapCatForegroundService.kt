package com.qqbot.keywordbot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.qqbot.keywordbot.ui.MainActivity
import com.qqbot.keywordbot.R
import com.qqbot.keywordbot.napcat.NapCatManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 前台服务：保持 NapCat 容器在后台运行，防止被系统杀死。
 */
@AndroidEntryPoint
class NapCatForegroundService : Service() {

    @Inject
    lateinit var napCatManager: NapCatManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("NapCat 运行中"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> napCatManager.start()
            ACTION_STOP -> {
                napCatManager.stop()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        napCatManager.stop()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "韵雨 陪伴服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持陪伴机器人后台运行"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("韵雨 · 陪伴机器人")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "napcat_service"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.qqbot.keywordbot.START"
        const val ACTION_STOP = "com.qqbot.keywordbot.STOP"

        fun start(context: Context) {
            val intent = Intent(context, NapCatForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NapCatForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
