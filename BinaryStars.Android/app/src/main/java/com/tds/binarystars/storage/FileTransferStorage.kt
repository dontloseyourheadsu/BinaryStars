package com.tds.binarystars.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite-backed cache for file transfer metadata.
 */
object FileTransferStorage {
    private const val DB_NAME = "binarystars_transfers.db"
    private const val DB_VERSION = 1

    private const val TABLE_TRANSFERS = "transfers"
    private const val COLUMN_ID = "transfer_id"
    private const val COLUMN_FILE_NAME = "file_name"
    private const val COLUMN_CONTENT_TYPE = "content_type"
    private const val COLUMN_SIZE_BYTES = "size_bytes"
    private const val COLUMN_SENDER_DEVICE_ID = "sender_device_id"
    private const val COLUMN_SENDER_DEVICE_NAME = "sender_device_name"
    private const val COLUMN_TARGET_DEVICE_ID = "target_device_id"
    private const val COLUMN_TARGET_DEVICE_NAME = "target_device_name"
    private const val COLUMN_STATUS = "status"
    private const val COLUMN_CREATED_AT = "created_at"
    private const val COLUMN_EXPIRES_AT = "expires_at"
    private const val COLUMN_IS_SENDER = "is_sender"

    private var dbHelper: TransfersDbHelper? = null

    /** Initializes the transfers database helper. */
    fun init(context: Context) {
        if (dbHelper == null) {
            dbHelper = TransfersDbHelper(context.applicationContext)
        }
    }

    /** Upserts a list of transfers in a transaction. */
    fun upsertTransfers(transfers: List<LocalFileTransfer>) {
        val db = dbHelper?.writableDatabase ?: return
        db.beginTransaction()
        try {
            transfers.forEach { transfer ->
                upsertTransferInternal(db, transfer)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Returns cached transfers ordered by creation time. */
    fun getTransfers(): List<LocalFileTransfer> {
        val db = dbHelper?.readableDatabase ?: return emptyList()
        val cursor = db.query(
            TABLE_TRANSFERS,
            arrayOf(
                COLUMN_ID,
                COLUMN_FILE_NAME,
                COLUMN_CONTENT_TYPE,
                COLUMN_SIZE_BYTES,
                COLUMN_SENDER_DEVICE_ID,
                COLUMN_SENDER_DEVICE_NAME,
                COLUMN_TARGET_DEVICE_ID,
                COLUMN_TARGET_DEVICE_NAME,
                COLUMN_STATUS,
                COLUMN_CREATED_AT,
                COLUMN_EXPIRES_AT,
                COLUMN_IS_SENDER
            ),
            null,
            null,
            null,
            null,
            "$COLUMN_CREATED_AT DESC"
        )

        return cursor.use { c ->
            val results = mutableListOf<LocalFileTransfer>()
            while (c.moveToNext()) {
                results.add(
                    LocalFileTransfer(
                        id = c.getString(0),
                        fileName = c.getString(1),
                        contentType = c.getString(2),
                        sizeBytes = c.getLong(3),
                        senderDeviceId = c.getString(4),
                        senderDeviceName = c.getString(5),
                        targetDeviceId = c.getString(6),
                        targetDeviceName = c.getString(7),
                        status = c.getString(8),
                        createdAt = c.getString(9),
                        expiresAt = c.getString(10),
                        isSender = c.getInt(11) == 1
                    )
                )
            }
            results
        }
    }

    private fun upsertTransferInternal(db: SQLiteDatabase, transfer: LocalFileTransfer) {
        val values = ContentValues().apply {
            put(COLUMN_ID, transfer.id)
            put(COLUMN_FILE_NAME, transfer.fileName)
            put(COLUMN_CONTENT_TYPE, transfer.contentType)
            put(COLUMN_SIZE_BYTES, transfer.sizeBytes)
            put(COLUMN_SENDER_DEVICE_ID, transfer.senderDeviceId)
            put(COLUMN_SENDER_DEVICE_NAME, transfer.senderDeviceName)
            put(COLUMN_TARGET_DEVICE_ID, transfer.targetDeviceId)
            put(COLUMN_TARGET_DEVICE_NAME, transfer.targetDeviceName)
            put(COLUMN_STATUS, transfer.status)
            put(COLUMN_CREATED_AT, transfer.createdAt)
            put(COLUMN_EXPIRES_AT, transfer.expiresAt)
            put(COLUMN_IS_SENDER, if (transfer.isSender) 1 else 0)
        }
        db.insertWithOnConflict(TABLE_TRANSFERS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private class TransfersDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS $TABLE_TRANSFERS (" +
                    "$COLUMN_ID TEXT PRIMARY KEY, " +
                    "$COLUMN_FILE_NAME TEXT NOT NULL, " +
                    "$COLUMN_CONTENT_TYPE TEXT NOT NULL, " +
                    "$COLUMN_SIZE_BYTES INTEGER NOT NULL, " +
                    "$COLUMN_SENDER_DEVICE_ID TEXT NOT NULL, " +
                    "$COLUMN_SENDER_DEVICE_NAME TEXT NOT NULL, " +
                    "$COLUMN_TARGET_DEVICE_ID TEXT NOT NULL, " +
                    "$COLUMN_TARGET_DEVICE_NAME TEXT NOT NULL, " +
                    "$COLUMN_STATUS TEXT NOT NULL, " +
                    "$COLUMN_CREATED_AT TEXT NOT NULL, " +
                    "$COLUMN_EXPIRES_AT TEXT NOT NULL, " +
                    "$COLUMN_IS_SENDER INTEGER NOT NULL)"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // No-op for now
        }
    }
}

/**
 * Local transfer metadata used by the file transfer UI.
 */
data class LocalFileTransfer(
    val id: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val senderDeviceId: String,
    val senderDeviceName: String,
    val targetDeviceId: String,
    val targetDeviceName: String,
    val status: String,
    val createdAt: String,
    val expiresAt: String,
    val isSender: Boolean
)
