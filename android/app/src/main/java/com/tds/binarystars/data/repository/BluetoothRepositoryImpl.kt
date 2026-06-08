package com.tds.binarystars.data.repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.Log
import com.tds.binarystars.domain.model.BtDevice
import com.tds.binarystars.domain.model.ChatMessage
import com.tds.binarystars.domain.repository.BluetoothRepository
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import com.tds.binarystars.MainActivity
import com.tds.binarystars.domain.repository.ConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID

class BluetoothRepositoryImpl(
    private val context: Context
) : BluetoothRepository {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        bluetoothManager.adapter
    }

    private val selfDeviceIdInternal: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "Android-${UUID.randomUUID().toString().take(6)}"
    }

    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    private val SERVICE_NAME = "BinaryStarsSPP"
    private val TAG = "BinaryStarsBT"

    private val dbHelper = com.tds.binarystars.data.database.DatabaseHelper(context)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override fun getConnectionState(): Flow<ConnectionState> = _connectionState.asStateFlow()

    private fun updateConnectionState(state: ConnectionState) {
        _connectionState.value = state
        val intent = Intent(context, BluetoothService::class.java)
        try {
            if (state is ConnectionState.Connected || state is ConnectionState.Connecting) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else if (state is ConnectionState.Disconnected) {
                context.stopService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting/stopping foreground service: ${e.message}")
        }
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    override fun getMessages(): Flow<List<ChatMessage>> = _messages.asStateFlow()

    override suspend fun getMessagesPaged(peerId: String, limit: Int, offset: Int): List<ChatMessage> {
        return withContext(Dispatchers.IO) {
            dbHelper.getMessagesPaged(peerId, limit, offset)
        }
    }

    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private var activeSocket: BluetoothSocket? = null
    private var reader: BufferedReader? = null
    private var writer: OutputStream? = null

    private var serverJob: Job? = null
    private var connectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @SuppressLint("HardwareIds")
    override fun getSelfDeviceId(): String = selfDeviceIdInternal

    @SuppressLint("MissingPermission")
    override fun getSelfDeviceName(): String {
        return try {
            if (hasBluetoothPermission()) {
                bluetoothAdapter?.name ?: Build.MODEL
            } else {
                Build.MODEL
            }
        } catch (e: Exception) {
            Build.MODEL
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    override fun startServer(): Flow<ConnectionState> = flow {
        if (!hasBluetoothPermission()) {
            emit(ConnectionState.Disconnected)
            return@flow
        }
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            emit(ConnectionState.Disconnected)
            return@flow
        }

        stopServer()
        disconnect()

        emit(ConnectionState.Disconnected)

        val serverFlow = callbackFlow<ConnectionState> {
            serverJob = scope.launch {
                try {
                    serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SPP_UUID)
                    Log.d(TAG, "Server socket started, listening...")
                    while (isActive) {
                        val socket = serverSocket?.accept() ?: break
                        Log.d(TAG, "Incoming connection accepted from ${socket.remoteDevice.address}")
                        
                        // Handle the connection asynchronously
                        launch {
                            handleIncomingSocket(socket, this@callbackFlow)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Server socket error: ${e.message}")
                    trySend(ConnectionState.Disconnected)
                }
            }
            awaitClose {
                stopServer()
            }
        }

        emitAll(serverFlow)
    }

    private suspend fun handleIncomingSocket(socket: BluetoothSocket, channel: kotlinx.coroutines.channels.ProducerScope<ConnectionState>) {
        withContext(Dispatchers.IO) {
            try {
                val inputReader = BufferedReader(InputStreamReader(socket.inputStream))
                val outputStream = socket.outputStream

                // Handshake
                val line = withTimeoutOrNull(5000) { inputReader.readLine() }
                if (line != null && line.startsWith("IDENTIFY|")) {
                    val peerId = line.split("|")[1]
                    Log.d(TAG, "Handshake successful with peer: $peerId")
                    Log.i(TAG, "CONNECTION SUCCESSFUL (SERVER): Connected to peer $peerId (${socket.remoteDevice.address})")
                    
                    outputStream.write("IDENTIFIED|$selfDeviceIdInternal\n".toByteArray())
                    outputStream.flush()

                    activeSocket = socket
                    reader = inputReader
                    writer = outputStream
                    
                    updateConnectionState(ConnectionState.Connected(peerId, socket.remoteDevice.address))
                    channel.trySend(ConnectionState.Connected(peerId, socket.remoteDevice.address))

                    // Start reading messages
                    startReading(peerId)
                } else {
                    Log.w(TAG, "Handshake failed from incoming client")
                    socket.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling incoming socket: ${e.message}")
                socket.close()
            }
        }
    }

    override fun stopServer() {
        serverJob?.cancel()
        serverJob = null
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket: ${e.message}")
        }
        serverSocket = null
    }

    @SuppressLint("MissingPermission")
    override fun connect(device: BtDevice): Flow<ConnectionState> = callbackFlow {
        if (!hasBluetoothPermission()) {
            trySend(ConnectionState.Disconnected)
            close()
            return@callbackFlow
        }
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            trySend(ConnectionState.Disconnected)
            close()
            return@callbackFlow
        }

        stopServer()
        disconnect()

        trySend(ConnectionState.Connecting)
        updateConnectionState(ConnectionState.Connecting)

        connectionJob = scope.launch {
            try {
                val remoteDevice = adapter.getRemoteDevice(device.address)
                val socket = remoteDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()

                val outputStream = socket.outputStream
                val inputReader = BufferedReader(InputStreamReader(socket.inputStream))

                // Handshake: Write identify, wait for identified
                outputStream.write("IDENTIFY|$selfDeviceIdInternal\n".toByteArray())
                outputStream.flush()

                val line = withTimeoutOrNull(5000) { inputReader.readLine() }
                if (line != null && line.startsWith("IDENTIFIED|")) {
                    val peerId = line.split("|")[1]
                    Log.d(TAG, "Client Handshake successful with peer: $peerId")
                    Log.i(TAG, "CONNECTION SUCCESSFUL (CLIENT): Connected to peer $peerId (${device.address})")

                    activeSocket = socket
                    reader = inputReader
                    writer = outputStream

                    val connectedState = ConnectionState.Connected(peerId, device.address)
                    updateConnectionState(connectedState)
                    trySend(connectedState)

                    // Start reading loop
                    startReading(peerId)
                } else {
                    Log.e(TAG, "Handshake verification failed from server")
                    Log.e(TAG, "CONNECTION FAILED (CLIENT): Handshake verification failed from ${device.address}")
                    socket.close()
                    trySend(ConnectionState.Disconnected)
                    updateConnectionState(ConnectionState.Disconnected)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Client connection failed: ${e.message}")
                Log.e(TAG, "CONNECTION FAILED (CLIENT): Connection to ${device.address} failed: ${e.message}")
                trySend(ConnectionState.Disconnected)
                updateConnectionState(ConnectionState.Disconnected)
            }
        }

        awaitClose {
            // Keep connection alive unless explicitly disconnected
        }
    }

    override fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        
        val r = reader
        val w = writer
        val s = activeSocket
        
        reader = null
        writer = null
        activeSocket = null
        
        scope.launch(Dispatchers.IO) {
            try {
                r?.close()
            } catch (_: Exception) {}
            try {
                w?.close()
            } catch (_: Exception) {}
            try {
                s?.close()
            } catch (_: Exception) {}
        }
        
        if (_connectionState.value != ConnectionState.Disconnected) {
            updateConnectionState(ConnectionState.Disconnected)
        }
    }

    override suspend fun sendMessage(content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val peerId = (getConnectionStateValue() as? ConnectionState.Connected)?.peerId ?: "Unknown"
            val isSelf = CommandParser.isSelfCommand(content)

            if (!isSelf) {
                val out = writer ?: return@withContext Result.failure(Exception("Not connected"))
                val sanitized = content.replace("\n", " ")
                out.write((sanitized + "\n").toByteArray())
                out.flush()
                Log.i(TAG, "MESSAGE SENT: From $selfDeviceIdInternal to $peerId")
            }

            val msg = ChatMessage(
                id = UUID.randomUUID().toString(),
                deviceId = peerId,
                senderDeviceId = selfDeviceIdInternal,
                body = content,
                sentAt = System.currentTimeMillis(),
                isOutgoing = true
            )
            // Save to SQLite
            dbHelper.insertMessage(peerId, msg)
            _messages.value = _messages.value + msg

            if (isSelf) {
                scope.launch {
                    delay(200)
                    val info = DeviceInfoProvider.getDeviceInfoString(context)
                    val reply = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        deviceId = peerId,
                        senderDeviceId = "System",
                        body = info,
                        sentAt = System.currentTimeMillis(),
                        isOutgoing = false
                    )
                    dbHelper.insertMessage(peerId, reply)
                    _messages.value = _messages.value + reply
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message: ${e.message}")
            if (!CommandParser.isSelfCommand(content)) {
                disconnect()
            }
            Result.failure(e)
        }
    }

    override suspend fun sendFile(fileName: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val out = writer ?: return@withContext Result.failure(Exception("Not connected"))
            val peerId = (getConnectionStateValue() as? ConnectionState.Connected)?.peerId ?: "Unknown"

            // Save outgoing file to internal storage first so we can display it with path
            val dir = File(context.filesDir, "transfers/sent")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "sent_${System.currentTimeMillis()}_$fileName")
            FileOutputStream(file).use { it.write(bytes) }

            val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
            out.write("FILE|$fileName|$base64Data\n".toByteArray())
            out.flush()
            Log.i(TAG, "FILE SENT: $fileName From $selfDeviceIdInternal to $peerId")

            val msg = ChatMessage(
                id = UUID.randomUUID().toString(),
                deviceId = peerId,
                senderDeviceId = selfDeviceIdInternal,
                body = "Sent file: $fileName",
                sentAt = System.currentTimeMillis(),
                isOutgoing = true,
                isFile = true,
                fileName = fileName,
                filePath = file.absolutePath
            )
            // Save to SQLite
            dbHelper.insertMessage(peerId, msg)
            _messages.value = _messages.value + msg
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send file: ${e.message}")
            disconnect()
            Result.failure(e)
        }
    }

    private fun startReading(peerId: String) {
        scope.launch {
            try {
                val inputReader = reader ?: return@launch
                while (isActive) {
                    val line = inputReader.readLine() ?: break
                    if (line.startsWith("GROUP_MSG|")) {
                        val parts = line.split("|", limit = 3)
                        if (parts.size >= 3) {
                            val senderId = parts[1]
                            val content = parts[2]
                            val msg = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                deviceId = peerId,
                                senderDeviceId = senderId,
                                body = content,
                                sentAt = System.currentTimeMillis(),
                                isOutgoing = false
                            )
                            dbHelper.insertMessage(peerId, msg)
                            _messages.value = _messages.value + msg
                            triggerNotification(senderId, msg.body, false)
                        }
                    } else if (line.startsWith("GROUP_FILE|")) {
                        val parts = line.split("|", limit = 4)
                        if (parts.size >= 4) {
                            val senderId = parts[1]
                            val fileName = parts[2]
                            val base64Data = parts[3]
                            saveReceivedFileWithSender(peerId, senderId, fileName, base64Data)
                        }
                    } else if (line.startsWith("FILE|")) {
                        val parts = line.split("|", limit = 3)
                        if (parts.size >= 3) {
                            val fileName = parts[1]
                            val base64Data = parts[2]
                            saveReceivedFile(peerId, fileName, base64Data)
                        }
                    } else {
                        Log.i(TAG, "MESSAGE RECEIVED: From peer $peerId")
                        val msg = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            deviceId = peerId,
                            senderDeviceId = peerId,
                            body = line,
                            sentAt = System.currentTimeMillis(),
                            isOutgoing = false
                        )
                        // Save to SQLite
                        dbHelper.insertMessage(peerId, msg)
                        _messages.value = _messages.value + msg
                        triggerNotification(peerId, msg.body, false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Reading error: ${e.message}")
            } finally {
                Log.d(TAG, "Reading loop ended, disconnecting...")
                disconnect()
            }
        }
    }

    private fun saveReceivedFileWithSender(peerId: String, senderId: String, fileName: String, base64Data: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = Base64.decode(base64Data, Base64.NO_WRAP)
                val dir = File(context.filesDir, "transfers/received")
                if (!dir.exists()) dir.mkdirs()
                
                val file = File(dir, "received_${System.currentTimeMillis()}_$fileName")
                FileOutputStream(file).use { it.write(bytes) }
                Log.d(TAG, "Saved received file to: ${file.absolutePath}")
                Log.i(TAG, "FILE RECEIVED: $fileName From $senderId to $selfDeviceIdInternal (Saved internally: ${file.absolutePath})")

                val msg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    deviceId = peerId,
                    senderDeviceId = senderId,
                    body = "Received file: $fileName",
                    sentAt = System.currentTimeMillis(),
                    isOutgoing = false,
                    isFile = true,
                    fileName = fileName,
                    filePath = file.absolutePath
                )
                // Save to SQLite
                dbHelper.insertMessage(peerId, msg)
                _messages.value = _messages.value + msg
                triggerNotification(senderId, msg.body, true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save file: ${e.message}")
            }
        }
    }

    private fun saveReceivedFile(peerId: String, fileName: String, base64Data: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = Base64.decode(base64Data, Base64.NO_WRAP)
                val dir = File(context.filesDir, "transfers/received")
                if (!dir.exists()) dir.mkdirs()
                
                val file = File(dir, "received_${System.currentTimeMillis()}_$fileName")
                FileOutputStream(file).use { it.write(bytes) }
                Log.d(TAG, "Saved received file to: ${file.absolutePath}")
                Log.i(TAG, "FILE RECEIVED: $fileName From $peerId to $selfDeviceIdInternal (Saved internally: ${file.absolutePath})")

                val msg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    deviceId = peerId,
                    senderDeviceId = peerId,
                    body = "Received file: $fileName",
                    sentAt = System.currentTimeMillis(),
                    isOutgoing = false,
                    isFile = true,
                    fileName = fileName,
                    filePath = file.absolutePath
                )
                // Save to SQLite
                dbHelper.insertMessage(peerId, msg)
                _messages.value = _messages.value + msg
                triggerNotification(peerId, msg.body, true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save file: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun scanDevices(): Flow<List<BtDevice>> = callbackFlow {
        val deviceList = mutableSetOf<BtDevice>()

        if (!hasBluetoothPermission()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        // Add already paired devices first
        val bondedDevices = adapter.bondedDevices
        bondedDevices?.forEach { device ->
            deviceList.add(
                BtDevice(
                    name = device.name ?: device.address,
                    address = device.address,
                    connected = false,
                    paired = true
                )
            )
        }
        trySend(deviceList.toList())

        // Register receiver for scanned devices
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        
                        device?.let {
                            val name = it.name ?: it.address
                            val btDevice = BtDevice(
                                name = name,
                                address = it.address,
                                connected = false,
                                paired = it.bondState == BluetoothDevice.BOND_BONDED
                            )
                            deviceList.add(btDevice)
                            trySend(deviceList.toList())
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(receiver, filter)
        adapter.startDiscovery()
        Log.d(TAG, "Discovery started")

        awaitClose {
            try {
                adapter.cancelDiscovery()
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering discovery receiver: ${e.message}")
            }
        }
    }

    private fun triggerNotification(peerId: String, content: String, isFile: Boolean) {
        val channelId = "binarystars_chat_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for received messages and files"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_PEER_ID", peerId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            peerId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isFile) "File received from $peerId" else "Message from $peerId"

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(peerId.hashCode(), notification)
    }

    private fun getConnectionStateValue(): ConnectionState {
        return _connectionState.value
    }
}
