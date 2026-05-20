package com.contextauth.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.contextauth.R
import com.contextauth.core.CollectionControlBus
import com.contextauth.core.LocaleText
import com.contextauth.ui.MainActivity

class DataCollectionService : Service() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "ContextAuthLab collection", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_COLLECTION) {
            CollectionControlBus.requestStop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(1001, notification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ContextAuthLab")
            .setContentText(LocaleText.pick("采集进行中。屏幕熄灭或锁屏时会暂停。", "Collection is running. It pauses when the screen is off or locked."))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START_COLLECTION = "com.contextauth.START_COLLECTION"
        const val ACTION_STOP_COLLECTION = "com.contextauth.STOP_COLLECTION"
        private const val CHANNEL_ID = "contextauthlab_collection"
    }
}
