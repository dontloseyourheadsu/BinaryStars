package com.tds.binarystars.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tds.binarystars.R

object NotificationUtils {

    private const val CHANNEL_ID = "binarystars_notifications"
    private const val LOG_TAG = "BinaryStarsNotifications"

    fun showNotification(context: Context, title: String, body: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.w(LOG_TAG, "OS notifications are disabled for app; dropping notification title=$title")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BinaryStars Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val id = System.currentTimeMillis().toInt()
        notificationManager.notify(id, notification)
        Log.i(LOG_TAG, "Notification posted locally id=$id title=$title")
    }
}
