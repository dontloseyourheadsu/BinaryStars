package com.tds.binarystars.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.MainActivity
import com.tds.binarystars.R
import com.tds.binarystars.adapter.DevicesAdapter
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.DeviceActionResultDto
import com.tds.binarystars.api.DeviceTypeDto
import com.tds.binarystars.api.LocationUpdateEventDto
import com.tds.binarystars.messaging.MessagingEventListener
import com.tds.binarystars.messaging.MessagingSocketManager
import com.tds.binarystars.model.Device
import com.tds.binarystars.model.DeviceType
import com.tds.binarystars.storage.DeviceCacheStorage
import com.tds.binarystars.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DevicesFragment : Fragment(), MessagingEventListener {
    private var tvHeaderSubtitle: TextView? = null
    private var tvOfflineHeader: TextView? = null
    private var rvOnline: RecyclerView? = null
    private var rvOffline: RecyclerView? = null
    private var btnLinkDevice: Button? = null
    private var viewNoConnection: View? = null
    private var viewContent: View? = null
    private var btnRetry: Button? = null

    private var allDevices: List<Device> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_devices, container, false)
    }

    @SuppressLint("HardwareIds")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<ImageView>(R.id.ivMenu)?.setOnClickListener { (activity as? MainActivity)?.openDrawer() }
        tvHeaderSubtitle = view.findViewById(R.id.tvHeaderSubtitle)
        tvOfflineHeader = view.findViewById(R.id.tvOfflineHeader)
        rvOnline = view.findViewById(R.id.rvOnlineDevices)
        rvOffline = view.findViewById(R.id.rvOfflineDevices)
        btnLinkDevice = view.findViewById(R.id.btnLinkDevice)
        viewNoConnection = view.findViewById(R.id.viewNoConnection)
        viewContent = view.findViewById(R.id.viewContent)
        btnRetry = view.findViewById(R.id.btnRetry)
        rvOnline?.layoutManager = LinearLayoutManager(requireContext())
        rvOffline?.layoutManager = LinearLayoutManager(requireContext())
        btnRetry?.setOnClickListener { refreshDevices() }
        refreshDevices()
    }

    override fun onStart() {
        super.onStart()
        MessagingSocketManager.addListener(this)
    }

    override fun onStop() {
        MessagingSocketManager.removeListener(this)
        super.onStop()
    }

    override fun onChatUpdated(deviceId: String) {}
    override fun onDeviceRemoved(deviceId: String, isSelf: Boolean) { refreshDevices() }
    override fun onConnectionStateChanged(isConnected: Boolean) { refreshDevices() }
    override fun onDevicePresenceChanged(deviceId: String, isOnline: Boolean, lastSeen: String) {
        val currentList = allDevices.toMutableList()
        val index = currentList.indexOfFirst { it.id == deviceId }
        if (index != -1) {
            currentList[index] = currentList[index].copy(isOnline = isOnline)
            allDevices = currentList
            val id = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
            val name = android.os.Build.MODEL
            updateUi(allDevices, fromCache = false, id, name)
        } else {
            refreshDevices()
        }
    }
    override fun onLocationUpdated(event: LocationUpdateEventDto) {}
    override fun onActionResult(result: DeviceActionResultDto) {}

    private fun refreshDevices() {
        viewLifecycleOwner.lifecycleScope.launch {
            val isOnline = NetworkUtils.isOnline(requireContext())
            val cached = withContext(Dispatchers.IO) { DeviceCacheStorage.getDevices() }
            val currentDeviceId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
            val currentDeviceName = android.os.Build.MODEL
            if (!isOnline && cached.isEmpty()) { setNoConnection(true); return@launch }
            setNoConnection(false)
            allDevices = cached
            updateUi(cached, fromCache = !isOnline, currentDeviceId, currentDeviceName)
            if (isOnline) {
                try {
                    val response = withContext(Dispatchers.IO) { ApiClient.apiService.getDevices() }
                    if (response.isSuccessful) {
                        val devices = response.body() ?: emptyList()
                        val domainDevices = devices.map { dto ->
                            Device(
                                id = dto.id, name = dto.name, type = if (dto.type == DeviceTypeDto.Linux) DeviceType.LINUX else DeviceType.ANDROID,
                                ipAddress = dto.ipAddress ?: "0.0.0.0", batteryLevel = dto.batteryLevel ?: 0,
                                isOnline = dto.isOnline, isSynced = true, wifiUploadSpeed = dto.wifiUploadSpeed ?: "0", 
                                wifiDownloadSpeed = dto.wifiDownloadSpeed ?: "0", isBluetoothOnline = false,
                                lastSeen = System.currentTimeMillis(), isAvailable = dto.isAvailable,
                                memoryLoadPercent = dto.memoryLoadPercent, cpuLoadPercent = dto.cpuLoadPercent,
                                publicKey = dto.publicKey, publicKeyAlgorithm = dto.publicKeyAlgorithm
                            )
                        }
                        withContext(Dispatchers.IO) { DeviceCacheStorage.saveDevices(domainDevices) }
                        allDevices = domainDevices
                        updateUi(domainDevices, fromCache = false, currentDeviceId, currentDeviceName)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun updateUi(devices: List<Device>, fromCache: Boolean, currentDeviceId: String, currentDeviceName: String) {
        val onlineDevices = devices.filter { it.isOnline }
        val offlineDevices = devices.filter { !it.isOnline }
        val isRegistered = devices.any { it.id == currentDeviceId }
        tvHeaderSubtitle?.text = if (fromCache) "Offline mode" else "${onlineDevices.size} devices online"
        tvOfflineHeader?.text = "Offline — ${offlineDevices.size}"
        rvOnline?.adapter = DevicesAdapter(onlineDevices, { openDeviceDetail(it) })
        rvOffline?.adapter = DevicesAdapter(offlineDevices, { openDeviceDetail(it) }, { (activity as? MainActivity)?.unlinkDevice(it.id) })
        btnLinkDevice?.text = if (isRegistered) "Unlink This Device" else "Link This Device"
        btnLinkDevice?.visibility = View.VISIBLE
        btnLinkDevice?.setOnClickListener {
            if (!NetworkUtils.isOnline(requireContext())) { Toast.makeText(requireContext(), "No connection", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (isRegistered) (activity as? MainActivity)?.unlinkDevice(currentDeviceId)
            else (activity as? MainActivity)?.registerDevice(currentDeviceId, currentDeviceName)
        }
    }

    private fun openDeviceDetail(device: Device) {
        parentFragmentManager.beginTransaction().replace(R.id.fragment_container, DeviceDetailFragment.newInstance(device)).addToBackStack(null).commit()
    }

    private fun setNoConnection(visible: Boolean) {
        viewNoConnection?.visibility = if (visible) View.VISIBLE else View.GONE
        viewContent?.visibility = if (visible) View.GONE else View.VISIBLE
    }
}
