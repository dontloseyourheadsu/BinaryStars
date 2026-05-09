package com.tds.binarystars.fragments

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tds.binarystars.MainActivity
import com.tds.binarystars.R
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.DeviceActionResultDto
import com.tds.binarystars.api.DeviceTypeDto
import com.tds.binarystars.api.SendActionRequestDto
import com.tds.binarystars.api.UpdateDeviceTelemetryRequest
import com.tds.binarystars.messaging.MessagingEventListener
import com.tds.binarystars.messaging.MessagingSocketManager
import com.tds.binarystars.model.Device
import com.tds.binarystars.model.DeviceType
import com.tds.binarystars.storage.SettingsStorage
import com.tds.binarystars.util.NetworkUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile
import java.util.UUID
import kotlin.math.roundToInt

class DeviceDetailFragment : Fragment(), MessagingEventListener {

    private val gson = Gson()
    
    private lateinit var deviceId: String
    private lateinit var name: String
    private lateinit var type: String
    private lateinit var ipAddress: String
    private var batteryLevel: Int = -1
    private var isOnline: Boolean = false
    private var isSynced: Boolean = false
    private var isAvailable: Boolean = true
    private var isBluetoothOnline: Boolean = false
    private var fallbackCpuLoadPercent: Int? = null
    private var fallbackMemoryLoadPercent: Int? = null
    private lateinit var uploadSpeed: String
    private lateinit var downloadSpeed: String
    private var isAndroidDevice: Boolean = false
    private var isCurrentDevice: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_device_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        deviceId = arguments?.getString(ARG_DEVICE_ID).orEmpty()
        name = arguments?.getString(ARG_NAME).orEmpty()
        type = arguments?.getString(ARG_TYPE).orEmpty()
        ipAddress = arguments?.getString(ARG_IP_ADDRESS).orEmpty()
        batteryLevel = arguments?.getInt(ARG_BATTERY_LEVEL) ?: -1
        isOnline = arguments?.getBoolean(ARG_IS_ONLINE) ?: false
        isSynced = arguments?.getBoolean(ARG_IS_SYNCED) ?: false
        isAvailable = arguments?.getBoolean(ARG_IS_AVAILABLE) ?: true
        fallbackCpuLoadPercent = arguments?.getInt(ARG_CPU_LOAD_PERCENT)?.takeIf { it >= 0 }
        fallbackMemoryLoadPercent = arguments?.getInt(ARG_MEMORY_LOAD_PERCENT)?.takeIf { it >= 0 }
        uploadSpeed = arguments?.getString(ARG_UPLOAD_SPEED).orEmpty()
        downloadSpeed = arguments?.getString(ARG_DOWNLOAD_SPEED).orEmpty()
        isBluetoothOnline = arguments?.getBoolean(ARG_IS_BLUETOOTH_ONLINE) ?: false
        isAndroidDevice = type.equals("Android", ignoreCase = true)
        isCurrentDevice = deviceId == Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)

        val tvSubtitle = view.findViewById<TextView>(R.id.tvSubtitle)
        val toggleAvailability = view.findViewById<Switch>(R.id.toggleShare)
        val btnUnlinkDevice = view.findViewById<Button>(R.id.btnUnlinkDevice)

        view.findViewById<TextView>(R.id.tvTitle).text = name.ifBlank { "Device Detail" }
        tvSubtitle.text = listOf(type, ipAddress).filter { it.isNotBlank() }.joinToString(" • ")
        view.findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        btnUnlinkDevice.setOnClickListener {
            (activity as? MainActivity)?.unlinkDevice(deviceId)
            parentFragmentManager.popBackStack()
        }

        btnUnlinkDevice.visibility = if (!isOnline || !isAvailable) View.VISIBLE else View.GONE

        if (!isCurrentDevice) {
            toggleAvailability.isEnabled = false
            toggleAvailability.alpha = 0.5f
        }

        val telemetryEnabled = if (isCurrentDevice) {
            SettingsStorage.isDeviceTelemetryEnabled(true)
        } else {
            isAvailable
        }

        toggleAvailability.isChecked = telemetryEnabled
        toggleAvailability.setOnCheckedChangeListener { _, checked ->
            if (isCurrentDevice) {
                SettingsStorage.setDeviceTelemetryEnabled(checked)
                updateUiState()
                if (!checked) {
                    pushTelemetryUpdate(
                        deviceId = deviceId,
                        batteryLevel = 0,
                        cpuLoadPercent = null,
                        memoryLoadPercent = null,
                        isOnline = false,
                        isAvailable = false,
                        isSynced = isSynced,
                        wifiUploadSpeed = "Not available",
                        wifiDownloadSpeed = "Not available"
                    )
                }
            }
        }

        updateUiState()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    if (!isCurrentDevice) {
                        refreshDeviceDetails()
                    } else {
                        updateUiState()
                    }
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        MessagingSocketManager.addListener(this)
    }

    override fun onStop() {
        MessagingSocketManager.removeListener(this)
        super.onStop()
    }

    override fun onActionResult(result: DeviceActionResultDto) {
        if (result.targetDeviceId == deviceId || result.senderDeviceId == deviceId) {
            viewLifecycleOwner.lifecycleScope.launch { refreshDeviceDetails() }
        }
    }

    override fun onChatUpdated(deviceId: String) {}

    override fun onDeviceRemoved(deviceId: String, isSelf: Boolean) {
        if (deviceId == this.deviceId) {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onConnectionStateChanged(isConnected: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch { refreshDeviceDetails() }
    }

    override fun onDevicePresenceChanged(deviceId: String, isOnline: Boolean, lastSeen: String) {
        if (deviceId == this.deviceId) {
            this.isOnline = isOnline
            viewLifecycleOwner.lifecycleScope.launch { refreshDeviceDetails() }
        }
    }

    private suspend fun refreshDeviceDetails() {
        if (!isCurrentDevice) {
            try {
                val response = ApiClient.apiService.getDevices()
                if (response.isSuccessful) {
                    val dto = response.body()?.firstOrNull { it.id == deviceId }
                    if (dto != null) {
                        batteryLevel = dto.batteryLevel
                        isOnline = dto.isOnline
                        isAvailable = dto.isAvailable
                        isSynced = dto.isSynced
                        fallbackCpuLoadPercent = dto.cpuLoadPercent
                        fallbackMemoryLoadPercent = dto.memoryLoadPercent
                        uploadSpeed = dto.wifiUploadSpeed
                        downloadSpeed = dto.wifiDownloadSpeed

                        val serverType = when (dto.type) {
                            DeviceTypeDto.Linux -> "Linux"
                            DeviceTypeDto.Android -> "Android"
                        }
                        view?.findViewById<TextView>(R.id.tvSubtitle)?.text = listOf(serverType, dto.ipAddress).joinToString(" • ")
                        view?.findViewById<Button>(R.id.btnUnlinkDevice)?.visibility = if (!isOnline || !isAvailable) View.VISIBLE else View.GONE
                    }
                }
            } catch (_: Exception) {}
        }
        updateUiState()
    }

    private fun updateUiState() {
        val view = view ?: return
        val tvCpu = view.findViewById<TextView>(R.id.tvCpu)
        val tvRam = view.findViewById<TextView>(R.id.tvRam)
        val tvUpSpeed = view.findViewById<TextView>(R.id.tvUpSpeed)
        val tvDownSpeed = view.findViewById<TextView>(R.id.tvDownSpeed)
        val tvConnection = view.findViewById<TextView>(R.id.tvUptime)
        val tvBattery = view.findViewById<TextView>(R.id.tvBattery)

        val telemetryEnabled = if (isCurrentDevice) SettingsStorage.isDeviceTelemetryEnabled(true) else isAvailable

        populateDeviceStats(
            isCurrentDevice = isCurrentDevice,
            deviceId = deviceId,
            isAndroidDevice = isAndroidDevice,
            fallbackCpuLoadPercent = fallbackCpuLoadPercent,
            fallbackMemoryLoadPercent = fallbackMemoryLoadPercent,
            fallbackBatteryLevel = batteryLevel,
            fallbackUploadSpeed = uploadSpeed,
            fallbackDownloadSpeed = downloadSpeed,
            isOnline = isOnline,
            isSynced = isSynced,
            telemetryEnabled = telemetryEnabled,
            isBluetoothOnline = isBluetoothOnline,
            tvCpu = tvCpu,
            tvRam = tvRam,
            tvUpSpeed = tvUpSpeed,
            tvDownSpeed = tvDownSpeed,
            tvConnection = tvConnection,
            tvBattery = tvBattery
        )
    }

    private fun populateDeviceStats(
        isCurrentDevice: Boolean,
        deviceId: String,
        isAndroidDevice: Boolean,
        fallbackCpuLoadPercent: Int?,
        fallbackMemoryLoadPercent: Int?,
        fallbackBatteryLevel: Int,
        fallbackUploadSpeed: String,
        fallbackDownloadSpeed: String,
        isOnline: Boolean,
        isSynced: Boolean,
        telemetryEnabled: Boolean,
        isBluetoothOnline: Boolean,
        tvCpu: TextView,
        tvRam: TextView,
        tvUpSpeed: TextView,
        tvDownSpeed: TextView,
        tvConnection: TextView,
        tvBattery: TextView
    ) {
        val connectionText = when {
            !telemetryEnabled -> "Unavailable"
            isOnline -> "Online"
            else -> "Offline"
        }
        val bluetoothText = if (isBluetoothOnline) " • Bluetooth online" else ""
        tvConnection.text = if (isSynced) {
            "$connectionText$bluetoothText • Synced"
        } else {
            "$connectionText$bluetoothText • Not Synced"
        }

        if (!telemetryEnabled) {
            tvCpu.text = "Not available"
            tvRam.text = "Not available"
            tvUpSpeed.text = "Not available"
            tvDownSpeed.text = "Not available"
            tvBattery.text = "Not available"
            return
        }

        if (isCurrentDevice) {
            lifecycleScope.launch {
                val ramPercent = readRamUsagePercent(requireContext())
                val batteryPercent = readBatteryPercent(requireContext())
                val speeds = withContext(Dispatchers.IO) { sampleNetworkSpeedKbps() }
                val onlineNow = NetworkUtils.isOnline(requireContext())
                val upSpeedText = speeds?.first?.let { "$it kbps" }
                    ?: fallbackUploadSpeed.takeIf { it.isNotBlank() && it != "0 Mbps" } ?: "Not available"
                val downSpeedText = speeds?.second?.let { "$it kbps" }
                    ?: fallbackDownloadSpeed.takeIf { it.isNotBlank() && it != "0 Mbps" } ?: "Not available"
                val batteryText = batteryPercent?.let { "$it%" }
                    ?: if (fallbackBatteryLevel >= 0) "$fallbackBatteryLevel%" else "Not available"

                val cpuPercent = withContext(Dispatchers.IO) {
                    readCpuUsagePercent()
                }
                tvCpu.text = cpuPercent?.let { "$it%" }
                    ?: fallbackCpuLoadPercent?.let { "$it%" }
                    ?: "Not available"
                tvRam.text = ramPercent?.let { "$it%" } ?: "Not available"
                tvBattery.text = batteryText
                tvUpSpeed.text = upSpeedText
                tvDownSpeed.text = downSpeedText

                pushTelemetryUpdate(
                    deviceId = deviceId,
                    batteryLevel = batteryPercent ?: fallbackBatteryLevel.coerceAtLeast(0),
                    cpuLoadPercent = cpuPercent,
                    memoryLoadPercent = ramPercent,
                    isOnline = onlineNow,
                    isAvailable = telemetryEnabled,
                    isSynced = isSynced,
                    wifiUploadSpeed = upSpeedText,
                    wifiDownloadSpeed = downSpeedText
                )
            }
        } else {
            tvCpu.text = fallbackCpuLoadPercent?.let { "$it%" } ?: "Not available"
            tvRam.text = fallbackMemoryLoadPercent?.let { "$it%" } ?: "Not available"
            tvBattery.text = if (fallbackBatteryLevel >= 0) "$fallbackBatteryLevel%" else "Not available"
            tvUpSpeed.text = fallbackUploadSpeed.takeIf { it.isNotBlank() && it != "0 Mbps" } ?: "Not available"
            tvDownSpeed.text = fallbackDownloadSpeed.takeIf { it.isNotBlank() && it != "0 Mbps" } ?: "Not available"
        }
    }

    private fun pushTelemetryUpdate(
        deviceId: String,
        batteryLevel: Int,
        cpuLoadPercent: Int?,
        memoryLoadPercent: Int?,
        isOnline: Boolean,
        isAvailable: Boolean,
        isSynced: Boolean,
        wifiUploadSpeed: String,
        wifiDownloadSpeed: String
    ) {
        lifecycleScope.launch {
            try {
                ApiClient.apiService.updateDeviceTelemetry(
                    deviceId,
                    UpdateDeviceTelemetryRequest(
                        batteryLevel = batteryLevel.coerceIn(0, 100),
                        cpuLoadPercent = cpuLoadPercent,
                        memoryLoadPercent = memoryLoadPercent,
                        isOnline = isOnline,
                        isAvailable = isAvailable,
                        isSynced = isSynced,
                        wifiUploadSpeed = wifiUploadSpeed,
                        wifiDownloadSpeed = wifiDownloadSpeed
                    )
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun readBatteryPercent(context: Context): Int? {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (level in 0..100) level else null
    }

    private fun readRamUsagePercent(context: Context): Int? {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        if (memoryInfo.totalMem <= 0L) return null
        val used = memoryInfo.totalMem - memoryInfo.availMem
        return ((used.toDouble() / memoryInfo.totalMem.toDouble()) * 100.0).roundToInt()
    }

    private suspend fun sampleNetworkSpeedKbps(): Pair<Int, Int>? {
        val rxStart = TrafficStats.getTotalRxBytes()
        val txStart = TrafficStats.getTotalTxBytes()
        if (rxStart == TrafficStats.UNSUPPORTED.toLong() || txStart == TrafficStats.UNSUPPORTED.toLong()) {
            return null
        }

        delay(1000)

        val rxEnd = TrafficStats.getTotalRxBytes()
        val txEnd = TrafficStats.getTotalTxBytes()
        if (rxEnd < rxStart || txEnd < txStart) return null

        val downKbps = (((rxEnd - rxStart) * 8.0) / 1000.0).roundToInt()
        val upKbps = (((txEnd - txStart) * 8.0) / 1000.0).roundToInt()
        return upKbps to downKbps
    }

    private fun readCpuUsagePercent(): Int? {
        val first = readCpuStat() ?: return null
        Thread.sleep(300)
        val second = readCpuStat() ?: return null

        val totalDelta = second.total - first.total
        val idleDelta = second.idle - first.idle
        if (totalDelta <= 0L) return null

        val busyRatio = 1.0 - (idleDelta.toDouble() / totalDelta.toDouble())
        return (busyRatio * 100.0).roundToInt().coerceIn(0, 100)
    }

    private fun readCpuStat(): CpuStat? {
        return try {
            RandomAccessFile("/proc/stat", "r").use { file ->
                val line = file.readLine() ?: return null
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size < 8 || parts[0] != "cpu") return null
                val user = parts[1].toLongOrNull() ?: return null
                val nice = parts[2].toLongOrNull() ?: return null
                val system = parts[3].toLongOrNull() ?: return null
                val idle = parts[4].toLongOrNull() ?: return null
                val iowait = parts[5].toLongOrNull() ?: 0L
                val irq = parts[6].toLongOrNull() ?: 0L
                val softirq = parts[7].toLongOrNull() ?: 0L
                val steal = parts.getOrNull(8)?.toLongOrNull() ?: 0L
                val total = user + nice + system + idle + iowait + irq + softirq + steal
                CpuStat(total = total, idle = idle + iowait)
            }
        } catch (_: Exception) {
            null
        }
    }

    private data class CpuStat(val total: Long, val idle: Long)

    companion object {
        private const val POLL_INTERVAL_MS = 10_000L
        private const val ARG_DEVICE_ID = "arg_device_id"
        private const val ARG_NAME = "arg_name"
        private const val ARG_TYPE = "arg_type"
        private const val ARG_IP_ADDRESS = "arg_ip_address"
        private const val ARG_BATTERY_LEVEL = "arg_battery_level"
        private const val ARG_IS_ONLINE = "arg_is_online"
        private const val ARG_IS_AVAILABLE = "arg_is_available"
        private const val ARG_IS_SYNCED = "arg_is_synced"
        private const val ARG_IS_BLUETOOTH_ONLINE = "arg_is_bluetooth_online"
        private const val ARG_CPU_LOAD_PERCENT = "arg_cpu_load_percent"
        private const val ARG_MEMORY_LOAD_PERCENT = "arg_memory_load_percent"
        private const val ARG_UPLOAD_SPEED = "arg_upload_speed"
        private const val ARG_DOWNLOAD_SPEED = "arg_download_speed"

        fun newInstance(device: Device): DeviceDetailFragment {
            return DeviceDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DEVICE_ID, device.id)
                    putString(ARG_NAME, device.name)
                    putString(
                        ARG_TYPE,
                        when (device.type) {
                            DeviceType.LINUX -> "Linux"
                            DeviceType.ANDROID -> "Android"
                        }
                    )
                    putString(ARG_IP_ADDRESS, device.ipAddress)
                    putInt(ARG_BATTERY_LEVEL, device.batteryLevel)
                    putBoolean(ARG_IS_ONLINE, device.isOnline)
                    putBoolean(ARG_IS_AVAILABLE, device.isAvailable)
                    putBoolean(ARG_IS_SYNCED, device.isSynced)
                    putBoolean(ARG_IS_BLUETOOTH_ONLINE, device.isBluetoothOnline)
                    putInt(ARG_CPU_LOAD_PERCENT, device.cpuLoadPercent ?: -1)
                    putInt(ARG_MEMORY_LOAD_PERCENT, device.memoryLoadPercent ?: -1)
                    putString(ARG_UPLOAD_SPEED, device.wifiUploadSpeed)
                    putString(ARG_DOWNLOAD_SPEED, device.wifiDownloadSpeed)
                }
            }
        }
    }
}
