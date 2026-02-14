package com.tds.binarystars.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Simple SQLite-backed store for app settings.
 */
object SettingsStorage {
    private const val DB_NAME = "binarystars_settings.db"
    private const val DB_VERSION = 1
    private const val TABLE_SETTINGS = "app_settings"
    private const val COLUMN_KEY = "key"
    private const val COLUMN_VALUE = "value"

    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_LOCATION_UPDATES_ENABLED = "location_updates_enabled"
    private const val KEY_LOCATION_UPDATE_MINUTES = "location_update_minutes"
    private const val KEY_DEVICE_TELEMETRY_ENABLED = "device_telemetry_enabled"

    private var dbHelper: SettingsDbHelper? = null

    /** Initializes the settings database helper. */
    fun init(context: Context) {
        if (dbHelper == null) {
            dbHelper = SettingsDbHelper(context.applicationContext)
        }
    }

    /** Persists the dark mode setting. */
    fun setDarkModeEnabled(enabled: Boolean) {
        val db = dbHelper?.writableDatabase ?: return
        val value = if (enabled) "1" else "0"
        db.execSQL(
            "INSERT OR REPLACE INTO $TABLE_SETTINGS ($COLUMN_KEY, $COLUMN_VALUE) VALUES (?, ?)",
            arrayOf(KEY_DARK_MODE, value)
        )
    }

    /** Reads the dark mode setting. */
    fun isDarkModeEnabled(defaultValue: Boolean = false): Boolean {
        val db = dbHelper?.readableDatabase ?: return defaultValue
        val cursor = db.rawQuery(
            "SELECT $COLUMN_VALUE FROM $TABLE_SETTINGS WHERE $COLUMN_KEY = ? LIMIT 1",
            arrayOf(KEY_DARK_MODE)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0) == "1"
            }
        }
        return defaultValue
    }

    /** Persists location update enablement. */
    fun setLocationUpdatesEnabled(enabled: Boolean) {
        val db = dbHelper?.writableDatabase ?: return
        val value = if (enabled) "1" else "0"
        db.execSQL(
            "INSERT OR REPLACE INTO $TABLE_SETTINGS ($COLUMN_KEY, $COLUMN_VALUE) VALUES (?, ?)",
            arrayOf(KEY_LOCATION_UPDATES_ENABLED, value)
        )
    }

    /** Reads location update enablement. */
    fun areLocationUpdatesEnabled(defaultValue: Boolean = false): Boolean {
        val db = dbHelper?.readableDatabase ?: return defaultValue
        val cursor = db.rawQuery(
            "SELECT $COLUMN_VALUE FROM $TABLE_SETTINGS WHERE $COLUMN_KEY = ? LIMIT 1",
            arrayOf(KEY_LOCATION_UPDATES_ENABLED)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0) == "1"
            }
        }
        return defaultValue
    }

    /** Stores the background update interval in minutes. */
    fun setLocationUpdateMinutes(minutes: Int) {
        val db = dbHelper?.writableDatabase ?: return
        db.execSQL(
            "INSERT OR REPLACE INTO $TABLE_SETTINGS ($COLUMN_KEY, $COLUMN_VALUE) VALUES (?, ?)",
            arrayOf(KEY_LOCATION_UPDATE_MINUTES, minutes.toString())
        )
    }

    /** Reads the background update interval in minutes. */
    fun getLocationUpdateMinutes(defaultValue: Int = 15): Int {
        val db = dbHelper?.readableDatabase ?: return defaultValue
        val cursor = db.rawQuery(
            "SELECT $COLUMN_VALUE FROM $TABLE_SETTINGS WHERE $COLUMN_KEY = ? LIMIT 1",
            arrayOf(KEY_LOCATION_UPDATE_MINUTES)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0).toIntOrNull() ?: defaultValue
            }
        }
        return defaultValue
    }

    /** Persists whether this device accepts telemetry/status requests. */
    fun setDeviceTelemetryEnabled(enabled: Boolean) {
        val db = dbHelper?.writableDatabase ?: return
        val value = if (enabled) "1" else "0"
        db.execSQL(
            "INSERT OR REPLACE INTO $TABLE_SETTINGS ($COLUMN_KEY, $COLUMN_VALUE) VALUES (?, ?)",
            arrayOf(KEY_DEVICE_TELEMETRY_ENABLED, value)
        )
    }

    /** Reads whether this device accepts telemetry/status requests. */
    fun isDeviceTelemetryEnabled(defaultValue: Boolean = true): Boolean {
        val db = dbHelper?.readableDatabase ?: return defaultValue
        val cursor = db.rawQuery(
            "SELECT $COLUMN_VALUE FROM $TABLE_SETTINGS WHERE $COLUMN_KEY = ? LIMIT 1",
            arrayOf(KEY_DEVICE_TELEMETRY_ENABLED)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0) == "1"
            }
        }
        return defaultValue
    }

    private class SettingsDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS $TABLE_SETTINGS (" +
                    "$COLUMN_KEY TEXT PRIMARY KEY, " +
                    "$COLUMN_VALUE TEXT NOT NULL)"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // No-op for now
        }
    }
}
