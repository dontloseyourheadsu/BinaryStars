package com.tds.binarystars.messaging

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.tds.binarystars.api.AuthTokenStore
import com.tds.binarystars.api.DeviceRemovedEventDto
import com.tds.binarystars.api.MessagingEnvelopeDto
import com.tds.binarystars.api.MessagingMessageDto
import com.tds.binarystars.api.SendMessageRequestDto
import com.tds.binarystars.model.ChatMessage
import com.tds.binarystars.storage.ChatStorage
import com.tds.binarystars.storage.FileTransferLocalStore
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.time.OffsetDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

object MessagingSocketManager {
    private const val WS_BASE_URL = "ws://10.0.2.2:5004/ws/messaging"
    private const val MAX_MESSAGE_LENGTH = 500

    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<MessagingEventListener>()

    private var webSocket: WebSocket? = null
    private var currentDeviceId: String? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    fun connect(context: Context, deviceId: String) {
        if (webSocket != null && currentDeviceId == deviceId) return

        val token = AuthTokenStore.getToken() ?: return
        currentDeviceId = deviceId

        val request = Request.Builder()
            .url("$WS_BASE_URL?deviceId=$deviceId")
            .addHeader("Authorization", "Bearer $token")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                notifyConnection(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(context, text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                notifyConnection(false)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                notifyConnection(false)
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Client closing")
        webSocket = null
    }

    fun sendMessage(request: SendMessageRequestDto): Boolean {
        if (request.body.length > MAX_MESSAGE_LENGTH) {
            return false
        }

        val socket = webSocket ?: return false
        val envelope = MessagingEnvelopeDto(
            type = "message",
            payload = gson.toJsonTree(request)
        )
        val json = gson.toJson(envelope)
        return socket.send(json)
    }

    fun addListener(listener: MessagingEventListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: MessagingEventListener) {
        listeners.remove(listener)
    }

    private fun handleIncomingMessage(context: Context, text: String) {
        val envelope = gson.fromJson(text, MessagingEnvelopeDto::class.java) ?: return
        when (envelope.type.lowercase()) {
            "message" -> {
                val payload = gson.fromJson(envelope.payload, MessagingMessageDto::class.java)
                storeIncomingMessage(context, payload)
            }
            "device_removed" -> {
                val payload = gson.fromJson(envelope.payload, DeviceRemovedEventDto::class.java)
                handleDeviceRemoved(context, payload)
            }
        }
    }

    private fun storeIncomingMessage(context: Context, message: MessagingMessageDto) {
        val deviceId = currentDeviceId ?: return
        val chatDeviceId = if (message.senderDeviceId.equals(deviceId, true)) {
            message.targetDeviceId
        } else {
            message.senderDeviceId
        }

        val chatMessage = ChatMessage(
            id = message.id,
            deviceId = chatDeviceId,
            senderDeviceId = message.senderDeviceId,
            body = message.body,
            sentAt = parseSentAt(message.sentAt),
            isOutgoing = message.senderDeviceId.equals(deviceId, true)
        )

        ChatStorage.upsertMessage(chatMessage)
        notifyChatUpdated(chatDeviceId)
    }

    private fun handleDeviceRemoved(context: Context, event: DeviceRemovedEventDto) {
        val deviceId = currentDeviceId ?: return
        if (event.removedDeviceId.equals(deviceId, true)) {
            ChatStorage.clearAll()
            FileTransferLocalStore.clearAll(context)
            AuthTokenStore.clear()
            disconnect()
            notifyDeviceRemoved(deviceId, true)
            return
        }

        ChatStorage.clearChat(event.removedDeviceId)
        notifyDeviceRemoved(event.removedDeviceId, false)
    }

    private fun parseSentAt(sentAt: String): Long {
        return try {
            OffsetDateTime.parse(sentAt).toInstant().toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun notifyChatUpdated(deviceId: String) {
        mainHandler.post {
            listeners.forEach { it.onChatUpdated(deviceId) }
        }
    }

    private fun notifyDeviceRemoved(deviceId: String, isSelf: Boolean) {
        mainHandler.post {
            listeners.forEach { it.onDeviceRemoved(deviceId, isSelf) }
        }
    }

    private fun notifyConnection(connected: Boolean) {
        mainHandler.post {
            listeners.forEach { it.onConnectionStateChanged(connected) }
        }
    }
}

interface MessagingEventListener {
    fun onChatUpdated(deviceId: String)
    fun onDeviceRemoved(deviceId: String, isSelf: Boolean)
    fun onConnectionStateChanged(isConnected: Boolean)
}
