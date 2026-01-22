package com.tds.binarystars.model

data class Device(
    val id: String,
    val name: String,
    val type: DeviceType,
    val ipAddress: String,
    val batteryLevel: Int,
    val isConnected: Boolean,
    val isSynced: Boolean,
    val wifiUploadSpeed: String,
    val wifiDownloadSpeed: String
)

enum class DeviceType {
    LINUX,
    ANDROID
}
