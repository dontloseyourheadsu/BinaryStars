package com.tds.binarystars.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.R
import com.tds.binarystars.adapter.DevicesAdapter
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.DeviceTypeDto
import com.tds.binarystars.model.Device
import com.tds.binarystars.model.DeviceType
import kotlinx.coroutines.launch

import android.provider.Settings
import android.annotation.SuppressLint
import android.widget.Button
import android.widget.ImageView
import com.tds.binarystars.MainActivity

class DevicesFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_devices, container, false)
    }

    @SuppressLint("HardwareIds")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.ivMenu)?.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }
        
        refreshDevices()
    }

    @SuppressLint("HardwareIds")
    fun refreshDevices() {
        val view = view ?: return
        val rvOnline = view.findViewById<RecyclerView>(R.id.rvOnlineDevices)
        val rvOffline = view.findViewById<RecyclerView>(R.id.rvOfflineDevices)
        val btnLinkDevice = view.findViewById<Button>(R.id.btnLinkDevice)

        rvOnline.layoutManager = LinearLayoutManager(context)
        rvOffline.layoutManager = LinearLayoutManager(context)

        // Get Current Device ID
        val context = requireContext()
        val currentDeviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val currentDeviceName = android.os.Build.MODEL

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getDevices()
                if (response.isSuccessful && response.body() != null) {
                    val dtos = response.body()!!
                    val devices = dtos.map { dto ->
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
                            isOnline = dto.isOnline,
                            isSynced = dto.isSynced,
                            wifiUploadSpeed = dto.wifiUploadSpeed,
                            wifiDownloadSpeed = dto.wifiDownloadSpeed,
                            lastSeen = System.currentTimeMillis()
                        )
                    }
                    
                    val onlineDevices = devices.filter { it.isOnline }
                    val offlineDevices = devices.filter { !it.isOnline }
                    val isRegistered = devices.any { it.id == currentDeviceId }
                    
                    // Update header subtitle if view exists
                    val tvHeaderSubtitle = view.findViewById<TextView>(R.id.tvHeaderSubtitle)
                    if (tvHeaderSubtitle != null) {
                        tvHeaderSubtitle.text = "${onlineDevices.size} devices online"
                    }

                    // Setup RecyclerViews for online/offline sections
                    rvOnline.adapter = DevicesAdapter(onlineDevices)
                    rvOffline.adapter = DevicesAdapter(offlineDevices)
                    
                    // Setup Button
                    if (isRegistered) {
                        btnLinkDevice.text = "Unlink This Device"
                        btnLinkDevice.visibility = View.VISIBLE
                        btnLinkDevice.setOnClickListener {
                            (activity as? MainActivity)?.unlinkDevice(currentDeviceId)
                        }
                    } else {
                        btnLinkDevice.text = "Link This Device"
                        btnLinkDevice.visibility = View.VISIBLE
                         btnLinkDevice.setOnClickListener {
                            (activity as? MainActivity)?.registerDevice(currentDeviceId, currentDeviceName)
                        }
                    }
                } else {
                    Toast.makeText(context, "Failed to load devices: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
               // Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
               // e.printStackTrace()
            }
        }
    }
}
