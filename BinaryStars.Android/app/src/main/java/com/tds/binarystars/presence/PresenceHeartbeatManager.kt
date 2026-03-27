package com.tds.binarystars.presence

import android.content.Context
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.NotificationSyncAckRequestDto
import com.tds.binarystars.util.NotificationUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Sends periodic device heartbeat updates independent of telemetry/location settings.
 */
object PresenceHeartbeatManager {
    private const val HEARTBEAT_INTERVAL_MS = 10_000L

    private var heartbeatScope: CoroutineScope? = null
    private var heartbeatJob: Job? = null
    private var currentDeviceId: String? = null

    fun start(context: Context, deviceId: String) {
        if (heartbeatJob != null && currentDeviceId == deviceId) {
            return
        }

        stop()
        currentDeviceId = deviceId
        val appContext = context.applicationContext
        heartbeatScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        heartbeatJob = heartbeatScope?.launch {
            while (isActive) {
                try {
                    val response = ApiClient.apiService.sendDeviceHeartbeat(deviceId)
                    if (response.isSuccessful) {
                        val deviceDto = response.body()
                        if (deviceDto?.hasPendingNotificationSync == true) {
                            val pullResp = ApiClient.apiService.pullNotifications(deviceId)
                            if (pullResp.isSuccessful) {
                                val instant = pullResp.body()?.instant ?: emptyList()
                                val ackIds = mutableListOf<String>()
                                instant.forEach { msg ->
                                    NotificationUtils.showNotification(appContext, msg.title, msg.body)
                                    ackIds.add(msg.id)
                                }
                                if (ackIds.isNotEmpty()) {
                                    ApiClient.apiService.ackNotificationSync(NotificationSyncAckRequestDto(deviceId, ackIds))
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        heartbeatScope = null
        currentDeviceId = null
    }
}
