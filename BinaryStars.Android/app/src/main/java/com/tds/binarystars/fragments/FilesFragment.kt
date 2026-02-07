package com.tds.binarystars.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.R
import com.tds.binarystars.adapter.FileTransfersAdapter
import com.tds.binarystars.adapter.TransferAction
import com.tds.binarystars.adapter.TransferUiItem
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.CreateFileTransferRequestDto
import com.tds.binarystars.api.FileTransferStatusDto
import com.tds.binarystars.api.StreamingRequestBody
import com.tds.binarystars.crypto.CryptoManager
import com.tds.binarystars.storage.FileTransferLocalStore
import com.tds.binarystars.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.DecimalFormat

class FilesFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: TextView
    private lateinit var sendButton: Button
    private lateinit var adapter: FileTransfersAdapter
    private val items = mutableListOf<TransferUiItem>()
    private var pendingMoveItem: TransferUiItem? = null

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            handleFileSelected(uri)
        }
    }

    private val createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val item = pendingMoveItem
        if (uri != null && item != null) {
            moveFileToDevice(uri, item)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_files, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.ivMenu)?.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        recyclerView = view.findViewById(R.id.rvFileTransfers)
        progressBar = view.findViewById(R.id.pbFilesLoading)
        emptyState = view.findViewById(R.id.tvFilesEmptyState)
        sendButton = view.findViewById(R.id.btnSendFile)

        adapter = FileTransfersAdapter(items) { item, action ->
            when (action) {
                TransferAction.Download -> downloadTransfer(item)
                TransferAction.Resend -> resendTransfer(item)
                TransferAction.Move -> {
                    pendingMoveItem = item
                    createDocumentLauncher.launch(item.fileName)
                }
                TransferAction.Open -> openLocalFile(item)
                TransferAction.Reject -> rejectTransfer(item)
            }
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        sendButton.setOnClickListener { pickFileLauncher.launch("*/*") }

        loadTransfers()
    }

    override fun onResume() {
        super.onResume()
        loadTransfers()
    }

    private fun loadTransfers() {
        viewLifecycleOwner.lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val devicesResponse = withContext(Dispatchers.IO) { ApiClient.apiService.getDevices() }
            val transfersResponse = withContext(Dispatchers.IO) { ApiClient.apiService.getFileTransfers() }
            progressBar.visibility = View.GONE

            if (devicesResponse.isSuccessful && transfersResponse.isSuccessful) {
                val devices = devicesResponse.body().orEmpty()
                val deviceLookup = devices.associateBy({ it.id }, { it.name })

                items.clear()
                transfersResponse.body().orEmpty().forEach { dto ->
                    val directionLabel = if (dto.isSender) {
                        "To: ${deviceLookup[dto.targetDeviceId] ?: dto.targetDeviceId}"
                    } else {
                        "From: ${deviceLookup[dto.senderDeviceId] ?: dto.senderDeviceId}"
                    }
                    val sizeText = formatSize(dto.sizeBytes)
                    val meta = "$directionLabel • $sizeText"
                    val localPath = FileTransferLocalStore.getLocalPath(requireContext(), dto.id)
                    items.add(buildUiItem(dto.id, dto.fileName, dto.contentType, meta, dto.status, dto.isSender, localPath))
                }

                adapter.notifyDataSetChanged()
                updateEmptyState()
            } else {
                emptyState.text = "Failed to load transfers"
                emptyState.visibility = View.VISIBLE
            }
        }
    }

    private fun updateEmptyState() {
        if (items.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun buildUiItem(
        id: String,
        fileName: String,
        contentType: String,
        metaText: String,
        status: FileTransferStatusDto,
        isSender: Boolean,
        localPath: String?
    ): TransferUiItem {
        val statusText = when (status) {
            FileTransferStatusDto.Queued -> if (isSender) "Queued" else "Preparing"
            FileTransferStatusDto.Uploading -> "Uploading"
            FileTransferStatusDto.Available -> if (isSender) "Waiting for download" else "Ready to download"
            FileTransferStatusDto.Downloaded -> if (isSender) "Delivered" else "Downloaded"
            FileTransferStatusDto.Failed -> "Failed"
            FileTransferStatusDto.Expired -> "Expired"
            FileTransferStatusDto.Rejected -> "Rejected"
        }

        val showProgress = status == FileTransferStatusDto.Uploading || status == FileTransferStatusDto.Queued
        val showSuccess = status == FileTransferStatusDto.Downloaded

        val primaryAction = when {
            !isSender && status == FileTransferStatusDto.Available -> TransferAction.Download
            !isSender && status == FileTransferStatusDto.Downloaded && localPath != null -> TransferAction.Move
            isSender && (status == FileTransferStatusDto.Failed || status == FileTransferStatusDto.Expired || status == FileTransferStatusDto.Rejected) -> TransferAction.Resend
            else -> null
        }

        val secondaryAction = when {
            !isSender && status == FileTransferStatusDto.Available -> TransferAction.Reject
            !isSender && status == FileTransferStatusDto.Downloaded && localPath != null -> TransferAction.Open
            else -> null
        }

        return TransferUiItem(
            id = id,
            fileName = fileName,
            contentType = contentType,
            metaText = metaText,
            statusText = statusText,
            isSender = isSender,
            showSuccessIcon = showSuccess,
            showProgress = showProgress,
            primaryAction = primaryAction,
            secondaryAction = secondaryAction,
            localPath = localPath
        )
    }

    private fun handleFileSelected(uri: Uri) {
        lifecycleScope.launch {
            val devicesResponse = withContext(Dispatchers.IO) { ApiClient.apiService.getDevices() }
            if (!devicesResponse.isSuccessful || devicesResponse.body().isNullOrEmpty()) {
                Toast.makeText(requireContext(), "No devices available", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val devices = devicesResponse.body()!!
            val names = devices.map { it.name }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Send to device")
                .setItems(names) { _, index ->
                    val device = devices[index]
                    sendFileToDevice(uri, device.id, device.publicKey, device.publicKeyAlgorithm)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    @SuppressLint("HardwareIds")
    private fun sendFileToDevice(uri: Uri, targetDeviceId: String, publicKey: String?, publicKeyAlgorithm: String?) {
        if (publicKey.isNullOrBlank() || publicKeyAlgorithm.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Target device encryption key missing", Toast.LENGTH_SHORT).show()
            return
        }

        if (!publicKeyAlgorithm.equals("RSA", ignoreCase = true)) {
            Toast.makeText(requireContext(), "Unsupported device key algorithm", Toast.LENGTH_SHORT).show()
            return
        }

        val resolver = requireContext().contentResolver
        val fileName = resolveFileName(uri)
        val contentType = resolver.getType(uri) ?: "application/octet-stream"
        val sizeBytes = resolveFileSize(uri)
        val senderDeviceId = Settings.Secure.getString(resolver, Settings.Secure.ANDROID_ID)

        if (sizeBytes <= 0) {
            Toast.makeText(requireContext(), "File size not available", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val sentDir = File(requireContext().filesDir, "transfers/sent")
                sentDir.mkdirs()
                val originalCopy = File(sentDir, "${System.currentTimeMillis()}_$fileName")
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(originalCopy).use { output ->
                        input.copyTo(output)
                    }
                }

                val encryptedFile = File(requireContext().cacheDir, "${System.currentTimeMillis()}_enc.bin")
                val envelope = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(encryptedFile).use { output ->
                            CryptoManager.encryptToStream(input, output, senderDeviceId, targetDeviceId, publicKey)
                        }
                    } ?: ""
                }

                if (envelope.isBlank()) {
                    Toast.makeText(requireContext(), "Failed to encrypt file", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val createRequest = CreateFileTransferRequestDto(
                    fileName = fileName,
                    contentType = contentType,
                    sizeBytes = sizeBytes,
                    senderDeviceId = senderDeviceId,
                    targetDeviceId = targetDeviceId,
                    encryptionEnvelope = envelope
                )

                val createResponse = withContext(Dispatchers.IO) { ApiClient.apiService.createFileTransfer(createRequest) }
                if (!createResponse.isSuccessful || createResponse.body() == null) {
                    Toast.makeText(requireContext(), "Failed to create transfer", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val transferId = createResponse.body()!!.id
                FileTransferLocalStore.setLocalPath(requireContext(), transferId, originalCopy.absolutePath, "sent")

                val uploadBody = StreamingRequestBody("application/octet-stream", FileInputStream(encryptedFile))
                val uploadResponse = withContext(Dispatchers.IO) { ApiClient.apiService.uploadFileTransfer(transferId, uploadBody) }

                if (!uploadResponse.isSuccessful) {
                    Toast.makeText(requireContext(), "Upload failed", Toast.LENGTH_SHORT).show()
                }

                encryptedFile.delete()
                loadTransfers()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to send file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("HardwareIds")
    private fun downloadTransfer(item: TransferUiItem) {
        val deviceId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { ApiClient.apiService.downloadFileTransfer(item.id, deviceId) }
                if (!response.isSuccessful || response.body() == null) {
                    Toast.makeText(requireContext(), "Download failed", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val envelopeHeader = response.headers()["X-Transfer-Envelope"]
                if (envelopeHeader.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "Missing encryption envelope", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val envelope = String(android.util.Base64.decode(envelopeHeader, android.util.Base64.NO_WRAP))

                val receivedDir = File(requireContext().filesDir, "transfers/received")
                receivedDir.mkdirs()
                val outputFile = File(receivedDir, "${item.id}_${item.fileName}")

                withContext(Dispatchers.IO) {
                    response.body()!!.byteStream().use { input ->
                        FileOutputStream(outputFile).use { output ->
                            CryptoManager.decryptToStream(input, output, envelope)
                        }
                    }
                }

                FileTransferLocalStore.setLocalPath(requireContext(), item.id, outputFile.absolutePath, "received")
                Toast.makeText(requireContext(), "File saved to app storage", Toast.LENGTH_SHORT).show()
                loadTransfers()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Download failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resendTransfer(item: TransferUiItem) {
        val localPath = item.localPath
        if (localPath.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Original file not available", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = Uri.fromFile(File(localPath))
        handleFileSelected(uri)
    }

    @SuppressLint("HardwareIds")
    private fun rejectTransfer(item: TransferUiItem) {
        val deviceId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) { ApiClient.apiService.rejectFileTransfer(item.id, deviceId) }
            if (response.isSuccessful) {
                Toast.makeText(requireContext(), "Transfer rejected", Toast.LENGTH_SHORT).show()
                loadTransfers()
            } else {
                Toast.makeText(requireContext(), "Failed to reject", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun moveFileToDevice(uri: Uri, item: TransferUiItem) {
        val localPath = item.localPath
        if (localPath.isNullOrBlank()) {
            Toast.makeText(requireContext(), "File not available", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val resolver = requireContext().contentResolver
                    resolver.openOutputStream(uri)?.use { output ->
                        FileInputStream(File(localPath)).use { input ->
                            input.copyTo(output)
                        }
                    }
                }
                Toast.makeText(requireContext(), "File moved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Move failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openLocalFile(item: TransferUiItem) {
        val localPath = item.localPath
        if (localPath.isNullOrBlank()) {
            Toast.makeText(requireContext(), "File not available", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(localPath)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.contentType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Open with"))
    }

    private fun resolveFileName(uri: Uri): String {
        if (uri.scheme == "file") {
            return File(uri.path ?: "").name
        }
        val resolver = requireContext().contentResolver
        val cursor = resolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    return it.getString(index)
                }
            }
        }
        return "file_${System.currentTimeMillis()}"
    }

    private fun resolveFileSize(uri: Uri): Long {
        if (uri.scheme == "file") {
            return File(uri.path ?: "").length()
        }
        val resolver = requireContext().contentResolver
        val cursor = resolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0) {
                    return it.getLong(index)
                }
            }
        }
        return 0
    }

    private fun formatSize(sizeBytes: Long): String {
        if (sizeBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = sizeBytes.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < units.lastIndex) {
            size /= 1024
            unitIndex++
        }
        val formatter = DecimalFormat("#,##0.##")
        return "${formatter.format(size)} ${units[unitIndex]}"
    }
}