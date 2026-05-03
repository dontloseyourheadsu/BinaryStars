package com.tds.binarystars.fragments

import android.content.ContentValues
import android.content.Context
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
import androidx.appcompat.app.AlertDialog
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.R
import com.tds.binarystars.MainActivity
import com.tds.binarystars.adapter.FileTransfersAdapter
import com.tds.binarystars.adapter.TransferAction
import com.tds.binarystars.adapter.TransferUiItem
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.ClearFileTransfersRequestDto
import com.tds.binarystars.api.CreateFileTransferRequestDto
import com.tds.binarystars.api.DeviceActionResultDto
import com.tds.binarystars.api.FileTransferStatusDto
import com.tds.binarystars.api.LocationUpdateEventDto
import com.tds.binarystars.api.StreamingRequestBody
import com.tds.binarystars.messaging.MessagingEventListener
import com.tds.binarystars.messaging.MessagingSocketManager
import com.tds.binarystars.storage.DeviceCacheStorage
import com.tds.binarystars.storage.FileTransferStorage
import com.tds.binarystars.storage.LocalFileTransfer
import com.tds.binarystars.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.DecimalFormat

class FilesFragment : Fragment(), MessagingEventListener {
    private lateinit var rvFileTransfers: RecyclerView
    private lateinit var pbFilesLoading: ProgressBar
    private lateinit var tvFilesEmptyState: TextView
    private lateinit var btnSendFile: Button
    private lateinit var viewContent: View
    private lateinit var viewNoConnection: View
    private lateinit var btnRetry: Button
    private lateinit var btnFilterAll: Button
    private lateinit var btnFilterSent: Button
    private lateinit var btnFilterReceived: Button
    private lateinit var btnClearSent: Button
    private lateinit var btnClearReceived: Button

