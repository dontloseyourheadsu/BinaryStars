package com.tds.binarystars.storage

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tds.binarystars.model.Device

object DeviceCacheStorage {
    private const val PREFS_NAME = "device_cache_prefs"
    private const val KEY_DEVICES = "devices"

    private var prefs: SharedPreferences? = null
    private val gson = Gson()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun saveDevices(devices: List<Device>) {
        prefs?.edit()
            ?.putString(KEY_DEVICES, gson.toJson(devices))
            ?.apply()
    }

    fun getDevices(): List<Device> {
        val raw = prefs?.getString(KEY_DEVICES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Device>>() {}.type
            gson.fromJson<List<Device>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear() {
        prefs?.edit()
            ?.remove(KEY_DEVICES)
            ?.apply()
    }
}