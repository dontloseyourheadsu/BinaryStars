package com.tds.binarystars.bluetooth

import android.content.Context

class BluetoothTransferManager(private val context: Context, val onUpdate: () -> Unit) {
    fun isEnabled(): Boolean = false
}
