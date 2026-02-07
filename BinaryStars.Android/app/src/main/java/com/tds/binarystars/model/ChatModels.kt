package com.tds.binarystars.model

data class ChatSummary(
    val deviceId: String,
    val lastMessage: String,
    val lastSentAt: Long
)

data class ChatMessage(
    val id: String,
    val deviceId: String,
    val senderDeviceId: String,
    val body: String,
    val sentAt: Long,
    val isOutgoing: Boolean
)
