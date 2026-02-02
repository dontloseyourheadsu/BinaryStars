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

data class DeviceDto(
    val id: String,
    val name: String,
    val type: Int, // Enum mapping needed
    val ipAddress: String,
    val batteryLevel: Int,
    val isOnline: Boolean,
    val isSynced: Boolean,
    val wifiUploadSpeed: String,
    val wifiDownloadSpeed: String,
    val lastSeen: String
)
