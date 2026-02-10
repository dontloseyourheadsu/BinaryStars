package com.tds.binarystars.fragments

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.content.ContextCompat
import com.tds.binarystars.R
import com.tds.binarystars.activities.MessagingChatActivity
import com.tds.binarystars.adapter.ChatListItem
import com.tds.binarystars.adapter.MessagingChatsAdapter
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.bluetooth.BluetoothChatListener
import com.tds.binarystars.bluetooth.BluetoothChatManager
import com.tds.binarystars.messaging.MessagingEventListener
import com.tds.binarystars.messaging.MessagingSocketManager
import com.tds.binarystars.storage.ChatStorage
import com.tds.binarystars.MainActivity
import com.tds.binarystars.util.NetworkUtils
import com.tds.binarystars.model.ChatSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessagingFragment : Fragment(), MessagingEventListener, BluetoothChatListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var newChatButton: Button
    private lateinit var adapter: MessagingChatsAdapter
    private lateinit var contentView: View
    private lateinit var noConnectionView: View
    private lateinit var retryButton: Button
    private val items = mutableListOf<ChatListItem>()
    private var bluetoothAvailableDevices: Set<String> = emptySet()
    private var lastNameLookup: Map<String, String> = emptyMap()

    private val bluetoothDiscoveryListener: (Map<String, android.bluetooth.BluetoothDevice>) -> Unit = { devices ->
        bluetoothAvailableDevices = devices.keys
        viewLifecycleOwner.lifecycleScope.launch {
            refreshFromCache()
        }
    }

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.any { it }
        if (granted) {
            startBluetoothFeatures()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(StartActivityForResult()) {
        if (BluetoothChatManager.isEnabled()) {
            startBluetoothFeatures()
        }
    }

    /**
     * Inflates the messaging list UI.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_messaging, container, false)
    }

    /**
     * Initializes UI, adapters, and loads chat summaries.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.ivMenu)?.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        recyclerView = view.findViewById(R.id.rvChats)
        emptyState = view.findViewById(R.id.tvMessagingEmptyState)
        progressBar = view.findViewById(R.id.pbMessagingLoading)
        newChatButton = view.findViewById(R.id.btnNewChat)
        contentView = view.findViewById(R.id.viewContent)
        noConnectionView = view.findViewById(R.id.viewNoConnection)
        retryButton = view.findViewById(R.id.btnRetry)

        adapter = MessagingChatsAdapter(items) { item ->
            openChat(item.deviceId, item.deviceName)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        newChatButton.setOnClickListener {
            pickDeviceToChat()
        }

        retryButton.setOnClickListener {
            loadChats()
        }

        val selfDeviceId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        val selfDeviceName = BluetoothAdapter.getDefaultAdapter()?.name ?: "Android"
        BluetoothChatManager.setSelf(selfDeviceId, selfDeviceName)

        loadChats()
    }

    /**
     * Registers websocket listeners and refreshes chats.
     */
    override fun onResume() {
        super.onResume()
        MessagingSocketManager.addListener(this)
        BluetoothChatManager.addListener(this)
        loadChats()
    }

    /**
     * Unregisters websocket listeners.
     */
    override fun onPause() {
        super.onPause()
        MessagingSocketManager.removeListener(this)
        BluetoothChatManager.removeListener(this)
    }

    override fun onStart() {
        super.onStart()
        ensureBluetoothReady()
    }

    override fun onStop() {
        super.onStop()
        stopBluetoothFeatures()
    }

    override fun onChatUpdated(deviceId: String) {
        loadChats()
    }

    override fun onDeviceRemoved(deviceId: String, isSelf: Boolean) {
        loadChats()
    }

    override fun onConnectionStateChanged(isConnected: Boolean) {
        // No-op for now
    }

    override fun onBluetoothConnectionChanged(deviceId: String, isConnected: Boolean, isConnecting: Boolean) {
        // No-op for now
    }

    /**
     * Loads cached chats and applies device name lookups when online.
     */
    private fun loadChats() {
        viewLifecycleOwner.lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val summaries = withContext(Dispatchers.IO) { ChatStorage.getChats() }
            val isOnline = NetworkUtils.isOnline(requireContext())
            if (!isOnline) {
                progressBar.visibility = View.GONE
                newChatButton.isEnabled = false
                updateItems(summaries, lastNameLookup)
                if (items.isEmpty()) {
                    setNoConnection(true)
                } else {
                    setNoConnection(false)
                }
                return@launch
            }

            newChatButton.isEnabled = true
            val devicesResponse = withContext(Dispatchers.IO) { ApiClient.apiService.getDevices() }
            progressBar.visibility = View.GONE

            val nameLookup = if (devicesResponse.isSuccessful) {
                devicesResponse.body().orEmpty().associateBy({ it.id }, { it.name })
            } else {
                emptyMap()
            }

            lastNameLookup = nameLookup

            updateItems(summaries, nameLookup)
            setNoConnection(false)
        }
    }

    private suspend fun refreshFromCache() {
        val summaries = withContext(Dispatchers.IO) { ChatStorage.getChats() }
        updateItems(summaries, lastNameLookup)
    }

    /**
     * Updates the adapter items from cached chat summaries.
     */
    private fun updateItems(summaries: List<ChatSummary>, nameLookup: Map<String, String>) {
        items.clear()
        summaries.forEach { summary ->
            val name = nameLookup[summary.deviceId] ?: summary.deviceId
            val bluetoothAvailable = bluetoothAvailableDevices.contains(name)
            items.add(
                ChatListItem(
                    deviceId = summary.deviceId,
                    deviceName = name,
                    lastMessage = summary.lastMessage,
                    lastSentAt = summary.lastSentAt,
                    isBluetoothAvailable = bluetoothAvailable
                )
            )
        }
        adapter.notifyDataSetChanged()
        emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * Toggles offline state UI panels.
     */
    private fun setNoConnection(show: Boolean) {
        noConnectionView.visibility = if (show) View.VISIBLE else View.GONE
        contentView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun ensureBluetoothReady() {
        if (!BluetoothChatManager.isSupported()) return
        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions()
            return
        }
        if (!BluetoothChatManager.isEnabled()) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        startBluetoothFeatures()
    }

    private fun startBluetoothFeatures() {
        BluetoothChatManager.acquireDiscovery(requireContext(), bluetoothDiscoveryListener)
        BluetoothChatManager.acquireServer()
    }

    private fun stopBluetoothFeatures() {
        BluetoothChatManager.releaseDiscovery(requireContext(), bluetoothDiscoveryListener)
        BluetoothChatManager.releaseServer()
    }

    private fun hasBluetoothPermissions(): Boolean {
        val permissions = requiredBluetoothPermissions()
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        bluetoothPermissionLauncher.launch(requiredBluetoothPermissions())
    }

    private fun requiredBluetoothPermissions(): Array<String> {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("HardwareIds")
    /**
     * Prompts the user to select a device and opens a chat.
     */
    private fun pickDeviceToChat() {
        viewLifecycleOwner.lifecycleScope.launch {
            val devicesResponse = withContext(Dispatchers.IO) { ApiClient.apiService.getDevices() }
            val devices = devicesResponse.body().orEmpty()
            val currentDeviceId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
            val availableDevices = devices.filter { it.id != currentDeviceId }
            if (availableDevices.isEmpty()) {
                return@launch
            }

            val names = availableDevices.map { it.name }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Start chat")
                .setItems(names) { _, index ->
                    val device = availableDevices[index]
                    openChat(device.id, device.name)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    /**
     * Opens the messaging chat activity for the selected device.
     */
    private fun openChat(deviceId: String, deviceName: String) {
        val intent = Intent(requireContext(), MessagingChatActivity::class.java)
        intent.putExtra(MessagingChatActivity.EXTRA_DEVICE_ID, deviceId)
        intent.putExtra(MessagingChatActivity.EXTRA_DEVICE_NAME, deviceName)
        startActivity(intent)
    }
}
