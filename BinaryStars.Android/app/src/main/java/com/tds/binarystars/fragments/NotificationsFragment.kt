package com.tds.binarystars.fragments

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tds.binarystars.MainActivity
import com.tds.binarystars.R
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.CreateNotificationScheduleRequestDto
import com.tds.binarystars.api.SendNotificationRequestDto
import com.tds.binarystars.storage.DeviceCacheStorage
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

class NotificationsFragment : Fragment() {
    private companion object {
        private const val LOG_TAG = "BinaryStarsNotifications"
    }

    private lateinit var spinnerTargetDevice: Spinner
    private lateinit var etNotificationTitle: EditText
    private lateinit var etNotificationBody: EditText
    private lateinit var cbScheduleEnabled: CheckBox
    private lateinit var tvScheduledAt: TextView
    private lateinit var etRepeatMinutes: EditText
    private lateinit var btnPickScheduledAt: Button
    private lateinit var btnClearScheduledAt: Button
    private lateinit var btnSendNotification: Button
    private lateinit var btnSaveSchedule: Button

    private var deviceMap = mutableMapOf<String, String>()
    private var selectedScheduledAtUtc: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        view.findViewById<ImageView>(R.id.ivMenu)?.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        spinnerTargetDevice = view.findViewById(R.id.spinnerTargetDevice)
        etNotificationTitle = view.findViewById(R.id.etNotificationTitle)
        etNotificationBody = view.findViewById(R.id.etNotificationBody)
        cbScheduleEnabled = view.findViewById(R.id.cbScheduleEnabled)
        tvScheduledAt = view.findViewById(R.id.tvScheduledAt)
        etRepeatMinutes = view.findViewById(R.id.etRepeatMinutes)
        btnPickScheduledAt = view.findViewById(R.id.btnPickScheduledAt)
        btnClearScheduledAt = view.findViewById(R.id.btnClearScheduledAt)
        btnSendNotification = view.findViewById(R.id.btnSendNotification)
        btnSaveSchedule = view.findViewById(R.id.btnSaveNotificationSchedule)

        cbScheduleEnabled.isChecked = true
        updateScheduledAtLabel()

        loadDevices()

        btnSendNotification.setOnClickListener {
            sendNotification()
        }

        btnPickScheduledAt.setOnClickListener {
            pickScheduledDateTime()
        }

        btnClearScheduledAt.setOnClickListener {
            selectedScheduledAtUtc = null
            updateScheduledAtLabel()
        }

        btnSaveSchedule.setOnClickListener {
            saveSchedule()
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
                Log.i(LOG_TAG, "Sending immediate notification target=$targetDeviceId titleLength=${title.length}")
                val request = SendNotificationRequestDto(
                    senderDeviceId = senderDeviceId,
                    targetDeviceId = targetDeviceId,
                    title = title,
                    body = body
                )
                
                val response = ApiClient.apiService.sendNotification(request)
                if (response.isSuccessful) {
                    Log.i(LOG_TAG, "Immediate notification accepted target=$targetDeviceId")
                    Toast.makeText(context, "Notification sent", Toast.LENGTH_SHORT).show()
                    etNotificationTitle.text.clear()
                    etNotificationBody.text.clear()
                } else {
                    Log.w(LOG_TAG, "Immediate notification rejected status=${response.code()} target=$targetDeviceId")
                    Toast.makeText(context, "Failed to send notification", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Immediate notification send error=${e.message}")
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnSendNotification.isEnabled = true
            }
        }
    }

    @SuppressLint("HardwareIds")
    private fun saveSchedule() {
        val context = context ?: return
        val title = etNotificationTitle.text.toString().trim()
        val body = etNotificationBody.text.toString().trim()
        val selectedName = spinnerTargetDevice.selectedItem as? String
        val repeatRaw = etRepeatMinutes.text.toString().trim()
        val hasRepeat = repeatRaw.isNotEmpty()
        val hasScheduledAt = !selectedScheduledAtUtc.isNullOrBlank()

        if (title.isEmpty() || body.isEmpty() || selectedName == null) {
            Toast.makeText(context, "Please provide title, body, and select a device", Toast.LENGTH_SHORT).show()
            return
        }

        if ((hasRepeat && hasScheduledAt) || (!hasRepeat && !hasScheduledAt)) {
            Toast.makeText(context, "Choose either one-time date/time or repeat minutes", Toast.LENGTH_SHORT).show()
            return
        }

        val repeatMinutes = if (hasRepeat) repeatRaw.toIntOrNull() else null
        if (hasRepeat && (repeatMinutes == null || repeatMinutes <= 0)) {
            Toast.makeText(context, "Repeat minutes must be a positive number", Toast.LENGTH_SHORT).show()
            return
        }

        val targetDeviceId = deviceMap[selectedName] ?: return
        val sourceDeviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        btnSaveSchedule.isEnabled = false

        lifecycleScope.launch {
            try {
                val request = CreateNotificationScheduleRequestDto(
                    sourceDeviceId = sourceDeviceId,
                    targetDeviceId = targetDeviceId,
                    title = title,
                    body = body,
                    isEnabled = cbScheduleEnabled.isChecked,
                    scheduledForUtc = if (hasScheduledAt) selectedScheduledAtUtc else null,
                    repeatMinutes = repeatMinutes
                )

                Log.i(
                    LOG_TAG,
                    "Saving schedule target=$targetDeviceId enabled=${request.isEnabled} scheduledForUtc=${request.scheduledForUtc} repeatMinutes=${request.repeatMinutes}"
                )
                val response = ApiClient.apiService.createNotificationSchedule(request)
                if (response.isSuccessful) {
                    Toast.makeText(context, "Schedule saved", Toast.LENGTH_SHORT).show()
                    etRepeatMinutes.text.clear()
                    selectedScheduledAtUtc = null
                    updateScheduledAtLabel()
                } else {
                    Log.w(LOG_TAG, "Schedule save rejected status=${response.code()} target=$targetDeviceId")
                    Toast.makeText(context, "Failed to save schedule", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Schedule save error=${e.message}")
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnSaveSchedule.isEnabled = true
            }
        }
    }

    private fun pickScheduledDateTime() {
        val context = context ?: return
        val now = Calendar.getInstance()

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        val localDateTime = LocalDateTime.of(year, month + 1, dayOfMonth, hourOfDay, minute)
                        val instant = localDateTime.atZone(ZoneId.systemDefault()).toInstant()
                        selectedScheduledAtUtc = instant.toString()
                        updateScheduledAtLabel()
                    },
                    now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE),
                    true
                ).show()
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateScheduledAtLabel() {
        val raw = selectedScheduledAtUtc
        if (raw.isNullOrBlank()) {
            tvScheduledAt.text = "Not set"
            return
        }

        val formatted = try {
            val local = Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDateTime()
            local.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        } catch (_: Exception) {
            raw
        }

        tvScheduledAt.text = formatted
    }
}
