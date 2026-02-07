package com.tds.binarystars.storage

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.tds.binarystars.model.ChatMessage
import com.tds.binarystars.model.ChatSummary

object ChatStorage {
    private const val DB_NAME = "binarystars_chat.db"
    private const val DB_VERSION = 1

    private const val TABLE_CHATS = "chats"
    private const val COLUMN_CHAT_DEVICE_ID = "chat_device_id"
    private const val COLUMN_LAST_MESSAGE = "last_message"
    private const val COLUMN_LAST_SENT_AT = "last_sent_at"

    private const val TABLE_MESSAGES = "messages"
    private const val COLUMN_MESSAGE_ID = "message_id"
    private const val COLUMN_SENDER_DEVICE_ID = "sender_device_id"
    private const val COLUMN_BODY = "body"
    private const val COLUMN_SENT_AT = "sent_at"
    private const val COLUMN_IS_OUTGOING = "is_outgoing"

    private var dbHelper: ChatDbHelper? = null

    fun init(context: Context) {
        if (dbHelper == null) {
            dbHelper = ChatDbHelper(context.applicationContext)
        }
    }

    fun upsertMessage(message: ChatMessage) {
        val db = dbHelper?.writableDatabase ?: return

        val values = ContentValues().apply {
            put(COLUMN_MESSAGE_ID, message.id)
            put(COLUMN_CHAT_DEVICE_ID, message.deviceId)
            put(COLUMN_SENDER_DEVICE_ID, message.senderDeviceId)
            put(COLUMN_BODY, message.body)
            put(COLUMN_SENT_AT, message.sentAt)
            put(COLUMN_IS_OUTGOING, if (message.isOutgoing) 1 else 0)
        }

        db.insertWithOnConflict(TABLE_MESSAGES, null, values, SQLiteDatabase.CONFLICT_IGNORE)

        val existing = db.rawQuery(
            "SELECT $COLUMN_LAST_SENT_AT FROM $TABLE_CHATS WHERE $COLUMN_CHAT_DEVICE_ID = ?",
            arrayOf(message.deviceId)
        )

        val shouldUpdate = existing.use {
            if (it.moveToFirst()) {
                message.sentAt >= it.getLong(0)
            } else {
                true
            }
        }

        if (shouldUpdate) {
            val chatValues = ContentValues().apply {
                put(COLUMN_CHAT_DEVICE_ID, message.deviceId)
                put(COLUMN_LAST_MESSAGE, message.body)
                put(COLUMN_LAST_SENT_AT, message.sentAt)
            }
            db.insertWithOnConflict(TABLE_CHATS, null, chatValues, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun getChats(): List<ChatSummary> {
        val db = dbHelper?.readableDatabase ?: return emptyList()
        val cursor = db.rawQuery(
            "SELECT $COLUMN_CHAT_DEVICE_ID, $COLUMN_LAST_MESSAGE, $COLUMN_LAST_SENT_AT FROM $TABLE_CHATS ORDER BY $COLUMN_LAST_SENT_AT DESC",
            null
        )

        return cursor.use { c ->
            val results = mutableListOf<ChatSummary>()
            while (c.moveToNext()) {
                results.add(
                    ChatSummary(
                        deviceId = c.getString(0),
                        lastMessage = c.getString(1),
                        lastSentAt = c.getLong(2)
                    )
                )
            }
            results
        }
    }

    fun getMessages(deviceId: String, beforeSentAt: Long?, limit: Int): List<ChatMessage> {
        val db = dbHelper?.readableDatabase ?: return emptyList()
        val args = mutableListOf<String>()
        val where = StringBuilder("$COLUMN_CHAT_DEVICE_ID = ?")
        args.add(deviceId)

        if (beforeSentAt != null) {
            where.append(" AND $COLUMN_SENT_AT < ?")
            args.add(beforeSentAt.toString())
        }

        val cursor = db.query(
            TABLE_MESSAGES,
            arrayOf(COLUMN_MESSAGE_ID, COLUMN_CHAT_DEVICE_ID, COLUMN_SENDER_DEVICE_ID, COLUMN_BODY, COLUMN_SENT_AT, COLUMN_IS_OUTGOING),
            where.toString(),
            args.toTypedArray(),
            null,
            null,
            "$COLUMN_SENT_AT DESC",
            limit.toString()
        )

        return cursor.use { c ->
            val results = mutableListOf<ChatMessage>()
            while (c.moveToNext()) {
                results.add(
                    ChatMessage(
                        id = c.getString(0),
                        deviceId = c.getString(1),
                        senderDeviceId = c.getString(2),
                        body = c.getString(3),
                        sentAt = c.getLong(4),
                        isOutgoing = c.getInt(5) == 1
                    )
                )
            }
            results.reversed()
        }
    }

    fun clearChat(deviceId: String) {
        val db = dbHelper?.writableDatabase ?: return
        db.delete(TABLE_MESSAGES, "$COLUMN_CHAT_DEVICE_ID = ?", arrayOf(deviceId))
        db.delete(TABLE_CHATS, "$COLUMN_CHAT_DEVICE_ID = ?", arrayOf(deviceId))
    }

    fun clearAll() {
        val db = dbHelper?.writableDatabase ?: return
        db.delete(TABLE_MESSAGES, null, null)
        db.delete(TABLE_CHATS, null, null)
    }

    private class ChatDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS $TABLE_CHATS (" +
                    "$COLUMN_CHAT_DEVICE_ID TEXT PRIMARY KEY, " +
                    "$COLUMN_LAST_MESSAGE TEXT NOT NULL, " +
                    "$COLUMN_LAST_SENT_AT INTEGER NOT NULL)"
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS $TABLE_MESSAGES (" +
                    "$COLUMN_MESSAGE_ID TEXT PRIMARY KEY, " +
                    "$COLUMN_CHAT_DEVICE_ID TEXT NOT NULL, " +
                    "$COLUMN_SENDER_DEVICE_ID TEXT NOT NULL, " +
                    "$COLUMN_BODY TEXT NOT NULL, " +
                    "$COLUMN_SENT_AT INTEGER NOT NULL, " +
                    "$COLUMN_IS_OUTGOING INTEGER NOT NULL, " +
                    "FOREIGN KEY($COLUMN_CHAT_DEVICE_ID) REFERENCES $TABLE_CHATS($COLUMN_CHAT_DEVICE_ID))"
            )

            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_messages_chat_sent ON $TABLE_MESSAGES ($COLUMN_CHAT_DEVICE_ID, $COLUMN_SENT_AT)"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // No-op for now
        }
    }
}
