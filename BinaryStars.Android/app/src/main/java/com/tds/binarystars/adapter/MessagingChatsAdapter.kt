package com.tds.binarystars.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MessagingChatsAdapter(
    private val items: List<ChatListItem>,
    private val onChatSelected: (ChatListItem) -> Unit
) : RecyclerView.Adapter<MessagingChatsAdapter.ChatViewHolder>() {

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvChatDeviceName)
        val lastMessage: TextView = view.findViewById(R.id.tvChatLastMessage)
        val lastTime: TextView = view.findViewById(R.id.tvChatLastTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.deviceName
        holder.lastMessage.text = item.lastMessage
        holder.lastTime.text = formatTime(item.lastSentAt)
        holder.itemView.setOnClickListener { onChatSelected(item) }
    }

    override fun getItemCount() = items.size

    private fun formatTime(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        val formatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm", Locale.getDefault())
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(formatter)
    }
}

data class ChatListItem(
    val deviceId: String,
    val deviceName: String,
    val lastMessage: String,
    val lastSentAt: Long
)
