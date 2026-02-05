package com.tds.binarystars.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

object SettingsStorage {
    private const val DB_NAME = "binarystars_settings.db"
    private const val DB_VERSION = 1
    private const val TABLE_SETTINGS = "app_settings"
    private const val COLUMN_KEY = "key"
    private const val COLUMN_VALUE = "value"

    private const val KEY_DARK_MODE = "dark_mode"

    private var dbHelper: SettingsDbHelper? = null

    fun init(context: Context) {
        if (dbHelper == null) {
            dbHelper = SettingsDbHelper(context.applicationContext)
        }
    }

    fun setDarkModeEnabled(enabled: Boolean) {
        val db = dbHelper?.writableDatabase ?: return
        val value = if (enabled) "1" else "0"
        db.execSQL(
            "INSERT OR REPLACE INTO $TABLE_SETTINGS ($COLUMN_KEY, $COLUMN_VALUE) VALUES (?, ?)",
            arrayOf(KEY_DARK_MODE, value)
        )
    }

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
