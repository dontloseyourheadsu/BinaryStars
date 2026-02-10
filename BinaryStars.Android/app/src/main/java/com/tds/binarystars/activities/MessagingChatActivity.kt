package com.tds.binarystars.activities

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.R
import com.tds.binarystars.adapter.MessagingMessagesAdapter
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.SendMessageRequestDto
import com.tds.binarystars.bluetooth.BluetoothChatListener
import com.tds.binarystars.bluetooth.BluetoothChatManager
import com.tds.binarystars.messaging.MessagingEventListener
import com.tds.binarystars.messaging.MessagingSocketManager
import com.tds.binarystars.model.ChatMessage
import com.tds.binarystars.storage.ChatStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.util.UUID

class MessagingChatActivity : AppCompatActivity(), MessagingEventListener, BluetoothChatListener {
    companion object {
        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_DEVICE_NAME = "device_name"
        private const val PAGE_SIZE = 50
        private const val MAX_MESSAGE_LENGTH = 500
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessagingMessagesAdapter
    private lateinit var input: EditText
    private lateinit var sendButton: ImageView
    private lateinit var clearButton: Button
    private lateinit var titleView: TextView
    private lateinit var bluetoothToggle: SwitchCompat

    private val messages = mutableListOf<ChatMessage>()
    private var deviceId: String = ""
    private var deviceName: String = ""
    private var isLoading = false
    private var hasMore = true
    private var oldestTimestamp: Long? = null
    private var bluetoothAvailable = false
    private var bluetoothConnected = false
    private var bluetoothConnecting = false
    private var bluetoothEnabled = false
    private var suppressBluetoothToggle = false

    private val bluetoothDiscoveryListener: (Map<String, android.bluetooth.BluetoothDevice>) -> Unit = { devices ->
        bluetoothAvailable = devices.keys.contains(deviceName)
        updateBluetoothUi()
    }

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.any { it }
        if (granted) {
            startBluetoothFeatures()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(StartActivityForResult()) {
        if (BluetoothChatManager.isEnabled()) {
            startBluetoothFeatures()
        }
    }

    /**
     * Initializes chat UI, loads history, and wires message actions.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messaging_chat)

        deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: ""
        deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: deviceId

        recyclerView = findViewById(R.id.rvChatMessages)
        input = findViewById(R.id.etChatInput)
        sendButton = findViewById(R.id.btnSendMessage)
        clearButton = findViewById(R.id.btnClearChat)
        titleView = findViewById(R.id.tvChatTitle)
        bluetoothToggle = findViewById(R.id.switchChatBluetooth)

        titleView.text = deviceName

        val selfDeviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val selfDeviceName = BluetoothAdapter.getDefaultAdapter()?.name ?: "Android"
        BluetoothChatManager.setSelf(selfDeviceId, selfDeviceName)

        adapter = MessagingMessagesAdapter(messages)
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        sendButton.setOnClickListener {
            sendMessage()
        }

        bluetoothToggle.setOnCheckedChangeListener { _, isChecked ->
            if (suppressBluetoothToggle) return@setOnCheckedChangeListener
            if (!bluetoothAvailable && isChecked) {
                suppressBluetoothToggle = true
                bluetoothToggle.isChecked = false
                suppressBluetoothToggle = false
                return@setOnCheckedChangeListener
            }

            bluetoothEnabled = isChecked
            BluetoothChatManager.setChatEnabled(deviceId, bluetoothEnabled)
            if (!bluetoothEnabled) {
                BluetoothChatManager.disconnect(deviceId)
                bluetoothConnected = false
                bluetoothConnecting = false
                updateBluetoothUi()
                return@setOnCheckedChangeListener
            }

            lifecycleScope.launch {
                bluetoothConnecting = true
                updateBluetoothUi()
                val connected = withContext(Dispatchers.IO) {
                    BluetoothChatManager.connectToDevice(deviceId, deviceName)
                }
                bluetoothConnected = connected
                bluetoothConnecting = false
                if (!connected && bluetoothEnabled) {
                    Toast.makeText(this@MessagingChatActivity, "Bluetooth not accepted", Toast.LENGTH_SHORT).show()
                }
                updateBluetoothUi()
            }
        }

        clearButton.setOnClickListener {
            confirmClearChat()
        }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy < 0) {
                    val firstVisible = layoutManager.findFirstVisibleItemPosition()
                    if (firstVisible == 0 && !isLoading && hasMore) {
                        loadMore()
                    }
                }
            }
        })

        loadInitial()
    }

    /**
     * Registers websocket listeners for chat updates.
     */
    override fun onResume() {
        super.onResume()
        MessagingSocketManager.addListener(this)
        BluetoothChatManager.addListener(this)
    }

    /**
     * Unregisters websocket listeners.
     */
    override fun onPause() {
        super.onPause()
        MessagingSocketManager.removeListener(this)
        BluetoothChatManager.removeListener(this)
    }

    override fun onStart() {
        super.onStart()
        ensureBluetoothReady()
    }

    override fun onStop() {
        super.onStop()
        BluetoothChatManager.setChatEnabled(deviceId, false)
        BluetoothChatManager.disconnect(deviceId)
        stopBluetoothFeatures()
    }

    /**
     * Refreshes the chat when new messages arrive.
     */
    override fun onChatUpdated(deviceId: String) {
        if (deviceId != this.deviceId) return
        loadLatest()
    }

    /**
     * Closes the chat if the device was removed.
     */
    override fun onDeviceRemoved(deviceId: String, isSelf: Boolean) {
        if (isSelf || deviceId == this.deviceId) {
            finish()
        }
    }

    override fun onConnectionStateChanged(isConnected: Boolean) {
        // No-op for now
    }

    override fun onBluetoothConnectionChanged(deviceId: String, isConnected: Boolean, isConnecting: Boolean) {
        if (deviceId != this.deviceId) return
        bluetoothConnected = isConnected
        bluetoothConnecting = isConnecting
        updateBluetoothUi()
    }

    /**
     * Loads the initial page of chat history.
     */
    private fun loadInitial() {
        val loaded = ChatStorage.getMessages(deviceId, null, PAGE_SIZE)
        messages.clear()
        messages.addAll(loaded)
        adapter.notifyDataSetChanged()

        oldestTimestamp = loaded.firstOrNull()?.sentAt
        hasMore = loaded.size == PAGE_SIZE
        if (loaded.isNotEmpty()) {
            recyclerView.scrollToPosition(messages.lastIndex)
        }
    }

    /**
     * Reloads the latest messages from storage.
     */
    private fun loadLatest() {
        val loaded = ChatStorage.getMessages(deviceId, null, PAGE_SIZE)
        adapter.replaceAll(loaded)
        oldestTimestamp = loaded.firstOrNull()?.sentAt
        hasMore = loaded.size == PAGE_SIZE
        if (loaded.isNotEmpty()) {
            recyclerView.scrollToPosition(messages.lastIndex)
        }
    }

    /**
     * Loads older messages when the user scrolls to the top.
     */
    private fun loadMore() {
        if (oldestTimestamp == null) return
        isLoading = true
        val older = ChatStorage.getMessages(deviceId, oldestTimestamp, PAGE_SIZE)
        if (older.isEmpty()) {
            hasMore = false
        } else {
            oldestTimestamp = older.firstOrNull()?.sentAt
            adapter.prepend(older)
        }
        isLoading = false
    }

    @SuppressLint("HardwareIds")
    /**
     * Sends a new message using websocket or REST fallback.
     */
    private fun sendMessage() {
        val body = input.text.toString().trim()
        if (body.isBlank()) return
        if (body.length > MAX_MESSAGE_LENGTH) {
            Toast.makeText(this, "Message too long (500 max)", Toast.LENGTH_SHORT).show()
            return
        }

        if (bluetoothEnabled) {
            if (!bluetoothConnected) {
                Toast.makeText(this, "Waiting for Bluetooth connection", Toast.LENGTH_SHORT).show()
                return
            }
            val senderDeviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            val sentAt = OffsetDateTime.now().toString()
            val sent = BluetoothChatManager.sendMessage(deviceId, body, sentAt)
            if (!sent) {
                Toast.makeText(this, "Bluetooth send failed", Toast.LENGTH_SHORT).show()
                return
            }

            val localMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                deviceId = deviceId,
                senderDeviceId = senderDeviceId,
                body = body,
                sentAt = OffsetDateTime.parse(sentAt).toInstant().toEpochMilli(),
                isOutgoing = true
            )
            ChatStorage.upsertMessage(localMessage)
            adapter.append(localMessage)
            recyclerView.scrollToPosition(messages.lastIndex)
            input.setText("")
            return
        }

        val senderDeviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val sentAt = OffsetDateTime.now().toString()
        val request = SendMessageRequestDto(
            senderDeviceId = senderDeviceId,
            targetDeviceId = deviceId,
            body = body,
            sentAt = sentAt
        )

        val sentViaSocket = MessagingSocketManager.sendMessage(request)
        if (sentViaSocket) {
            val localMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                deviceId = deviceId,
                senderDeviceId = senderDeviceId,
                body = body,
                sentAt = OffsetDateTime.parse(sentAt).toInstant().toEpochMilli(),
                isOutgoing = true
            )
            ChatStorage.upsertMessage(localMessage)
            adapter.append(localMessage)
            recyclerView.scrollToPosition(messages.lastIndex)
            input.setText("")
            return
        }

        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) { ApiClient.apiService.sendMessage(request) }
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                val localMessage = ChatMessage(
                    id = dto.id,
                    deviceId = deviceId,
                    senderDeviceId = senderDeviceId,
                    body = dto.body,
                    sentAt = OffsetDateTime.parse(dto.sentAt).toInstant().toEpochMilli(),
                    isOutgoing = true
                )
                ChatStorage.upsertMessage(localMessage)
                adapter.append(localMessage)
                recyclerView.scrollToPosition(messages.lastIndex)
                input.setText("")
            } else {
                Toast.makeText(this@MessagingChatActivity, "Failed to send message", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Confirms and clears the local chat history.
     */
    private fun confirmClearChat() {
        AlertDialog.Builder(this)
            .setTitle("Clear chat")
            .setMessage("This will remove all messages on this device only.")
            .setPositiveButton("Clear") { _, _ ->
                ChatStorage.clearChat(deviceId)
                loadInitial()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateBluetoothUi() {
        bluetoothToggle.visibility = if (bluetoothAvailable) View.VISIBLE else View.GONE
        bluetoothToggle.isEnabled = !bluetoothConnecting

        if (!bluetoothAvailable && bluetoothEnabled) {
            suppressBluetoothToggle = true
            bluetoothToggle.isChecked = false
            suppressBluetoothToggle = false
            bluetoothEnabled = false
            BluetoothChatManager.setChatEnabled(deviceId, false)
            BluetoothChatManager.disconnect(deviceId)
        }

        sendButton.isEnabled = !bluetoothEnabled || bluetoothConnected
        bluetoothToggle.text = when {
            bluetoothConnecting -> "Bluetooth (connecting)"
            bluetoothEnabled && bluetoothConnected -> "Bluetooth (connected)"
            else -> "Bluetooth"
        }
    }

    private fun ensureBluetoothReady() {
        if (!BluetoothChatManager.isSupported()) {
            bluetoothAvailable = false
            updateBluetoothUi()
            return
        }
        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions()
            return
        }
        if (!BluetoothChatManager.isEnabled()) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        startBluetoothFeatures()
    }

    private fun startBluetoothFeatures() {
        BluetoothChatManager.acquireDiscovery(this, bluetoothDiscoveryListener)
        BluetoothChatManager.acquireServer()
    }

    private fun stopBluetoothFeatures() {
        BluetoothChatManager.releaseDiscovery(this, bluetoothDiscoveryListener)
        BluetoothChatManager.releaseServer()
    }

    private fun hasBluetoothPermissions(): Boolean {
        val permissions = requiredBluetoothPermissions()
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        bluetoothPermissionLauncher.launch(requiredBluetoothPermissions())
    }

    private fun requiredBluetoothPermissions(): Array<String> {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}
