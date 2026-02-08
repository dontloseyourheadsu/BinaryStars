package com.tds.binarystars.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.R
import com.tds.binarystars.adapter.MessagingMessagesAdapter
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.SendMessageRequestDto
import com.tds.binarystars.messaging.MessagingEventListener
import com.tds.binarystars.messaging.MessagingSocketManager
import com.tds.binarystars.model.ChatMessage
import com.tds.binarystars.storage.ChatStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.util.UUID

class MessagingChatActivity : AppCompatActivity(), MessagingEventListener {
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

    private val messages = mutableListOf<ChatMessage>()
    private var deviceId: String = ""
    private var deviceName: String = ""
    private var isLoading = false
    private var hasMore = true
    private var oldestTimestamp: Long? = null

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

        titleView.text = deviceName

        adapter = MessagingMessagesAdapter(messages)
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        sendButton.setOnClickListener {
            sendMessage()
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
    }

    /**
     * Unregisters websocket listeners.
     */
    override fun onPause() {
        super.onPause()
        MessagingSocketManager.removeListener(this)
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
}
