package com.tds.binarystars.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tds.binarystars.domain.model.BtDevice
import com.tds.binarystars.domain.model.ChatMessage
import com.tds.binarystars.domain.repository.ConnectionState
import com.tds.binarystars.domain.usecase.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BluetoothViewModel(
    private val getMessagesUseCase: GetMessagesUseCase,
    private val getConnectionStateUseCase: GetConnectionStateUseCase,
    private val scanDevicesUseCase: ScanDevicesUseCase,
    private val connectToDeviceUseCase: ConnectToDeviceUseCase,
    private val disconnectUseCase: DisconnectUseCase,
    private val startServerUseCase: StartServerUseCase,
    private val stopServerUseCase: StopServerUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val sendFileUseCase: SendFileUseCase,
    private val getMessagesPagedUseCase: GetMessagesPagedUseCase,
    getSelfDeviceDetailsUseCase: GetSelfDeviceDetailsUseCase,
    private val getTabletRatioUseCase: GetTabletRatioUseCase,
    private val sendTabletSignalUseCase: SendTabletSignalUseCase,
    private val requestTabletRatioUseCase: RequestTabletRatioUseCase,
    private val sendKeyboardKeyUseCase: SendKeyboardKeyUseCase,
    private val sendMouseSignalUseCase: SendMouseSignalUseCase
) : ViewModel() {

    val selfDeviceId = getSelfDeviceDetailsUseCase.getDeviceId()
    val selfDeviceName = getSelfDeviceDetailsUseCase.getDeviceName()

    val tabletRatio = getTabletRatioUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun sendTabletSignal(action: String, x: Float, y: Float) {
        viewModelScope.launch {
            sendTabletSignalUseCase(action, x, y)
        }
    }

    fun requestTabletRatio() {
        viewModelScope.launch {
            requestTabletRatioUseCase()
        }
    }

    fun sendKeyboardKey(action: String, value: String) {
        viewModelScope.launch {
            sendKeyboardKeyUseCase(action, value)
        }
    }

    fun sendMouseSignal(action: String, dx: Int, dy: Int, button: String = "") {
        viewModelScope.launch {
            sendMouseSignalUseCase(action, dx, dy, button)
        }
    }




    val connectionState = getConnectionStateUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.Disconnected)

    // Combined list of SQLite history + real-time session messages
    private val _loadedMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val loadedMessages = _loadedMessages.asStateFlow()

    private var loadedHistory = mutableListOf<ChatMessage>()
    private var historyOffset = 0
    private val _hasMoreHistory = MutableStateFlow(true)
    val hasMoreHistory = _hasMoreHistory.asStateFlow()

    private val _isLoadingHistory = MutableStateFlow(false)
    val isLoadingHistory = _isLoadingHistory.asStateFlow()

    private var currentSessionMessages = emptyList<ChatMessage>()

    private val _discoveredDevices = MutableStateFlow<List<BtDevice>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _isHosting = MutableStateFlow(false)
    val isHosting = _isHosting.asStateFlow()

    // UI custom theme state: true = Dark theme, false = Light theme
    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme = _isDarkTheme.asStateFlow()

    private val _viewingHistoryPeerId = MutableStateFlow<String?>(null)
    val viewingHistoryPeerId = _viewingHistoryPeerId.asStateFlow()

    fun setViewingHistoryPeerId(peerId: String?) {
        _viewingHistoryPeerId.value = peerId
        if (peerId != null) {
            initMessageSession(peerId)
        } else {
            clearMessageSession()
        }
    }

    fun openChatFromIntent(peerId: String) {
        val state = connectionState.value
        if (state is ConnectionState.Connected && state.peerId == peerId) {
            _viewingHistoryPeerId.value = null
        } else {
            setViewingHistoryPeerId(peerId)
        }
    }

    private var scanJob: Job? = null
    private var serverJob: Job? = null
    private var messageCollectorJob: Job? = null

    init {
        // Collect connection state to trigger initial history load on connect
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state is ConnectionState.Connected) {
                    _viewingHistoryPeerId.value = null // Clear history view if connected
                    initMessageSession(state.peerId)
                } else {
                    if (_viewingHistoryPeerId.value == null) {
                        clearMessageSession()
                    }
                }
            }
        }
    }

    private fun initMessageSession(peerId: String) {
        messageCollectorJob?.cancel()
        loadedHistory.clear()
        currentSessionMessages = emptyList()
        historyOffset = 0
        _hasMoreHistory.value = true
        
        viewModelScope.launch {
            // Load page 0 history
            val initialHistory = getMessagesPagedUseCase(peerId, 20, 0)
            loadedHistory.addAll(initialHistory)
            historyOffset = initialHistory.size
            if (initialHistory.size < 20) {
                _hasMoreHistory.value = false
            }
            
            _loadedMessages.value = loadedHistory.toList()
            
            // Collect live session messages and append them
            messageCollectorJob = launch {
                getMessagesUseCase().collect { sessionMessages ->
                    currentSessionMessages = sessionMessages
                    val existingIds = loadedHistory.map { it.id }.toSet()
                    val newSessionMessages = sessionMessages.filter { it.id !in existingIds }
                    _loadedMessages.value = loadedHistory + newSessionMessages
                }
            }
        }
    }

    private fun clearMessageSession() {
        messageCollectorJob?.cancel()
        messageCollectorJob = null
        loadedHistory.clear()
        currentSessionMessages = emptyList()
        _loadedMessages.value = emptyList()
        historyOffset = 0
        _hasMoreHistory.value = true
    }

    fun loadMoreHistory(peerId: String) {
        if (_isLoadingHistory.value || !_hasMoreHistory.value) return
        _isLoadingHistory.value = true
        viewModelScope.launch {
            try {
                val olderHistory = getMessagesPagedUseCase(peerId, 20, historyOffset)
                if (olderHistory.isNotEmpty()) {
                    loadedHistory.addAll(0, olderHistory)
                    historyOffset += olderHistory.size
                    if (olderHistory.size < 20) {
                        _hasMoreHistory.value = false
                    }
                    // Trigger flow updates with merged session messages
                    val existingIds = loadedHistory.map { it.id }.toSet()
                    val newSessionMessages = currentSessionMessages.filter { it.id !in existingIds }
                    _loadedMessages.value = loadedHistory + newSessionMessages
                } else {
                    _hasMoreHistory.value = false
                }
            } catch (_: Exception) {
            } finally {
                _isLoadingHistory.value = false
            }
        }
    }

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun startScanning() {
        if (_isScanning.value) return
        _isScanning.value = true
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            scanDevicesUseCase()
                .onCompletion {
                    _isScanning.value = false
                }
                .catch {
                    _isScanning.value = false
                }
                .collect { devices ->
                    _discoveredDevices.value = devices
                }
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
        _isScanning.value = false
    }

    fun startHosting() {
        if (_isHosting.value) return
        _isHosting.value = true
        serverJob?.cancel()
        serverJob = viewModelScope.launch {
            startServerUseCase()
                .onCompletion {
                    _isHosting.value = false
                }
                .catch {
                    _isHosting.value = false
                }
                .collect { state ->
                    if (state is ConnectionState.Connected) {
                        // Server successfully accepted a client, stop hosting server to lock it in
                        _isHosting.value = false
                        stopServerUseCase()
                    }
                }
        }
    }

    fun stopHosting() {
        serverJob?.cancel()
        stopServerUseCase()
        _isHosting.value = false
    }

    fun connectToDevice(device: BtDevice) {
        viewModelScope.launch {
            connectToDeviceUseCase(device)
                .catch {
                    // Handle connection failure
                }
                .collect { state ->
                    // Flow updates connectionState which is collected by UI
                }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            disconnectUseCase()
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            sendMessageUseCase(content)
        }
    }

    fun sendFile(fileName: String, bytes: ByteArray) {
        viewModelScope.launch {
            sendFileUseCase(fileName, bytes)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScanning()
        stopHosting()
        disconnect()
    }
}
