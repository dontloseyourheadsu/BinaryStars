package com.tds.binarystars.model

/**
 * Summary row for a chat thread.
 */
data class ChatSummary(
    val deviceId: String,
    val lastMessage: String,
    val lastSentAt: Long
)

/**
 * Chat message stored locally.
 */
data class ChatMessage(
    val id: String,
    val deviceId: String,
    val senderDeviceId: String,
    val body: String,
    val sentAt: Long,
    val isOutgoing: Boolean
)
