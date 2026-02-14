package com.tds.binarystars.fragments

import android.app.ActivityManager
import android.content.Context
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tds.binarystars.R
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.UpdateDeviceTelemetryRequest
import com.tds.binarystars.model.Device
import com.tds.binarystars.model.DeviceType
import com.tds.binarystars.storage.SettingsStorage
import com.tds.binarystars.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile
import kotlin.math.roundToInt

class DeviceDetailFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_device_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val name = arguments?.getString(ARG_NAME).orEmpty()
        val type = arguments?.getString(ARG_TYPE).orEmpty()
        val ipAddress = arguments?.getString(ARG_IP_ADDRESS).orEmpty()
        val batteryLevel = arguments?.getInt(ARG_BATTERY_LEVEL) ?: -1
        val isOnline = arguments?.getBoolean(ARG_IS_ONLINE) ?: false
        val isSynced = arguments?.getBoolean(ARG_IS_SYNCED) ?: false
        val isAvailable = arguments?.getBoolean(ARG_IS_AVAILABLE) ?: true
        val fallbackCpuLoadPercent = arguments?.getInt(ARG_CPU_LOAD_PERCENT)?.takeIf { it >= 0 }
        val uploadSpeed = arguments?.getString(ARG_UPLOAD_SPEED).orEmpty()
        val downloadSpeed = arguments?.getString(ARG_DOWNLOAD_SPEED).orEmpty()
        val deviceId = arguments?.getString(ARG_DEVICE_ID).orEmpty()
        val isAndroidDevice = type.equals("Android", ignoreCase = true)
        val isCurrentDevice = deviceId == Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)

        val tvCpu = view.findViewById<TextView>(R.id.tvCpu)
        val tvRam = view.findViewById<TextView>(R.id.tvRam)
        val tvUpSpeed = view.findViewById<TextView>(R.id.tvUpSpeed)
        val tvDownSpeed = view.findViewById<TextView>(R.id.tvDownSpeed)
        val tvConnection = view.findViewById<TextView>(R.id.tvUptime)
        val tvBattery = view.findViewById<TextView>(R.id.tvBattery)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvSubtitle)
        val toggleAvailability = view.findViewById<Switch>(R.id.toggleShare)

        view.findViewById<TextView>(R.id.tvTitle).text = name.ifBlank { "Device Detail" }
        tvSubtitle.text = listOf(type, ipAddress).filter { it.isNotBlank() }.joinToString(" • ")
        view.findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

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
                tvConnection.text = if (!checked) "Unavailable" else if (isOnline) "Online" else "Offline"
                if (!checked) {
                    tvCpu.text = "Not available"
                    tvRam.text = "Not available"
                    tvUpSpeed.text = "Not available"
                    tvDownSpeed.text = "Not available"
                    tvBattery.text = "Not available"

                    pushTelemetryUpdate(
                        deviceId = deviceId,
                        batteryLevel = 0,
                        cpuLoadPercent = null,
                        isOnline = false,
                        isAvailable = false,
                        isSynced = isSynced,
                        wifiUploadSpeed = "Not available",
                        wifiDownloadSpeed = "Not available"
                    )
                } else {
                    populateDeviceStats(
                        isCurrentDevice = true,
                        deviceId = deviceId,
                        isAndroidDevice = isAndroidDevice,
                        fallbackCpuLoadPercent = fallbackCpuLoadPercent,
                        fallbackBatteryLevel = batteryLevel,
                        fallbackUploadSpeed = uploadSpeed,
                        fallbackDownloadSpeed = downloadSpeed,
                        isOnline = isOnline,
                        isSynced = isSynced,
                        telemetryEnabled = true,
                        tvCpu = tvCpu,
                        tvRam = tvRam,
                        tvUpSpeed = tvUpSpeed,
                        tvDownSpeed = tvDownSpeed,
                        tvConnection = tvConnection,
                        tvBattery = tvBattery
                    )
                }
            }
        }

        populateDeviceStats(
            isCurrentDevice = isCurrentDevice,
            deviceId = deviceId,
            isAndroidDevice = isAndroidDevice,
            fallbackCpuLoadPercent = fallbackCpuLoadPercent,
            fallbackBatteryLevel = batteryLevel,
            fallbackUploadSpeed = uploadSpeed,
            fallbackDownloadSpeed = downloadSpeed,
            isOnline = isOnline,
            isSynced = isSynced,
            telemetryEnabled = telemetryEnabled,
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
        fallbackBatteryLevel: Int,
        fallbackUploadSpeed: String,
        fallbackDownloadSpeed: String,
        isOnline: Boolean,
        isSynced: Boolean,
        telemetryEnabled: Boolean,
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
        tvConnection.text = if (isSynced) "$connectionText • Synced" else "$connectionText • Not Synced"

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

                tvCpu.text = if (isAndroidDevice) {
                    "Not available"
                } else {
                    withContext(Dispatchers.IO) {
                        readCpuUsagePercent()?.let { "$it%" } ?: "Not available"
                    }
                }
                tvRam.text = ramPercent?.let { "$it%" } ?: "Not available"
                tvBattery.text = batteryText
                tvUpSpeed.text = upSpeedText
                tvDownSpeed.text = downSpeedText

                pushTelemetryUpdate(
                    deviceId = deviceId,
                    batteryLevel = batteryPercent ?: fallbackBatteryLevel.coerceAtLeast(0),
                    cpuLoadPercent = null,
                    isOnline = onlineNow,
                    isAvailable = telemetryEnabled,
                    isSynced = isSynced,
                    wifiUploadSpeed = upSpeedText,
                    wifiDownloadSpeed = downSpeedText
                )
            }
        } else {
            tvCpu.text = if (isAndroidDevice) {
                "Not available"
            } else {
                fallbackCpuLoadPercent?.let { "$it%" } ?: "Not available"
            }
            tvRam.text = "Not available"
            tvBattery.text = if (fallbackBatteryLevel >= 0) "$fallbackBatteryLevel%" else "Not available"
            tvUpSpeed.text = fallbackUploadSpeed.takeIf { it.isNotBlank() && it != "0 Mbps" } ?: "Not available"
            tvDownSpeed.text = fallbackDownloadSpeed.takeIf { it.isNotBlank() && it != "0 Mbps" } ?: "Not available"
        }
    }

    private fun pushTelemetryUpdate(
        deviceId: String,
        batteryLevel: Int,
        cpuLoadPercent: Int?,
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
        private const val ARG_DEVICE_ID = "arg_device_id"
        private const val ARG_NAME = "arg_name"
        private const val ARG_TYPE = "arg_type"
        private const val ARG_IP_ADDRESS = "arg_ip_address"
        private const val ARG_BATTERY_LEVEL = "arg_battery_level"
        private const val ARG_IS_ONLINE = "arg_is_online"
        private const val ARG_IS_AVAILABLE = "arg_is_available"
        private const val ARG_IS_SYNCED = "arg_is_synced"
        private const val ARG_CPU_LOAD_PERCENT = "arg_cpu_load_percent"
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
                    putInt(ARG_CPU_LOAD_PERCENT, device.cpuLoadPercent ?: -1)
                    putString(ARG_UPLOAD_SPEED, device.wifiUploadSpeed)
                    putString(ARG_DOWNLOAD_SPEED, device.wifiDownloadSpeed)
                }
            }
        }
    }
}
