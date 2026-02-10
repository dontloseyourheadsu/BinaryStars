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
import com.tds.binarystars.model.ChatMessage
import com.tds.binarystars.storage.ChatStorage
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages Bluetooth discovery and RFCOMM messaging sessions.
 */
object BluetoothChatManager {
    private const val SERVICE_NAME = "BinaryStarsChat"
    private val SERVICE_UUID = UUID.fromString("a64f3f3b-5ad0-4b65-9b8f-585a45c7e9c4")
    private const val MAX_MESSAGE_LENGTH = 500

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val availableDevices = mutableMapOf<String, BluetoothDevice>()
    private val discoveryListeners = CopyOnWriteArrayList<(Map<String, BluetoothDevice>) -> Unit>()
    private var discoveryReceiver: BroadcastReceiver? = null
    private var discoveryRefs = 0

    private var serverThread: Thread? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var serverRefs = 0

    private var selfDeviceId: String? = null
    private var selfDeviceName: String? = null

    private val listeners = CopyOnWriteArrayList<BluetoothChatListener>()
    private val connections = mutableMapOf<String, ChatConnection>()
    private val connectingDevices = mutableSetOf<String>()
    private val allowedDevices = mutableSetOf<String>()

    fun isSupported(): Boolean = adapter != null

    fun isEnabled(): Boolean = adapter?.isEnabled == true

    fun setSelf(deviceId: String, deviceName: String) {
        selfDeviceId = deviceId
        selfDeviceName = deviceName
    }

    fun addListener(listener: BluetoothChatListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: BluetoothChatListener) {
        listeners.remove(listener)
    }

    fun getAvailableDeviceNames(): Set<String> = availableDevices.keys

    fun getAvailableDeviceByName(name: String): BluetoothDevice? = availableDevices[name]

    fun acquireDiscovery(context: Context, listener: (Map<String, BluetoothDevice>) -> Unit) {
        discoveryListeners.add(listener)
        if (discoveryRefs == 0) {
            startDiscovery(context)
        }
        discoveryRefs++
    }

    fun releaseDiscovery(context: Context, listener: (Map<String, BluetoothDevice>) -> Unit) {
        discoveryListeners.remove(listener)
        discoveryRefs = (discoveryRefs - 1).coerceAtLeast(0)
        if (discoveryRefs == 0) {
            stopDiscovery(context)
        }
    }

    fun acquireServer() {
        serverRefs++
        if (serverRefs == 1) {
            startServer()
        }
    }

    fun releaseServer() {
        serverRefs = (serverRefs - 1).coerceAtLeast(0)
        if (serverRefs == 0) {
            stopServer()
        }
    }

    fun setChatEnabled(deviceId: String, enabled: Boolean) {
        if (enabled) {
            allowedDevices.add(deviceId)
        } else {
            allowedDevices.remove(deviceId)
            disconnect(deviceId)
        }
    }

    suspend fun connectToDevice(targetDeviceId: String, targetDeviceName: String): Boolean {
        if (connections.containsKey(targetDeviceId)) {
            notifyConnection(targetDeviceId, true, false)
            return true
        }

        val bluetoothAdapter = adapter ?: return false
        val device = availableDevices[targetDeviceName] ?: return false
        val currentDeviceId = selfDeviceId ?: return false
        val currentDeviceName = selfDeviceName ?: "Android"

        setConnecting(targetDeviceId, true)

        return try {
            bluetoothAdapter.cancelDiscovery()
            val socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
            socket.connect()

            val input = DataInputStream(BufferedInputStream(socket.inputStream))
            val output = DataOutputStream(BufferedOutputStream(socket.outputStream))

            val request = ChatHandshakeRequest(
                senderDeviceId = currentDeviceId,
                senderDeviceName = currentDeviceName,
                targetDeviceId = targetDeviceId
            )
            writeFrame(output, gson.toJson(request))

            val responseText = readFrame(input) ?: ""
            val response = gson.fromJson(responseText, ChatHandshakeResponse::class.java)
            if (response.accepted != true) {
                socket.close()
                setConnecting(targetDeviceId, false)
                notifyConnection(targetDeviceId, false, false)
                return false
            }

            val connection = ChatConnection(
                deviceId = targetDeviceId,
                socket = socket,
                input = input,
                output = output,
                cancelFlag = AtomicBoolean(false)
            )
            connections[targetDeviceId] = connection
            startReader(connection)
            setConnecting(targetDeviceId, false)
            notifyConnection(targetDeviceId, true, false)
            true
        } catch (_: Exception) {
            setConnecting(targetDeviceId, false)
            notifyConnection(targetDeviceId, false, false)
            false
        }
    }

    fun disconnect(deviceId: String) {
        val connection = connections.remove(deviceId) ?: return
        connection.cancelFlag.set(true)
        runCatching { connection.socket.close() }
        notifyConnection(deviceId, false, false)
    }

    fun sendMessage(targetDeviceId: String, body: String, sentAt: String): Boolean {
        if (body.length > MAX_MESSAGE_LENGTH) return false
        val connection = connections[targetDeviceId] ?: return false
        val currentDeviceId = selfDeviceId ?: return false

        return try {
            val payload = ChatMessagePayload(
                id = UUID.randomUUID().toString(),
                senderDeviceId = currentDeviceId,
                targetDeviceId = targetDeviceId,
                body = body,
                sentAt = sentAt
            )
            synchronized(connection) {
                writeFrame(connection.output, gson.toJson(payload))
            }
            true
        } catch (_: Exception) {
            disconnect(targetDeviceId)
            false
        }
    }

