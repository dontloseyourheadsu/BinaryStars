package com.tds.binarystars

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.AuthTokenStore
import com.tds.binarystars.api.DeviceTypeDto
import com.tds.binarystars.api.RegisterDeviceRequest
import com.tds.binarystars.background.BackgroundRequirementsActivity
import com.tds.binarystars.background.BackgroundRequirementsManager
import com.tds.binarystars.background.DeviceSyncForegroundService
import com.tds.binarystars.crypto.CryptoManager
import com.tds.binarystars.fragments.*
import com.tds.binarystars.messaging.MessagingSocketManager
import com.tds.binarystars.model.Device
import com.tds.binarystars.model.DeviceType
import com.tds.binarystars.storage.*
import com.tds.binarystars.util.NativeLogging as L
import com.tds.binarystars.util.NetworkUtils
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!BackgroundRequirementsManager.areMandatoryRequirementsMet(this)) {
            startActivity(Intent(this, BackgroundRequirementsActivity::class.java))
            finish()
            return
        }

        if (!ensureAuthenticatedSession()) {
            return
        }

        setContentView(R.layout.activity_main)

        registerReconnectListener()
        syncCachedDevicesFromServer()
        checkDeviceRegistration()
        checkPendingTransfers()

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        MessagingSocketManager.connect(this, deviceId)
        DeviceSyncForegroundService.start(this)

        drawerLayout = findViewById(R.id.drawer_layout)

        if (savedInstanceState == null) {
            loadFragment(DevicesFragment())
        }

        setupSidebarListeners()
        // No initial updateSidebarConnectivity here, let registerReconnectListener handle it
    }

    private fun ensureAuthenticatedSession(): Boolean {
        if (AuthTokenStore.hasStoredSession()) {
            return true
        }

        MessagingSocketManager.disconnect()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
        return false
    }

    private fun setupSidebarListeners() {
        val navDevices = findViewById<LinearLayout>(R.id.nav_devices)
        val navFiles = findViewById<LinearLayout>(R.id.nav_files)
        val navNotes = findViewById<LinearLayout>(R.id.nav_notes)
        val navMessaging = findViewById<LinearLayout>(R.id.nav_messaging)
        val navMap = findViewById<LinearLayout>(R.id.nav_map)
        val navActions = findViewById<LinearLayout>(R.id.nav_actions)
        val navNotifications = findViewById<LinearLayout>(R.id.nav_notifications)
        val navSettings = findViewById<LinearLayout>(R.id.nav_settings)
        val navLogs = findViewById<LinearLayout>(R.id.nav_logs)
        val navBluetooth = findViewById<LinearLayout>(R.id.nav_bluetooth)

        navDevices.setOnClickListener { loadFragment(DevicesFragment()); drawerLayout.closeDrawers() }
        navFiles.setOnClickListener { loadFragment(FilesFragment()); drawerLayout.closeDrawers() }
        navNotes.setOnClickListener { loadFragment(NotesFragment()); drawerLayout.closeDrawers() }
        navMessaging.setOnClickListener { loadFragment(MessagingFragment()); drawerLayout.closeDrawers() }
        navMap.setOnClickListener { loadFragment(MapFragment()); drawerLayout.closeDrawers() }
        navActions.setOnClickListener { loadFragment(ActionsFragment()); drawerLayout.closeDrawers() }
        navNotifications.setOnClickListener { loadFragment(com.tds.binarystars.fragments.NotificationsFragment()); drawerLayout.closeDrawers() }
        navSettings.setOnClickListener { loadFragment(SettingsFragment()); drawerLayout.closeDrawers() }
        navLogs.setOnClickListener { 
            startActivity(Intent(this, com.tds.binarystars.activities.DebugLogsActivity::class.java))
            drawerLayout.closeDrawers()
        }
        navBluetooth.setOnClickListener { loadFragment(BluetoothTabFragment()); drawerLayout.closeDrawers() }
    }

    fun openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START)
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun registerReconnectListener() {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return
        
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                L.d("MainActivity", "Network Available: $network")
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: android.net.NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val isWifi = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                val isCellular = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                
                // We define "online" as having validated internet via WiFi OR Mobile Data
                val isOnline = isValidated && (isWifi || isCellular)

                L.d("MainActivity", "Network Capabilities Changed: $network. Online=$isOnline (Validated=$isValidated, WiFi=$isWifi, Cell=$isCellular)")

                runOnUiThread {
                    if (isOnline) {
                        syncCachedDevicesFromServer()
                        reconnectRealtimeTransports()
                    }
                    updateSidebarConnectivity(isOnline)
                    
                    val current = supportFragmentManager.findFragmentById(R.id.fragment_container)
                    if (isOnline && current is com.tds.binarystars.fragments.BluetoothTabFragment) {
                        L.i("MainActivity", "Redirecting from Bluetooth to Devices (Online restored)")
                        Toast.makeText(this@MainActivity, "Online mode restored", Toast.LENGTH_SHORT).show()
                        com.tds.binarystars.bluetooth.BluetoothChatManager.clearSession()
                        loadFragment(com.tds.binarystars.fragments.DevicesFragment())
                    }
                }
            }

            override fun onLost(network: Network) {
                L.w("MainActivity", "Network Lost: $network")
                runOnUiThread {
                    // When a network is lost, re-evaluate the overall state
                    val stillOnline = NetworkUtils.isOnline(this@MainActivity)
                    L.d("MainActivity", "Re-evaluating connectivity after loss. Still online: $stillOnline")
                    updateSidebarConnectivity(stillOnline)
                }
            }
        }
        
        networkCallback = callback
        connectivityManager.registerDefaultNetworkCallback(callback)

        // Initial check using current active network
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val initialOnline = capabilities?.let {
            it.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            (it.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || 
             it.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR))
        } ?: false
        
        L.d("MainActivity", "Initial connectivity check: Online=$initialOnline")
        updateSidebarConnectivity(initialOnline)
    }

    private fun updateSidebarConnectivity(online: Boolean) {
        val navBluetooth = findViewById<LinearLayout>(R.id.nav_bluetooth) ?: return
        // Tab is VISIBLE only when NOT online (neither WiFi nor Mobile Data)
        val shouldShow = !online
        L.d("MainActivity", "Updating Sidebar Connectivity: Online=$online -> BluetoothVisible=$shouldShow")
        navBluetooth.visibility = if (shouldShow) View.VISIBLE else View.GONE
    }

    private fun reconnectRealtimeTransports() {
        if (!AuthTokenStore.hasStoredSession()) return
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        MessagingSocketManager.connect(this, deviceId)
        DeviceSyncForegroundService.start(this)
    }

    private fun syncCachedDevicesFromServer() {
        if (!NetworkUtils.isOnline(this)) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.apiService.getDevices()
                if (response.isSuccessful) {
                    val devices = response.body() ?: emptyList()
                    val domainDevices = devices.map { dto ->
                        Device(
                            id = dto.id,
                            name = dto.name,
                            type = if (dto.type == DeviceTypeDto.Linux) DeviceType.LINUX else DeviceType.ANDROID,
                            ipAddress = dto.ipAddress ?: "0.0.0.0",
                            publicKey = dto.publicKey,
                            publicKeyAlgorithm = dto.publicKeyAlgorithm,
                            batteryLevel = dto.batteryLevel ?: 0,
                            isOnline = dto.isOnline,
                            isAvailable = dto.isAvailable,
                            isSynced = dto.isSynced,
                            cpuLoadPercent = dto.cpuLoadPercent,
                            memoryLoadPercent = dto.memoryLoadPercent,
                            wifiUploadSpeed = dto.wifiUploadSpeed ?: "0",
                            wifiDownloadSpeed = dto.wifiDownloadSpeed ?: "0",
                            isBluetoothOnline = false,
                            lastSeen = System.currentTimeMillis()
                        )
                    }
                    DeviceCacheStorage.saveDevices(domainDevices)
                }
            } catch (_: Exception) {}
        }
    }

    private fun checkDeviceRegistration() {
        if (!NetworkUtils.isOnline(this)) return
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val deviceName = android.os.Build.MODEL
        
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getDevices()
                if (response.isSuccessful && response.body() != null) {
                    val devices = response.body()!!
                    if (devices.none { it.id == deviceId }) {
                        showRegistrationPrompt(deviceId, deviceName)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun showRegistrationPrompt(deviceId: String, deviceName: String) {
        AlertDialog.Builder(this)
            .setTitle("Link Device")
            .setMessage("Do you want to link this device ($deviceName) to your account?")
            .setPositiveButton("Link") { _, _ -> registerDevice(deviceId, deviceName) }
            .setNegativeButton("No", null)
            .show()
    }

    fun registerDevice(deviceId: String, deviceName: String) {
        if (!NetworkUtils.isOnline(this)) {
            Toast.makeText(this, "No connection available", Toast.LENGTH_SHORT).show()
            return
        }

        val ipAddress = "127.0.0.1" 
        val ipv6Address = "::1"
        val publicKey = CryptoManager.getPublicKeyBase64()
        val publicKeyAlgorithm = CryptoManager.getPublicKeyAlgorithm()

        lifecycleScope.launch {
            try {
                val req = RegisterDeviceRequest(deviceId, deviceName, ipAddress, ipv6Address, publicKey, publicKeyAlgorithm)
                val response = ApiClient.apiService.registerDevice(req)
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "Device Linked Successfully", Toast.LENGTH_SHORT).show()
                    syncCachedDevicesFromServer()
                } else {
                    Toast.makeText(this@MainActivity, "Failed to link device.", Toast.LENGTH_SHORT).show()
                }
            } catch(e: Exception) {
                Toast.makeText(this@MainActivity, "Error linking device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun unlinkDevice(deviceId: String) {
        if (!NetworkUtils.isOnline(this)) {
            Toast.makeText(this, "No connection available", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Unlinking device...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.unlinkDevice(deviceId)
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "Device Unlinked", Toast.LENGTH_SHORT).show()
                    syncCachedDevicesFromServer()
                } else {
                    Toast.makeText(this@MainActivity, "Failed to unlink device", Toast.LENGTH_SHORT).show()
                }
            } catch(e: Exception) {
                Toast.makeText(this@MainActivity, "Error unlinking device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPendingTransfers() {
        if (!NetworkUtils.isOnline(this)) return
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getPendingTransfers(deviceId)
                if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("New Files")
                        .setMessage("You have files ready to download.")
                        .setPositiveButton("Open") { _, _ -> loadFragment(FilesFragment()) }
                        .setNegativeButton("Later", null)
                        .show()
                }
            } catch (_: Exception) {}
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    override fun onDestroy() {
        networkCallback?.let { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(it) }
        super.onDestroy()
        MessagingSocketManager.disconnect()
    }
}