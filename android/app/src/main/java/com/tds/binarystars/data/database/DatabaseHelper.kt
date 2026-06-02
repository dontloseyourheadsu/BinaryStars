package com.tds.binarystars.data.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.tds.binarystars.domain.model.ChatMessage

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "binarystars.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_MESSAGES = "messages"
        private const val KEY_ID = "id"
        private const val KEY_PEER_ID = "peer_id"
        private const val KEY_SENDER = "sender"
        private const val KEY_CONTENT = "content"
        private const val KEY_IS_FILE = "is_file"
        private const val KEY_FILE_NAME = "file_name"
        private const val KEY_FILE_PATH = "file_path"
        private const val KEY_SENT_AT = "sent_at"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE " + TABLE_MESSAGES + "("
                + KEY_ID + " TEXT PRIMARY KEY,"
                + KEY_PEER_ID + " TEXT NOT NULL,"
                + KEY_SENDER + " TEXT NOT NULL,"
                + KEY_CONTENT + " TEXT NOT NULL,"
                + KEY_IS_FILE + " INTEGER NOT NULL,"
                + KEY_FILE_NAME + " TEXT,"
                + KEY_FILE_PATH + " TEXT,"
                + KEY_SENT_AT + " INTEGER NOT NULL" + ")")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES)
        onCreate(db)
    }

    fun insertMessage(peerId: String, msg: ChatMessage) {
        try {
            val db = this.writableDatabase
            val values = ContentValues().apply {
                put(KEY_ID, msg.id)
                put(KEY_PEER_ID, peerId)
                put(KEY_SENDER, msg.senderDeviceId)
                put(KEY_CONTENT, msg.body)
                put(KEY_IS_FILE, if (msg.isFile) 1 else 0)
                put(KEY_FILE_NAME, msg.fileName)
                put(KEY_FILE_PATH, msg.filePath)
                put(KEY_SENT_AT, msg.sentAt)
            }
            db.insertWithOnConflict(TABLE_MESSAGES, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        } catch (_: Exception) {}
    }

    fun getMessagesPaged(peerId: String, limit: Int, offset: Int): List<ChatMessage> {
        val list = mutableListOf<ChatMessage>()
        try {
            val db = this.readableDatabase
            val selectQuery = "SELECT * FROM $TABLE_MESSAGES WHERE $KEY_PEER_ID = ? ORDER BY $KEY_SENT_AT DESC LIMIT ? OFFSET ?"
            val cursor = db.rawQuery(selectQuery, arrayOf(peerId, limit.toString(), offset.toString()))

            if (cursor.moveToFirst()) {
                do {
                    val isFileInt = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_IS_FILE))
                    val senderId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_SENDER))
                    val peer = cursor.getString(cursor.getColumnIndexOrThrow(KEY_PEER_ID))
                    val isOutgoing = senderId != peer
                    
                    list.add(
                        ChatMessage(
                            id = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ID)),
                            deviceId = peer,
                            senderDeviceId = senderId,
                            body = cursor.getString(cursor.getColumnIndexOrThrow(KEY_CONTENT)),
                            sentAt = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_SENT_AT)),
                            isOutgoing = isOutgoing,
                            isFile = isFileInt == 1,
                            fileName = cursor.getString(cursor.getColumnIndexOrThrow(KEY_FILE_NAME)),
                            filePath = cursor.getString(cursor.getColumnIndexOrThrow(KEY_FILE_PATH))
                        )
                    )
                } while (cursor.moveToNext())
            }
            cursor.close()
        } catch (_: Exception) {}
        return list.reversed()
    }
}
