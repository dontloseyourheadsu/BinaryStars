package com.tds.binarystars.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.tds.binarystars.R
import com.tds.binarystars.model.Device
import com.tds.binarystars.model.DeviceType

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
        val uploadSpeed = arguments?.getString(ARG_UPLOAD_SPEED).orEmpty()
        val downloadSpeed = arguments?.getString(ARG_DOWNLOAD_SPEED).orEmpty()

        view.findViewById<TextView>(R.id.tvTitle).text = name.ifBlank { "Device Detail" }
        view.findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<TextView>(R.id.tvCpu).text = if (isOnline) "Online" else "Offline"
        view.findViewById<TextView>(R.id.tvRam).text = "$type • $ipAddress"
        view.findViewById<TextView>(R.id.tvUpSpeed).text = uploadSpeed.ifBlank { "--" }
        view.findViewById<TextView>(R.id.tvDownSpeed).text = downloadSpeed.ifBlank { "--" }
        view.findViewById<TextView>(R.id.tvUptime).text = if (isSynced) "Synced" else "Not Synced"
        view.findViewById<TextView>(R.id.tvBattery).text = if (batteryLevel >= 0) "$batteryLevel%" else "--"
    }

    companion object {
        private const val ARG_NAME = "arg_name"
        private const val ARG_TYPE = "arg_type"
        private const val ARG_IP_ADDRESS = "arg_ip_address"
        private const val ARG_BATTERY_LEVEL = "arg_battery_level"
        private const val ARG_IS_ONLINE = "arg_is_online"
        private const val ARG_IS_SYNCED = "arg_is_synced"
        private const val ARG_UPLOAD_SPEED = "arg_upload_speed"
        private const val ARG_DOWNLOAD_SPEED = "arg_download_speed"

        fun newInstance(device: Device): DeviceDetailFragment {
            return DeviceDetailFragment().apply {
                arguments = Bundle().apply {
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
                    putBoolean(ARG_IS_SYNCED, device.isSynced)
                    putString(ARG_UPLOAD_SPEED, device.wifiUploadSpeed)
                    putString(ARG_DOWNLOAD_SPEED, device.wifiDownloadSpeed)
                }
            }
        }
    }
}
