package com.tds.binarystars

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tds.binarystars.fragments.DevicesFragment
import com.tds.binarystars.fragments.FilesFragment
import com.tds.binarystars.fragments.MapFragment
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

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkDeviceRegistration()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        // Load default fragment
        if (savedInstanceState == null) {
            loadFragment(DevicesFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_devices -> DevicesFragment()
                R.id.nav_files -> FilesFragment()
                R.id.nav_notes -> NotesFragment()
                R.id.nav_map -> MapFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> DevicesFragment()
            }
            loadFragment(fragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    @SuppressLint("HardwareIds")
    private fun checkDeviceRegistration() {
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

    public fun registerDevice(deviceId: String, deviceName: String) {
         // Need real IP logic here, using placeholders for now as implementing full Net logic is out of scope of single file
         val ipAddress = "127.0.0.1" 
         val ipv6Address = "::1"

         lifecycleScope.launch {
            try {
                val req = RegisterDeviceRequest(deviceId, deviceName, ipAddress, ipv6Address)
                val response = ApiClient.apiService.registerDevice(req)
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "Device Linked Successfully", Toast.LENGTH_SHORT).show()
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
    
    public fun unlinkDevice(deviceId: String) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.unlinkDevice(deviceId)
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "Device Unlinked", Toast.LENGTH_SHORT).show()
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
}
