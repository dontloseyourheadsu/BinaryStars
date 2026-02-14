package com.tds.binarystars.model

/**
 * Domain model representing a registered device.
 */
data class Device(
    val id: String,
    val name: String,
    val type: DeviceType,
    val ipAddress: String,
    val publicKey: String? = null,
    val publicKeyAlgorithm: String? = null,
    val batteryLevel: Int,
    val isOnline: Boolean,
    val isAvailable: Boolean = true,
    val isSynced: Boolean,
    val cpuLoadPercent: Int? = null,
    val wifiUploadSpeed: String,
    val wifiDownloadSpeed: String,
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * Supported local device types.
 */
enum class DeviceType {
    LINUX,
    ANDROID
}
