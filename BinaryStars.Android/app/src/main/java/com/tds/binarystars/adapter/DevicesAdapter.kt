package com.tds.binarystars.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.R
import com.tds.binarystars.model.Device
import com.tds.binarystars.model.DeviceType

class DevicesAdapter(private val devices: List<Device>) : RecyclerView.Adapter<DevicesAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val name: TextView = view.findViewById(R.id.tvName)
        val statusDot: View = view.findViewById(R.id.vStatusDot)
        val statusText: TextView = view.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.name.text = device.name
        
        val platformStr = when(device.type) {
             DeviceType.LINUX -> "Linux"
             DeviceType.ANDROID -> "Android"
             else -> "Unknown"
        }
        
        holder.statusText.text = "$platformStr • ${device.ipAddress}"

        val context = holder.itemView.context
        
        val statusColor = if (device.isOnline) {
             ContextCompat.getColor(context, R.color.carbon_status_online)
        } else {
             ContextCompat.getColor(context, R.color.carbon_status_offline)
        }

        holder.statusDot.backgroundTintList = ColorStateList.valueOf(statusColor)

        when (device.type) {
            DeviceType.LINUX -> holder.icon.setImageResource(R.drawable.ic_computer)
            DeviceType.ANDROID -> holder.icon.setImageResource(R.drawable.ic_smartphone)
        }
    }

    override fun getItemCount() = devices.size
}
