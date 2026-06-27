package com.tds.binarystars.domain.repository

import com.tds.binarystars.domain.model.BtDevice
import com.tds.binarystars.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

sealed interface ConnectionState {
    object Disconnected : ConnectionState
    object Connecting : ConnectionState
    data class Connected(val peerId: String, val deviceAddress: String) : ConnectionState
}

interface BluetoothRepository {
    fun getSelfDeviceId(): String
    fun getSelfDeviceName(): String
    fun startServer(): Flow<ConnectionState>
    fun stopServer()
    fun connect(device: BtDevice): Flow<ConnectionState>
    fun disconnect()
    suspend fun sendMessage(content: String): Result<Unit>
    suspend fun sendFile(fileName: String, bytes: ByteArray): Result<Unit>
    suspend fun getMessagesPaged(peerId: String, limit: Int, offset: Int): List<ChatMessage>
    fun scanDevices(): Flow<List<BtDevice>>
    fun getMessages(): Flow<List<ChatMessage>>
    fun getConnectionState(): Flow<ConnectionState>
    fun getTabletRatio(): Flow<Pair<Int, Int>?>
    suspend fun sendTabletSignal(action: String, x: Float, y: Float): Result<Unit>
    suspend fun requestTabletRatio(): Result<Unit>
}

