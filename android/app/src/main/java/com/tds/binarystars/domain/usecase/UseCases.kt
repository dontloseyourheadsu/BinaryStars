package com.tds.binarystars.domain.usecase

import com.tds.binarystars.domain.model.BtDevice
import com.tds.binarystars.domain.model.ChatMessage
import com.tds.binarystars.domain.repository.BluetoothRepository
import com.tds.binarystars.domain.repository.ConnectionState
import kotlinx.coroutines.flow.Flow

class GetMessagesUseCase(private val repository: BluetoothRepository) {
    operator fun invoke(): Flow<List<ChatMessage>> = repository.getMessages()
}

class GetConnectionStateUseCase(private val repository: BluetoothRepository) {
    operator fun invoke(): Flow<ConnectionState> = repository.getConnectionState()
}

class ScanDevicesUseCase(private val repository: BluetoothRepository) {
    operator fun invoke(): Flow<List<BtDevice>> = repository.scanDevices()
}

class ConnectToDeviceUseCase(private val repository: BluetoothRepository) {
    operator fun invoke(device: BtDevice): Flow<ConnectionState> = repository.connect(device)
}

class DisconnectUseCase(private val repository: BluetoothRepository) {
    operator fun invoke() = repository.disconnect()
}

class StartServerUseCase(private val repository: BluetoothRepository) {
    operator fun invoke(): Flow<ConnectionState> = repository.startServer()
}

class StopServerUseCase(private val repository: BluetoothRepository) {
    operator fun invoke() = repository.stopServer()
}

class SendMessageUseCase(private val repository: BluetoothRepository) {
    suspend operator fun invoke(content: String): Result<Unit> = repository.sendMessage(content)
}

class SendFileUseCase(private val repository: BluetoothRepository) {
    suspend operator fun invoke(fileName: String, bytes: ByteArray): Result<Unit> = repository.sendFile(fileName, bytes)
}

class GetMessagesPagedUseCase(private val repository: BluetoothRepository) {
    suspend operator fun invoke(peerId: String, limit: Int, offset: Int): List<ChatMessage> = repository.getMessagesPaged(peerId, limit, offset)
}

class GetSelfDeviceDetailsUseCase(private val repository: BluetoothRepository) {
    fun getDeviceId(): String = repository.getSelfDeviceId()
    fun getDeviceName(): String = repository.getSelfDeviceName()
}
