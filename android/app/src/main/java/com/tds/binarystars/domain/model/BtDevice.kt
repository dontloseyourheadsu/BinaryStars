package com.tds.binarystars.domain.model

data class BtDevice(
    val name: String,
    val address: String,
    val connected: Boolean = false,
    val paired: Boolean = false
)
