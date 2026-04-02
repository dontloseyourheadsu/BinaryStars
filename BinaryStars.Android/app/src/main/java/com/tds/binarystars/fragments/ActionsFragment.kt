package com.tds.binarystars.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.MainActivity
import com.tds.binarystars.R
import com.tds.binarystars.adapter.ActionAppRow
import com.tds.binarystars.adapter.ActionAppsAdapter
import com.tds.binarystars.adapter.DevicesAdapter
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.DeviceActionResultDto
import com.tds.binarystars.api.DeviceTypeDto
import com.tds.binarystars.api.LaunchableAppItemDto
import com.tds.binarystars.api.RunningAppItemDto
import com.tds.binarystars.api.SendActionRequestDto
import com.tds.binarystars.model.Device
import com.tds.binarystars.model.DeviceType
import com.tds.binarystars.messaging.MessagingEventListener
import com.tds.binarystars.messaging.MessagingSocketManager
import com.tds.binarystars.util.NetworkUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class ActionsFragment : Fragment(), MessagingEventListener {

    private companion object {
        private const val POLL_INTERVAL_MS = 10_000L
    }

    private enum class ActionMode {
        Base,
        OpenApps,
        CloseApps,
    }

    private var selectedDevice: Device? = null
    private var pendingCorrelationId: String? = null
    private var actionMode: ActionMode = ActionMode.Base
    private val gson = Gson()
    private var launchableApps: List<LaunchableAppItemDto> = emptyList()
    private var runningApps: List<RunningAppItemDto> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_actions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.ivMenu)?.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        view.findViewById<Button>(R.id.btnBackToActionsList).setOnClickListener {
            selectedDevice = null
            renderDetail(null)
        }

        view.findViewById<Button>(R.id.btnBlockScreen).setOnClickListener {
            sendBlockScreenAction()
        }

        view.findViewById<Button>(R.id.btnShutdown).setOnClickListener {
            sendAction("shutdown")
        }

        view.findViewById<Button>(R.id.btnReset).setOnClickListener {
            sendAction("reboot")
        }

        view.findViewById<Button>(R.id.btnOpenApps).setOnClickListener {
            requestLaunchableApps()
        }

        view.findViewById<Button>(R.id.btnCloseApps).setOnClickListener {
            requestRunningApps()
        }

        refreshLinuxDevices()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    delay(POLL_INTERVAL_MS)
                    refreshLinuxDevices()
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

    @SuppressLint("HardwareIds")
    private fun sendBlockScreenAction() {
        sendAction("block_screen")
    }

    @SuppressLint("HardwareIds")
    private fun sendAction(actionType: String, payloadJson: String? = null) {
        val target = selectedDevice ?: return
        if (!NetworkUtils.isOnline(requireContext())) {
            Toast.makeText(requireContext(), "No connection available", Toast.LENGTH_SHORT).show()
            return
        }

        if (!target.isOnline) {
            Toast.makeText(requireContext(), "Target device must be online", Toast.LENGTH_SHORT).show()
            return
        }

        val senderId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        val correlationId = UUID.randomUUID().toString()
        pendingCorrelationId = correlationId

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val sent = MessagingSocketManager.sendAction(
                    SendActionRequestDto(
                        senderDeviceId = senderId,
                        targetDeviceId = target.id,
                        actionType = actionType,
                        payloadJson = payloadJson,
                        correlationId = correlationId
                    )
                )

                if (sent) {
                    Toast.makeText(requireContext(), "Action sent", Toast.LENGTH_SHORT).show()
                } else {
                    pendingCorrelationId = null
                    Toast.makeText(requireContext(), "Realtime channel is not connected", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                pendingCorrelationId = null
                Toast.makeText(requireContext(), "Failed to send action", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("HardwareIds")
    private fun requestLaunchableApps() {
        actionMode = ActionMode.OpenApps
        renderActionMode()
        sendAction("list_installed_apps")
    }

    @SuppressLint("HardwareIds")
    private fun requestRunningApps() {
        actionMode = ActionMode.CloseApps
        renderActionMode()
        sendAction("list_running_apps")
    }

    @SuppressLint("HardwareIds")
    private fun openApp(app: LaunchableAppItemDto) {
        val payload = gson.toJson(mapOf("exec" to app.exec))
        sendAction("launch_app", payload)
    }

    @SuppressLint("HardwareIds")
    private fun closeApp(app: RunningAppItemDto) {
        val payload = gson.toJson(mapOf("pid" to app.pid, "force" to false))
        sendAction("close_app", payload)
    }

    override fun onActionResult(result: DeviceActionResultDto) {
        if (!isAdded || view == null) {
            return
        }

        handleActionResult(result)
    }

    private fun handleActionResult(result: DeviceActionResultDto) {
        if (pendingCorrelationId != null && result.correlationId != pendingCorrelationId) {
            return
        }

        if (!result.status.equals("success", ignoreCase = true)) {
            Toast.makeText(
                requireContext(),
                result.error ?: "Action failed: not possible on target system",
                Toast.LENGTH_SHORT
            ).show()
            pendingCorrelationId = null
            return
        }

        if (result.actionType == "list_installed_apps") {
            val payload = result.payloadJson ?: "[]"
            val listType = object : TypeToken<List<LaunchableAppItemDto>>() {}.type
            val apps: List<LaunchableAppItemDto> = try {
                gson.fromJson(payload, listType)
            } catch (_: Exception) {
                emptyList()
            }
            renderLaunchableApps(apps)
            pendingCorrelationId = null
            return
        }

        if (result.actionType == "list_running_apps") {
            val payload = result.payloadJson ?: "[]"
            val listType = object : TypeToken<List<RunningAppItemDto>>() {}.type
            val apps: List<RunningAppItemDto> = try {
                gson.fromJson(payload, listType)
            } catch (_: Exception) {
                emptyList()
            }
            renderRunningApps(apps)
            pendingCorrelationId = null
            return
        }

        if (result.actionType == "launch_app" || result.actionType == "close_app") {
            Toast.makeText(requireContext(), "Action completed", Toast.LENGTH_SHORT).show()
            pendingCorrelationId = null
        }
    }

    private fun renderActionMode() {
        val root = view ?: return
        val openView = root.findViewById<View>(R.id.viewOpenApps)
        val closeView = root.findViewById<View>(R.id.viewCloseApps)
        val openList = root.findViewById<RecyclerView>(R.id.listOpenApps)
        val closeList = root.findViewById<RecyclerView>(R.id.listCloseApps)

        openList.layoutManager = LinearLayoutManager(requireContext())
        closeList.layoutManager = LinearLayoutManager(requireContext())

        openView.visibility = if (actionMode == ActionMode.OpenApps) View.VISIBLE else View.GONE
        closeView.visibility = if (actionMode == ActionMode.CloseApps) View.VISIBLE else View.GONE

        if (actionMode == ActionMode.OpenApps) {
            renderLaunchableApps(launchableApps)
        }

        if (actionMode == ActionMode.CloseApps) {
            renderRunningApps(runningApps)
        }
    }

    private fun renderLaunchableApps(apps: List<LaunchableAppItemDto>) {
        val root = view ?: return
        launchableApps = apps
        val list = root.findViewById<RecyclerView>(R.id.listOpenApps)
        val rows = apps.map { app ->
            ActionAppRow(
                id = app.exec,
                title = app.name,
                subtitle = app.exec
            )
        }

        list.adapter = ActionAppsAdapter(rows, "Open") { row ->
            val app = launchableApps.firstOrNull { it.exec == row.id } ?: return@ActionAppsAdapter
            openApp(app)
        }
    }

    private fun renderRunningApps(apps: List<RunningAppItemDto>) {
        val root = view ?: return
        runningApps = apps
        val list = root.findViewById<RecyclerView>(R.id.listCloseApps)
        val rows = apps.map { app ->
            ActionAppRow(
                id = app.pid.toString(),
                title = app.name,
                subtitle = "PID ${app.pid} • ${app.commandLine}"
            )
        }

        list.adapter = ActionAppsAdapter(rows, "Close") { row ->
            val app = runningApps.firstOrNull { it.pid.toString() == row.id } ?: return@ActionAppsAdapter
            closeApp(app)
        }
    }

    private fun refreshLinuxDevices() {
        val root = view ?: return
        val list = root.findViewById<RecyclerView>(R.id.rvActionLinuxDevices)
        list.layoutManager = LinearLayoutManager(requireContext())

        if (!NetworkUtils.isOnline(requireContext())) {
            root.findViewById<TextView>(R.id.tvActionsSubtitle).text = "Offline mode"
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getDevices()
                if (!response.isSuccessful || response.body() == null) {
                    return@launch
                }

                val linuxDevices = response.body()!!
                    .filter { it.type == DeviceTypeDto.Linux && it.isOnline }
                    .map { dto ->
                        Device(
                            id = dto.id,
                            name = dto.name,
                            type = DeviceType.LINUX,
                            ipAddress = dto.ipAddress,
                            publicKey = dto.publicKey,
                            publicKeyAlgorithm = dto.publicKeyAlgorithm,
                            batteryLevel = dto.batteryLevel,
                            isOnline = dto.isOnline,
                            isAvailable = dto.isAvailable,
                            isSynced = dto.isSynced,
                            cpuLoadPercent = dto.cpuLoadPercent,
                            wifiUploadSpeed = dto.wifiUploadSpeed,
                            wifiDownloadSpeed = dto.wifiDownloadSpeed,
                            lastSeen = System.currentTimeMillis()
                        )
                    }

                root.findViewById<TextView>(R.id.tvActionsSubtitle).text = "Online Linux devices — ${linuxDevices.size}"

                list.adapter = DevicesAdapter(
                    devices = linuxDevices,
                    onDeviceClick = { device ->
                        selectedDevice = device
                        renderDetail(device)
                    }
                )

                if (selectedDevice != null) {
                    val refreshed = linuxDevices.firstOrNull { it.id == selectedDevice?.id }
                    selectedDevice = refreshed
                    renderDetail(refreshed)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun renderDetail(device: Device?) {
        val root = view ?: return
        val listContainer = root.findViewById<View>(R.id.viewActionsList)
        val detailContainer = root.findViewById<View>(R.id.viewActionDetail)
        val blockButton = root.findViewById<Button>(R.id.btnBlockScreen)
        val shutdownButton = root.findViewById<Button>(R.id.btnShutdown)
        val resetButton = root.findViewById<Button>(R.id.btnReset)
        val openAppsButton = root.findViewById<Button>(R.id.btnOpenApps)
        val closeAppsButton = root.findViewById<Button>(R.id.btnCloseApps)

        if (device == null) {
            listContainer.visibility = View.VISIBLE
            detailContainer.visibility = View.GONE
            actionMode = ActionMode.Base
            renderActionMode()
            return
        }

        listContainer.visibility = View.GONE
        detailContainer.visibility = View.VISIBLE

        root.findViewById<TextView>(R.id.tvActionDetailName).text = device.name
        root.findViewById<TextView>(R.id.tvActionDetailMeta).text = "${device.id} • ${if (device.isOnline) "Online" else "Offline"}"
        root.findViewById<TextView>(R.id.tvActionSupportInfo).text =
            "Supported target: Linux desktops (GNOME/KDE/GTK-focused). Shutdown/reset request PolicyKit authorization on target when needed."

        blockButton.isEnabled = device.isOnline
        shutdownButton.isEnabled = device.isOnline
        resetButton.isEnabled = device.isOnline
        openAppsButton.isEnabled = device.isOnline
        closeAppsButton.isEnabled = device.isOnline
        renderActionMode()
    }
}
