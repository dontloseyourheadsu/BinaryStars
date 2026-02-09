package com.tds.binarystars.bluetooth

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * SharedPreferences-backed store for Bluetooth transfer session metadata.
 */
object BluetoothTransferStore {
    private const val PREFS_NAME = "bluetooth_transfer_store"
    private const val KEY_SESSIONS = "sessions"

    private val gson = Gson()

    fun getAll(context: Context): Map<String, BluetoothTransferState> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SESSIONS, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, BluetoothTransferState>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }

    fun get(context: Context, transferId: String): BluetoothTransferState? {
        return getAll(context)[transferId]
    }

    fun upsert(context: Context, state: BluetoothTransferState) {
        val sessions = getAll(context).toMutableMap()
        sessions[state.transferId] = state
        persist(context, sessions)
    }

    fun remove(context: Context, transferId: String) {
        val sessions = getAll(context).toMutableMap()
        sessions.remove(transferId)
        persist(context, sessions)
    }

    fun update(context: Context, transferId: String, updater: (BluetoothTransferState) -> BluetoothTransferState) {
        val sessions = getAll(context).toMutableMap()
        val current = sessions[transferId] ?: return
        sessions[transferId] = updater(current)
        persist(context, sessions)
    }

    private fun persist(context: Context, sessions: Map<String, BluetoothTransferState>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_SESSIONS, gson.toJson(sessions))
            .apply()
    }
}

/**
 * Persisted Bluetooth transfer state used for resume/cancel flows.
 */
data class BluetoothTransferState(
    val transferId: String,
    val fileName: String,
    val contentType: String,
    val originalSizeBytes: Long,
    val encryptedSizeBytes: Long,
    val senderDeviceId: String,
    val senderDeviceName: String,
    val targetDeviceId: String,
    val targetDeviceName: String,
    val envelope: String,
    val encryptedPath: String?,
    val decryptedPath: String?,
    val bytesTransferred: Long,
    val status: String,
    val isSender: Boolean,
    val createdAt: String,
    val expiresAt: String
)
