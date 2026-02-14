package com.tds.binarystars.api

/** Login request payload. */
data class LoginRequest(
    val email: String,
    val password: String
)

/** Registration request payload. */
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

/** External provider login payload. */
data class ExternalAuthRequest(
    val provider: String,
    val token: String,
    val username: String
)

/** JWT response payload. */
data class AuthResponse(
    val tokenType: String,
    val accessToken: String,
    val expiresIn: Int
)

/** User role values returned by the API. */
enum class UserRoleDto {
    Disabled,
    Free,
    Premium
}

/** User profile response payload. */
data class AccountProfileDto(
    val id: String,
    val username: String,
    val email: String,
    val role: UserRoleDto
)

/** Device type values returned by the API. */
enum class DeviceTypeDto {
    Linux,
    Android
}

/** Device response payload. */
data class DeviceDto(
    val id: String,
    val name: String,
    val type: DeviceTypeDto,
    val ipAddress: String,
    val publicKey: String?,
    val publicKeyAlgorithm: String?,
    val batteryLevel: Int,
    val isOnline: Boolean,
    val isAvailable: Boolean = true,
    val isSynced: Boolean,
    val cpuLoadPercent: Int? = null,
    val wifiUploadSpeed: String,
    val wifiDownloadSpeed: String,
    val lastSeen: String
)

/** Device telemetry update request payload. */
data class UpdateDeviceTelemetryRequest(
    val batteryLevel: Int,
    val cpuLoadPercent: Int?,
    val isOnline: Boolean,
    val isAvailable: Boolean,
    val isSynced: Boolean,
    val wifiUploadSpeed: String,
    val wifiDownloadSpeed: String
)

/** Transfer status values returned by the API. */
enum class FileTransferStatusDto {
    Queued,
    Uploading,
    Available,
    Downloaded,
    Failed,
    Expired,
    Rejected
}

/** Summary response for transfer lists. */
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

/** Detailed response for a transfer. */
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

/** Transfer creation request payload. */
data class CreateFileTransferRequestDto(
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val senderDeviceId: String,
    val targetDeviceId: String,
    val encryptionEnvelope: String
)

// Notes Models
/** Note content type. */
enum class NoteType {
    Plaintext,
    Markdown
}

/** Note response payload. */
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

/** Note creation request payload. */
data class CreateNoteRequest(
    val name: String,
    val deviceId: String,
    val contentType: NoteType,
    val content: String
)

/** Note update request payload. */
data class UpdateNoteRequestDto(
    val name: String,
    val content: String
)

// Messaging Models
/** Websocket envelope wrapper. */
data class MessagingEnvelopeDto(
    val type: String,
    val payload: com.google.gson.JsonElement
)

/** Messaging payload returned by the API. */
data class MessagingMessageDto(
    val id: String,
    val userId: String,
    val senderDeviceId: String,
    val targetDeviceId: String,
    val body: String,
    val sentAt: String
)

/** Send message request payload. */
data class SendMessageRequestDto(
    val senderDeviceId: String,
    val targetDeviceId: String,
    val body: String,
    val sentAt: String?
)

// Location Models
/** Location update request payload. */
data class LocationUpdateRequestDto(
    val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val recordedAt: String
)

/** Location history response payload. */
data class LocationHistoryPointDto(
    val id: String,
    val title: String,
    val recordedAt: String,
    val latitude: Double,
    val longitude: Double
)

/** Device removal event payload. */
data class DeviceRemovedEventDto(
    val id: String,
    val userId: String,
    val removedDeviceId: String,
    val occurredAt: String
)
