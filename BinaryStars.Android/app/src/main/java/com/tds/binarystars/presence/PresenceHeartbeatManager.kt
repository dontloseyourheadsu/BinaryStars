package com.tds.binarystars.presence

import com.tds.binarystars.api.ApiClient
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
    private const val HEARTBEAT_INTERVAL_MS = 15_000L

    private var heartbeatScope: CoroutineScope? = null
    private var heartbeatJob: Job? = null
    private var currentDeviceId: String? = null

    fun start(deviceId: String) {
        if (heartbeatJob != null && currentDeviceId == deviceId) {
            return
        }

        stop()
        currentDeviceId = deviceId
        heartbeatScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        heartbeatJob = heartbeatScope?.launch {
            while (isActive) {
                try {
                    ApiClient.apiService.sendDeviceHeartbeat(deviceId)
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
