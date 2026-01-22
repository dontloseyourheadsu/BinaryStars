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
            )
        )

        val rvDevices = findViewById<RecyclerView>(R.id.rvDevices)
        rvDevices.layoutManager = LinearLayoutManager(this)
        rvDevices.adapter = DevicesAdapter(devices)
    }
}
