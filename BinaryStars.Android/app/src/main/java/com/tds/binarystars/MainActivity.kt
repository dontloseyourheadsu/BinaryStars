package com.tds.binarystars

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.adapter.DevicesAdapter
import com.tds.binarystars.model.Device
import com.tds.binarystars.model.DeviceType

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val devices = listOf(
            Device(
                id = "1",
                name = "Ubuntu Workstation",
                type = DeviceType.LINUX,
                ipAddress = "192.168.1.10",
                batteryLevel = 100,
                isConnected = true,
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
                isConnected = true,
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
                isConnected = true,
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
                isConnected = false,
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
                isConnected = false,
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
                isConnected = true,
                isSynced = true,
                wifiUploadSpeed = "10 Kbps",
                wifiDownloadSpeed = "500 Kbps"
            )
        )

        val connectedDevices = devices.filter { it.isConnected }
        val availableDevices = devices // "Available" list has connected true/false as per request (all devices)
        
        // Update header subtitle
        val tvHeaderSubtitle = findViewById<android.widget.TextView>(R.id.tvHeaderSubtitle)
        tvHeaderSubtitle.text = "${connectedDevices.size} devices connected"

        val rvDevices = findViewById<RecyclerView>(R.id.rvDevices)
        rvDevices.layoutManager = LinearLayoutManager(this)
        rvDevices.adapter = DevicesAdapter(connectedDevices)
        
        val rvAvailableDevices = findViewById<RecyclerView>(R.id.rvAvailableDevices)
        rvAvailableDevices.layoutManager = LinearLayoutManager(this)
        rvAvailableDevices.adapter = DevicesAdapter(availableDevices)
    }
}
