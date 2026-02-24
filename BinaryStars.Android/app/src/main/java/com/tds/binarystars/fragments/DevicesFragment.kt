package com.tds.binarystars.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import com.tds.binarystars.R
import com.tds.binarystars.adapter.DevicesAdapter
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.DeviceTypeDto
import com.tds.binarystars.model.Device
import com.tds.binarystars.model.DeviceType
import kotlinx.coroutines.launch
import com.tds.binarystars.util.NetworkUtils

import android.provider.Settings
import android.annotation.SuppressLint
import android.widget.Button
import android.widget.ImageView
import com.tds.binarystars.MainActivity
import com.tds.binarystars.messaging.MessagingEventListener
import com.tds.binarystars.messaging.MessagingSocketManager
import com.tds.binarystars.storage.DeviceCacheStorage
import com.tds.binarystars.storage.SettingsStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class DevicesFragment : Fragment(), MessagingEventListener {

    private companion object {
        private const val POLL_INTERVAL_MS = 10_000L
        private const val TABLET_MIN_WIDTH_DP = 600
    }

    private fun isTabletLayout(): Boolean {
        return resources.configuration.smallestScreenWidthDp >= TABLET_MIN_WIDTH_DP
    }

    /**
     * Inflates the devices list UI.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_devices, container, false)
    }

    @SuppressLint("HardwareIds")
    /**
     * Initializes the list and triggers device refresh.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.ivMenu)?.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }
        
        refreshDevices()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    delay(POLL_INTERVAL_MS)
                    refreshDevices()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        MessagingSocketManager.addListener(this)
    }

    override fun onPause() {
        super.onPause()
        MessagingSocketManager.removeListener(this)
    }

    override fun onChatUpdated(deviceId: String) {
        // No-op.
    }

    override fun onDeviceRemoved(deviceId: String, isSelf: Boolean) {
        refreshDevices()
    }

    override fun onConnectionStateChanged(isConnected: Boolean) {
        // No-op.
    }

    override fun onDevicePresenceChanged(deviceId: String, isOnline: Boolean, lastSeen: String) {
        refreshDevices()
    }

    @SuppressLint("HardwareIds")
    /**
     * Loads devices, handles offline mode, and updates the UI.
     */
    fun refreshDevices() {
        val view = view ?: return
        val rvOnline = view.findViewById<RecyclerView>(R.id.rvOnlineDevices)
        val rvOffline = view.findViewById<RecyclerView>(R.id.rvOfflineDevices)
        val btnLinkDevice = view.findViewById<Button>(R.id.btnLinkDevice)
        val contentView = view.findViewById<View>(R.id.viewContent)
        val noConnectionView = view.findViewById<View>(R.id.viewNoConnection)
        val retryButton = view.findViewById<Button>(R.id.btnRetry)
        val tvHeaderSubtitle = view.findViewById<TextView>(R.id.tvHeaderSubtitle)

        val isTablet = isTabletLayout()
        rvOnline.layoutManager = if (isTablet) GridLayoutManager(context, 2) else LinearLayoutManager(context)
        rvOffline.layoutManager = if (isTablet) GridLayoutManager(context, 2) else LinearLayoutManager(context)

        val context = requireContext()
        val currentDeviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val currentDeviceName = android.os.Build.MODEL

        retryButton.setOnClickListener {
            refreshDevices()
        }

        if (!NetworkUtils.isOnline(requireContext())) {
            val cachedDevices = DeviceCacheStorage.getDevices()
            if (cachedDevices.isNotEmpty()) {
                applyDevicesToUi(
                    devices = cachedDevices,
                    currentDeviceId = currentDeviceId,
                    currentDeviceName = currentDeviceName,
                    rvOnline = rvOnline,
                    rvOffline = rvOffline,
                    btnLinkDevice = btnLinkDevice,
                    tvHeaderSubtitle = tvHeaderSubtitle,
                    fromCache = true
                )
                contentView.visibility = View.VISIBLE
                noConnectionView.visibility = View.GONE
            } else {
                contentView.visibility = View.GONE
                noConnectionView.visibility = View.VISIBLE
            }
            return
        }

        contentView.visibility = View.VISIBLE
        noConnectionView.visibility = View.GONE
        val telemetryEnabled = SettingsStorage.isDeviceTelemetryEnabled(true)

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getDevices()
                if (response.isSuccessful && response.body() != null) {
                    val dtos = response.body()!!
                    val devices = dtos.map { dto ->
                        val effectiveOnline = if (dto.id == currentDeviceId && !telemetryEnabled) {
                            false
                        } else {
                            dto.isOnline
                        }

                        Device(
                            id = dto.id,
                            name = dto.name,
                            type = when (dto.type) {
                                DeviceTypeDto.Linux -> DeviceType.LINUX
                                DeviceTypeDto.Android -> DeviceType.ANDROID
                            },
                            ipAddress = dto.ipAddress,
                            publicKey = dto.publicKey,
                            publicKeyAlgorithm = dto.publicKeyAlgorithm,
                            batteryLevel = dto.batteryLevel,
                            isOnline = effectiveOnline,
                            isAvailable = dto.isAvailable,
                            isSynced = dto.isSynced,
                            cpuLoadPercent = dto.cpuLoadPercent,
                            wifiUploadSpeed = dto.wifiUploadSpeed,
                            wifiDownloadSpeed = dto.wifiDownloadSpeed,
                            lastSeen = System.currentTimeMillis()
                        )
                    }

                    DeviceCacheStorage.saveDevices(devices)
                    applyDevicesToUi(
                        devices = devices,
                        currentDeviceId = currentDeviceId,
                        currentDeviceName = currentDeviceName,
                        rvOnline = rvOnline,
                        rvOffline = rvOffline,
                        btnLinkDevice = btnLinkDevice,
                        tvHeaderSubtitle = tvHeaderSubtitle,
                        fromCache = false
                    )
                } else {
                    Toast.makeText(context, "Failed to load devices: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                val cachedDevices = DeviceCacheStorage.getDevices()
                if (cachedDevices.isNotEmpty()) {
                    applyDevicesToUi(
                        devices = cachedDevices,
                        currentDeviceId = currentDeviceId,
                        currentDeviceName = currentDeviceName,
                        rvOnline = rvOnline,
                        rvOffline = rvOffline,
                        btnLinkDevice = btnLinkDevice,
                        tvHeaderSubtitle = tvHeaderSubtitle,
                        fromCache = true
                    )
                }
            }
        }
    }

    private fun applyDevicesToUi(
        devices: List<Device>,
        currentDeviceId: String,
        currentDeviceName: String,
        rvOnline: RecyclerView,
        rvOffline: RecyclerView,
        btnLinkDevice: Button,
        tvHeaderSubtitle: TextView?,
        fromCache: Boolean
    ) {
        val onlineDevices = devices.filter { it.isOnline }
        val offlineDevices = devices.filter { !it.isOnline }
        val isRegistered = devices.any { it.id == currentDeviceId }

        tvHeaderSubtitle?.text = if (fromCache) {
            "Offline mode · ${onlineDevices.size} devices online"
        } else {
            "${onlineDevices.size} devices online"
        }

        rvOnline.adapter = DevicesAdapter(onlineDevices) { device ->
            openDeviceDetail(device)
        }
        rvOffline.adapter = DevicesAdapter(offlineDevices) { device ->
            openDeviceDetail(device)
        }

        btnLinkDevice.text = if (isRegistered) "Unlink This Device" else "Link This Device"
        btnLinkDevice.visibility = View.VISIBLE
        btnLinkDevice.setOnClickListener {
            if (!NetworkUtils.isOnline(requireContext())) {
                Toast.makeText(requireContext(), "No connection available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isRegistered) {
                (activity as? MainActivity)?.unlinkDevice(currentDeviceId)
            } else {
                (activity as? MainActivity)?.registerDevice(currentDeviceId, currentDeviceName)
            }
        }
    }

    private fun openDeviceDetail(device: Device) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, DeviceDetailFragment.newInstance(device))
            .addToBackStack(null)
            .commit()
    }
}
