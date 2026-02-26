package com.tds.binarystars.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Tasks
import com.tds.binarystars.R
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.LocationUpdateRequestDto
import com.tds.binarystars.storage.SettingsStorage
import com.tds.binarystars.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

class LiveLocationService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var liveJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!SettingsStorage.areLocationUpdatesEnabled(false)) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (liveJob?.isActive == true) {
            return START_STICKY
        }

        liveJob = serviceScope.launch {
            while (isActive) {
                sendLiveLocationUpdate()
                delay(LIVE_INTERVAL_MS)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        liveJob?.cancel()
        super.onDestroy()
    }

    private suspend fun sendLiveLocationUpdate() {
        val hasPermission = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return
        }

        if (!NetworkUtils.isOnline(applicationContext) || !NetworkUtils.isWifiConnected(applicationContext)) {
            return
        }

        val client = LocationServices.getFusedLocationProviderClient(applicationContext)
        val location = try {
            val task = client.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token
            )
            Tasks.await(task, 10, TimeUnit.SECONDS)
        } catch (_: Exception) {
            null
        }

        if (location == null) {
            return
        }

        val request = LocationUpdateRequestDto(
            deviceId = Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID),
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy.toDouble(),
            recordedAt = OffsetDateTime.now().toString()
        )

        try {
            ApiClient.apiService.sendLiveLocation(request)
        } catch (_: Exception) {
            // no-op
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Live location sharing",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_map)
            .setContentTitle("BinaryStars")
            .setContentText("Sharing live location every 15 seconds")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "live_location_channel"
        private const val NOTIFICATION_ID = 2001
        private const val LIVE_INTERVAL_MS = 15_000L

        fun start(context: Context) {
            val intent = Intent(context, LiveLocationService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LiveLocationService::class.java)
            context.stopService(intent)
        }
    }
}
