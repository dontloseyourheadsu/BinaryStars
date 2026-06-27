package com.tds.binarystars.presentation.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tds.binarystars.domain.model.BtDevice
import com.tds.binarystars.domain.model.ChatMessage
import com.tds.binarystars.domain.repository.ConnectionState
import com.tds.binarystars.presentation.ui.theme.*
import com.tds.binarystars.presentation.viewmodel.BluetoothViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(
    viewModel: BluetoothViewModel,
    onPickFile: () -> Unit,
    onOpenFile: (ChatMessage) -> Unit,
    onSaveFile: (ChatMessage) -> Unit,
    onShareFile: (ChatMessage) -> Unit
) {
    val isDark by viewModel.isDarkTheme.collectAsState()
    val connState by viewModel.connectionState.collectAsState()
    val devices by viewModel.discoveredDevices.collectAsState()
    val messages by viewModel.loadedMessages.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isHosting by viewModel.isHosting.collectAsState()

    var activeTab by remember { mutableStateOf("discovery") }

    // Auto-navigate between tabs on connection changes
    LaunchedEffect(connState) {
        when (connState) {
            is ConnectionState.Connected -> {
                activeTab = "chat"
            }
            is ConnectionState.Disconnected -> {
                activeTab = "discovery"
            }
            else -> {}
        }
    }

    BinaryStarsTheme(darkTheme = isDark) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            // Apply systemBarsPadding here to prevent camera cutout/notch and navigation bar clippings
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Header Bar
                HeaderBar(
                    title = "BINARY STARS",
                    isDark = isDark,
                    onThemeToggle = { viewModel.toggleTheme() },
                    selfId = viewModel.selfDeviceId,
                    selfName = viewModel.selfDeviceName
                )

                Divider(
                    color = if (isDark) DarkBorder else LightBorder,
                    thickness = 1.dp
                )

                // Main Content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    val historyPeerId by viewModel.viewingHistoryPeerId.collectAsState()

                    if (historyPeerId != null) {
                        ChatScreen(
                            isDark = isDark,
                            peerId = historyPeerId!!,
                            messages = messages,
                            onSendMessage = { /* Cannot send message in history */ },
                            onPickFile = { /* Cannot pick file in history */ },
                            onDisconnect = { viewModel.setViewingHistoryPeerId(null) },
                            onLoadMore = { viewModel.loadMoreHistory(historyPeerId!!) },
                            onOpenFile = onOpenFile,
                            onSaveFile = onSaveFile,
                            onShareFile = onShareFile,
                            isOffline = true
                        )
                    } else if (activeTab == "chat") {
                        when (val state = connState) {
                            is ConnectionState.Disconnected -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "📡 No Active Connection",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDark) TextDarkPrimary else TextLightPrimary
                                        )
                                        Text(
                                            text = "Chat room becomes active once connected.",
                                            fontSize = 12.sp,
                                            color = if (isDark) TextDarkSecondary else TextLightSecondary
                                        )
                                    }
                                }
                            }
                            is ConnectionState.Connecting -> {
                                ConnectingScreen(isDark = isDark)
                            }
                            is ConnectionState.Connected -> {
                                ChatScreen(
                                    isDark = isDark,
                                    peerId = state.peerId,
                                    messages = messages,
                                    onSendMessage = { viewModel.sendMessage(it) },
                                    onPickFile = onPickFile,
                                    onDisconnect = { viewModel.disconnect() },
                                    onLoadMore = { viewModel.loadMoreHistory(state.peerId) },
                                    onOpenFile = onOpenFile,
                                    onSaveFile = onSaveFile,
                                    onShareFile = onShareFile,
                                    isOffline = false
                                )
                            }
                        }
                    } else if (activeTab == "tablet") {
                        val state = connState
                        if (state is ConnectionState.Connected && state.peerId != "Group Chat Session") {
                            TabletScreen(
                                isDark = isDark,
                                viewModel = viewModel,
                                onBackToOptions = { activeTab = "discovery" }
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "🎨 Tablet Mode Offline",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) TextDarkPrimary else TextLightPrimary
                                    )
                                    Text(
                                        text = "Requires a direct 1-on-1 device connection to a Linux host.",
                                        fontSize = 12.sp,
                                        color = if (isDark) TextDarkSecondary else TextLightSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { activeTab = "discovery" },
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                                    ) {
                                        Text("Go to Discovery", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else if (activeTab == "keyboard") {
                        val state = connState
                        if (state is ConnectionState.Connected && state.peerId != "Group Chat Session") {
                            KeyboardScreen(
                                isDark = isDark,
                                viewModel = viewModel,
                                onBackToOptions = { activeTab = "discovery" }
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "⌨️ Keyboard Mode Offline",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) TextDarkPrimary else TextLightPrimary
                                    )
                                    Text(
                                        text = "Requires a direct 1-on-1 device connection to a Linux host.",
                                        fontSize = 12.sp,
                                        color = if (isDark) TextDarkSecondary else TextLightSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { activeTab = "discovery" },
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                                    ) {
                                        Text("Go to Discovery", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                    } else {
                        // activeTab == "discovery"
                        when (val state = connState) {
                            is ConnectionState.Connecting -> {
                                ConnectingScreen(isDark = isDark)
                            }
                            else -> {
                                DashboardScreen(
                                    isDark = isDark,
                                    devices = devices,
                                    isScanning = isScanning,
                                    isHosting = isHosting,
                                    onScanToggle = {
                                        if (isScanning) viewModel.stopScanning() else viewModel.startScanning()
                                    },
                                    onHostToggle = {
                                        if (isHosting) viewModel.stopHosting() else viewModel.startHosting()
                                    },
                                    onConnect = { viewModel.connectToDevice(it) }
                                )
                            }
                        }
                    }
                }

                // Bottom Navigation Bar
                val historyPeerId by viewModel.viewingHistoryPeerId.collectAsState()
                if (historyPeerId == null && connState !is ConnectionState.Connecting) {
                    NavigationBar(
                        containerColor = if (isDark) DarkCardBg else LightCardBg,
                        tonalElevation = 8.dp,
                        modifier = Modifier.height(64.dp)
                    ) {
                        NavigationBarItem(
                            selected = activeTab == "discovery",
                            onClick = { activeTab = "discovery" },
                            icon = { Icon(Icons.Default.Search, contentDescription = "Discovery") },
                            label = { Text("Discovery", fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryBlue,
                                selectedTextColor = PrimaryBlue,
                                unselectedIconColor = if (isDark) TextDarkSecondary else TextLightSecondary,
                                unselectedTextColor = if (isDark) TextDarkSecondary else TextLightSecondary
                            )
                        )
                        
                        NavigationBarItem(
                            selected = activeTab == "chat",
                            onClick = { activeTab = "chat" },
                            icon = { Icon(Icons.Default.Send, contentDescription = "Chat") },
                            label = { Text("Chat Room", fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryBlue,
                                selectedTextColor = PrimaryBlue,
                                unselectedIconColor = if (isDark) TextDarkSecondary else TextLightSecondary,
                                unselectedTextColor = if (isDark) TextDarkSecondary else TextLightSecondary
                            )
                        )

                        NavigationBarItem(
                            selected = activeTab == "tablet",
                            onClick = { activeTab = "tablet" },
                            icon = { Icon(Icons.Default.Edit, contentDescription = "Tablet") },
                            label = { Text("Tablet Mode", fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryBlue,
                                selectedTextColor = PrimaryBlue,
                                unselectedIconColor = if (isDark) TextDarkSecondary else TextLightSecondary,
                                unselectedTextColor = if (isDark) TextDarkSecondary else TextLightSecondary
                            )
                        )

                        NavigationBarItem(
                            selected = activeTab == "keyboard",
                            onClick = { activeTab = "keyboard" },
                            icon = { Icon(Icons.Default.List, contentDescription = "Keyboard") },
                            label = { Text("Keyboard Mode", fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryBlue,
                                selectedTextColor = PrimaryBlue,
                                unselectedIconColor = if (isDark) TextDarkSecondary else TextLightSecondary,
                                unselectedTextColor = if (isDark) TextDarkSecondary else TextLightSecondary
                            )
                        )

                    }
                }

            }
        }
    }
}


@Composable
fun HeaderBar(
    title: String,
    isDark: Boolean,
    onThemeToggle: () -> Unit,
    selfId: String,
    selfName: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            Text(
                text = "ID: ${selfId.take(8)} | $selfName",
                fontSize = 11.sp,
                color = if (isDark) TextDarkSecondary else TextLightSecondary,
                fontWeight = FontWeight.Medium
            )
        }

        Button(
            onClick = onThemeToggle,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier
                .height(32.dp)
                .border(
                    width = 1.dp,
                    color = if (isDark) DarkBorder else LightBorder,
                    shape = RoundedCornerShape(6.dp)
                )
        ) {
            Text(
                text = if (isDark) "LIGHT MODE" else "DARK MODE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) TextDarkPrimary else TextLightPrimary
            )
        }
    }
}

@Composable
fun DashboardScreen(
    isDark: Boolean,
    devices: List<BtDevice>,
    isScanning: Boolean,
    isHosting: Boolean,
    onScanToggle: () -> Unit,
    onHostToggle: () -> Unit,
    onConnect: (BtDevice) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hosting Panel
        GlassCard(
            isDark = isDark,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Receive Connections",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) TextDarkPrimary else TextLightPrimary
                    )
                    Text(
                        text = if (isHosting) "Waiting for connections..." else "Offline - invisible to others",
                        fontSize = 12.sp,
                        color = if (isHosting) PrimaryBlue else (if (isDark) TextDarkSecondary else TextLightSecondary)
                    )
                }

                Button(
                    onClick = onHostToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(
                            1.dp,
                            if (isHosting) PrimaryBlue else (if (isDark) DarkBorder else LightBorder),
                            RoundedCornerShape(6.dp)
                        )
                        .background(
                            if (isHosting) PrimaryBlue.copy(alpha = 0.1f) else Color.Transparent
                        )
                ) {
                    Text(
                        text = if (isHosting) "STOP HOST" else "HOST",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isHosting) PrimaryBlue else (if (isDark) TextDarkPrimary else TextLightPrimary)
                    )
                }
            }
        }

        // Discovery Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Nearby Devices",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) TextDarkPrimary else TextLightPrimary
            )

            Button(
                onClick = onScanToggle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(end = 6.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text(
                        text = if (isScanning) "STOP SCAN" else "SCAN",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }
        }

        // Devices List
        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isScanning) "Searching for bluetooth devices..." else "No devices. Press SCAN to search.",
                    color = if (isDark) TextDarkSecondary else TextLightSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(devices) { device ->
                    DeviceCard(
                        isDark = isDark,
                        device = device,
                        onClick = { onConnect(device) }
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceCard(
    isDark: Boolean,
    device: BtDevice,
    onClick: () -> Unit
) {
    GlassCard(
        isDark = isDark,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = if (device.paired) PrimaryBlue.copy(alpha = 0.1f) else (if (isDark) DarkBorder else LightBorder),
                        shape = RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Bluetooth Device",
                    tint = if (device.paired) PrimaryBlue else (if (isDark) TextDarkSecondary else TextLightSecondary),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name.ifEmpty { "Unknown Device" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isDark) TextDarkPrimary else TextLightPrimary
                )
                Text(
                    text = device.address,
                    fontSize = 12.sp,
                    color = if (isDark) TextDarkSecondary else TextLightSecondary
                )
            }

            if (device.paired) {
                Text(
                    text = "PAIRED",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue,
                    modifier = Modifier
                        .border(
                            1.dp,
                            PrimaryBlue.copy(alpha = 0.4f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun ConnectingScreen(isDark: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                color = PrimaryBlue,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Connecting...",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) TextDarkPrimary else TextLightPrimary
            )
            Text(
                text = "Exchanging encryption keys",
                fontSize = 12.sp,
                color = if (isDark) TextDarkSecondary else TextLightSecondary
            )
        }
    }
}

@Composable
fun ChatScreen(
    isDark: Boolean,
    peerId: String,
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    onPickFile: () -> Unit,
    onDisconnect: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenFile: (ChatMessage) -> Unit,
    onSaveFile: (ChatMessage) -> Unit,
    onShareFile: (ChatMessage) -> Unit,
    isOffline: Boolean = false
) {
    var textState by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Trigger lazy loading of history when scrolled to the top
    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems > 0 && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 && listState.isScrollInProgress
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            onLoadMore()
        }
    }

    // Scroll to bottom on new messages
    var lastSize by remember { mutableStateOf(0) }
    LaunchedEffect(messages.size) {
        if (messages.size > lastSize) {
            val lastMessage = messages.lastOrNull()
            if (lastMessage != null && (lastMessage.isOutgoing || listState.firstVisibleItemIndex >= messages.size - 5)) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
        lastSize = messages.size
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Chat Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isDark) DarkCardBg else LightCardBg)
                .border(width = 1.dp, color = if (isDark) DarkBorder else LightBorder)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (isOffline) Color(0xFF888888) else Color(0xFF2EA85C), // Grey or Green dot
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (isOffline) "Historical Archive" else "Connected Peer",
                        fontSize = 10.sp,
                        color = if (isDark) TextDarkSecondary else TextLightSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = peerId,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) TextDarkPrimary else TextLightPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Button(
                onClick = onDisconnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier
                    .height(32.dp)
                    .border(
                        1.dp,
                        if (isOffline) (if (isDark) DarkBorder else LightBorder) else Color.Red.copy(alpha = 0.5f),
                        RoundedCornerShape(6.dp)
                    )
            ) {
                Text(
                    text = if (isOffline) "CLOSE HISTORY" else "DISCONNECT",
                    color = if (isOffline) (if (isDark) TextDarkPrimary else TextLightPrimary) else Color.Red,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(
                    isDark = isDark,
                    message = message,
                    onOpenFile = onOpenFile,
                    onSaveFile = onSaveFile,
                    onShareFile = onShareFile
                )
            }
        }

        // Bottom Input Row
        if (isOffline) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .background(
                        color = (if (isDark) DarkCardBg else LightCardBg).copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isDark) DarkBorder else LightBorder,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🪐 Viewing historical chat archive. Connect to device to transmit new signals.",
                    fontSize = 12.sp,
                    color = if (isDark) TextDarkSecondary else TextLightSecondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // File Attachment Button
                IconButton(
                    onClick = onPickFile,
                    modifier = Modifier
                        .size(44.dp)
                        .border(
                            width = 1.dp,
                            color = if (isDark) DarkBorder else LightBorder,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .background(
                            color = if (isDark) DarkCardBg else LightCardBg,
                            shape = RoundedCornerShape(6.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Send File",
                        tint = if (isDark) TextDarkPrimary else TextLightPrimary
                    )
                }

                // Text Input Field
                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    placeholder = {
                        Text(
                            text = "Type a message...",
                            color = if (isDark) TextDarkSecondary else TextLightSecondary,
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isDark) DarkCardBg else LightCardBg, shape = RoundedCornerShape(6.dp)),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = if (isDark) DarkBorder else LightBorder,
                        focusedTextColor = if (isDark) TextDarkPrimary else TextLightPrimary,
                        unfocusedTextColor = if (isDark) TextDarkPrimary else TextLightPrimary
                    ),
                    shape = RoundedCornerShape(6.dp),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (textState.isNotBlank()) {
                            onSendMessage(textState)
                            textState = ""
                        }
                    })
                )

                // Send Button
                IconButton(
                    onClick = {
                        if (textState.isNotBlank()) {
                            onSendMessage(textState)
                            textState = ""
                        }
                    },
                    enabled = textState.isNotBlank(),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (textState.isNotBlank()) PrimaryBlue else (if (isDark) DarkCardBg else LightCardBg)
                        )
                        .border(
                            1.dp,
                            if (textState.isNotBlank()) PrimaryBlue else (if (isDark) DarkBorder else LightBorder),
                            RoundedCornerShape(6.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (textState.isNotBlank()) Color.White else (if (isDark) TextDarkSecondary else TextLightSecondary)
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    isDark: Boolean,
    message: ChatMessage,
    onOpenFile: (ChatMessage) -> Unit,
    onSaveFile: (ChatMessage) -> Unit,
    onShareFile: (ChatMessage) -> Unit
) {
    val alignment = if (message.isOutgoing) Alignment.End else Alignment.Start
    val bubbleShape = if (message.isOutgoing) {
        RoundedCornerShape(8.dp, 8.dp, 0.dp, 8.dp)
    } else {
        RoundedCornerShape(8.dp, 8.dp, 8.dp, 0.dp)
    }

    val bubbleModifier = Modifier
        .clip(bubbleShape)
        .let { modifier ->
            if (message.isOutgoing) {
                modifier.background(PrimaryBlue)
            } else {
                val inBg = if (isDark) IncomingDarkBg else IncomingLightBg
                modifier
                    .background(inBg)
                    .border(
                        1.dp,
                        if (isDark) DarkBorder else LightBorder,
                        bubbleShape
                    )
            }
        }
        .padding(horizontal = 12.dp, vertical = 8.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (message.isOutgoing) 48.dp else 0.dp,
                end = if (message.isOutgoing) 0.dp else 48.dp
            ),
        horizontalAlignment = alignment
    ) {
        if (!message.isOutgoing && message.senderDeviceId != message.deviceId) {
            Text(
                text = message.senderDeviceId,
                fontSize = 10.sp,
                color = if (isDark) TextDarkSecondary else TextLightSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }
        Box(modifier = bubbleModifier) {
            if (message.isFile) {
                FileMessageBody(
                    isDark = isDark,
                    message = message,
                    onOpenFile = onOpenFile,
                    onSaveFile = onSaveFile,
                    onShareFile = onShareFile
                )
            } else {
                Text(
                    text = message.body,
                    color = if (message.isOutgoing) Color.White else (if (isDark) TextDarkPrimary else TextLightPrimary),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
        
        Spacer(modifier = Modifier.height(2.dp))
        
        val timeString = try {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.sentAt))
        } catch (e: Exception) {
            ""
        }
        Text(
            text = timeString,
            fontSize = 9.sp,
            color = if (isDark) TextDarkSecondary else TextLightSecondary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun FileMessageBody(
    isDark: Boolean,
    message: ChatMessage,
    onOpenFile: (ChatMessage) -> Unit,
    onSaveFile: (ChatMessage) -> Unit,
    onShareFile: (ChatMessage) -> Unit
) {
    val isOutgoing = message.isOutgoing
    val fileName = message.fileName ?: "File"
    val filePath = message.filePath ?: ""
    val fileExists = filePath.isNotEmpty() && File(filePath).exists()

    Column(
        modifier = Modifier.widthIn(max = 240.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = if (isOutgoing) Color.White.copy(alpha = 0.2f) else (if (isDark) DarkCardBg else LightCardBg),
                        shape = RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "File Icon",
                    tint = if (isOutgoing) Color.White else PrimaryBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = fileName,
                    color = if (isOutgoing) Color.White else (if (isDark) TextDarkPrimary else TextLightPrimary),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isOutgoing) "Sent File" else "Received File",
                    color = if (isOutgoing) Color.White.copy(alpha = 0.7f) else (if (isDark) TextDarkSecondary else TextLightSecondary),
                    fontSize = 10.sp
                )
            }
        }

        if (fileExists) {
            Divider(
                color = if (isOutgoing) Color.White.copy(alpha = 0.2f) else (if (isDark) DarkBorder else LightBorder),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "OPEN",
                    color = if (isOutgoing) Color.White else PrimaryBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onOpenFile(message) }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                )
                Text(
                    text = "DOWNLOAD",
                    color = if (isOutgoing) Color.White else PrimaryBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onSaveFile(message) }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                )
                Text(
                    text = "SHARE",
                    color = if (isOutgoing) Color.White else PrimaryBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onShareFile(message) }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                )
            }
        } else {
            Text(
                text = "File not on device",
                color = if (isOutgoing) Color.White.copy(alpha = 0.5f) else Color.Red,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun GlassCard(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val bg = if (isDark) DarkCardBg else LightCardBg
    val border = if (isDark) DarkBorder else LightBorder

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(
                width = 1.dp,
                color = border,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        content()
    }
}
