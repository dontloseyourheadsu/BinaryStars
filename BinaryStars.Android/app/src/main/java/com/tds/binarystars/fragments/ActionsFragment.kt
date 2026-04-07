package com.tds.binarystars.fragments

import android.annotation.SuppressLint
import android.util.Log
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
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
import kotlinx.coroutines.Job
import java.util.UUID

class ActionsFragment : Fragment(), MessagingEventListener {

    private companion object {
        private const val POLL_INTERVAL_MS = 10_000L
        private const val LOG_TAG = "BinaryStarsActions"
        private const val RESULT_POLL_TIMEOUT_MS = 45_000L
    }

    private enum class ActionMode {
        Base,
        OpenApps,
        CloseApps,
    }

    private var selectedDevice: Device? = null
    private var pendingCorrelationId: String? = null
    private var pendingActionType: String? = null
    private var pendingActionStartedAtMs: Long? = null
    private var actionMode: ActionMode = ActionMode.Base
    private val gson = Gson()
    private var launchableApps: List<LaunchableAppItemDto> = emptyList()
    private var runningApps: List<RunningAppItemDto> = emptyList()
    private var openAppsQuery: String = ""
    private var closeAppsQuery: String = ""
    private var actionResultPollJob: Job? = null
    private var actionPendingUiJob: Job? = null

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

