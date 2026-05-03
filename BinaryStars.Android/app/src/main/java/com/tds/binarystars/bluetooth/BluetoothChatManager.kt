package com.tds.binarystars.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.tds.binarystars.util.NativeLogging as L
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * ISOLATED Bluetooth Manager (Last Hope - MVP Style).
 */
object BluetoothChatManager {
    private const val TAG = "BinaryStarsBT"
    private const val SPP_UUID = "00001101-0000-1000-8000-00805f9b34fb"
    private const val SERVICE_NAME = "BinaryStarsSPP"

    private var selfDeviceId: String = ""
    private val activeConnections = ConcurrentHashMap<String, BluetoothConnection>()
    private val macToPeerId = ConcurrentHashMap<String, String>()
    
    private val _messages = MutableStateFlow<List<com.tds.binarystars.model.ChatMessage>>(emptyList())
    val messages: StateFlow<List<com.tds.binarystars.model.ChatMessage>> = _messages

    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        selfDeviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
    }

    fun clearSession() {
        activeConnections.values.forEach { it.close() }
        activeConnections.clear()
        _messages.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean {
        val macAddress = device.address
        return withContext(Dispatchers.IO) {
            try {
                val socket = device.createRfcommSocketToServiceRecord(UUID.fromString(SPP_UUID))
                socket.connect()
                
                val writer = socket.outputStream
                val reader = BufferedReader(InputStreamReader(socket.inputStream))
                
                // Handshake (Same User Check)
                writer.write("IDENTIFY|$selfDeviceId\n".toByteArray())
                writer.flush()
                
                val line = withTimeoutOrNull(5000) { reader.readLine() }
                
                if (line?.startsWith("IDENTIFIED|") == true) {
                    val peerId = line.split("|")[1]
                    val accountDevices = com.tds.binarystars.storage.DeviceCacheStorage.getDevices()
                    if (accountDevices.any { it.id == peerId }) {
                        val connection = BluetoothConnection(socket, peerId, reader, writer)
                        macToPeerId[macAddress] = peerId
                        activeConnections[peerId] = connection
                        connection.startReading()
                        return@withContext true
                    }
                }
                socket.close()
                false
            } catch (e: Exception) {
                L.e(TAG, "Connect failed: ${e.message}")
                false
            }
        }
    }

    fun disconnect(deviceIdOrMac: String) {
        val peerId = macToPeerId[deviceIdOrMac] ?: deviceIdOrMac
        activeConnections[peerId]?.close()
        activeConnections.remove(peerId)
        macToPeerId.remove(deviceIdOrMac)
        // Also remove reverse mapping if any
        macToPeerId.values.removeIf { it == peerId }
    }

    fun acquireServer() {
        if (serverJob != null) return
        serverJob = scope.launch { startServer() }
    }

    fun releaseServer() {
        serverJob?.cancel()
        serverJob = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun startServer() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        var serverSocket: BluetoothServerSocket? = null
        try {
            serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, UUID.fromString(SPP_UUID))
            while (coroutineContext.isActive) {
                val socket = serverSocket.accept() ?: continue
                scope.launch {
                    val writer = socket.outputStream
                    val reader = BufferedReader(InputStreamReader(socket.inputStream))
                    
                    val line = withTimeoutOrNull(5000) { reader.readLine() }
                    if (line?.startsWith("IDENTIFY|") == true) {
                        val peerId = line.split("|")[1]
                        val accountDevices = com.tds.binarystars.storage.DeviceCacheStorage.getDevices()
                        if (accountDevices.any { it.id == peerId }) {
                            writer.write("IDENTIFIED|$selfDeviceId\n".toByteArray())
                            writer.flush()
                            val connection = BluetoothConnection(socket, peerId, reader, writer)
                            val macAddress = socket.remoteDevice.address
                            macToPeerId[macAddress] = peerId
                            activeConnections[peerId] = connection
                            connection.startReading()
                        } else {
                            socket.close()
                        }
                    } else {
                        socket.close()
                    }
                }
            }
        } catch (e: Exception) {
            L.e(TAG, "Server error: ${e.message}")
        } finally {
            try { serverSocket?.close() } catch (_: Exception) {}
        }
    }

    fun sendMessage(deviceIdOrMac: String, content: String): Boolean {
        val peerId = macToPeerId[deviceIdOrMac] ?: deviceIdOrMac
        val sanitized = content.replace("\n", " ")
        val success = activeConnections[peerId]?.send(sanitized) ?: false
        if (success) addLocalMessage(peerId, sanitized, true)
        return success
    }

    fun sendFile(deviceIdOrMac: String, fileName: String, base64Data: String): Boolean {
        val peerId = macToPeerId[deviceIdOrMac] ?: deviceIdOrMac
        val success = activeConnections[peerId]?.send("FILE|$fileName|$base64Data") ?: false
        if (success) addLocalMessage(peerId, "Sent file: $fileName", true)
        return success
    }

    internal fun addLocalMessage(deviceId: String, body: String, isOutgoing: Boolean) {
        val msg = com.tds.binarystars.model.ChatMessage(
            id = UUID.randomUUID().toString(),
            deviceId = deviceId,
            senderDeviceId = if (isOutgoing) selfDeviceId else deviceId,
            body = body,
            sentAt = System.currentTimeMillis(),
            isOutgoing = isOutgoing
        )
        _messages.value = _messages.value + msg
    }

    fun removeMessage(id: String) {
        _messages.value = _messages.value.filter { it.id != id }
    }

    private class BluetoothConnection(
        private val socket: android.bluetooth.BluetoothSocket, 
        val peerId: String,
        private val reader: java.io.BufferedReader,
        private val writer: java.io.OutputStream
    ) {
        private var isClosed = false

        fun startReading() {
            scope.launch {
                try {
                    while (!isClosed) {
                        val line = reader.readLine() ?: break
                        handleLine(line)
                    }
                } catch (e: Exception) {
                    L.e(TAG, "Read error: ${e.message}")
                } finally {
                    close()
                }
            }
        }

        private fun handleLine(line: String) {
            if (line.startsWith("FILE|")) {
                val parts = line.split("|", limit = 3)
                if (parts.size >= 3) {
                    saveReceivedFile(peerId, parts[1], parts[2])
                }
            } else {
                BluetoothChatManager.addLocalMessage(peerId, line, false)
            }
        }

        private fun saveReceivedFile(senderId: String, fileName: String, base64Data: String) {
            val context = appContext ?: return
            scope.launch {
                try {
                    val bytes = android.util.Base64.decode(base64Data, android.util.Base64.NO_WRAP)
                    val file = java.io.File(context.getExternalFilesDir(null), fileName)
                    java.io.FileOutputStream(file).use { it.write(bytes) }
                    BluetoothChatManager.addLocalMessage(senderId, "Received file: $fileName", false)
                } catch (e: Exception) {
                    L.e(TAG, "File save error: ${e.message}")
                }
            }
        }

        fun send(content: String): Boolean {
            return try {
                // Content is already sanitized for newlines by callers
                writer.write((content + "\n").toByteArray())
                writer.flush()
                true
            } catch (e: Exception) {
                false
            }
        }

        fun close() {
            if (isClosed) return
            isClosed = true
            try { socket.close() } catch (_: Exception) {}
            // Notify manager to remove from maps without calling close() again
            BluetoothChatManager.onConnectionClosed(peerId)
        }
    }

    internal fun onConnectionClosed(peerId: String) {
        activeConnections.remove(peerId)
        macToPeerId.values.removeIf { it == peerId }
    }
}
