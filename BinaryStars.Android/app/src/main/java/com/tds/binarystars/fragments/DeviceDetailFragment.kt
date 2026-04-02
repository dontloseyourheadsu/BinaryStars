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
    private var pendingClipboardCorrelationId: String? = null
    private var clipboardStatusView: TextView? = null
    private var clipboardRefreshButton: Button? = null
    private var clipboardListView: LinearLayout? = null

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
        var batteryLevel = arguments?.getInt(ARG_BATTERY_LEVEL) ?: -1
        var isOnline = arguments?.getBoolean(ARG_IS_ONLINE) ?: false
        var isSynced = arguments?.getBoolean(ARG_IS_SYNCED) ?: false
        var isAvailable = arguments?.getBoolean(ARG_IS_AVAILABLE) ?: true
        var fallbackCpuLoadPercent = arguments?.getInt(ARG_CPU_LOAD_PERCENT)?.takeIf { it >= 0 }
        var uploadSpeed = arguments?.getString(ARG_UPLOAD_SPEED).orEmpty()
        var downloadSpeed = arguments?.getString(ARG_DOWNLOAD_SPEED).orEmpty()
        val isBluetoothOnline = arguments?.getBoolean(ARG_IS_BLUETOOTH_ONLINE) ?: false
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
        val tvClipboardHistoryStatus = view.findViewById<TextView>(R.id.tvClipboardHistoryStatus)
        val btnRefreshClipboardHistory = view.findViewById<Button>(R.id.btnRefreshClipboardHistory)
        val clipboardHistoryList = view.findViewById<LinearLayout>(R.id.clipboardHistoryList)
        clipboardStatusView = tvClipboardHistoryStatus
        clipboardRefreshButton = btnRefreshClipboardHistory
        clipboardListView = clipboardHistoryList
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
                        isBluetoothOnline = isBluetoothOnline,
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
            isBluetoothOnline = isBluetoothOnline,
            tvCpu = tvCpu,
            tvRam = tvRam,
            tvUpSpeed = tvUpSpeed,
            tvDownSpeed = tvDownSpeed,
            tvConnection = tvConnection,
            tvBattery = tvBattery
        )

        renderClipboardSupport(
            isAndroidTarget = isAndroidDevice,
            isTargetOnline = isOnline,
            tvClipboardHistoryStatus = tvClipboardHistoryStatus,
            btnRefreshClipboardHistory = btnRefreshClipboardHistory,
            clipboardHistoryList = clipboardHistoryList
        )

        btnRefreshClipboardHistory.setOnClickListener {
            requestClipboardHistory(
                targetDeviceId = deviceId,
                isAndroidTarget = isAndroidDevice,
                isTargetOnline = isOnline,
                tvClipboardHistoryStatus = tvClipboardHistoryStatus,
                btnRefreshClipboardHistory = btnRefreshClipboardHistory,
                clipboardHistoryList = clipboardHistoryList
            )
        }

        if (!isAndroidDevice && isOnline) {
            requestClipboardHistory(
                targetDeviceId = deviceId,
                isAndroidTarget = false,
                isTargetOnline = true,
                tvClipboardHistoryStatus = tvClipboardHistoryStatus,
                btnRefreshClipboardHistory = btnRefreshClipboardHistory,
                clipboardHistoryList = clipboardHistoryList
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    delay(POLL_INTERVAL_MS)

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
                                    uploadSpeed = dto.wifiUploadSpeed
                                    downloadSpeed = dto.wifiDownloadSpeed

                                    val serverType = when (dto.type) {
                                        DeviceTypeDto.Linux -> "Linux"
                                        DeviceTypeDto.Android -> "Android"
                                    }
                                    tvSubtitle.text = listOf(serverType, dto.ipAddress).joinToString(" • ")
                                    btnUnlinkDevice.visibility = if (!isOnline || !isAvailable) View.VISIBLE else View.GONE
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }

                    val telemetryEnabled = if (isCurrentDevice) {
                        SettingsStorage.isDeviceTelemetryEnabled(true)
                    } else {
                        isAvailable
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
                        isBluetoothOnline = isBluetoothOnline,
                        tvCpu = tvCpu,
                        tvRam = tvRam,
                        tvUpSpeed = tvUpSpeed,
                        tvDownSpeed = tvDownSpeed,
                        tvConnection = tvConnection,
                        tvBattery = tvBattery
                    )

                    renderClipboardSupport(
                        isAndroidTarget = isAndroidDevice,
                        isTargetOnline = isOnline,
                        tvClipboardHistoryStatus = tvClipboardHistoryStatus,
                        btnRefreshClipboardHistory = btnRefreshClipboardHistory,
                        clipboardHistoryList = clipboardHistoryList
                    )

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

    override fun onDestroyView() {
        clipboardStatusView = null
        clipboardRefreshButton = null
        clipboardListView = null
        super.onDestroyView()
    }

    override fun onActionResult(result: DeviceActionResultDto) {
        if (!isAdded) {
            return
        }

        val expectedCorrelationId = pendingClipboardCorrelationId ?: return
        if (!result.actionType.equals("get_clipboard_history", ignoreCase = true) || result.correlationId != expectedCorrelationId) {
            return
        }

        val tvClipboardHistoryStatus = clipboardStatusView ?: return
        val btnRefreshClipboardHistory = clipboardRefreshButton ?: return
        val clipboardHistoryList = clipboardListView ?: return

        pendingClipboardCorrelationId = null
        btnRefreshClipboardHistory.isEnabled = true

        if (!result.status.equals("success", ignoreCase = true)) {
            tvClipboardHistoryStatus.text = result.error ?: "Failed to fetch clipboard history from target device."
            clipboardHistoryList.removeAllViews()
            return
        }

        val payload = result.payloadJson ?: "[]"
        val listType = object : TypeToken<List<String>>() {}.type
        val values: List<String> = try {
            gson.fromJson<List<String>>(payload, listType)
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        renderClipboardHistoryEntries(values, clipboardHistoryList)
        tvClipboardHistoryStatus.text = if (values.isEmpty()) {
            "No clipboard history available."
        } else {
            "${values.size} clipboard item(s) available (max 20)."
        }
    }

    private fun renderClipboardSupport(
        isAndroidTarget: Boolean,
        isTargetOnline: Boolean,
        tvClipboardHistoryStatus: TextView,
        btnRefreshClipboardHistory: Button,
        clipboardHistoryList: LinearLayout
    ) {
        if (isAndroidTarget) {
            btnRefreshClipboardHistory.isEnabled = false
            tvClipboardHistoryStatus.text =
                "Clipboard history for Android targets is not available due OS-level clipboard access restrictions."
            clipboardHistoryList.removeAllViews()
            return
        }

        if (!isTargetOnline) {
            btnRefreshClipboardHistory.isEnabled = false
            tvClipboardHistoryStatus.text = "Target device must be online to fetch clipboard history."
            clipboardHistoryList.removeAllViews()
            return
        }

        btnRefreshClipboardHistory.isEnabled = true
        if (pendingClipboardCorrelationId == null && tvClipboardHistoryStatus.text.isNullOrBlank()) {
            tvClipboardHistoryStatus.text =
                "Ready to fetch clipboard history (up to 20 entries; falls back to current clipboard when history providers are unavailable)."
        }
    }

    private fun requestClipboardHistory(
        targetDeviceId: String,
        isAndroidTarget: Boolean,
        isTargetOnline: Boolean,
        tvClipboardHistoryStatus: TextView,
        btnRefreshClipboardHistory: Button,
        clipboardHistoryList: LinearLayout
    ) {
        if (isAndroidTarget) {
            tvClipboardHistoryStatus.text =
                "Clipboard history for Android targets is not available due OS-level clipboard access restrictions."
            btnRefreshClipboardHistory.isEnabled = false
            clipboardHistoryList.removeAllViews()
            return
        }

        if (!isTargetOnline) {
            tvClipboardHistoryStatus.text = "Target device must be online to fetch clipboard history."
            btnRefreshClipboardHistory.isEnabled = false
            clipboardHistoryList.removeAllViews()
            return
        }

        if (!NetworkUtils.isOnline(requireContext())) {
            tvClipboardHistoryStatus.text = "No connection available."
            return
        }

        val senderId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        val correlationId = UUID.randomUUID().toString()
        pendingClipboardCorrelationId = correlationId
        tvClipboardHistoryStatus.text = "Requesting clipboard history…"
        clipboardHistoryList.removeAllViews()
        btnRefreshClipboardHistory.isEnabled = false

        lifecycleScope.launch {
            try {
                val sent = MessagingSocketManager.sendAction(
                    SendActionRequestDto(
                        senderDeviceId = senderId,
                        targetDeviceId = targetDeviceId,
                        actionType = "get_clipboard_history",
                        payloadJson = null,
                        correlationId = correlationId
                    )
                )

                if (!sent) {
                    pendingClipboardCorrelationId = null
                    tvClipboardHistoryStatus.text = "Realtime channel is not connected."
                    btnRefreshClipboardHistory.isEnabled = true
                } else {
                    tvClipboardHistoryStatus.text = "Waiting for target device response…"
                }
            } catch (_: Exception) {
                pendingClipboardCorrelationId = null
                tvClipboardHistoryStatus.text = "Failed to request clipboard history."
                btnRefreshClipboardHistory.isEnabled = true
            }
        }
    }

    private fun renderClipboardHistoryEntries(entries: List<String>, container: LinearLayout) {
        container.removeAllViews()

        entries.forEachIndexed { index, value ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8
                }
            }

            val textView = TextView(requireContext()).apply {
                text = value
                setTextColor(resources.getColor(R.color.carbon_text_main, null))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = 12
                }
                setPadding(0, 8, 0, 8)
                maxLines = 4
            }

            val copyButton = Button(requireContext()).apply {
                text = "Copy"
                isAllCaps = false
                setOnClickListener {
                    copyClipboardEntry(value)
                }
            }

            row.addView(textView)
            row.addView(copyButton)
            container.addView(row)
        }
    }

    private fun copyClipboardEntry(value: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            Toast.makeText(requireContext(), "Clipboard service unavailable", Toast.LENGTH_SHORT).show()
            return
        }

        clipboard.setPrimaryClip(ClipData.newPlainText("BinaryStars Clipboard", value))
        Toast.makeText(requireContext(), "Copied", Toast.LENGTH_SHORT).show()
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
                    putString(ARG_UPLOAD_SPEED, device.wifiUploadSpeed)
                    putString(ARG_DOWNLOAD_SPEED, device.wifiDownloadSpeed)
                }
            }
        }
    }
}