        val openAppsSearch = view.findViewById<EditText>(R.id.etOpenAppsSearch)
        openAppsSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                openAppsQuery = s?.toString().orEmpty()
                updateOpenAppsAdapter()
            }
        })

        val closeAppsSearch = view.findViewById<EditText>(R.id.etCloseAppsSearch)
        closeAppsSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                closeAppsQuery = s?.toString().orEmpty()
                updateCloseAppsAdapter()
            }
        })

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
        actionResultPollJob?.cancel()
        actionResultPollJob = null
        actionPendingUiJob?.cancel()
        actionPendingUiJob = null
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

        if (!MessagingSocketManager.isConnected()) {
            Toast.makeText(requireContext(), "Realtime channel is disconnected", Toast.LENGTH_SHORT).show()
            return
        }

        val senderId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        val correlationId = UUID.randomUUID().toString()
        val actionTimeoutMs = getActionTimeoutMs(actionType)
        pendingCorrelationId = correlationId
        pendingActionType = actionType
        pendingActionStartedAtMs = System.currentTimeMillis()
        Log.i(
            LOG_TAG,
            "Action send requested: actionType=$actionType correlationId=$correlationId target=${target.id} timeoutMs=$actionTimeoutMs"
        )
        showPendingActionStatus(actionType, actionTimeoutMs)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val request = SendActionRequestDto(
                    senderDeviceId = senderId,
                    targetDeviceId = target.id,
                    actionType = actionType,
                    payloadJson = payloadJson,
                    correlationId = correlationId
                )
                val response = ApiClient.apiService.sendAction(request)
                if (response.isSuccessful) {
                    Log.i(LOG_TAG, "Action API send accepted: actionType=$actionType correlationId=$correlationId target=${target.id}")
                    startActionResultTimeoutWatch(correlationId, actionType, actionTimeoutMs)
                    Toast.makeText(requireContext(), "Action sent", Toast.LENGTH_SHORT).show()
                } else {
                    pendingCorrelationId = null
                    pendingActionType = null
                    pendingActionStartedAtMs = null
                    clearPendingActionStatus()
                    val body = response.errorBody()?.string()
                    Log.w(LOG_TAG, "Action API send rejected: status=${response.code()} actionType=$actionType correlationId=$correlationId body=$body")
                    Toast.makeText(requireContext(), "Action rejected (${response.code()})", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                pendingCorrelationId = null
                pendingActionType = null
                pendingActionStartedAtMs = null
                clearPendingActionStatus()
                Log.e(LOG_TAG, "Action API send failed: actionType=$actionType correlationId=$correlationId", e)
                Toast.makeText(requireContext(), "Failed to send action", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startActionResultTimeoutWatch(correlationId: String, actionType: String, timeoutMs: Long) {
        actionResultPollJob?.cancel()
        actionResultPollJob = null
        actionResultPollJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(timeoutMs)

            if (pendingCorrelationId == correlationId) {
                pendingCorrelationId = null
                pendingActionType = null
                pendingActionStartedAtMs = null
                showPendingActionTimeout(actionType, timeoutMs)
                Toast.makeText(requireContext(), "Action response timed out", Toast.LENGTH_SHORT).show()
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
        val groupedPids = if (app.pids.isNotEmpty()) app.pids else listOf(if (app.mainPid > 0) app.mainPid else app.pid)
        val payload = gson.toJson(mapOf("pid" to (if (app.mainPid > 0) app.mainPid else app.pid), "pids" to groupedPids, "force" to false))
        sendAction("close_app", payload)
    }

    override fun onActionResult(result: DeviceActionResultDto) {
        if (!isAdded || view == null) {
            return
        }

        val elapsedMs = pendingActionStartedAtMs?.let { System.currentTimeMillis() - it }
        Log.i(
            LOG_TAG,
            "Action result received in fragment: actionType=${result.actionType} status=${result.status} correlationId=${result.correlationId} pendingCorrelation=$pendingCorrelationId pendingActionType=$pendingActionType elapsedMs=${elapsedMs ?: -1}"
        )

        if (pendingCorrelationId != null && result.correlationId == pendingCorrelationId) {
            actionResultPollJob?.cancel()
            actionResultPollJob = null
            clearPendingActionStatus()
        }

        handleActionResult(result)
    }

    override fun onChatUpdated(deviceId: String) {}

    override fun onDeviceRemoved(deviceId: String, isSelf: Boolean) {}

    override fun onConnectionStateChanged(isConnected: Boolean) {}

    override fun onDevicePresenceChanged(deviceId: String, isOnline: Boolean, lastSeen: String) {}

    private fun handleActionResult(result: DeviceActionResultDto) {
        if (pendingCorrelationId != null && result.correlationId != pendingCorrelationId) {
            val sameActionType = pendingActionType != null && result.actionType.equals(pendingActionType, ignoreCase = true)
            if (!sameActionType) {
                return
            }

            Log.w(
                LOG_TAG,
                "Accepting action result by actionType fallback: pendingCorrelation=$pendingCorrelationId resultCorrelation=${result.correlationId} actionType=${result.actionType}"
            )
        }

        if (result.actionType == "list_installed_apps" && result.status.equals("partial", ignoreCase = true)) {
            val payload = result.payloadJson ?: "[]"
            val listType = object : TypeToken<List<LaunchableAppItemDto>>() {}.type
            val partialApps: List<LaunchableAppItemDto> = try {
                gson.fromJson(payload, listType)
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed parsing partial installed apps payload: correlationId=${result.correlationId}", e)
                emptyList()
            }

            if (partialApps.isNotEmpty()) {
                val merged = (launchableApps + partialApps)
                    .distinctBy { it.exec }
                    .sortedBy { it.name.lowercase() }
                renderLaunchableApps(merged)
            }

            return
        }

        if (!result.status.equals("success", ignoreCase = true)) {
            Toast.makeText(
                requireContext(),
                result.error ?: "Action failed: not possible on target system",
                Toast.LENGTH_SHORT
            ).show()
            pendingCorrelationId = null
            pendingActionType = null
            pendingActionStartedAtMs = null
            clearPendingActionStatus()
            return
        }

        if (result.actionType == "list_installed_apps") {
            val payload = result.payloadJson ?: "[]"
            val listType = object : TypeToken<List<LaunchableAppItemDto>>() {}.type
            val apps: List<LaunchableAppItemDto> = try {
                gson.fromJson(payload, listType)
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed parsing installed apps payload: correlationId=${result.correlationId}", e)
                emptyList()
            }
            Log.i(LOG_TAG, "Parsed installed apps result count=${apps.size} correlationId=${result.correlationId}")
            renderLaunchableApps(apps)
            pendingCorrelationId = null
            pendingActionType = null
            pendingActionStartedAtMs = null
            clearPendingActionStatus()
            return
        }

        if (result.actionType == "list_running_apps") {
            val payload = result.payloadJson ?: "[]"
            val listType = object : TypeToken<List<RunningAppItemDto>>() {}.type
            val apps: List<RunningAppItemDto> = try {
                gson.fromJson(payload, listType)
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed parsing running apps payload: correlationId=${result.correlationId}", e)
                emptyList()
            }
            renderRunningApps(apps)
            pendingCorrelationId = null
            pendingActionType = null
            pendingActionStartedAtMs = null
            clearPendingActionStatus()
            return
        }

        if (result.actionType == "launch_app" || result.actionType == "close_app") {
            Toast.makeText(requireContext(), "Action completed", Toast.LENGTH_SHORT).show()
            pendingCorrelationId = null
            pendingActionType = null
            pendingActionStartedAtMs = null
            clearPendingActionStatus()
        }
    }

    private fun getActionTimeoutMs(actionType: String): Long {
        return when (actionType) {
            "list_installed_apps", "launch_app" -> 120_000L
            else -> RESULT_POLL_TIMEOUT_MS
        }
    }

    private fun showPendingActionStatus(actionType: String, timeoutMs: Long) {
        val root = view ?: return
        val statusContainer = root.findViewById<View>(R.id.viewPendingActionStatus)
        val spinner = root.findViewById<ProgressBar>(R.id.pbActionPending)
        val statusText = root.findViewById<TextView>(R.id.tvPendingActionStatus)
        val timeoutText = root.findViewById<TextView>(R.id.tvPendingActionTimeout)
        val actionLabel = actionType.replace('_', ' ')

        statusContainer.visibility = View.VISIBLE
        spinner.visibility = View.VISIBLE
        timeoutText.visibility = View.GONE
        timeoutText.text = ""

        actionPendingUiJob?.cancel()
        val countdownStartedAt = System.currentTimeMillis()
        actionPendingUiJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive && pendingActionType == actionType && pendingCorrelationId != null) {
                val elapsedMs = System.currentTimeMillis() - countdownStartedAt
                val remainingMs = timeoutMs - elapsedMs
                val remainingSeconds = if (remainingMs <= 0L) 0L else (remainingMs + 999L) / 1000L
                statusText.text = "Waiting for $actionLabel result... ${remainingSeconds}s left"
                delay(1_000L)
            }
        }
    }

    private fun clearPendingActionStatus() {
        val root = view ?: return
        actionPendingUiJob?.cancel()
        actionPendingUiJob = null
        root.findViewById<View>(R.id.viewPendingActionStatus).visibility = View.GONE
        root.findViewById<ProgressBar>(R.id.pbActionPending).visibility = View.GONE
        root.findViewById<TextView>(R.id.tvPendingActionStatus).text = ""
        root.findViewById<TextView>(R.id.tvPendingActionTimeout).visibility = View.GONE
        root.findViewById<TextView>(R.id.tvPendingActionTimeout).text = ""
    }

    private fun showPendingActionTimeout(actionType: String, timeoutMs: Long) {
        val root = view ?: return
        clearPendingActionStatus()
        val timeoutText = root.findViewById<TextView>(R.id.tvPendingActionTimeout)
        val actionLabel = actionType.replace('_', ' ')
        timeoutText.text = "$actionLabel timed out after ${timeoutMs / 1000L}s. You can retry this action."
        timeoutText.visibility = View.VISIBLE
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
        launchableApps = apps
        Log.i(LOG_TAG, "Rendering launchable apps: count=${apps.size}")
        updateOpenAppsAdapter()
    }

    private fun updateOpenAppsAdapter() {
        val root = view ?: return
        val list = root.findViewById<RecyclerView>(R.id.listOpenApps)
        val filtered = filterLaunchableApps()
        val rows = filtered.map { app ->
            ActionAppRow(
                id = app.exec,
                title = app.name,
                subtitle = trimForSubtitle(app.exec)
            )
        }

        list.adapter = ActionAppsAdapter(rows, "Open") { row ->
            val app = launchableApps.firstOrNull { it.exec == row.id } ?: return@ActionAppsAdapter
            openApp(app)
        }
    }

    private fun renderRunningApps(apps: List<RunningAppItemDto>) {
        runningApps = apps
        updateCloseAppsAdapter()
    }

    private fun updateCloseAppsAdapter() {
        val root = view ?: return
        val list = root.findViewById<RecyclerView>(R.id.listCloseApps)
        val filtered = filterRunningApps()
        val rows = filtered.map { app ->
            val commandPreview = app.commandLine.ifBlank { app.exe }
            val mainPid = if (app.mainPid > 0) app.mainPid else app.pid
            ActionAppRow(
                id = mainPid.toString(),
                title = app.name,
                subtitle = "Main PID $mainPid • ${app.processCount} processes • ${trimForSubtitle(commandPreview)}"
            )
        }

        list.adapter = ActionAppsAdapter(rows, "Close") { row ->
            val app = runningApps.firstOrNull {
                val mainPid = if (it.mainPid > 0) it.mainPid else it.pid
                mainPid.toString() == row.id
            } ?: return@ActionAppsAdapter
            closeApp(app)
        }
    }

    private fun filterLaunchableApps(): List<LaunchableAppItemDto> {
        val query = openAppsQuery.trim().lowercase()
        if (query.isEmpty()) {
            return launchableApps
        }

        return launchableApps.filter { app ->
            app.name.lowercase().contains(query) || app.exec.lowercase().contains(query)
        }
    }

    private fun filterRunningApps(): List<RunningAppItemDto> {
        val query = closeAppsQuery.trim().lowercase()
        if (query.isEmpty()) {
            return runningApps
        }

        return runningApps.filter { app ->
            val mainPid = if (app.mainPid > 0) app.mainPid else app.pid
            app.name.lowercase().contains(query) ||
                app.commandLine.lowercase().contains(query) ||
                app.exe.lowercase().contains(query) ||
                app.pid.toString().contains(query) ||
                mainPid.toString().contains(query) ||
                app.processCount.toString().contains(query)
        }
    }

    private fun trimForSubtitle(value: String, maxChars: Int = 80): String {
        val normalized = value.trim()
        if (normalized.length <= maxChars) {
            return normalized
        }

        return normalized.take(maxChars - 3) + "..."
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

                val currentDeviceId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)

                val linuxDevices = response.body()!!
                    .filter { it.type == DeviceTypeDto.Linux && it.isOnline && it.id != currentDeviceId }
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