    private val items = mutableListOf<TransferUiItem>()
    private lateinit var adapter: FileTransfersAdapter
    private var filterScope: String = "all"

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) handleFileSelected(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_files, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<ImageView>(R.id.ivMenu)?.setOnClickListener { (activity as? MainActivity)?.openDrawer() }
        rvFileTransfers = view.findViewById(R.id.rvFileTransfers)
        pbFilesLoading = view.findViewById(R.id.pbFilesLoading)
        tvFilesEmptyState = view.findViewById(R.id.tvFilesEmptyState)
        btnSendFile = view.findViewById(R.id.btnSendFile)
        viewContent = view.findViewById(R.id.viewContent)
        viewNoConnection = view.findViewById(R.id.viewNoConnection)
        btnRetry = view.findViewById(R.id.btnRetry)

        btnFilterAll = view.findViewById(R.id.btnFilterAll)
        btnFilterSent = view.findViewById(R.id.btnFilterSent)
        btnFilterReceived = view.findViewById(R.id.btnFilterReceived)
        btnClearSent = view.findViewById(R.id.btnClearSent)
        btnClearReceived = view.findViewById(R.id.btnClearReceived)

        adapter = FileTransfersAdapter(items) { item, action -> handleTransferAction(item, action) }
        rvFileTransfers.layoutManager = LinearLayoutManager(requireContext())
        rvFileTransfers.adapter = adapter

        btnSendFile.setOnClickListener { pickFileLauncher.launch("*/*") }
        btnRetry.setOnClickListener { loadTransfers() }

        btnFilterAll.setOnClickListener { filterScope = "all"; viewLifecycleOwner.lifecycleScope.launch { refreshTransfersFromCache() } }
        btnFilterSent.setOnClickListener { filterScope = "sent"; viewLifecycleOwner.lifecycleScope.launch { refreshTransfersFromCache() } }
        btnFilterReceived.setOnClickListener { filterScope = "received"; viewLifecycleOwner.lifecycleScope.launch { refreshTransfersFromCache() } }
        
        btnClearSent.setOnClickListener { clearTransfers("sent") }
        btnClearReceived.setOnClickListener { clearTransfers("received") }

        loadTransfers()
    }

    override fun onStart() {
        super.onStart()
        MessagingSocketManager.addListener(this)
    }

    override fun onStop() {
        MessagingSocketManager.removeListener(this)
        super.onStop()
    }

    override fun onChatUpdated(deviceId: String) {}
    override fun onDeviceRemoved(deviceId: String, isSelf: Boolean) { loadTransfers() }
    override fun onConnectionStateChanged(isConnected: Boolean) { loadTransfers() }
    override fun onDevicePresenceChanged(deviceId: String, isOnline: Boolean, lastSeen: String) { loadTransfers() }
    override fun onLocationUpdated(event: LocationUpdateEventDto) {}
    override fun onActionResult(result: DeviceActionResultDto) {
        if (result.actionType == "list_installed_apps" || result.actionType == "launch_app" || result.actionType == "close_app") {
            loadTransfers()
        }
    }

    private fun clearTransfers(scope: String) {
        if (!NetworkUtils.isOnline(requireContext())) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                pbFilesLoading.visibility = View.VISIBLE
                val resp = withContext(Dispatchers.IO) { 
                    ApiClient.apiService.clearFileTransfers(ClearFileTransfersRequestDto(getCurrentDeviceId(), scope))
                }
                if (resp.isSuccessful) {
                    Toast.makeText(requireContext(), "Cleared $scope", Toast.LENGTH_SHORT).show()
                    loadTransfers()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Clear error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                pbFilesLoading.visibility = View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadTransfers()
    }

    private fun setNoConnection(v: Boolean) {
        viewContent.visibility = if (v) View.GONE else View.VISIBLE
        viewNoConnection.visibility = if (v) View.VISIBLE else View.GONE
    }

    private fun loadTransfers() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (!NetworkUtils.isOnline(requireContext())) {
                setNoConnection(true)
                refreshTransfersFromCache()
                return@launch
            }

            try {
                setNoConnection(false)
                val myId = getCurrentDeviceId()
                android.util.Log.d("FilesFragment", "Fetching transfers for device: $myId")
                
                val resp = withContext(Dispatchers.IO) { ApiClient.apiService.getFileTransfers(myId) }
                if (resp.isSuccessful) {
                    val devices = withContext(Dispatchers.IO) { ApiClient.apiService.getDevices() }.body().orEmpty()
                    val lookup = devices.associateBy({ it.id }, { it.name })
                    val locals = resp.body().orEmpty().map { dto ->
                        android.util.Log.v("FilesFragment", "Transfer ${dto.id}: isSender=${dto.isSender}, sender=${dto.senderDeviceId}")
                        LocalFileTransfer(
                            id = dto.id, fileName = dto.fileName, contentType = dto.contentType,
                            sizeBytes = dto.sizeBytes, senderDeviceId = dto.senderDeviceId,
                            senderDeviceName = lookup[dto.senderDeviceId] ?: dto.senderDeviceId,
                            targetDeviceId = dto.targetDeviceId, targetDeviceName = lookup[dto.targetDeviceId] ?: dto.targetDeviceId,
                            status = dto.status.name, createdAt = dto.createdAt, expiresAt = dto.expiresAt,
                            isSender = dto.isSender
                        )
                    }
                    withContext(Dispatchers.IO) { FileTransferStorage.replaceTransfers(locals) }
                } else {
                    android.util.Log.e("FilesFragment", "Failed to fetch transfers: ${resp.message()}")
                }
                refreshTransfersFromCache()
            } catch (e: Exception) {
                android.util.Log.e("FilesFragment", "Error loading transfers", e)
                refreshTransfersFromCache()
            } finally {
                pbFilesLoading.visibility = View.GONE
            }
        }
    }

    private suspend fun refreshTransfersFromCache() {
        val cached = withContext(Dispatchers.IO) { FileTransferStorage.getTransfers() }
        val filtered = when(filterScope) {
            "sent" -> cached.filter { it.isSender }
            "received" -> cached.filter { !it.isSender }
            else -> cached
        }
        val mapped = filtered.map { t ->
            val status = try { FileTransferStatusDto.valueOf(t.status) } catch(_: Exception) { FileTransferStatusDto.Queued }
            val isReady = status == FileTransferStatusDto.Available || status == FileTransferStatusDto.Queued || status == FileTransferStatusDto.Uploading
            val isDownloaded = status == FileTransferStatusDto.Downloaded || t.localPath != null
            TransferUiItem(
                id = t.id, fileName = t.fileName, contentType = t.contentType,
                metaText = (if (t.isSender) "To: ${t.targetDeviceName}" else "From: ${t.senderDeviceName}") + " • ${formatSize(t.sizeBytes)}",
                statusText = when(status) {
                    FileTransferStatusDto.Queued -> if (t.isSender) "Queued for upload" else "Waiting for sender"
                    FileTransferStatusDto.Uploading -> if (t.isSender) "Uploading..." else "Sender is uploading..."
                    FileTransferStatusDto.Available -> if (t.isSender) "Waiting for download" else "Ready to download"
                    FileTransferStatusDto.Downloaded -> if (t.isSender) "Delivered" else "Downloaded"
                    else -> t.status
                }, 
                isSender = t.isSender,
                showSuccessIcon = status == FileTransferStatusDto.Downloaded,
                showProgress = status == FileTransferStatusDto.Uploading || status == FileTransferStatusDto.Queued,
                showBluetoothIcon = false, isBluetoothTransfer = false,
                primaryAction = if (!t.isSender && isReady) TransferAction.Download else if (!t.isSender && isDownloaded) TransferAction.Move else null,
                secondaryAction = if (!t.isSender && isReady) TransferAction.Reject else null,
                localPath = t.localPath
            )
        }
        items.clear(); items.addAll(mapped); adapter.notifyDataSetChanged()
        tvFilesEmptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun downloadFileFromCloud(item: TransferUiItem) {
        if (!NetworkUtils.isOnline(requireContext())) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                pbFilesLoading.visibility = View.VISIBLE
                val resp = withContext(Dispatchers.IO) { ApiClient.apiService.downloadFileTransfer(item.id, getCurrentDeviceId()) }
                if (resp.isSuccessful) {
                    val body = resp.body()
                    if (body != null) {
                        val localPath = saveFileToInternal(item.id, item.fileName, body)
                        withContext(Dispatchers.IO) { FileTransferStorage.updateLocalPath(item.id, localPath) }
                        Toast.makeText(requireContext(), "Downloaded to app storage", Toast.LENGTH_SHORT).show()
                        refreshTransfersFromCache()
                    }
                } else {
                    val errorBody = resp.errorBody()?.string()
                    android.util.Log.e("FilesFragment", "Download failed: $errorBody")
                    Toast.makeText(requireContext(), "Download failed: ${resp.message()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("FilesFragment", "Download error", e)
                Toast.makeText(requireContext(), "Download error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                pbFilesLoading.visibility = View.GONE
            }
        }
    }

    private suspend fun saveFileToInternal(transferId: String, fileName: String, body: okhttp3.ResponseBody): String {
        return withContext(Dispatchers.IO) {
            val dir = File(requireContext().filesDir, "transfers/received")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "${transferId}_$fileName")
            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        }
    }

    private fun moveFileToDownloads(item: TransferUiItem) {
        val path = item.localPath ?: return
        val sourceFile = File(path)
        if (!sourceFile.exists()) {
            com.tds.binarystars.util.NativeLogging.e("FilesFragment", "Source file not found at $path")
            Toast.makeText(requireContext(), "Source file not found", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                pbFilesLoading.visibility = View.VISIBLE
                val success = withContext(Dispatchers.IO) {
                    val finalFileName = if (item.fileName.contains(".")) {
                        val base = item.fileName.substringBeforeLast(".")
                        val ext = item.fileName.substringAfterLast(".")
                        "${base}_${System.currentTimeMillis()}.$ext"
                    } else {
                        "${item.fileName}_${System.currentTimeMillis()}"
                    }

                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, item.contentType)
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
                                FileInputStream(sourceFile).use { input ->
                                    input.copyTo(output)
                                }
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                contentValues.clear()
                                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                                resolver.update(uri, contentValues, null, null)
                            }
                            
                            // Explicitly trigger media scan for older devices
                            android.media.MediaScannerConnection.scanFile(requireContext(), arrayOf(uri.path), null, null)
                            
                            com.tds.binarystars.util.NativeLogging.i("FilesFragment", "Successfully moved ${item.fileName} to Downloads as $finalFileName")
                            true
                        } catch (e: Exception) {
                            com.tds.binarystars.util.NativeLogging.e("FilesFragment", "Failed to write to MediaStore URI", e)
                            resolver.delete(uri, null, null)
                            false
                        }
                    } else {
                        com.tds.binarystars.util.NativeLogging.e("FilesFragment", "Failed to create MediaStore entry")
                        false
                    }
                }

                if (success) {
                    Toast.makeText(requireContext(), "Saved to Downloads", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to move file", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                com.tds.binarystars.util.NativeLogging.e("FilesFragment", "Move exception", e)
                Toast.makeText(requireContext(), "Move error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                pbFilesLoading.visibility = View.GONE
            }
        }
    }

    private fun handleTransferAction(item: TransferUiItem, action: TransferAction) {
        when (action) {
            TransferAction.Download -> downloadFileFromCloud(item)
            TransferAction.Reject -> rejectFileFromCloud(item)
            TransferAction.Move -> moveFileToDownloads(item)
            else -> {
                // Other actions not yet implemented for cloud transfers
            }
        }
    }

    private fun rejectFileFromCloud(item: TransferUiItem) {
        if (!NetworkUtils.isOnline(requireContext())) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                pbFilesLoading.visibility = View.VISIBLE
                val resp = withContext(Dispatchers.IO) { ApiClient.apiService.rejectFileTransfer(item.id, getCurrentDeviceId()) }
                if (resp.isSuccessful) {
                    Toast.makeText(requireContext(), "Rejected", Toast.LENGTH_SHORT).show()
                    loadTransfers()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Reject error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                pbFilesLoading.visibility = View.GONE
            }
        }
    }

    private fun handleFileSelected(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val myId = getCurrentDeviceId()
            val targets = DeviceCacheStorage.getDevices().filter { it.id.trim().lowercase() != myId.trim().lowercase() }
            val names = targets.map { it.name }.toTypedArray()
            
            if (names.isEmpty()) {
                Toast.makeText(requireContext(), "No other devices linked", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            AlertDialog.Builder(requireContext()).setTitle("Send to").setItems(names) { _, i ->
                sendFileCloud(uri, targets[i].id)
            }.show()
        }
    }

    private fun sendFileCloud(uri: Uri, targetId: String) {
        if (!NetworkUtils.isOnline(requireContext())) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                pbFilesLoading.visibility = View.VISIBLE
                val fileName = getFileName(uri)
                val contentType = requireContext().contentResolver.getType(uri) ?: "application/octet-stream"
                val size = getFileSize(uri)
                val myId = getCurrentDeviceId()
                
                android.util.Log.d("FilesFragment", "Creating transfer: file=$fileName, size=$size, from=$myId, to=$targetId")
                
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.apiService.createFileTransfer(CreateFileTransferRequestDto(fileName, contentType, size, myId, targetId, "{\"alg\":\"none\"}"))
                }

                if (resp.isSuccessful) {
                    val created = resp.body()
                    if (created != null) {
                        val inputStream = withContext(Dispatchers.IO) {
                            requireContext().contentResolver.openInputStream(uri)
                        }
                        
                        if (inputStream == null) {
                            Toast.makeText(requireContext(), "Failed to open file", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        val body = StreamingRequestBody(contentType, inputStream, size)
                        android.util.Log.d("FilesFragment", "Uploading file ${created.id} (size=$size)...")
                        
                        try {
                            val uploadResp = withContext(Dispatchers.IO) {
                                ApiClient.apiService.uploadFileTransfer(created.id, body)
                            }

                            if (uploadResp.isSuccessful) {
                                android.util.Log.i("FilesFragment", "Upload complete for ${created.id}")
                                Toast.makeText(requireContext(), "Upload complete", Toast.LENGTH_SHORT).show()
                            } else {
                                val errorBody = uploadResp.errorBody()?.string()
                                android.util.Log.e("FilesFragment", "Upload failed for ${created.id}: status=${uploadResp.code()}, body=$errorBody")
                                Toast.makeText(requireContext(), "Upload failed: ${uploadResp.message()}", Toast.LENGTH_SHORT).show()
                            }
                        } finally {
                            try { inputStream.close() } catch (_: Exception) {}
                        }
                    }
                } else {
                    val errorBody = resp.errorBody()?.string()
                    android.util.Log.e("FilesFragment", "Create transfer failed: $errorBody")
                    Toast.makeText(requireContext(), "Send failed: ${resp.message()}", Toast.LENGTH_SHORT).show()
                }
                loadTransfers()
            } catch (e: Exception) {
                android.util.Log.e("FilesFragment", "Transfer exception", e)
                Toast.makeText(requireContext(), "Upload error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                pbFilesLoading.visibility = View.GONE
            }
        }
    }

    private fun getFileSize(uri: Uri): Long {
        var size = 0L
        try {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) size = it.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("FilesFragment", "Failed to get size via provider", e)
        }
        
        return if (size > 0) size else {
            try {
                requireContext().contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L
            } catch (e: Exception) { 0L }
        }
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        try {
            if (uri.scheme == "content") {
                requireContext().contentResolver.query(uri, null, null, null, null)?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) result = it.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("FilesFragment", "Failed to get name via provider", e)
        }
        return result ?: uri.path?.substringAfterLast('/') ?: "file"
    }

    private fun getCurrentDeviceId(): String {
        val id = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        android.util.Log.d("FilesFragment", "Current device ID: $id")
        return id ?: "unknown-android"
    }

    private fun formatSize(b: Long): String { val f = DecimalFormat("#,##0.##"); return if (b < 1024) "$b B" else if (b < 1048576) "${f.format(b/1024.0)} KB" else "${f.format(b/1048576.0)} MB" }
}
