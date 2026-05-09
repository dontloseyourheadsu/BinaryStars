package com.tds.binarystars

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationTriggerInstrumentedTest {

    @Test
    fun postsNotificationThroughSystemNotificationCenter() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "instrumented_notifications"
        val notificationId = 91021

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.POST_NOTIFICATIONS)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Instrumentation notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                manager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("BinaryStars Android test")
                .setContentText("This verifies the official Notification Center API path")
                .setAutoCancel(true)
                .build()

            manager.notify(notificationId, notification)
            SystemClock.sleep(300)

            val active = manager.activeNotifications
            assertTrue(
                "Expected notification to be active in the Android notification center",
                active.any { it.id == notificationId }
            )
        } finally {
            manager.cancel(notificationId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                instrumentation.uiAutomation.dropShellPermissionIdentity()
            }
        }
    }
}
