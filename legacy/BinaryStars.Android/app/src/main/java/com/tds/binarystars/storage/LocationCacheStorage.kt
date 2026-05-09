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
    private const val KEY_PENDING_UPLOADS = "pending_uploads"

    private var prefs: SharedPreferences? = null
    private val gson = Gson()

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
        // No-op: Map history is only stored on the API now.
    }

    fun getLocalHistory(deviceId: String): List<LocationHistoryPoint> {
        // No-op: Map history is only fetched from the API now.
        return emptyList()
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
