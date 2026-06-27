package com.tds.binarystars.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tds.binarystars.data.repository.BluetoothRepositoryImpl
import com.tds.binarystars.domain.usecase.*

class BluetoothViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BluetoothViewModel::class.java)) {
            val repository = com.tds.binarystars.BinaryStarsApp.repository
            
            @Suppress("UNCHECKED_CAST")
            return BluetoothViewModel(
                getMessagesUseCase = GetMessagesUseCase(repository),
                getConnectionStateUseCase = GetConnectionStateUseCase(repository),
                scanDevicesUseCase = ScanDevicesUseCase(repository),
                connectToDeviceUseCase = ConnectToDeviceUseCase(repository),
                disconnectUseCase = DisconnectUseCase(repository),
                startServerUseCase = StartServerUseCase(repository),
                stopServerUseCase = StopServerUseCase(repository),
                sendMessageUseCase = SendMessageUseCase(repository),
                sendFileUseCase = SendFileUseCase(repository),
                getMessagesPagedUseCase = GetMessagesPagedUseCase(repository),
                getSelfDeviceDetailsUseCase = GetSelfDeviceDetailsUseCase(repository),
                getTabletRatioUseCase = GetTabletRatioUseCase(repository),
                sendTabletSignalUseCase = SendTabletSignalUseCase(repository),
                requestTabletRatioUseCase = RequestTabletRatioUseCase(repository),
                sendKeyboardKeyUseCase = SendKeyboardKeyUseCase(repository)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
