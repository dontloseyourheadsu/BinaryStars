package com.tds.binarystars.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
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
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
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
import com.tds.binarystars.bluetooth.BluetoothTransferManager
import com.tds.binarystars.bluetooth.BluetoothTransferState
import com.tds.binarystars.bluetooth.BluetoothTransferStore
import com.tds.binarystars.api.StreamingRequestBody
import com.tds.binarystars.crypto.CryptoManager
import com.tds.binarystars.storage.FileTransferLocalStore
import com.tds.binarystars.storage.FileTransferStorage
import com.tds.binarystars.storage.LocalFileTransfer
import com.tds.binarystars.MainActivity
import com.tds.binarystars.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.time.Instant
import java.util.UUID
import androidx.core.content.ContextCompat
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager

class FilesFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: TextView
    private lateinit var sendButton: Button
    private lateinit var adapter: FileTransfersAdapter
    private lateinit var contentView: View
    private lateinit var noConnectionView: View
    private lateinit var retryButton: Button
    private val items = mutableListOf<TransferUiItem>()
    private var pendingMoveItem: TransferUiItem? = null
    private lateinit var bluetoothManager: BluetoothTransferManager
    private var bluetoothAvailableDevices: Set<String> = emptySet()

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.any { it }
        if (granted) {
            startBluetoothFeatures()
        } else {
            Toast.makeText(requireContext(), "Bluetooth permissions denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(StartActivityForResult()) {
        if (bluetoothManager.isEnabled()) {
            startBluetoothFeatures()
        } else {
            Toast.makeText(requireContext(), "Bluetooth is disabled", Toast.LENGTH_SHORT).show()
        }
    }

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

    /**
     * Inflates the file transfers UI.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_files, container, false)
    }

    /**
     * Initializes UI widgets, adapters, and loads transfers.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.ivMenu)?.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        recyclerView = view.findViewById(R.id.rvFileTransfers)
        progressBar = view.findViewById(R.id.pbFilesLoading)
        emptyState = view.findViewById(R.id.tvFilesEmptyState)
        sendButton = view.findViewById(R.id.btnSendFile)
        contentView = view.findViewById(R.id.viewContent)
        noConnectionView = view.findViewById(R.id.viewNoConnection)
        retryButton = view.findViewById(R.id.btnRetry)

        bluetoothManager = BluetoothTransferManager(requireContext()) {
            viewLifecycleOwner.lifecycleScope.launch {
                refreshTransfersFromCache()
            }
        }

        adapter = FileTransfersAdapter(items) { item, action ->
            when (action) {
                TransferAction.Download -> downloadTransfer(item)
                TransferAction.Resend -> resendTransfer(item)
                TransferAction.Resume -> resumeBluetoothTransfer(item)
                TransferAction.Cancel -> cancelBluetoothTransfer(item)
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

        retryButton.setOnClickListener {
            loadTransfers()
        }

        loadTransfers()
    }

    override fun onResume() {
        super.onResume()
        loadTransfers()
    }

    override fun onStart() {
        super.onStart()
        ensureBluetoothReady()
    }

    override fun onStop() {
        super.onStop()
        stopBluetoothFeatures()
    }

    /**
     * Loads transfers from API or cache, updating the UI for offline mode.
     */
    private fun loadTransfers() {
        viewLifecycleOwner.lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val isOnline = NetworkUtils.isOnline(requireContext())
            val bluetoothStates = withContext(Dispatchers.IO) { BluetoothTransferStore.getAll(requireContext()) }
            if (!isOnline) {
                val cached = withContext(Dispatchers.IO) { FileTransferStorage.getTransfers() }
                progressBar.visibility = View.GONE
                sendButton.isEnabled = false
                if (cached.isEmpty()) {
                    setNoConnection(true)
                    return@launch
                }

                setNoConnection(false)
                items.clear()
                items.addAll(buildUiItemsFromLocal(cached, bluetoothStates, bluetoothAvailableDevices))
                adapter.notifyDataSetChanged()
                updateEmptyState()
                return@launch
            }

            sendButton.isEnabled = true
            setNoConnection(false)
            val devicesResponse = withContext(Dispatchers.IO) { ApiClient.apiService.getDevices() }
            val transfersResponse = withContext(Dispatchers.IO) { ApiClient.apiService.getFileTransfers() }
            progressBar.visibility = View.GONE

            if (devicesResponse.isSuccessful && transfersResponse.isSuccessful) {
                val devices = devicesResponse.body().orEmpty()
                val deviceLookup = devices.associateBy({ it.id }, { it.name })

                val locals = transfersResponse.body().orEmpty().map { dto ->
                    LocalFileTransfer(
                        id = dto.id,
                        fileName = dto.fileName,
                        contentType = dto.contentType,
                        sizeBytes = dto.sizeBytes,
                        senderDeviceId = dto.senderDeviceId,
                        senderDeviceName = deviceLookup[dto.senderDeviceId] ?: dto.senderDeviceId,
                        targetDeviceId = dto.targetDeviceId,
                        targetDeviceName = deviceLookup[dto.targetDeviceId] ?: dto.targetDeviceId,
                        status = dto.status.name,
                        createdAt = dto.createdAt,
                        expiresAt = dto.expiresAt,
                        isSender = dto.isSender
                    )
                }

                withContext(Dispatchers.IO) { FileTransferStorage.upsertTransfers(locals) }
                refreshTransfersFromCache()
            } else {
                emptyState.text = "Failed to load transfers"
                emptyState.visibility = View.VISIBLE
            }
        }
    }

    private suspend fun refreshTransfersFromCache() {
        val cached = withContext(Dispatchers.IO) { FileTransferStorage.getTransfers() }
        val bluetoothStates = withContext(Dispatchers.IO) { BluetoothTransferStore.getAll(requireContext()) }
        items.clear()
        items.addAll(buildUiItemsFromLocal(cached, bluetoothStates, bluetoothAvailableDevices))
        adapter.notifyDataSetChanged()
        setNoConnection(false)
        updateEmptyState()
    }

    /**
     * Updates the empty state UI based on list contents.
     */
    private fun updateEmptyState() {
        if (items.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    /**
     * Maps cached transfers into UI list items.
     */
    private fun buildUiItemsFromLocal(
        transfers: List<LocalFileTransfer>,
        bluetoothStates: Map<String, BluetoothTransferState>,
        bluetoothAvailable: Set<String>
    ): List<TransferUiItem> {
        return transfers.map { transfer ->
            val directionLabel = if (transfer.isSender) {
                "To: ${transfer.targetDeviceName}"
            } else {
                "From: ${transfer.senderDeviceName}"
            }
            val sizeText = formatSize(transfer.sizeBytes)
            val meta = "$directionLabel • $sizeText"
            val status = FileTransferStatusDto.valueOf(transfer.status)
            val localPath = FileTransferLocalStore.getLocalPath(requireContext(), transfer.id)
            val bluetoothState = bluetoothStates[transfer.id]
            val isBluetoothTransfer = bluetoothState != null
            val bluetoothAvailableForTarget = transfer.isSender && bluetoothAvailable.contains(transfer.targetDeviceName)
            val showBluetooth = bluetoothAvailableForTarget
            buildUiItem(
                transfer.id,
                transfer.fileName,
                transfer.contentType,
                meta,
                status,
                transfer.isSender,
                localPath,
                showBluetooth,
                isBluetoothTransfer,
                bluetoothAvailableForTarget
            )
        }
    }

    /**
     * Toggles offline state UI panels.
     */
    private fun setNoConnection(show: Boolean) {
        noConnectionView.visibility = if (show) View.VISIBLE else View.GONE
        contentView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun ensureBluetoothReady() {
        if (!bluetoothManager.isSupported()) return
        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions()
            return
        }
        if (!bluetoothManager.isEnabled()) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        startBluetoothFeatures()
    }

    private fun startBluetoothFeatures() {
        bluetoothManager.startDiscovery { devices ->
            bluetoothAvailableDevices = devices.keys
            viewLifecycleOwner.lifecycleScope.launch {
                refreshTransfersFromCache()
            }
        }
        bluetoothManager.startServer()
    }

    private fun stopBluetoothFeatures() {
        bluetoothManager.stopDiscovery()
        bluetoothManager.stopServer()
    }

    private fun hasBluetoothPermissions(): Boolean {
        val permissions = requiredBluetoothPermissions()
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        bluetoothPermissionLauncher.launch(requiredBluetoothPermissions())
    }

    private fun requiredBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /**
     * Builds a UI model for a file transfer row.
     */
    private fun buildUiItem(
        id: String,
        fileName: String,
        contentType: String,
        metaText: String,
        status: FileTransferStatusDto,
        isSender: Boolean,
        localPath: String?,
        showBluetooth: Boolean,
        isBluetoothTransfer: Boolean,
        bluetoothAvailableForTarget: Boolean
    ): TransferUiItem {
        val statusText = when (status) {
            FileTransferStatusDto.Queued -> if (isSender) "Queued" else "Preparing"
            FileTransferStatusDto.Uploading -> "Uploading"
            FileTransferStatusDto.Available -> if (isSender) "Waiting for download" else "Ready to download"
            FileTransferStatusDto.Downloaded -> if (isSender) "Delivered" else "Downloaded"
            FileTransferStatusDto.Failed -> if (isBluetoothTransfer) "Interrupted" else "Failed"
            FileTransferStatusDto.Expired -> "Expired"
            FileTransferStatusDto.Rejected -> if (isBluetoothTransfer) "Canceled" else "Rejected"
        }

        val showProgress = status == FileTransferStatusDto.Uploading || status == FileTransferStatusDto.Queued
        val showSuccess = status == FileTransferStatusDto.Downloaded

        val primaryAction = when {
            isBluetoothTransfer && isSender && status == FileTransferStatusDto.Uploading -> TransferAction.Cancel
            isBluetoothTransfer && isSender && status == FileTransferStatusDto.Failed && bluetoothAvailableForTarget -> TransferAction.Resume
            !isBluetoothTransfer && !isSender && status == FileTransferStatusDto.Available -> TransferAction.Download
            !isSender && status == FileTransferStatusDto.Downloaded && localPath != null -> TransferAction.Move
            !isBluetoothTransfer && isSender &&
                (status == FileTransferStatusDto.Failed || status == FileTransferStatusDto.Expired || status == FileTransferStatusDto.Rejected) -> TransferAction.Resend
            else -> null
        }

        val secondaryAction = when {
            !isBluetoothTransfer && !isSender && status == FileTransferStatusDto.Available -> TransferAction.Reject
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
            showBluetoothIcon = showBluetooth,
            isBluetoothTransfer = isBluetoothTransfer,
            primaryAction = primaryAction,
            secondaryAction = secondaryAction,
            localPath = localPath
        )
    }

    /**
     * Opens a device picker to choose a target for sending a file.
     */
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
                    val bluetoothAvailable = bluetoothAvailableDevices.contains(device.name)
                    if (bluetoothAvailable) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Send via")
                            .setItems(arrayOf("Bluetooth", "API")) { _, methodIndex ->
                                if (methodIndex == 0) {
                                    sendFileToDeviceBluetooth(uri, device.id, device.name, device.publicKey, device.publicKeyAlgorithm)
                                } else {
                                    sendFileToDeviceApi(uri, device.id, device.publicKey, device.publicKeyAlgorithm)
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    } else {
                        sendFileToDeviceApi(uri, device.id, device.publicKey, device.publicKeyAlgorithm)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    @SuppressLint("HardwareIds")
    /**
     * Encrypts and uploads a file to the selected device.
     */
    private fun sendFileToDeviceApi(uri: Uri, targetDeviceId: String, publicKey: String?, publicKeyAlgorithm: String?) {
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
    private fun sendFileToDeviceBluetooth(
        uri: Uri,
        targetDeviceId: String,
        targetDeviceName: String,
        publicKey: String?,
        publicKeyAlgorithm: String?
    ) {
        if (publicKey.isNullOrBlank() || publicKeyAlgorithm.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Target device encryption key missing", Toast.LENGTH_SHORT).show()
            return
        }

        if (!publicKeyAlgorithm.equals("RSA", ignoreCase = true)) {
            Toast.makeText(requireContext(), "Unsupported device key algorithm", Toast.LENGTH_SHORT).show()
            return
        }

        val device = bluetoothManager.getAvailableDeviceByName(targetDeviceName)
        if (device == null) {
            Toast.makeText(requireContext(), "Bluetooth device not in range", Toast.LENGTH_SHORT).show()
            return
        }

        val resolver = requireContext().contentResolver
        val fileName = resolveFileName(uri)
        val contentType = resolver.getType(uri) ?: "application/octet-stream"
        val sizeBytes = resolveFileSize(uri)
        val senderDeviceId = Settings.Secure.getString(resolver, Settings.Secure.ANDROID_ID)
        val senderDeviceName = BluetoothAdapter.getDefaultAdapter()?.name ?: "Android"

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

                val encryptedFile = File(requireContext().cacheDir, "${System.currentTimeMillis()}_bt_enc.bin")
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

                val transferId = "bt_${UUID.randomUUID()}"
                val createdAt = Instant.now().toString()
                val transfer = LocalFileTransfer(
                    id = transferId,
                    fileName = fileName,
                    contentType = contentType,
                    sizeBytes = sizeBytes,
                    senderDeviceId = senderDeviceId,
                    senderDeviceName = senderDeviceName,
                    targetDeviceId = targetDeviceId,
                    targetDeviceName = targetDeviceName,
                    status = FileTransferStatusDto.Uploading.name,
                    createdAt = createdAt,
                    expiresAt = createdAt,
                    isSender = true
                )
                withContext(Dispatchers.IO) { FileTransferStorage.upsertTransfer(transfer) }
                FileTransferLocalStore.setLocalPath(requireContext(), transferId, originalCopy.absolutePath, "sent")

                val state = BluetoothTransferState(
                    transferId = transferId,
                    fileName = fileName,
                    contentType = contentType,
                    originalSizeBytes = sizeBytes,
                    encryptedSizeBytes = encryptedFile.length(),
                    senderDeviceId = senderDeviceId,
                    senderDeviceName = senderDeviceName,
                    targetDeviceId = targetDeviceId,
                    targetDeviceName = targetDeviceName,
                    envelope = envelope,
                    encryptedPath = encryptedFile.absolutePath,
                    decryptedPath = null,
                    bytesTransferred = 0,
                    status = FileTransferStatusDto.Uploading.name,
                    isSender = true,
                    createdAt = createdAt,
                    expiresAt = createdAt
                )
                withContext(Dispatchers.IO) { BluetoothTransferStore.upsert(requireContext(), state) }

                withContext(Dispatchers.IO) { bluetoothManager.sendTransfer(state, device) }
                loadTransfers()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Bluetooth send failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("HardwareIds")
    /**
     * Downloads, decrypts, and stores a transfer locally.
     */
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

    /**
     * Resends a previously sent transfer using the cached file path.
     */
    private fun resendTransfer(item: TransferUiItem) {
        val localPath = item.localPath
        if (localPath.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Original file not available", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = Uri.fromFile(File(localPath))
        handleFileSelected(uri)
    }

    private fun resumeBluetoothTransfer(item: TransferUiItem) {
        val state = BluetoothTransferStore.get(requireContext(), item.id)
        if (state == null) {
            Toast.makeText(requireContext(), "Bluetooth transfer not available", Toast.LENGTH_SHORT).show()
            return
        }

        if (state.encryptedPath.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Resume file not available", Toast.LENGTH_SHORT).show()
            return
        }

        val device = bluetoothManager.getAvailableDeviceByName(state.targetDeviceName)
        if (device == null) {
            Toast.makeText(requireContext(), "Target device not in range", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { bluetoothManager.sendTransfer(state, device) }
                loadTransfers()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Resume failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cancelBluetoothTransfer(item: TransferUiItem) {
        val state = BluetoothTransferStore.get(requireContext(), item.id)
        if (state == null) {
            Toast.makeText(requireContext(), "Bluetooth transfer not available", Toast.LENGTH_SHORT).show()
            return
        }

        bluetoothManager.cancelTransfer(item.id)
        state.encryptedPath?.let { path ->
            runCatching { File(path).delete() }
        }
        val canceledState = state.copy(
            status = FileTransferStatusDto.Rejected.name,
            encryptedPath = null
        )
        BluetoothTransferStore.upsert(requireContext(), canceledState)

        val canceledTransfer = LocalFileTransfer(
            id = canceledState.transferId,
            fileName = canceledState.fileName,
            contentType = canceledState.contentType,
            sizeBytes = canceledState.originalSizeBytes,
            senderDeviceId = canceledState.senderDeviceId,
            senderDeviceName = canceledState.senderDeviceName,
            targetDeviceId = canceledState.targetDeviceId,
            targetDeviceName = canceledState.targetDeviceName,
            status = canceledState.status,
            createdAt = canceledState.createdAt,
            expiresAt = canceledState.expiresAt,
            isSender = true
        )
        FileTransferStorage.upsertTransfer(canceledTransfer)
        loadTransfers()
    }

    @SuppressLint("HardwareIds")
    /**
     * Rejects a pending transfer for the current device.
     */
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

    /**
     * Moves a downloaded transfer from app storage to a user-selected location.
     */
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

    /**
     * Opens a local file using an external app.
     */
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

    /**
     * Resolves a display name for a content Uri.
     */
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

    /**
     * Resolves size in bytes for a content Uri.
     */
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

    /**
     * Formats bytes into a human readable size string.
     */
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