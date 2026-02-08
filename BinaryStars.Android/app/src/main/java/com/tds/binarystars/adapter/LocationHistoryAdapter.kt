package com.tds.binarystars.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.R
import com.tds.binarystars.model.LocationHistoryPoint

class LocationHistoryAdapter(
    private val items: List<LocationHistoryPoint>,
    private val onClick: (LocationHistoryPoint) -> Unit
) : RecyclerView.Adapter<LocationHistoryAdapter.HistoryViewHolder>() {

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tvHistoryTitle)
        private val meta: TextView = itemView.findViewById(R.id.tvHistoryMeta)

        fun bind(item: LocationHistoryPoint) {
            title.text = item.title
            meta.text = item.timestamp
            itemView.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_location_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
