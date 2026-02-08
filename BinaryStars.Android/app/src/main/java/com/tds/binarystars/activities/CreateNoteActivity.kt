package com.tds.binarystars.activities

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.tds.binarystars.R
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.CreateNoteRequest
import com.tds.binarystars.api.DeviceDto
import com.tds.binarystars.api.NoteType
import com.tds.binarystars.storage.NotesStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateNoteActivity : AppCompatActivity() {
    private lateinit var etNoteName: EditText
    private lateinit var noteTypeSpinner: Spinner
    private lateinit var deviceSpinner: Spinner
    private lateinit var btnEditContent: Button
    private lateinit var tvContentPreview: TextView
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var progressBar: ProgressBar

    private var devices: List<DeviceDto> = emptyList()
    private var selectedContentType = NoteType.Plaintext
    private var isEdited = false
    private var noteContent: String = ""

    private val editContentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val updatedContent = result.data?.getStringExtra(ContentEditorActivity.EXTRA_CONTENT) ?: ""
            noteContent = updatedContent
            isEdited = true
            updateContentPreview()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_note)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleCancel()
            }
        })

        etNoteName = findViewById(R.id.etNoteName)
        noteTypeSpinner = findViewById(R.id.spinnerNoteType)
        deviceSpinner = findViewById(R.id.spinnerDevice)
        btnEditContent = findViewById(R.id.btnEditContent)
        tvContentPreview = findViewById(R.id.tvContentPreview)
        btnSave = findViewById(R.id.btnSaveNote)
        btnCancel = findViewById(R.id.btnCancelNote)
        progressBar = findViewById(R.id.pbCreatingNote)

        setupTypeSpinner()
        loadDevices()

        etNoteName.addTextChangedListener { isEdited = true }

        btnEditContent.setOnClickListener { openContentEditor() }

        btnSave.setOnClickListener { saveNote() }
        btnCancel.setOnClickListener { handleCancel() }
    }

    private fun setupTypeSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            NoteType.values().map { it.name }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        noteTypeSpinner.adapter = adapter

        noteTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedContentType = NoteType.values()[position]
                updateEditorUI()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateEditorUI() {
        if (selectedContentType == NoteType.Markdown) {
            btnEditContent.text = "Edit Markdown Content"
        } else {
            btnEditContent.text = "Edit Plaintext Content"
        }
        updateContentPreview()
    }

    private fun loadDevices() {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { ApiClient.apiService.getDevices() }
                if (response.isSuccessful && response.body() != null) {
                    devices = response.body()!!
                    setupDeviceSpinner()
                } else {
                    Toast.makeText(this@CreateNoteActivity, "Failed to load devices", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CreateNoteActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupDeviceSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            devices.map { it.name }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        deviceSpinner.adapter = adapter
    }

    private fun saveNote() {
        val name = etNoteName.text.toString().trim()
        val content = noteContent

        if (name.isEmpty()) {
            Toast.makeText(this, "Note name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        if (devices.isEmpty()) {
            Toast.makeText(this, "No devices available", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedDevice = devices[deviceSpinner.selectedItemPosition]
        val request = CreateNoteRequest(
            name = name,
            deviceId = selectedDevice.id,
            contentType = selectedContentType,
            content = content
        )

        lifecycleScope.launch {
            try {
                progressBar.visibility = android.view.View.VISIBLE
                val response = withContext(Dispatchers.IO) { ApiClient.apiService.createNote(request) }
                progressBar.visibility = android.view.View.GONE

                if (response.isSuccessful) {
                    response.body()?.let { NotesStorage.upsertNote(it) }
                    Toast.makeText(this@CreateNoteActivity, "Note created successfully", Toast.LENGTH_SHORT).show()
                    isEdited = false
                    finish()
                } else {
                    Toast.makeText(this@CreateNoteActivity, "Failed to create note", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = android.view.View.GONE
                Toast.makeText(this@CreateNoteActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openContentEditor() {
        val intent = ContentEditorActivity.newIntent(
            context = this,
            contentType = selectedContentType,
            initialContent = noteContent
        )
        editContentLauncher.launch(intent)
    }

    private fun updateContentPreview() {
        if (noteContent.isBlank()) {
            tvContentPreview.text = "No content yet"
            return
        }

        val preview = if (noteContent.length > 140) {
            noteContent.substring(0, 140) + "..."
        } else {
            noteContent
        }
        tvContentPreview.text = preview
    }

    private fun handleCancel() {
        if (isEdited) {
            AlertDialog.Builder(this)
                .setTitle("Discard Changes?")
                .setMessage("You have unsaved changes. Do you want to discard them?")
                .setPositiveButton("Discard") { _, _ -> finish() }
                .setNegativeButton("Keep Editing", null)
                .show()
        } else {
            finish()
        }
    }

    
}
