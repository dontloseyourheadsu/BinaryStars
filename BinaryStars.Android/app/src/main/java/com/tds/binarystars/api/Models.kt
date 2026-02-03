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

enum class DeviceTypeDto {
    Linux,
    Android
}

data class DeviceDto(
    val id: String,
    val name: String,
    val type: DeviceTypeDto,
    val ipAddress: String,
    val batteryLevel: Int,
    val isOnline: Boolean,
    val isSynced: Boolean,
    val wifiUploadSpeed: String,
    val wifiDownloadSpeed: String,
    val lastSeen: String
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
