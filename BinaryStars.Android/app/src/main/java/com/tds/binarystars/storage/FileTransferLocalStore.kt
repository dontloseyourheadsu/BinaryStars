package com.tds.binarystars.storage

import android.content.Context

object FileTransferLocalStore {
    private const val PREFS_NAME = "file_transfer_local"
    private const val KEY_PREFIX = "transfer_"
    private const val KEY_DIR_PREFIX = "direction_"

    fun setLocalPath(context: Context, transferId: String, path: String, direction: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PREFIX + transferId, path)
            .putString(KEY_DIR_PREFIX + transferId, direction)
            .apply()
    }

    fun getLocalPath(context: Context, transferId: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PREFIX + transferId, null)
    }

    fun getDirection(context: Context, transferId: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DIR_PREFIX + transferId, null)
    }

    fun clear(context: Context, transferId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_PREFIX + transferId)
            .remove(KEY_DIR_PREFIX + transferId)
            .apply()
    }
}
