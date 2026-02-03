package com.tds.binarystars.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.tds.binarystars.R
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.NoteType
import com.tds.binarystars.api.UpdateNoteRequestDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class NoteDetailActivity : AppCompatActivity() {
    private lateinit var tvNoteName: TextView
    private lateinit var tvDeviceInfo: TextView
    private lateinit var tvTimestamps: TextView
    private lateinit var contentDisplay: EditText
    private lateinit var btnEditDevice: Button
    private lateinit var btnEdit: Button
    private lateinit var progressBar: ProgressBar

    private var noteId: String = ""
    private var noteName: String = ""
    private var deviceId: String = ""
    private var contentType: NoteType = NoteType.Plaintext
    private var content: String = ""
    private var createdAt: String = ""
    private var updatedAt: String = ""
    private var isEditing = false
    private var isEdited = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_detail)

        tvNoteName = findViewById(R.id.tvNoteName)
        tvDeviceInfo = findViewById(R.id.tvDeviceInfo)
        tvTimestamps = findViewById(R.id.tvTimestamps)
        contentDisplay = findViewById(R.id.etNoteContent)
        btnEditDevice = findViewById(R.id.btnEditDevice)
        btnEdit = findViewById(R.id.btnEdit)
        progressBar = findViewById(R.id.pbLoading)

        loadNoteData()
        setupUI()
    }

    private fun loadNoteData() {
        noteId = intent.getStringExtra("noteId") ?: ""
        noteName = intent.getStringExtra("noteName") ?: ""
        deviceId = intent.getStringExtra("deviceId") ?: ""
        contentType = NoteType.values()[intent.getIntExtra("contentType", 0)]
        content = intent.getStringExtra("content") ?: ""
        createdAt = intent.getStringExtra("createdAt") ?: ""
        updatedAt = intent.getStringExtra("updatedAt") ?: ""
    }

    private fun setupUI() {
        tvNoteName.text = noteName
        tvDeviceInfo.text = "Signed by Device: $deviceId"
        contentDisplay.setText(content)
        contentDisplay.isEnabled = false

        contentDisplay.addTextChangedListener {
            if (isEditing) isEdited = true
        }

        val createdFormatted = formatDate(createdAt)
        val updatedFormatted = formatDate(updatedAt)
        tvTimestamps.text = "Created: $createdFormatted\nUpdated: $updatedFormatted"

        // Add toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = noteName

        btnEdit.setOnClickListener { toggleEditMode() }
        btnEditDevice.setOnClickListener { changeDevice() }
    }

    private fun toggleEditMode() {
        if (!isEditing) {
            isEditing = true
            contentDisplay.isEnabled = true
            contentDisplay.requestFocus()
            btnEdit.text = "Save"
        } else {
            saveNote()
        }
    }

    private fun saveNote() {
        val updatedContent = contentDisplay.text.toString()
        if (updatedContent == content && !isEdited) {
            isEditing = false
            contentDisplay.isEnabled = false
            btnEdit.text = "Edit"
            return
        }

        val request = UpdateNoteRequestDto(noteName, updatedContent)

        GlobalScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                val response = ApiClient.apiService.updateNote(noteId, request)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE

                    if (response.isSuccessful) {
                        content = updatedContent
                        isEdited = false
                        isEditing = false
                        contentDisplay.isEnabled = false
                        btnEdit.text = "Edit"
                        Toast.makeText(this@NoteDetailActivity, "Note saved successfully", Toast.LENGTH_SHORT).show()
                        // Update timestamp
                        val currentTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date())
                        updatedAt = currentTime
                        tvTimestamps.text = "Created: ${formatDate(createdAt)}\nUpdated: ${formatDate(currentTime)}"
                    } else {
                        Toast.makeText(this@NoteDetailActivity, "Failed to save note", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@NoteDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun changeDevice() {
        // Load available devices and let user select one to re-sign the note
        GlobalScope.launch {
            try {
                val response = ApiClient.apiService.getDevices()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val devices = response.body()!!
                        val deviceNames = devices.map { it.name }.toTypedArray()

                        AlertDialog.Builder(this@NoteDetailActivity)
                            .setTitle("Select Device to Sign Note")
                            .setItems(deviceNames) { _, which ->
                                deviceId = devices[which].id
                                tvDeviceInfo.text = "Signed by Device: $deviceId"
                                // In a real app, you'd re-sign the note with the new device
                                Toast.makeText(this@NoteDetailActivity, "Note re-signed with ${devices[which].name}", Toast.LENGTH_SHORT).show()
                            }
                            .show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@NoteDetailActivity, "Error loading devices", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            val outputFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateString
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_note_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                handleBackPress()
                true
            }
            R.id.action_delete -> {
                deleteNote()
                true
            }
            R.id.action_undo -> {
                // TODO: Implement undo with history
                Toast.makeText(this, "Undo functionality coming soon", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_redo -> {
                // TODO: Implement redo with history
                Toast.makeText(this, "Redo functionality coming soon", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_font_size -> {
                showFontSizeDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun deleteNote() {
        AlertDialog.Builder(this)
            .setTitle("Delete Note?")
            .setMessage("This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                GlobalScope.launch {
                    try {
                        val response = ApiClient.apiService.deleteNote(noteId)
                        withContext(Dispatchers.Main) {
                            if (response.isSuccessful) {
                                Toast.makeText(this@NoteDetailActivity, "Note deleted", Toast.LENGTH_SHORT).show()
                                finish()
                            } else {
                                Toast.makeText(this@NoteDetailActivity, "Failed to delete note", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@NoteDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFontSizeDialog() {
        val sizes = arrayOf("Small (12sp)", "Medium (16sp)", "Large (20sp)")
        AlertDialog.Builder(this)
            .setTitle("Font Size")
            .setItems(sizes) { _, which ->
                val size = when (which) {
                    0 -> 12f
                    1 -> 16f
                    2 -> 20f
                    else -> 16f
                }
                contentDisplay.textSize = size
            }
            .show()
    }

    private fun handleBackPress() {
        if (isEditing && isEdited) {
            AlertDialog.Builder(this)
                .setTitle("Save Changes?")
                .setMessage("You have unsaved changes.")
                .setPositiveButton("Save") { _, _ -> saveNote() }
                .setNeutralButton("Discard") { _, _ -> finish() }
                .setNegativeButton("Keep Editing", null)
                .show()
        } else {
            finish()
        }
    }

    override fun onBackPressed() {
        handleBackPress()
    }
}
