package com.tds.binarystars.api

/** Device registration request payload. */
data class RegisterDeviceRequest(
    val id: String,
    val name: String,
    val ipAddress: String,
    val ipv6Address: String?,
    val publicKey: String?,
    val publicKeyAlgorithm: String?
)
