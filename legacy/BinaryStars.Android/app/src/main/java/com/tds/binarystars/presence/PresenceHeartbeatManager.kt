package com.tds.binarystars.presence

import android.content.Context
import android.util.Log
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.AuthTokenStore
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
    private const val LOG_TAG = "BinaryStarsNotifications"

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
                if (!AuthTokenStore.hasStoredSession()) {
                    break
                }

                try {
                    val response = ApiClient.apiService.sendDeviceHeartbeat(deviceId)
                    if (!response.isSuccessful) {
                        Log.w(LOG_TAG, "Heartbeat failed: status=${response.code()} deviceId=$deviceId")
                    }
                } catch (e: Exception) {
                    Log.w(LOG_TAG, "Heartbeat exception: deviceId=$deviceId error=${e.message}")
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
