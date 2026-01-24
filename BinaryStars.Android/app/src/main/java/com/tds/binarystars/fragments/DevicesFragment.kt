package com.tds.binarystars.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.R
import com.tds.binarystars.adapter.DevicesAdapter
import com.tds.binarystars.model.Device
import com.tds.binarystars.model.DeviceType

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

        val devices = listOf(
            Device(
                id = "1",
                name = "Ubuntu Workstation",
                type = DeviceType.LINUX,
                ipAddress = "192.168.1.10",
                batteryLevel = 100,
                isOnline = true,
                isSynced = true,
                wifiUploadSpeed = "12 Kbps",
                wifiDownloadSpeed = "4.2 Mbps"
            ),
            Device(
                id = "2",
                name = "Pixel 7 Pro",
                type = DeviceType.ANDROID,
                ipAddress = "192.168.1.15",
                batteryLevel = 87,
                isOnline = true,
                isSynced = true,
                wifiUploadSpeed = "0 Kbps",
                wifiDownloadSpeed = "2 Kbps"
            ),
            Device(
                id = "3",
                name = "Linux Server",
                type = DeviceType.LINUX,
                ipAddress = "192.168.1.200",
                batteryLevel = 100,
                isOnline = true,
                isSynced = true,
                wifiUploadSpeed = "50 Mbps",
                wifiDownloadSpeed = "20 Mbps"
            ),
            Device(
                id = "4",
                name = "Samsung Tablet",
                type = DeviceType.ANDROID,
                ipAddress = "192.168.1.25",
                batteryLevel = 45,
                isOnline = false,
                isSynced = false,
                wifiUploadSpeed = "-",
                wifiDownloadSpeed = "-"
            ),
             Device(
                id = "5",
                name = "Arch Linux Laptop",
                type = DeviceType.LINUX,
                ipAddress = "192.168.1.101",
                batteryLevel = 60,
                isOnline = false,
                isSynced = false,
                wifiUploadSpeed = "-",
                wifiDownloadSpeed = "-"
            ),
            Device(
                id = "6",
                name = "OnePlus 9",
                type = DeviceType.ANDROID,
                ipAddress = "192.168.1.18",
                batteryLevel = 92,
                isOnline = true,
                isSynced = true,
                wifiUploadSpeed = "10 Kbps",
                wifiDownloadSpeed = "500 Kbps"
            )
        )

        val onlineDevices = devices.filter { it.isOnline }
        val offlineDevices = devices.filter { !it.isOnline }
        
        // Update header subtitle
        val tvHeaderSubtitle = view.findViewById<TextView>(R.id.tvHeaderSubtitle)
        tvHeaderSubtitle.text = "${onlineDevices.size} devices online"

        // Setup online devices RecyclerView
        val rvOnlineDevices = view.findViewById<RecyclerView>(R.id.rvOnlineDevices)
        rvOnlineDevices.layoutManager = LinearLayoutManager(context)
        rvOnlineDevices.adapter = DevicesAdapter(onlineDevices)
        
        // Setup offline devices RecyclerView
        val rvOfflineDevices = view.findViewById<RecyclerView>(R.id.rvOfflineDevices)
        rvOfflineDevices.layoutManager = LinearLayoutManager(context)
        rvOfflineDevices.adapter = DevicesAdapter(offlineDevices)
    }
}