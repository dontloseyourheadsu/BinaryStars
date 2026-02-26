package com.tds.binarystars.storage

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tds.binarystars.api.LocationUpdateRequestDto
import com.tds.binarystars.model.LocationHistoryPoint
import java.util.UUID

object LocationCacheStorage {
    private const val PREFS_NAME = "location_cache_prefs"
    private const val KEY_LOCAL_HISTORY = "local_history"
    private const val KEY_PENDING_UPLOADS = "pending_uploads"

    private var prefs: SharedPreferences? = null
    private val gson = Gson()

    private data class CachedLocationPoint(
        val id: String,
        val deviceId: String,
        val title: String,
        val timestamp: String,
        val latitude: Double,
        val longitude: Double
    )

    private data class PendingLocationUpload(
        val id: String,
        val request: LocationUpdateRequestDto
    )

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun saveLocalPoint(deviceId: String, latitude: Double, longitude: Double, recordedAt: String) {
        val next = mutableListOf(
            CachedLocationPoint(
                id = UUID.randomUUID().toString(),
                deviceId = deviceId,
                title = "Local snapshot",
                timestamp = recordedAt,
                latitude = latitude,
                longitude = longitude
            )
        )

        next.addAll(readLocalHistory())

        val bounded = next
            .filter { it.deviceId == deviceId }
            .take(2_000)

        val others = readLocalHistory().filter { it.deviceId != deviceId }

        prefs?.edit()
            ?.putString(KEY_LOCAL_HISTORY, gson.toJson(others + bounded))
            ?.apply()
    }

    fun getLocalHistory(deviceId: String): List<LocationHistoryPoint> {
        return readLocalHistory()
            .filter { it.deviceId == deviceId }
            .sortedByDescending { it.timestamp }
            .map {
                LocationHistoryPoint(
                    id = it.id,
                    title = it.title,
                    timestamp = it.timestamp,
                    latitude = it.latitude,
                    longitude = it.longitude
                )
            }
    }

    fun enqueuePendingUpload(request: LocationUpdateRequestDto) {
        val current = readPendingUploads().toMutableList()
        current.add(
            PendingLocationUpload(
                id = UUID.randomUUID().toString(),
                request = request
            )
        )

        val bounded = current.takeLast(1_000)

        prefs?.edit()
            ?.putString(KEY_PENDING_UPLOADS, gson.toJson(bounded))
            ?.apply()
    }

    fun getPendingUploads(deviceId: String): List<Pair<String, LocationUpdateRequestDto>> {
        return readPendingUploads()
            .filter { it.request.deviceId == deviceId }
            .map { it.id to it.request }
    }

    fun removePendingUploadsByIds(ids: Set<String>) {
        if (ids.isEmpty()) {
            return
        }

        val filtered = readPendingUploads().filterNot { ids.contains(it.id) }
        prefs?.edit()
            ?.putString(KEY_PENDING_UPLOADS, gson.toJson(filtered))
            ?.apply()
    }

    private fun readLocalHistory(): List<CachedLocationPoint> {
        val raw = prefs?.getString(KEY_LOCAL_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<CachedLocationPoint>>() {}.type
            gson.fromJson<List<CachedLocationPoint>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readPendingUploads(): List<PendingLocationUpload> {
        val raw = prefs?.getString(KEY_PENDING_UPLOADS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<PendingLocationUpload>>() {}.type
            gson.fromJson<List<PendingLocationUpload>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
