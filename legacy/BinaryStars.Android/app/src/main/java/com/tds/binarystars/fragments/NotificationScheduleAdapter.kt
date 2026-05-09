package com.tds.binarystars.fragments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.R
import com.tds.binarystars.storage.NotificationScheduleStorage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class NotificationScheduleAdapter(
    private val schedules: List<NotificationScheduleStorage.LocalSchedule>,
    private val onDelete: (NotificationScheduleStorage.LocalSchedule) -> Unit
) : RecyclerView.Adapter<NotificationScheduleAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvScheduleTitle)
        val tvBody: TextView = view.findViewById(R.id.tvScheduleBody)
        val tvTrigger: TextView = view.findViewById(R.id.tvScheduleTrigger)
        val btnDelete: Button = view.findViewById(R.id.btnDeleteSchedule)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification_schedule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = schedules[position]
        holder.tvTitle.text = item.title
        holder.tvBody.text = item.body

        val triggerText = when {
            item.repeatMinutes != null && item.repeatMinutes > 0 -> {
                "Repeats every ${item.repeatMinutes} min"
            }
            item.scheduledForUtc != null -> {
                try {
                    val local = Instant.parse(item.scheduledForUtc).atZone(ZoneId.systemDefault()).toLocalDateTime()
                    "Runs at ${local.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}"
                } catch (_: Exception) {
                    "Runs at ${item.scheduledForUtc}"
                }
            }
            else -> "No trigger"
        }
        holder.tvTrigger.text = triggerText

        holder.btnDelete.setOnClickListener {
            onDelete(item)
        }
    }

    override fun getItemCount() = schedules.size
}
