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
import com.tds.binarystars.model.Device
import com.tds.binarystars.model.DeviceType
import kotlinx.coroutines.launch

class DevicesFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_devices, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvDevices = view.findViewById<RecyclerView>(R.id.rvDevices)
        rvDevices.layoutManager = LinearLayoutManager(context)

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getDevices()
                if (response.isSuccessful && response.body() != null) {
                    val dtos = response.body()!!
                    val devices = dtos.map { dto ->
                        Device(
                            id = dto.id,
                            name = dto.name,
                            type = if (dto.type == 0) DeviceType.LINUX else DeviceType.ANDROID,
                            ipAddress = dto.ipAddress,
                            batteryLevel = dto.batteryLevel,
                            isOnline = dto.isOnline,
                            isSynced = dto.isSynced,
                            wifiUploadSpeed = dto.wifiUploadSpeed,
                            wifiDownloadSpeed = dto.wifiDownloadSpeed,
                            lastSeen = System.currentTimeMillis()
                        )
                    }
                    
                    val onlineDevices = devices.filter { it.isOnline }
                    
                    // Update header subtitle if view exists
                    val tvHeaderSubtitle = view.findViewById<TextView>(R.id.tvHeaderSubtitle)
                    if (tvHeaderSubtitle != null) {
                        tvHeaderSubtitle.text = "${onlineDevices.size} devices online"
                    }

                    // Setup RecyclerView
                    rvDevices.adapter = DevicesAdapter(devices)
                } else {
                    Toast.makeText(context, "Failed to load devices: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }
}
