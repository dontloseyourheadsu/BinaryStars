package com.tds.binarystars.api

data class RegisterDeviceRequest(
    val id: String,
    val name: String,
    val ipAddress: String,
    val ipv6Address: String?
)
