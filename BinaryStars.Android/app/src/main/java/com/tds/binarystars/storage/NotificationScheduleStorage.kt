package com.tds.binarystars.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.tds.binarystars.api.NotificationScheduleResponse

/**
 * SQLite-backed cache for notification schedules.
 */
object NotificationScheduleStorage {
    private const val LOG_TAG = "BinaryStarsSchedules"
    private const val DB_NAME = "binarystars_schedules.db"
    private const val DB_VERSION = 1

    private const val TABLE_SCHEDULES = "schedules"
    private const val COLUMN_ID = "id"
    private const val COLUMN_SOURCE_DEVICE_ID = "source_device_id"
    private const val COLUMN_TARGET_DEVICE_ID = "target_device_id"
    private const val COLUMN_TITLE = "title"
    private const val COLUMN_BODY = "body"
    private const val COLUMN_IS_ENABLED = "is_enabled"
    private const val COLUMN_SCHEDULED_FOR_UTC = "scheduled_for_utc"
    private const val COLUMN_REPEAT_MINUTES = "repeat_minutes"
    private const val COLUMN_LAST_NOTIFIED_AT = "last_notified_at"

    private var dbHelper: ScheduleDbHelper? = null

    fun init(context: Context) {
        if (dbHelper == null) {
            dbHelper = ScheduleDbHelper(context.applicationContext)
        }
    }

    fun syncSchedules(schedules: List<NotificationScheduleResponse>) {
        Log.i(LOG_TAG, "Syncing ${schedules.size} schedules from server")
        val db = dbHelper?.writableDatabase ?: return
        db.beginTransaction()
        try {
            // 1. Get current IDs to see what was deleted
            val serverIds = schedules.map { it.id }.toSet()
            val localCursor = db.query(TABLE_SCHEDULES, arrayOf(COLUMN_ID), null, null, null, null, null)
            val localIds = mutableSetOf<String>()
            localCursor.use { c ->
                while (c.moveToNext()) {
                    localIds.add(c.getString(0))
                }
            }

            // 2. Remove deleted ones
            val deletedIds = localIds.filterNot { serverIds.contains(it) }
            if (deletedIds.isNotEmpty()) {
                Log.i(LOG_TAG, "Removing ${deletedIds.size} deleted schedules")
                deletedIds.forEach { id ->
                    db.delete(TABLE_SCHEDULES, "$COLUMN_ID = ?", arrayOf(id))
                }
            }

            // 3. Upsert new/updated ones
            schedules.forEach { schedule ->
                val values = ContentValues().apply {
                    put(COLUMN_ID, schedule.id)
                    put(COLUMN_SOURCE_DEVICE_ID, schedule.sourceDeviceId)
                    put(COLUMN_TARGET_DEVICE_ID, schedule.targetDeviceId)
                    put(COLUMN_TITLE, schedule.title)
                    put(COLUMN_BODY, schedule.body)
                    put(COLUMN_IS_ENABLED, if (schedule.isEnabled) 1 else 0)
                    put(COLUMN_SCHEDULED_FOR_UTC, schedule.scheduledForUtc)
                    put(COLUMN_REPEAT_MINUTES, schedule.repeatMinutes)
                }
                // insert or update. update preserves last_notified_at since it's not in values
                val affected = db.update(TABLE_SCHEDULES, values, "$COLUMN_ID = ?", arrayOf(schedule.id))
                if (affected == 0) {
                    db.insert(TABLE_SCHEDULES, null, values)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun upsertSchedules(schedules: List<NotificationScheduleResponse>) {
        syncSchedules(schedules)
    }

    fun getActiveSchedules(): List<LocalSchedule> {
        val db = dbHelper?.readableDatabase ?: return emptyList()
        val cursor = db.query(TABLE_SCHEDULES, null, "$COLUMN_IS_ENABLED = 1", null, null, null, null)
        return cursor.use { c ->
            val results = mutableListOf<LocalSchedule>()
            while (c.moveToNext()) {
                results.add(
                    LocalSchedule(
                        id = c.getString(c.getColumnIndexOrThrow(COLUMN_ID)),
                        title = c.getString(c.getColumnIndexOrThrow(COLUMN_TITLE)),
                        body = c.getString(c.getColumnIndexOrThrow(COLUMN_BODY)),
                        scheduledForUtc = c.getString(c.getColumnIndexOrThrow(COLUMN_SCHEDULED_FOR_UTC)),
                        repeatMinutes = if (c.isNull(c.getColumnIndexOrThrow(COLUMN_REPEAT_MINUTES))) null else c.getInt(c.getColumnIndexOrThrow(COLUMN_REPEAT_MINUTES)),
                        lastNotifiedAt = if (c.isNull(c.getColumnIndexOrThrow(COLUMN_LAST_NOTIFIED_AT))) 0L else c.getLong(c.getColumnIndexOrThrow(COLUMN_LAST_NOTIFIED_AT))
                    )
                )
            }
            results
        }
    }

    fun updateLastNotified(id: String, timestamp: Long) {
        val db = dbHelper?.writableDatabase ?: return
        val values = ContentValues().apply {
            put(COLUMN_LAST_NOTIFIED_AT, timestamp)
        }
        db.update(TABLE_SCHEDULES, values, "$COLUMN_ID = ?", arrayOf(id))
    }

    fun deleteSchedule(id: String) {
        val db = dbHelper?.writableDatabase ?: return
        db.delete(TABLE_SCHEDULES, "$COLUMN_ID = ?", arrayOf(id))
    }

    data class LocalSchedule(
        val id: String,
        val title: String,
        val body: String,
        val scheduledForUtc: String?,
        val repeatMinutes: Int?,
        val lastNotifiedAt: Long
    )

    private class ScheduleDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS $TABLE_SCHEDULES (" +
                    "$COLUMN_ID TEXT PRIMARY KEY, " +
                    "$COLUMN_SOURCE_DEVICE_ID TEXT, " +
                    "$COLUMN_TARGET_DEVICE_ID TEXT, " +
                    "$COLUMN_TITLE TEXT, " +
                    "$COLUMN_BODY TEXT, " +
                    "$COLUMN_IS_ENABLED INTEGER, " +
                    "$COLUMN_SCHEDULED_FOR_UTC TEXT, " +
                    "$COLUMN_REPEAT_MINUTES INTEGER, " +
                    "$COLUMN_LAST_NOTIFIED_AT INTEGER)"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }
}
