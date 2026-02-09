package com.tds.binarystars.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.tds.binarystars.api.FileTransferStatusDto
import com.tds.binarystars.crypto.CryptoManager
import com.tds.binarystars.storage.FileTransferLocalStore
import com.tds.binarystars.storage.FileTransferStorage
import com.tds.binarystars.storage.LocalFileTransfer
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages Bluetooth discovery, server socket, and RFCOMM transfers.
 */
class BluetoothTransferManager(
    private val context: Context,
    private val onTransferStateChanged: (String) -> Unit
) {
    companion object {
        private const val SERVICE_NAME = "BinaryStarsTransfer"
        private val SERVICE_UUID = UUID.fromString("2b3d7e64-6d7b-4ad4-9d92-4b4c4b8d0d4c")
        private const val BUFFER_SIZE = 16 * 1024
    }

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())

    private val availableDevices = mutableMapOf<String, BluetoothDevice>()
    private var discoveryReceiver: BroadcastReceiver? = null

    private var serverThread: Thread? = null
    private var serverSocket: BluetoothServerSocket? = null

    private val activeTransfers = mutableMapOf<String, TransferJob>()

    fun isSupported(): Boolean = adapter != null

    fun isEnabled(): Boolean = adapter?.isEnabled == true

    fun getAvailableDeviceNames(): Set<String> = availableDevices.keys

    fun getAvailableDeviceByName(name: String): BluetoothDevice? = availableDevices[name]

    fun startDiscovery(onUpdate: (Map<String, BluetoothDevice>) -> Unit) {
        val bluetoothAdapter = adapter ?: return
        if (discoveryReceiver != null) return

        discoveryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        val deviceName = device?.name
                        if (!deviceName.isNullOrBlank() && device != null) {
                            availableDevices[deviceName] = device
                            onUpdate(availableDevices.toMap())
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        if (bluetoothAdapter.isEnabled) {
                            bluetoothAdapter.startDiscovery()
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(discoveryReceiver, filter)

        availableDevices.clear()
        bluetoothAdapter.cancelDiscovery()
        bluetoothAdapter.startDiscovery()
    }

    fun stopDiscovery() {
        val bluetoothAdapter = adapter ?: return
        discoveryReceiver?.let {
            context.unregisterReceiver(it)
        }
        discoveryReceiver = null
        availableDevices.clear()
        bluetoothAdapter.cancelDiscovery()
    }

    fun startServer() {
        val bluetoothAdapter = adapter ?: return
        if (serverThread != null) return

        serverThread = Thread {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                while (!Thread.currentThread().isInterrupted) {
                    val socket = serverSocket?.accept() ?: break
                    handleIncomingTransfer(socket)
                }
            } catch (e: Exception) {
                // Server socket closed or failed.
            } finally {
                try {
                    serverSocket?.close()
                } catch (e: Exception) {
                    // Ignore.
                }
                serverSocket = null
            }
        }.also { it.start() }
    }

    fun stopServer() {
        serverThread?.interrupt()
        serverThread = null
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore.
        }
        serverSocket = null
    }

    fun cancelTransfer(transferId: String) {
        activeTransfers[transferId]?.cancel()
    }

    suspend fun sendTransfer(state: BluetoothTransferState, device: BluetoothDevice) {
        val transferId = state.transferId
        val cancelFlag = AtomicBoolean(false)
        val job = TransferJob(transferId, cancelFlag)
        activeTransfers[transferId] = job

        try {
            val bluetoothAdapter = adapter
            bluetoothAdapter?.cancelDiscovery()

            val socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
            job.socket = socket
            socket.connect()

            val input = DataInputStream(BufferedInputStream(socket.inputStream))
            val output = DataOutputStream(BufferedOutputStream(socket.outputStream))
            val headerBytes = gson.toJson(state.toHeader()).toByteArray(Charsets.UTF_8)
            output.writeInt(headerBytes.size)
            output.write(headerBytes)
            output.flush()

            val offset = input.readLong().coerceAtLeast(0)
            val encryptedFile = File(state.encryptedPath ?: "")
            if (!encryptedFile.exists()) {
                updateTransferStatus(state, FileTransferStatusDto.Failed, state.bytesTransferred)
                return
            }

            val totalSize = state.encryptedSizeBytes
            var bytesSent = offset
            updateTransferStatus(state, FileTransferStatusDto.Uploading, bytesSent)

            RandomAccessFile(encryptedFile, "r").use { raf ->
                raf.seek(offset)
                val buffer = ByteArray(BUFFER_SIZE)
                while (bytesSent < totalSize && !cancelFlag.get()) {
                    val read = raf.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    bytesSent += read
                    updateTransferStatus(state, FileTransferStatusDto.Uploading, bytesSent)
                }
                output.flush()
            }

            if (cancelFlag.get()) {
                updateTransferStatus(state, FileTransferStatusDto.Rejected, bytesSent)
                return
            }

            if (bytesSent >= totalSize) {
                updateTransferStatus(state, FileTransferStatusDto.Downloaded, bytesSent)
                encryptedFile.delete()
            } else {
                updateTransferStatus(state, FileTransferStatusDto.Failed, bytesSent)
            }
        } catch (e: Exception) {
            updateTransferStatus(state, FileTransferStatusDto.Failed, state.bytesTransferred)
        } finally {
            try {
                job.socket?.close()
            } catch (e: Exception) {
                // Ignore.
            }
            activeTransfers.remove(transferId)
        }
    }

    private fun handleIncomingTransfer(socket: BluetoothSocket) {
        val transferId: String
        try {
            val input = DataInputStream(BufferedInputStream(socket.inputStream))
            val output = DataOutputStream(BufferedOutputStream(socket.outputStream))
            val headerLength = input.readInt()
            val headerBytes = ByteArray(headerLength)
            input.readFully(headerBytes)
            val header = gson.fromJson(String(headerBytes, Charsets.UTF_8), BluetoothTransferHeader::class.java)
            transferId = header.transferId

            val encryptedFile = File(context.cacheDir, "bt_${header.transferId}.bin")
            var currentSize = if (encryptedFile.exists()) encryptedFile.length() else 0L
            if (currentSize > header.encryptedSizeBytes) {
                encryptedFile.delete()
                currentSize = 0L
            }

            output.writeLong(currentSize)
            output.flush()

            val transfer = ensureReceiverTransfer(header)
            updateReceiverState(
                transfer,
                header,
                FileTransferStatusDto.Uploading,
                currentSize,
                encryptedFile.absolutePath
            )

            FileOutputStream(encryptedFile, currentSize > 0).use { fileOut ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesReceived = currentSize
                while (bytesReceived < header.encryptedSizeBytes) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    fileOut.write(buffer, 0, read)
                    bytesReceived += read
                    updateReceiverState(
                        transfer,
                        header,
                        FileTransferStatusDto.Uploading,
                        bytesReceived,
                        encryptedFile.absolutePath
                    )
                }
            }

            if (encryptedFile.length() >= header.encryptedSizeBytes) {
                val receivedDir = File(context.filesDir, "transfers/received")
                receivedDir.mkdirs()
                val outputFile = File(receivedDir, "${header.transferId}_${header.fileName}")
                FileInputStream(encryptedFile).use { encryptedInput ->
                    FileOutputStream(outputFile).use { decryptedOutput ->
                        CryptoManager.decryptToStream(encryptedInput, decryptedOutput, header.envelope)
                    }
                }

                FileTransferLocalStore.setLocalPath(context, header.transferId, outputFile.absolutePath, "received")
                updateReceiverState(
                    transfer,
                    header,
                    FileTransferStatusDto.Downloaded,
                    header.encryptedSizeBytes,
                    null,
                    outputFile.absolutePath
                )
                encryptedFile.delete()
            } else {
                updateReceiverState(
                    transfer,
                    header,
                    FileTransferStatusDto.Failed,
                    encryptedFile.length(),
                    encryptedFile.absolutePath
                )
            }
        } catch (e: Exception) {
            // Ignore and keep whatever partial data is stored.
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                // Ignore.
            }
        }
    }

    private fun ensureReceiverTransfer(header: BluetoothTransferHeader): LocalFileTransfer {
        val transfer = LocalFileTransfer(
            id = header.transferId,
            fileName = header.fileName,
            contentType = header.contentType,
            sizeBytes = header.originalSizeBytes,
            senderDeviceId = header.senderDeviceId,
            senderDeviceName = header.senderDeviceName,
            targetDeviceId = header.targetDeviceId,
            targetDeviceName = header.targetDeviceName,
            status = FileTransferStatusDto.Uploading.name,
            createdAt = header.createdAt,
            expiresAt = header.expiresAt,
            isSender = false
        )
        FileTransferStorage.upsertTransfer(transfer)
        return transfer
    }

    private fun updateReceiverState(
        transfer: LocalFileTransfer,
        header: BluetoothTransferHeader,
        status: FileTransferStatusDto,
        bytesTransferred: Long,
        encryptedPath: String?,
        decryptedPath: String? = null
    ) {
        val updated = transfer.copy(status = status.name)
        FileTransferStorage.upsertTransfer(updated)

        val currentState = BluetoothTransferStore.get(context, transfer.id)
        val nextState = (currentState ?: BluetoothTransferState(
            transferId = transfer.id,
            fileName = transfer.fileName,
            contentType = transfer.contentType,
            originalSizeBytes = transfer.sizeBytes,
            encryptedSizeBytes = header.encryptedSizeBytes,
            senderDeviceId = transfer.senderDeviceId,
            senderDeviceName = transfer.senderDeviceName,
            targetDeviceId = transfer.targetDeviceId,
            targetDeviceName = transfer.targetDeviceName,
            envelope = header.envelope,
            encryptedPath = encryptedPath,
            decryptedPath = decryptedPath,
            bytesTransferred = bytesTransferred,
            status = status.name,
            isSender = false,
            createdAt = transfer.createdAt,
            expiresAt = transfer.expiresAt
        )).copy(
            encryptedPath = encryptedPath,
            decryptedPath = decryptedPath ?: currentState?.decryptedPath,
            bytesTransferred = bytesTransferred,
            status = status.name
        )

        BluetoothTransferStore.upsert(context, nextState)
        notifyTransferChanged(transfer.id)
    }

    private fun updateTransferStatus(state: BluetoothTransferState, status: FileTransferStatusDto, bytesTransferred: Long) {
        val clearedPath = if (status == FileTransferStatusDto.Downloaded || status == FileTransferStatusDto.Rejected) {
            null
        } else {
            state.encryptedPath
        }
        val updatedState = state.copy(
            bytesTransferred = bytesTransferred,
            status = status.name,
            encryptedPath = clearedPath
        )
        BluetoothTransferStore.upsert(context, updatedState)

        val transfer = LocalFileTransfer(
            id = state.transferId,
            fileName = state.fileName,
            contentType = state.contentType,
            sizeBytes = state.originalSizeBytes,
            senderDeviceId = state.senderDeviceId,
            senderDeviceName = state.senderDeviceName,
            targetDeviceId = state.targetDeviceId,
            targetDeviceName = state.targetDeviceName,
            status = status.name,
            createdAt = state.createdAt,
            expiresAt = state.expiresAt,
            isSender = true
        )
        FileTransferStorage.upsertTransfer(transfer)
        notifyTransferChanged(state.transferId)
    }

    private fun notifyTransferChanged(transferId: String) {
        handler.post { onTransferStateChanged(transferId) }
    }

    private fun BluetoothTransferState.toHeader(): BluetoothTransferHeader {
        return BluetoothTransferHeader(
            transferId = transferId,
            fileName = fileName,
            contentType = contentType,
            originalSizeBytes = originalSizeBytes,
            encryptedSizeBytes = encryptedSizeBytes,
            senderDeviceId = senderDeviceId,
            senderDeviceName = senderDeviceName,
            targetDeviceId = targetDeviceId,
            targetDeviceName = targetDeviceName,
            envelope = envelope,
            createdAt = createdAt,
            expiresAt = expiresAt
        )
    }

    private data class TransferJob(
        val transferId: String,
        val cancelFlag: AtomicBoolean,
        var socket: BluetoothSocket? = null
    ) {
        fun cancel() {
            cancelFlag.set(true)
            try {
                socket?.close()
            } catch (e: Exception) {
                // Ignore.
            }
        }
    }
}

private data class BluetoothTransferHeader(
    val transferId: String,
    val fileName: String,
    val contentType: String,
    val originalSizeBytes: Long,
    val encryptedSizeBytes: Long,
    val senderDeviceId: String,
    val senderDeviceName: String,
    val targetDeviceId: String,
    val targetDeviceName: String,
    val envelope: String,
    val createdAt: String,
    val expiresAt: String
)
