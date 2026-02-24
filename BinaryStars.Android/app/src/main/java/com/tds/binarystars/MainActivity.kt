package com.tds.binarystars

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import android.widget.LinearLayout
import android.net.ConnectivityManager
import android.net.Network
import com.tds.binarystars.fragments.DevicesFragment
import com.tds.binarystars.fragments.FilesFragment
import com.tds.binarystars.fragments.MapFragment
import com.tds.binarystars.fragments.MessagingFragment
import com.tds.binarystars.fragments.NotesFragment
import com.tds.binarystars.fragments.SettingsFragment

import android.provider.Settings
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.RegisterDeviceRequest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.tds.binarystars.crypto.CryptoManager
import androidx.drawerlayout.widget.DrawerLayout
import com.tds.binarystars.messaging.MessagingSocketManager
import com.tds.binarystars.presence.PresenceHeartbeatManager
import androidx.core.view.GravityCompat
import com.tds.binarystars.api.DeviceTypeDto
import com.tds.binarystars.model.Device
import com.tds.binarystars.model.DeviceType
import com.tds.binarystars.storage.DeviceCacheStorage
import com.tds.binarystars.storage.SettingsStorage
import com.tds.binarystars.util.NetworkUtils

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Sets up the navigation drawer, device registration checks, and messaging socket.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        registerReconnectListener()
        syncCachedDevicesFromServer()
        checkDeviceRegistration()
        checkPendingTransfers()

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        MessagingSocketManager.connect(this, deviceId)
        PresenceHeartbeatManager.start(deviceId)

        drawerLayout = findViewById(R.id.drawer_layout)

        // Load default fragment
        if (savedInstanceState == null) {
            loadFragment(DevicesFragment())
        }

        // Set up sidebar item click listeners
        setupSidebarListeners()
    }

    /**
     * Hooks up click handlers for the sidebar navigation items.
     */
    private fun setupSidebarListeners() {
        val navDevices = findViewById<LinearLayout>(R.id.nav_devices)
        val navFiles = findViewById<LinearLayout>(R.id.nav_files)
        val navNotes = findViewById<LinearLayout>(R.id.nav_notes)
        val navMessaging = findViewById<LinearLayout>(R.id.nav_messaging)
        val navMap = findViewById<LinearLayout>(R.id.nav_map)
        val navSettings = findViewById<LinearLayout>(R.id.nav_settings)

        navDevices.setOnClickListener {
            loadFragment(DevicesFragment())
            drawerLayout.closeDrawers()
        }

        navFiles.setOnClickListener {
            loadFragment(FilesFragment())
            drawerLayout.closeDrawers()
        }

        navNotes.setOnClickListener {
            loadFragment(NotesFragment())
            drawerLayout.closeDrawers()
        }

        navMessaging.setOnClickListener {
            loadFragment(MessagingFragment())
            drawerLayout.closeDrawers()
        }

        navMap.setOnClickListener {
            loadFragment(MapFragment())
            drawerLayout.closeDrawers()
        }

        navSettings.setOnClickListener {
            loadFragment(SettingsFragment())
            drawerLayout.closeDrawers()
        }
    }

    /**
     * Opens the navigation drawer.
     */
    fun openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START)
    }

    override fun onDestroy() {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        networkCallback?.let {
            connectivityManager?.unregisterNetworkCallback(it)
        }
        networkCallback = null
        super.onDestroy()
        MessagingSocketManager.disconnect()
        PresenceHeartbeatManager.stop()
    }

    private fun registerReconnectListener() {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    syncCachedDevicesFromServer()
                }
            }
        }
        networkCallback = callback
        connectivityManager.registerDefaultNetworkCallback(callback)
    }

    private fun syncCachedDevicesFromServer() {
        if (!NetworkUtils.isOnline(this)) {
            return
        }

        val currentDeviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val telemetryEnabled = SettingsStorage.isDeviceTelemetryEnabled(true)

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getDevices()
                if (!response.isSuccessful || response.body() == null) {
                    return@launch
                }

                val mapped = response.body()!!.map { dto ->
                    val effectiveOnline = if (dto.id == currentDeviceId && !telemetryEnabled) {
                        false
                    } else {
                        dto.isOnline
                    }

                    Device(
                        id = dto.id,
                        name = dto.name,
                        type = when (dto.type) {
                            DeviceTypeDto.Linux -> DeviceType.LINUX
                            DeviceTypeDto.Android -> DeviceType.ANDROID
                        },
                        ipAddress = dto.ipAddress,
                        publicKey = dto.publicKey,
                        publicKeyAlgorithm = dto.publicKeyAlgorithm,
                        batteryLevel = dto.batteryLevel,
                        isOnline = effectiveOnline,
                        isAvailable = dto.isAvailable,
                        isSynced = dto.isSynced,
                        cpuLoadPercent = dto.cpuLoadPercent,
                        wifiUploadSpeed = dto.wifiUploadSpeed,
                        wifiDownloadSpeed = dto.wifiDownloadSpeed,
                        isBluetoothOnline = false,
                        lastSeen = System.currentTimeMillis()
                    )
                }

                DeviceCacheStorage.saveDevices(mapped)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Replaces the main content fragment.
     */
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    @SuppressLint("HardwareIds")
    /**
     * Ensures the current device is registered and prompts the user when needed.
     */
    private fun checkDeviceRegistration() {
        if (!NetworkUtils.isOnline(this)) {
            return
        }

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val deviceName = android.os.Build.MODEL
        // Getting IP requires more complex logic or a helper function. 
        // For simplicity assuming we can get it later or pass dummy for initial check if we had a dedicated "check" endpoint.
        // But the requirement says "if user is logged in, then register the device".
        // It implies we should try to register or check if registered.
        
        // We will try to register lightly or just prompt.
        // Requirement: "If there is space to register the device, prompt the user if they wanna link it"
        // This implies we don't auto-register without consent? 
        // "prompt the user if they wanna link it, and if they say no, still leave them a button..."
        
        // Implementation Strategy:
        // Attempt to Fetch Device info from API to see if "this" deviceId is in the list?
        // OR Just rely on a local flag "isRegistered"? Local flag is unreliable if user unlinks from another device.
        // Better: Fetch all devices, check if my current ID is in the list.
        
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getDevices()
                if (response.isSuccessful && response.body() != null) {
                    val devices = response.body()!!
                    val isRegistered = devices.any { it.id == deviceId }
                    
                    if (!isRegistered) {
                        showRegistrationPrompt(deviceId, deviceName)
                    }
                }
            } catch (e: Exception) {
                // Ignore network errors on startup check
            }
        }
    }

    /**
     * Shows a user prompt to link the device to the account.
     */
    private fun showRegistrationPrompt(deviceId: String, deviceName: String) {
        AlertDialog.Builder(this)
            .setTitle("Link Device")
            .setMessage("Do you want to link this device ($deviceName) to your account?")
            .setPositiveButton("Link") { _, _ -> 
                registerDevice(deviceId, deviceName)
            }
            .setNegativeButton("No", null)
            .show()
    }

    /**
     * Registers the current device with the backend.
     */
    public fun registerDevice(deviceId: String, deviceName: String) {
         if (!NetworkUtils.isOnline(this)) {
             Toast.makeText(this, "No connection available", Toast.LENGTH_SHORT).show()
             return
         }

         // Need real IP logic here, using placeholders for now as implementing full Net logic is out of scope of single file
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
                    // Reload fragment
                    val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                    if (currentFragment is com.tds.binarystars.fragments.DevicesFragment) {
                        currentFragment.refreshDevices()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Failed to link device.", Toast.LENGTH_SHORT).show()
                }
            } catch(e: Exception) {
                Toast.makeText(this@MainActivity, "Error linking device", Toast.LENGTH_SHORT).show()
            }
         }
    }
    
    /**
     * Unlinks a device from the current account.
     */
    public fun unlinkDevice(deviceId: String) {
        if (!NetworkUtils.isOnline(this)) {
            Toast.makeText(this, "No connection available", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.unlinkDevice(deviceId)
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "Device Unlinked", Toast.LENGTH_SHORT).show()
                    syncCachedDevicesFromServer()
                    val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                    if (currentFragment is com.tds.binarystars.fragments.DevicesFragment) {
                        currentFragment.refreshDevices()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Failed to unlink device", Toast.LENGTH_SHORT).show()
                }
            } catch(e: Exception) {
                Toast.makeText(this@MainActivity, "Error unlinking device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("HardwareIds")
    /**
     * Checks for pending file transfers and prompts the user to view them.
     */
    private fun checkPendingTransfers() {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getPendingTransfers(deviceId)
                if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("New Files")
                        .setMessage("You have files ready to download.")
                        .setPositiveButton("Open") { _, _ ->
                            loadFragment(FilesFragment())
                        }
                        .setNegativeButton("Later", null)
                        .show()
                }
            } catch (_: Exception) {
                // Ignore network errors
            }
        }
    }
}
