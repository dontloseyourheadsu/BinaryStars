package com.tds.binarystars.storage

import android.content.Context

/**
 * SharedPreferences-backed store for local transfer file paths.
 */
object FileTransferLocalStore {
    private const val PREFS_NAME = "file_transfer_local"
    private const val KEY_PREFIX = "transfer_"
    private const val KEY_DIR_PREFIX = "direction_"

    /** Stores the local path and direction for a transfer. */
    fun setLocalPath(context: Context, transferId: String, path: String, direction: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PREFIX + transferId, path)
            .putString(KEY_DIR_PREFIX + transferId, direction)
            .apply()
    }

    /** Returns the local path for a transfer if available. */
    fun getLocalPath(context: Context, transferId: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PREFIX + transferId, null)
    }

    /** Returns transfer direction metadata (sent/received). */
    fun getDirection(context: Context, transferId: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DIR_PREFIX + transferId, null)
    }

    /** Removes cached metadata for a transfer. */
    fun clear(context: Context, transferId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_PREFIX + transferId)
            .remove(KEY_DIR_PREFIX + transferId)
            .apply()
    }

    /** Clears all cached transfer metadata. */
    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
