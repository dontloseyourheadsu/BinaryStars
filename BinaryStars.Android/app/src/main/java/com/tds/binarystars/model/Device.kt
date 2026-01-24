package com.tds.binarystars.model

data class Device(
    val id: String,
    val name: String,
    val type: DeviceType,
    val ipAddress: String,
    val batteryLevel: Int,
    val isOnline: Boolean,
    val isSynced: Boolean,
    val wifiUploadSpeed: String,
    val wifiDownloadSpeed: String,
    val lastSeen: Long = System.currentTimeMillis()
)

enum class DeviceType {
    LINUX,
    ANDROID
}
