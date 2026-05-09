package com.tds.binarystars.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.tds.binarystars.api.NoteResponse
import com.tds.binarystars.api.NoteType

/**
 * SQLite-backed cache for notes to support offline access.
 */
object NotesStorage {
    private const val DB_NAME = "binarystars_notes.db"
    private const val DB_VERSION = 1

    private const val TABLE_NOTES = "notes"
    private const val COLUMN_ID = "note_id"
    private const val COLUMN_NAME = "name"
    private const val COLUMN_SIGNED_DEVICE_ID = "signed_device_id"
    private const val COLUMN_SIGNED_DEVICE_NAME = "signed_device_name"
    private const val COLUMN_CONTENT_TYPE = "content_type"
    private const val COLUMN_CONTENT = "content"
    private const val COLUMN_CREATED_AT = "created_at"
    private const val COLUMN_UPDATED_AT = "updated_at"

    private var dbHelper: NotesDbHelper? = null

    /** Initializes the notes database helper. */
    fun init(context: Context) {
        if (dbHelper == null) {
            dbHelper = NotesDbHelper(context.applicationContext)
        }
    }

    /** Upserts a list of notes in a transaction. */
    fun upsertNotes(notes: List<NoteResponse>) {
        val db = dbHelper?.writableDatabase ?: return
        db.beginTransaction()
        try {
            notes.forEach { note ->
                upsertNoteInternal(db, note)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Upserts a single note. */
    fun upsertNote(note: NoteResponse) {
        val db = dbHelper?.writableDatabase ?: return
        upsertNoteInternal(db, note)
    }

    /** Returns all cached notes sorted by updated time. */
    fun getNotes(): List<NoteResponse> {
        val db = dbHelper?.readableDatabase ?: return emptyList()
        val cursor = db.query(
            TABLE_NOTES,
            arrayOf(
                COLUMN_ID,
                COLUMN_NAME,
                COLUMN_SIGNED_DEVICE_ID,
                COLUMN_SIGNED_DEVICE_NAME,
                COLUMN_CONTENT_TYPE,
                COLUMN_CONTENT,
                COLUMN_CREATED_AT,
                COLUMN_UPDATED_AT
            ),
            null,
            null,
            null,
            null,
            "$COLUMN_UPDATED_AT DESC"
        )

        return cursor.use { c ->
            val results = mutableListOf<NoteResponse>()
            while (c.moveToNext()) {
                val contentTypeIndex = c.getInt(4)
                results.add(
                    NoteResponse(
                        id = c.getString(0),
                        name = c.getString(1),
                        signedByDeviceId = c.getString(2),
                        signedByDeviceName = c.getString(3),
                        contentType = NoteType.values()[contentTypeIndex],
                        content = c.getString(5),
                        createdAt = c.getString(6),
                        updatedAt = c.getString(7)
                    )
                )
            }
            results
        }
    }

    /** Deletes a cached note by ID. */
    fun deleteNote(noteId: String) {
        val db = dbHelper?.writableDatabase ?: return
        db.delete(TABLE_NOTES, "$COLUMN_ID = ?", arrayOf(noteId))
    }

    /** Clears all cached notes. */
    fun clearAll() {
        val db = dbHelper?.writableDatabase ?: return
        db.delete(TABLE_NOTES, null, null)
    }

    private fun upsertNoteInternal(db: SQLiteDatabase, note: NoteResponse) {
        val values = ContentValues().apply {
            put(COLUMN_ID, note.id)
            put(COLUMN_NAME, note.name)
            put(COLUMN_SIGNED_DEVICE_ID, note.signedByDeviceId)
            put(COLUMN_SIGNED_DEVICE_NAME, note.signedByDeviceName)
            put(COLUMN_CONTENT_TYPE, note.contentType.ordinal)
            put(COLUMN_CONTENT, note.content)
            put(COLUMN_CREATED_AT, note.createdAt)
            put(COLUMN_UPDATED_AT, note.updatedAt)
        }
        db.insertWithOnConflict(TABLE_NOTES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private class NotesDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS $TABLE_NOTES (" +
                    "$COLUMN_ID TEXT PRIMARY KEY, " +
                    "$COLUMN_NAME TEXT NOT NULL, " +
                    "$COLUMN_SIGNED_DEVICE_ID TEXT NOT NULL, " +
                    "$COLUMN_SIGNED_DEVICE_NAME TEXT, " +
                    "$COLUMN_CONTENT_TYPE INTEGER NOT NULL, " +
                    "$COLUMN_CONTENT TEXT NOT NULL, " +
                    "$COLUMN_CREATED_AT TEXT NOT NULL, " +
                    "$COLUMN_UPDATED_AT TEXT NOT NULL)"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // No-op for now
        }
    }
}
