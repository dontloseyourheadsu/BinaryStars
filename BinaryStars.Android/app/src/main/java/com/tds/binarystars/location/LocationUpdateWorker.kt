package com.tds.binarystars.location

import android.Manifest
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Tasks
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.LocationUpdateRequestDto
import com.tds.binarystars.storage.LocationCacheStorage
import com.tds.binarystars.util.NetworkUtils
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * WorkManager task for uploading device location in the background.
 */
class LocationUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    /**
     * Gets location data and posts it to the API.
     */
    override suspend fun doWork(): Result {
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission) {
            return Result.success()
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
            return Result.success()
        }

        val deviceId = Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
        val recordedAt = OffsetDateTime.now().toString()
        val request = LocationUpdateRequestDto(
            deviceId = deviceId,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy.toDouble(),
            recordedAt = recordedAt
        )

        LocationCacheStorage.saveLocalPoint(
            deviceId = deviceId,
            latitude = location.latitude,
            longitude = location.longitude,
            recordedAt = recordedAt
        )

        if (!NetworkUtils.isOnline(applicationContext) || !NetworkUtils.isWifiConnected(applicationContext)) {
            LocationCacheStorage.enqueuePendingUpload(request)
            return Result.success()
        }

        return try {
            val sentIds = mutableSetOf<String>()
            val pending = LocationCacheStorage.getPendingUploads(deviceId)
            for ((id, pendingRequest) in pending) {
                try {
                    ApiClient.apiService.sendLocation(pendingRequest)
                    sentIds.add(id)
                } catch (_: Exception) {
                    break
                }
            }
            LocationCacheStorage.removePendingUploadsByIds(sentIds)

            ApiClient.apiService.sendLocation(request)
            Result.success()
        } catch (_: Exception) {
            LocationCacheStorage.enqueuePendingUpload(request)
            Result.success()
        }
    }
}
