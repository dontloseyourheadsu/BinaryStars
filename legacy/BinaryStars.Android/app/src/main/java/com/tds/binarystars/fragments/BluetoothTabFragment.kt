package com.tds.binarystars.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.MainActivity
import com.tds.binarystars.R
import com.tds.binarystars.adapter.BluetoothDevicesAdapter
import com.tds.binarystars.adapter.MessagingMessagesAdapter
import com.tds.binarystars.bluetooth.BluetoothChatManager
import com.tds.binarystars.model.ChatMessage
import com.tds.binarystars.storage.DeviceCacheStorage
import com.tds.binarystars.util.NativeLogging as L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class BluetoothTabFragment : Fragment(), MessagingMessagesAdapter.OnMessageActionListener {

    private lateinit var viewConnection: LinearLayout
    private lateinit var viewChat: LinearLayout
    private lateinit var rvDevices: RecyclerView
    private lateinit var rvMessages: RecyclerView
    private lateinit var etInput: EditText
    private lateinit var btnSend: ImageView
    private lateinit var btnAttach: ImageView
    private lateinit var btnDisconnect: Button
    private lateinit var pbConnecting: ProgressBar

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var messagesAdapter: MessagingMessagesAdapter
    private var connectedDevice: BluetoothDevice? = null

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) sendFile(uri)
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.all { it }) loadPairedDevices()
        else Toast.makeText(requireContext(), "Permissions required", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_bluetooth_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.ivMenu).setOnClickListener { (activity as? MainActivity)?.openDrawer() }
        
        viewConnection = view.findViewById(R.id.viewConnection)
        viewChat = view.findViewById(R.id.viewChat)
        rvDevices = view.findViewById(R.id.rvBluetoothDevices)
        rvMessages = view.findViewById(R.id.rvBluetoothMessages)
        etInput = view.findViewById(R.id.etBluetoothInput)
        btnSend = view.findViewById(R.id.btnSendBluetooth)
        btnAttach = view.findViewById(R.id.btnAttachFile)
        btnDisconnect = view.findViewById(R.id.btnDisconnect)
        pbConnecting = view.findViewById(R.id.pbConnecting)

        messagesAdapter = MessagingMessagesAdapter(messages, this)
        rvMessages.layoutManager = LinearLayoutManager(requireContext())
        rvMessages.adapter = messagesAdapter

        btnSend.setOnClickListener { sendMessage() }
        btnAttach.setOnClickListener { pickFileLauncher.launch("*/*") }
        btnDisconnect.setOnClickListener { disconnect() }

        checkPermissions()
        observeMessages()
    }

    override fun onSaveFile(fileName: String, localPath: String) {
        moveFileToDownloads(fileName, localPath)
    }

    private fun moveFileToDownloads(fileName: String, path: String) {
        val sourceFile = java.io.File(path)
        if (!sourceFile.exists()) {
            L.e("BluetoothTab", "Source file not found at $path")
            Toast.makeText(requireContext(), "Source file not found", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                pbConnecting.visibility = View.VISIBLE
                val success = withContext(Dispatchers.IO) {
                    val finalFileName = if (fileName.contains(".")) {
                        val base = fileName.substringBeforeLast(".")
                        val ext = fileName.substringAfterLast(".")
                        "${base}_${System.currentTimeMillis()}.$ext"
                    } else {
                        "${fileName}_${System.currentTimeMillis()}"
                    }

                    val contentType = context?.contentResolver?.getType(Uri.fromFile(sourceFile)) ?: "application/octet-stream"

                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, contentType)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                    }

                    val resolver = requireContext().contentResolver
                    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Files.getContentUri("external")
                    }

                    val uri = resolver.insert(collection, contentValues)
                    
                    if (uri != null) {
                        try {
                            resolver.openOutputStream(uri)?.use { output ->
                                java.io.FileInputStream(sourceFile).use { input ->
                                    input.copyTo(output)
                                }
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                contentValues.clear()
                                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                                resolver.update(uri, contentValues, null, null)
                            }
                            
                            android.media.MediaScannerConnection.scanFile(requireContext(), arrayOf(uri.path), null, null)
                            L.i("BluetoothTab", "Successfully moved $fileName to Downloads as $finalFileName")
                            true
                        } catch (e: Exception) {
                            L.e("BluetoothTab", "Failed to write to MediaStore URI", e)
                            resolver.delete(uri, null, null)
                            false
                        }
                    } else {
                        L.e("BluetoothTab", "Failed to create MediaStore entry")
                        false
                    }
                }

                if (success) {
                    Toast.makeText(requireContext(), "Saved to Downloads", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to save file", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                L.e("BluetoothTab", "Save exception", e)
                Toast.makeText(requireContext(), "Save error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                pbConnecting.visibility = View.GONE
            }
        }
    }

    private fun checkPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (perms.all { ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED }) {
            loadPairedDevices()
        } else {
            permissionLauncher.launch(perms)
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadPairedDevices() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val devices = adapter?.bondedDevices?.toList() ?: emptyList()
        rvDevices.layoutManager = LinearLayoutManager(requireContext())
        rvDevices.adapter = BluetoothDevicesAdapter(devices) { connectToDevice(it) }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        pbConnecting.visibility = View.VISIBLE
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                BluetoothChatManager.connect(device)
            }
            pbConnecting.visibility = View.GONE
            if (success) {
                connectedDevice = device
                showChatView()
            } else {
                Toast.makeText(requireContext(), "Connection failed or user mismatch", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showChatView() {
        viewConnection.visibility = View.GONE
        viewChat.visibility = View.VISIBLE
        btnDisconnect.visibility = View.VISIBLE
    }

    private fun disconnect() {
        connectedDevice?.let { BluetoothChatManager.disconnect(it.address) }
        connectedDevice = null
        BluetoothChatManager.clearSession()
        viewConnection.visibility = View.VISIBLE
        viewChat.visibility = View.GONE
        btnDisconnect.visibility = View.GONE
        messages.clear()
        messagesAdapter.notifyDataSetChanged()
    }

    private fun observeMessages() {
        lifecycleScope.launch {
            BluetoothChatManager.messages.collect { msgs ->
                withContext(Dispatchers.Main) {
                    messages.clear()
                    messages.addAll(msgs)
                    messagesAdapter.notifyDataSetChanged()
                    if (messages.isNotEmpty()) rvMessages.scrollToPosition(messages.lastIndex)
                    
                    // Auto-download files if they appear in transient list
                    msgs.filter { !it.isOutgoing && it.body.contains("[Received file:") }.forEach {
                        val fileName = it.body.substringAfter("[Received file: ").substringBefore("]")
                        // In MVP we have the base64 in the message payload or separate flow
                        // For now we'll rely on the Manager having saved it already in init or similar
                    }
                }
            }
        }
    }

    private fun sendMessage() {
        val text = etInput.text.toString().trim()
        if (text.isEmpty()) return
        val device = connectedDevice ?: return
        
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                BluetoothChatManager.sendMessage(device.address, text)
            }
            if (success) {
                etInput.setText("")
            } else {
                Toast.makeText(requireContext(), "Failed to send", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendFile(uri: Uri) {
        val device = connectedDevice ?: return
        lifecycleScope.launch {
            try {
                val fileName = getFileName(uri)
                val bytes = requireContext().contentResolver.openInputStream(uri)?.readBytes()
                if (bytes != null) {
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    val success = withContext(Dispatchers.IO) {
                        BluetoothChatManager.sendFile(device.address, fileName, base64)
                    }
                    if (success) {
                        // Manager adds a local message "Sent file: ..."
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error sending file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("Range")
    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    result = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        return result ?: uri.path?.substringAfterLast('/') ?: "file"
    }
}
