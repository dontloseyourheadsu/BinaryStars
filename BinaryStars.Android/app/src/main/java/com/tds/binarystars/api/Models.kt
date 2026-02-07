package com.tds.binarystars.api

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

data class ExternalAuthRequest(
    val provider: String,
    val token: String,
    val username: String
)

data class AuthResponse(
    val tokenType: String,
    val accessToken: String,
    val expiresIn: Int
)

enum class UserRoleDto {
    Disabled,
    Free,
    Premium
}

data class AccountProfileDto(
    val id: String,
    val username: String,
    val email: String,
    val role: UserRoleDto
)

enum class DeviceTypeDto {
    Linux,
    Android
}

data class DeviceDto(
    val id: String,
    val name: String,
    val type: DeviceTypeDto,
    val ipAddress: String,
    val publicKey: String?,
    val publicKeyAlgorithm: String?,
    val batteryLevel: Int,
    val isOnline: Boolean,
    val isSynced: Boolean,
    val wifiUploadSpeed: String,
    val wifiDownloadSpeed: String,
    val lastSeen: String
)

enum class FileTransferStatusDto {
    Queued,
    Uploading,
    Available,
    Downloaded,
    Failed,
    Expired,
    Rejected
}

data class FileTransferSummaryDto(
    val id: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val senderDeviceId: String,
    val targetDeviceId: String,
    val status: FileTransferStatusDto,
    val createdAt: String,
    val expiresAt: String,
    val isSender: Boolean
)

data class FileTransferDetailDto(
    val id: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val senderUserId: String,
    val targetUserId: String,
    val senderDeviceId: String,
    val targetDeviceId: String,
    val status: FileTransferStatusDto,
    val encryptionEnvelope: String?,
    val chunkSizeBytes: Int,
    val packetCount: Int,
    val kafkaTopic: String,
    val kafkaPartition: Int?,
    val kafkaStartOffset: Long?,
    val kafkaEndOffset: Long?,
    val kafkaAuthMode: String,
    val createdAt: String,
    val expiresAt: String,
    val completedAt: String?
)

data class CreateFileTransferRequestDto(
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val senderDeviceId: String,
    val targetDeviceId: String,
    val encryptionEnvelope: String
)

// Notes Models
enum class NoteType {
    Plaintext,
    Markdown
}

data class NoteResponse(
    val id: String,
    val name: String,
    val signedByDeviceId: String,
    val signedByDeviceName: String?,
    val contentType: NoteType,
    val content: String,
    val createdAt: String,
    val updatedAt: String
)

data class CreateNoteRequest(
    val name: String,
    val deviceId: String,
    val contentType: NoteType,
    val content: String
)

data class UpdateNoteRequestDto(
    val name: String,
    val content: String
)

// Messaging Models
data class MessagingEnvelopeDto(
    val type: String,
    val payload: com.google.gson.JsonElement
)

data class MessagingMessageDto(
    val id: String,
    val userId: String,
    val senderDeviceId: String,
    val targetDeviceId: String,
    val body: String,
    val sentAt: String
)

data class SendMessageRequestDto(
    val senderDeviceId: String,
    val targetDeviceId: String,
    val body: String,
    val sentAt: String?
)

data class DeviceRemovedEventDto(
    val id: String,
    val userId: String,
    val removedDeviceId: String,
    val occurredAt: String
)
