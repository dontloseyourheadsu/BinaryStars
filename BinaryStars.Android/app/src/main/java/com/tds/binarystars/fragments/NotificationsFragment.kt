package com.tds.binarystars.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tds.binarystars.R
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.SendNotificationRequestDto
import com.tds.binarystars.storage.DeviceCacheStorage
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {

    private lateinit var spinnerTargetDevice: Spinner
    private lateinit var etNotificationTitle: EditText
    private lateinit var etNotificationBody: EditText
    private lateinit var btnSendNotification: Button

    private var deviceMap = mutableMapOf<String, String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        spinnerTargetDevice = view.findViewById(R.id.spinnerTargetDevice)
        etNotificationTitle = view.findViewById(R.id.etNotificationTitle)
        etNotificationBody = view.findViewById(R.id.etNotificationBody)
        btnSendNotification = view.findViewById(R.id.btnSendNotification)

        loadDevices()

        btnSendNotification.setOnClickListener {
            sendNotification()
        }
    }

    @SuppressLint("HardwareIds")
    private fun loadDevices() {
        val context = context ?: return
        val myDeviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val devices = DeviceCacheStorage.getDevices()
        
        val validDevices = devices.filter { it.isOnline || it.id == myDeviceId }
        
        val deviceNames = mutableListOf<String>()
        deviceMap.clear()
        
        for (device in validDevices) {
            deviceNames.add(device.name)
            deviceMap[device.name] = device.id
        }

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, deviceNames)
        spinnerTargetDevice.adapter = adapter
    }

    @SuppressLint("HardwareIds")
    private fun sendNotification() {
        val context = context ?: return
        val title = etNotificationTitle.text.toString().trim()
        val body = etNotificationBody.text.toString().trim()
        val selectedName = spinnerTargetDevice.selectedItem as? String
        
        if (title.isEmpty() || body.isEmpty() || selectedName == null) {
            Toast.makeText(context, "Please provide title, body, and select a device", Toast.LENGTH_SHORT).show()
            return
        }

        val targetDeviceId = deviceMap[selectedName] ?: return
        val senderDeviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

        btnSendNotification.isEnabled = false

        lifecycleScope.launch {
            try {
                val request = SendNotificationRequestDto(
                    senderDeviceId = senderDeviceId,
                    targetDeviceId = targetDeviceId,
                    title = title,
                    body = body
                )
                
                val response = ApiClient.apiService.sendNotification(request)
                if (response.isSuccessful) {
                    Toast.makeText(context, "Notification sent", Toast.LENGTH_SHORT).show()
                    etNotificationTitle.text.clear()
                    etNotificationBody.text.clear()
                } else {
                    Toast.makeText(context, "Failed to send notification", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnSendNotification.isEnabled = true
            }
        }
    }
}
