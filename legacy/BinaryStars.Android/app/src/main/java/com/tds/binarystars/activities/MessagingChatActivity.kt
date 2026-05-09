package com.tds.binarystars.activities

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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.R
import com.tds.binarystars.adapter.MessagingMessagesAdapter
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.ClearConversationRequestDto
import com.tds.binarystars.api.SendMessageRequestDto
import com.tds.binarystars.messaging.MessagingEventListener
import com.tds.binarystars.messaging.MessagingSocketManager
import com.tds.binarystars.model.ChatMessage
import com.tds.binarystars.storage.ChatStorage
import com.tds.binarystars.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime

class MessagingChatActivity : AppCompatActivity(), MessagingEventListener {
    companion object {
        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_DEVICE_NAME = "device_name"
        private const val PAGE_SIZE = 50
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessagingMessagesAdapter
    private lateinit var input: EditText
    private lateinit var sendButton: ImageView
    private lateinit var backButton: ImageView
    private lateinit var clearButton: Button
    private lateinit var titleView: TextView

    private val messages = mutableListOf<ChatMessage>()
    private var deviceId: String = ""
    private var deviceName: String = ""
    private var isLoading = false
    private var hasMore = true
    private var oldestTimestamp: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messaging_chat)

        deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: ""
        deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: deviceId

        recyclerView = findViewById(R.id.rvChatMessages)
        input = findViewById(R.id.etChatInput)
        sendButton = findViewById(R.id.btnSendMessage)
        backButton = findViewById(R.id.btnBackChat)
        clearButton = findViewById(R.id.btnClearChat)
        titleView = findViewById(R.id.tvChatTitle)

        titleView.text = deviceName
        findViewById<View>(R.id.switchChatBluetooth).visibility = View.GONE

        adapter = MessagingMessagesAdapter(messages)
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        sendButton.setOnClickListener { sendMessage() }
        backButton.setOnClickListener { finish() }
        clearButton.setOnClickListener { confirmClearChat() }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy < 0) {
                    val firstVisible = layoutManager.findFirstVisibleItemPosition()
                    if (firstVisible == 0 && !isLoading && hasMore) loadMore()
                }
            }
        })

        loadInitial()
    }

    override fun onResume() {
        super.onResume()
        MessagingSocketManager.addListener(this)
    }

    override fun onPause() {
        super.onPause()
        MessagingSocketManager.removeListener(this)
    }

    private fun loadInitial() {
        isLoading = true
        lifecycleScope.launch {
            val cached = withContext(Dispatchers.IO) { ChatStorage.getMessages(deviceId, null, PAGE_SIZE) }
            messages.clear(); messages.addAll(cached); adapter.notifyDataSetChanged()
            if (messages.isNotEmpty()) { oldestTimestamp = messages.first().sentAt; recyclerView.scrollToPosition(messages.lastIndex) }
            hasMore = cached.size >= PAGE_SIZE; isLoading = false
            refreshFromCloud()
        }
    }

    private fun loadMore() {
        if (isLoading || !hasMore) return
        isLoading = true
        lifecycleScope.launch {
            val cached = withContext(Dispatchers.IO) { ChatStorage.getMessages(deviceId, oldestTimestamp, PAGE_SIZE) }
            if (cached.isNotEmpty()) {
                messages.addAll(0, cached); adapter.notifyItemRangeInserted(0, cached.size)
                oldestTimestamp = cached.first().sentAt
            }
            hasMore = cached.size >= PAGE_SIZE; isLoading = false
        }
    }

    private suspend fun refreshFromCloud() {
        if (!NetworkUtils.isOnline(this)) return
        try {
            val selfId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            val response = withContext(Dispatchers.IO) { ApiClient.apiService.getMessageHistory(selfId, deviceId) }
            if (response.isSuccessful) {
                val cloudMessages = response.body()?.map { dto ->
                    ChatMessage(id = dto.id, deviceId = deviceId, senderDeviceId = dto.senderDeviceId, body = dto.body,
                        sentAt = OffsetDateTime.parse(dto.sentAt).toInstant().toEpochMilli(), isOutgoing = dto.senderDeviceId == selfId)
                } ?: emptyList()
                withContext(Dispatchers.IO) { cloudMessages.forEach { ChatStorage.upsertMessage(it) } }
                val updated = withContext(Dispatchers.IO) { ChatStorage.getMessages(deviceId, null, messages.size.coerceAtLeast(PAGE_SIZE)) }
                messages.clear(); messages.addAll(updated); adapter.notifyDataSetChanged()
                if (messages.isNotEmpty()) recyclerView.scrollToPosition(messages.lastIndex)
            }
        } catch (_: Exception) {}
    }

    private fun sendMessage() {
        val body = input.text.toString().trim()
        if (body.isBlank()) return
        if (!NetworkUtils.isOnline(this)) { Toast.makeText(this, "Offline", Toast.LENGTH_SHORT).show(); return }
        lifecycleScope.launch {
            val selfId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            val request = SendMessageRequestDto(selfId, deviceId, body, OffsetDateTime.now().toString())
            try {
                if (ApiClient.apiService.sendMessage(request).isSuccessful) { input.setText(""); refreshFromCloud() }
            } catch (_: Exception) {}
        }
    }

    private fun confirmClearChat() {
        AlertDialog.Builder(this).setTitle("Clear Chat").setMessage("Delete all?").setPositiveButton("Clear") { _, _ -> clearChat() }.setNegativeButton("Cancel", null).show()
    }

    private fun clearChat() {
        lifecycleScope.launch {
            try {
                val selfId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                ApiClient.apiService.clearConversation(ClearConversationRequestDto(selfId, deviceId))
                withContext(Dispatchers.IO) { ChatStorage.clearChat(deviceId) }
                messages.clear(); adapter.notifyDataSetChanged()
            } catch (_: Exception) {}
        }
    }

    override fun onChatUpdated(deviceId: String) { if (deviceId == this.deviceId) lifecycleScope.launch { refreshFromCloud() } }
    override fun onDeviceRemoved(deviceId: String, isSelf: Boolean) { if (deviceId == this.deviceId) finish() }
    override fun onConnectionStateChanged(isConnected: Boolean) {}
    override fun onDevicePresenceChanged(deviceId: String, isOnline: Boolean, lastSeen: String) {}
    override fun onLocationUpdated(event: com.tds.binarystars.api.LocationUpdateEventDto) {}
    override fun onActionResult(result: com.tds.binarystars.api.DeviceActionResultDto) {}
}
