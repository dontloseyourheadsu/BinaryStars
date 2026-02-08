package com.tds.binarystars.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.R
import com.tds.binarystars.model.ChatMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Recycler adapter for chat messages.
 */
class MessagingMessagesAdapter(
    private val items: MutableList<ChatMessage>
) : RecyclerView.Adapter<MessagingMessagesAdapter.MessageViewHolder>() {

    companion object {
        private const val TYPE_IN = 0
        private const val TYPE_OUT = 1
    }

    abstract class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val body: TextView = view.findViewById(R.id.tvMessageBody)
        val time: TextView = view.findViewById(R.id.tvMessageTime)
    }

    class InViewHolder(view: View) : MessageViewHolder(view)
    class OutViewHolder(view: View) : MessageViewHolder(view)

    override fun getItemViewType(position: Int): Int {
        return if (items[position].isOutgoing) TYPE_OUT else TYPE_IN
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = if (viewType == TYPE_OUT) R.layout.item_message_out else R.layout.item_message_in
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return if (viewType == TYPE_OUT) OutViewHolder(view) else InViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val item = items[position]
        holder.body.text = item.body
        holder.time.text = formatTime(item.sentAt)
    }

    override fun getItemCount() = items.size

    /** Replaces the adapter contents. */
    fun replaceAll(newItems: List<ChatMessage>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    /** Prepends older messages at the top of the list. */
    fun prepend(itemsToAdd: List<ChatMessage>) {
        if (itemsToAdd.isEmpty()) return
        items.addAll(0, itemsToAdd)
        notifyItemRangeInserted(0, itemsToAdd.size)
    }

    /** Appends a single new message. */
    fun append(item: ChatMessage) {
        items.add(item)
        notifyItemInserted(items.lastIndex)
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(formatter)
    }
}