    private fun startDiscovery(context: Context) {
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
                            notifyDiscoveryUpdated()
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

    private fun stopDiscovery(context: Context) {
        val bluetoothAdapter = adapter ?: return
        discoveryReceiver?.let {
            context.unregisterReceiver(it)
        }
        discoveryReceiver = null
        availableDevices.clear()
        bluetoothAdapter.cancelDiscovery()
    }

    private fun startServer() {
        val bluetoothAdapter = adapter ?: return
        if (serverThread != null) return

        serverThread = Thread {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                while (!Thread.currentThread().isInterrupted) {
                    val socket = serverSocket?.accept() ?: break
                    handleIncomingSocket(socket)
                }
            } catch (_: Exception) {
                // Server socket closed or failed.
            } finally {
                runCatching { serverSocket?.close() }
                serverSocket = null
            }
        }.also { it.start() }
    }

    private fun stopServer() {
        serverThread?.interrupt()
        serverThread = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun handleIncomingSocket(socket: BluetoothSocket) {
        val currentDeviceId = selfDeviceId
        if (currentDeviceId.isNullOrBlank()) {
            runCatching { socket.close() }
            return
        }

        try {
            val input = DataInputStream(BufferedInputStream(socket.inputStream))
            val output = DataOutputStream(BufferedOutputStream(socket.outputStream))
            val requestText = readFrame(input) ?: ""
            val request = gson.fromJson(requestText, ChatHandshakeRequest::class.java)

            val allowed = request.targetDeviceId == currentDeviceId && allowedDevices.contains(request.senderDeviceId)
            val response = ChatHandshakeResponse(accepted = allowed)
            writeFrame(output, gson.toJson(response))

            if (!allowed) {
                socket.close()
                return
            }

            val connection = ChatConnection(
                deviceId = request.senderDeviceId,
                socket = socket,
                input = input,
                output = output,
                cancelFlag = AtomicBoolean(false)
            )
            connections[request.senderDeviceId] = connection
            startReader(connection)
            notifyConnection(request.senderDeviceId, true, false)
        } catch (_: Exception) {
            runCatching { socket.close() }
        }
    }

    private fun startReader(connection: ChatConnection) {
        connection.readerThread = Thread {
            while (!connection.cancelFlag.get()) {
                val payloadText = readFrame(connection.input) ?: break
                val payload = gson.fromJson(payloadText, ChatMessagePayload::class.java)
                if (payload.type != "message") {
                    continue
                }
                storeIncomingMessage(payload)
            }
            disconnect(connection.deviceId)
        }.also { it.start() }
    }

    private fun storeIncomingMessage(payload: ChatMessagePayload) {
        val currentDeviceId = selfDeviceId ?: return
        val chatDeviceId = if (payload.senderDeviceId.equals(currentDeviceId, true)) {
            payload.targetDeviceId
        } else {
            payload.senderDeviceId
        }

        val chatMessage = ChatMessage(
            id = payload.id,
            deviceId = chatDeviceId,
            senderDeviceId = payload.senderDeviceId,
            body = payload.body,
            sentAt = parseSentAt(payload.sentAt),
            isOutgoing = payload.senderDeviceId.equals(currentDeviceId, true)
        )

        ChatStorage.upsertMessage(chatMessage)
        notifyChatUpdated(chatDeviceId)
    }

    private fun parseSentAt(sentAt: String): Long {
        return try {
            OffsetDateTime.parse(sentAt).toInstant().toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun setConnecting(deviceId: String, connecting: Boolean) {
        if (connecting) {
            connectingDevices.add(deviceId)
        } else {
            connectingDevices.remove(deviceId)
        }
        notifyConnection(deviceId, connections.containsKey(deviceId), connectingDevices.contains(deviceId))
    }

    private fun notifyDiscoveryUpdated() {
        val snapshot = availableDevices.toMap()
        mainHandler.post {
            discoveryListeners.forEach { it(snapshot) }
        }
    }

    private fun notifyConnection(deviceId: String, isConnected: Boolean, isConnecting: Boolean) {
        mainHandler.post {
            listeners.forEach { it.onBluetoothConnectionChanged(deviceId, isConnected, isConnecting) }
        }
    }

    private fun notifyChatUpdated(deviceId: String) {
        mainHandler.post {
            listeners.forEach { it.onChatUpdated(deviceId) }
        }
    }

    private fun readFrame(input: DataInputStream): String? {
        return try {
            val length = input.readInt()
            if (length <= 0) return null
            val buffer = ByteArray(length)
            input.readFully(buffer)
            String(buffer, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeFrame(output: DataOutputStream, payload: String) {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }

    private data class ChatConnection(
        val deviceId: String,
        val socket: BluetoothSocket,
        val input: DataInputStream,
        val output: DataOutputStream,
        val cancelFlag: AtomicBoolean,
        var readerThread: Thread? = null
    )

    private data class ChatHandshakeRequest(
        val type: String = "chat_request",
        val senderDeviceId: String,
        val senderDeviceName: String,
        val targetDeviceId: String
    )

    private data class ChatHandshakeResponse(
        val type: String = "chat_response",
        val accepted: Boolean,
        val reason: String? = null
    )

    private data class ChatMessagePayload(
        val type: String = "message",
        val id: String,
        val senderDeviceId: String,
        val targetDeviceId: String,
        val body: String,
        val sentAt: String
    )
}

interface BluetoothChatListener {
    fun onChatUpdated(deviceId: String)
    fun onBluetoothConnectionChanged(deviceId: String, isConnected: Boolean, isConnecting: Boolean)
}
