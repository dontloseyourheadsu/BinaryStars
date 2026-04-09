package com.tds.binarystars.background

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.AuthTokenStore
import com.tds.binarystars.api.NotificationSyncAckRequestDto
import com.tds.binarystars.api.UpdateDeviceTelemetryRequest
import com.tds.binarystars.util.NetworkUtils
import com.tds.binarystars.util.NotificationUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class DeviceSyncForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (syncJob?.isActive == true) {
            return START_STICKY
        }

        syncJob = serviceScope.launch {
            var telemetryTick = 0
            while (isActive) {
                if (!AuthTokenStore.hasStoredSession()) {
                    stopSelf()
                    break
                }

                val deviceId = Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
                runSyncStep(deviceId, telemetryTick)

                telemetryTick = (telemetryTick + 1) % TELEMETRY_EVERY_N_TICKS
                delay(SYNC_INTERVAL_MS)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        syncJob?.cancel()
        super.onDestroy()
    }

    private suspend fun runSyncStep(deviceId: String, telemetryTick: Int) {
        if (!NetworkUtils.isOnline(applicationContext)) {
            return
        }

        try {
            val heartbeat = ApiClient.apiService.sendDeviceHeartbeat(deviceId)
            if (heartbeat.isSuccessful) {
                val body = heartbeat.body()
                if (body?.hasPendingNotificationSync == true) {
                    pullAndShowNotifications(deviceId)
                }
            }
        } catch (_: Exception) {
        }

        if (telemetryTick == 0) {
            pushTelemetry(deviceId)
        }
    }

    private suspend fun pullAndShowNotifications(deviceId: String) {
        try {
            val pullResponse = ApiClient.apiService.pullNotifications(deviceId)
            if (!pullResponse.isSuccessful) {
                return
            }

            val payload = pullResponse.body() ?: return
            payload.notifications.forEach { notification ->
                NotificationUtils.showNotification(applicationContext, notification.title, notification.body)
            }

            if (!(payload.notifications.isEmpty() && payload.hasPendingNotificationSync)) {
                ApiClient.apiService.ackNotificationSync(NotificationSyncAckRequestDto(deviceId))
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun pushTelemetry(deviceId: String) {
        val batteryLevel = readBatteryPercent() ?: 0
        val cpuLoadPercent = readCpuUsagePercent()
        val memoryPercent = readRamUsagePercent()
        val speedPair = sampleNetworkSpeedKbps()

        val uploadText = speedPair?.first?.let { "$it kbps" } ?: "0 kbps"
        val downloadText = speedPair?.second?.let { "$it kbps" } ?: "0 kbps"

        val available = (memoryPercent ?: 0) < 98

        try {
            ApiClient.apiService.updateDeviceTelemetry(
                deviceId,
                UpdateDeviceTelemetryRequest(
                    batteryLevel = batteryLevel.coerceIn(0, 100),
                    cpuLoadPercent = cpuLoadPercent,
                    isOnline = true,
                    isAvailable = available,
                    isSynced = true,
                    wifiUploadSpeed = uploadText,
                    wifiDownloadSpeed = downloadText
                )
            )
        } catch (_: Exception) {
        }
    }

    private fun readBatteryPercent(): Int? {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (level in 0..100) level else null
    }

    private fun readRamUsagePercent(): Int? {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        if (memoryInfo.totalMem <= 0L) {
            return null
        }

        val used = memoryInfo.totalMem - memoryInfo.availMem
        return ((used.toDouble() / memoryInfo.totalMem.toDouble()) * 100.0).roundToInt()
    }

    private suspend fun sampleNetworkSpeedKbps(): Pair<Int, Int>? {
        val rxStart = TrafficStats.getTotalRxBytes()
        val txStart = TrafficStats.getTotalTxBytes()
        if (rxStart == TrafficStats.UNSUPPORTED.toLong() || txStart == TrafficStats.UNSUPPORTED.toLong()) {
            return null
        }

        delay(1000)

        val rxEnd = TrafficStats.getTotalRxBytes()
        val txEnd = TrafficStats.getTotalTxBytes()
        if (rxEnd < rxStart || txEnd < txStart) {
            return null
        }

        val downKbps = (((rxEnd - rxStart) * 8.0) / 1000.0).roundToInt()
        val upKbps = (((txEnd - txStart) * 8.0) / 1000.0).roundToInt()
        return upKbps to downKbps
    }

    private suspend fun readCpuUsagePercent(): Int? {
        val first = readCpuStat() ?: return null
        delay(300)
        val second = readCpuStat() ?: return null

        val totalDelta = second.total - first.total
        val idleDelta = second.idle - first.idle
        if (totalDelta <= 0L) {
            return null
        }

        val busyRatio = 1.0 - (idleDelta.toDouble() / totalDelta.toDouble())
        return (busyRatio * 100.0).roundToInt().coerceIn(0, 100)
    }

    private fun readCpuStat(): CpuStat? {
        return try {
            val line = java.io.RandomAccessFile("/proc/stat", "r").use { file -> file.readLine() }
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size < 8 || parts[0] != "cpu") {
                return null
            }

            val user = parts[1].toLongOrNull() ?: return null
            val nice = parts[2].toLongOrNull() ?: return null
            val system = parts[3].toLongOrNull() ?: return null
            val idle = parts[4].toLongOrNull() ?: return null
            val iowait = parts[5].toLongOrNull() ?: 0L
            val irq = parts[6].toLongOrNull() ?: 0L
            val softirq = parts[7].toLongOrNull() ?: 0L
            val steal = parts.getOrNull(8)?.toLongOrNull() ?: 0L

            val total = user + nice + system + idle + iowait + irq + softirq + steal
            CpuStat(total = total, idle = idle + iowait)
        } catch (_: Exception) {
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "BinaryStars Background Sync",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("BinaryStars background sync")
            .setContentText("Receiving notifications and syncing device updates")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private data class CpuStat(val total: Long, val idle: Long)

    companion object {
        private const val CHANNEL_ID = "binary_stars_sync"
        private const val NOTIFICATION_ID = 3001
        private const val SYNC_INTERVAL_MS = 10_000L
        private const val TELEMETRY_EVERY_N_TICKS = 3

        fun start(context: Context) {
            val intent = Intent(context, DeviceSyncForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
