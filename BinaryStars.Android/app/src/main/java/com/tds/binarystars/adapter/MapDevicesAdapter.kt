package com.tds.binarystars.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.R
import com.tds.binarystars.api.DeviceTypeDto
import com.tds.binarystars.model.MapDeviceItem

class MapDevicesAdapter(
    private val items: List<MapDeviceItem>,
    private val onClick: (MapDeviceItem) -> Unit
) : RecyclerView.Adapter<MapDevicesAdapter.DeviceViewHolder>() {

    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.ivDeviceIcon)
        private val name: TextView = itemView.findViewById(R.id.tvDeviceName)
        private val meta: TextView = itemView.findViewById(R.id.tvDeviceMeta)

        fun bind(item: MapDeviceItem) {
            name.text = item.name
            val status = if (item.isCurrent) {
                "Current device"
            } else if (item.isOnline) {
                "Online"
            } else {
                "Offline"
            }
            meta.text = status

            val iconRes = when (item.type) {
                DeviceTypeDto.Linux -> R.drawable.ic_computer
                DeviceTypeDto.Android -> R.drawable.ic_smartphone
            }
            icon.setImageResource(iconRes)

            itemView.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_map_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
