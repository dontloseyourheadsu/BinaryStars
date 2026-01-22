package com.tds.binarystars.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.tds.binarystars.R
import com.tds.binarystars.model.Device
import com.tds.binarystars.model.DeviceType

class DevicesAdapter(private val devices: List<Device>) : RecyclerView.Adapter<DevicesAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val name: TextView = view.findViewById(R.id.tvName)
        val statusDot: View = view.findViewById(R.id.vStatusDot)
        val statusText: TextView = view.findViewById(R.id.tvStatus)
        val battery: TextView = view.findViewById(R.id.tvBattery)
        val ip: TextView = view.findViewById(R.id.tvIp)
        val upDown: TextView = view.findViewById(R.id.tvUpDown)
        val card: MaterialCardView = view as MaterialCardView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.name.text = device.name
        holder.ip.text = device.ipAddress
        holder.battery.text = "${device.batteryLevel}%"
        holder.upDown.text = "${device.wifiUploadSpeed} / ${device.wifiDownloadSpeed}"

        if (device.isConnected) {
            holder.statusText.text = "Connected"
            holder.statusDot.setBackgroundResource(R.drawable.shape_status_dot_active)
            holder.card.strokeWidth = 4 // thicker border as per sketch (2px in sketch, but 4px visible)
            holder.card.strokeColor = ContextCompat.getColor(holder.itemView.context, R.color.accent_yellow)
            holder.card.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.status_connected_bg_start))
        } else {
            holder.statusText.text = "Disconnected"
            holder.statusDot.setBackgroundResource(R.drawable.shape_status_dot)
            holder.card.strokeWidth = 0
            holder.card.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.card_bg_light))
        }

        when (device.type) {
            DeviceType.LINUX -> {
                holder.icon.setImageResource(R.drawable.ic_computer)
                holder.icon.setBackgroundResource(R.drawable.bg_icon_laptop)
            }
            DeviceType.ANDROID -> {
                holder.icon.setImageResource(R.drawable.ic_smartphone)
                holder.icon.setBackgroundResource(R.drawable.bg_icon_phone)
            }
        }
    }

    override fun getItemCount() = devices.size
}
