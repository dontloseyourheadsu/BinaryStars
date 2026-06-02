package com.tds.binarystars.domain.model

data class ChatMessage(
    val id: String,
    val deviceId: String,
    val senderDeviceId: String,
    val body: String,
    val sentAt: Long,
    val isOutgoing: Boolean,
    val isFile: Boolean = false,
    val fileName: String? = null,
    val filePath: String? = null
)
