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
import com.tds.binarystars.adapter.DevicesAdapter
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.DeviceTypeDto
import com.tds.binarystars.api.SendActionRequestDto
import com.tds.binarystars.model.Device
import com.tds.binarystars.model.DeviceType
import com.tds.binarystars.util.NetworkUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ActionsFragment : Fragment() {

    private companion object {
        private const val POLL_INTERVAL_MS = 10_000L
    }

    private var selectedDevice: Device? = null

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

    @SuppressLint("HardwareIds")
    private fun sendBlockScreenAction() {
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

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.sendAction(
                    SendActionRequestDto(
                        senderDeviceId = senderId,
                        targetDeviceId = target.id,
                        actionType = "block_screen"
                    )
                )

                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "Action sent", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to send action", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Failed to send action", Toast.LENGTH_SHORT).show()
            }
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
                    .filter { it.type == DeviceTypeDto.Linux }
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

                root.findViewById<TextView>(R.id.tvActionsSubtitle).text = "Linux devices — ${linuxDevices.size}"

                list.adapter = DevicesAdapter(linuxDevices) { device ->
                    selectedDevice = device
                    renderDetail(device)
                }

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

        if (device == null) {
            listContainer.visibility = View.VISIBLE
            detailContainer.visibility = View.GONE
            return
        }

        listContainer.visibility = View.GONE
        detailContainer.visibility = View.VISIBLE

        root.findViewById<TextView>(R.id.tvActionDetailName).text = device.name
        root.findViewById<TextView>(R.id.tvActionDetailMeta).text = "${device.id} • ${if (device.isOnline) "Online" else "Offline"}"
        root.findViewById<TextView>(R.id.tvActionSupportInfo).text =
            "Supported target: Linux desktops (GNOME/KDE/GTK-focused). Unsupported desktop/session APIs are handled on target device."

        blockButton.isEnabled = device.isOnline
    }
}
