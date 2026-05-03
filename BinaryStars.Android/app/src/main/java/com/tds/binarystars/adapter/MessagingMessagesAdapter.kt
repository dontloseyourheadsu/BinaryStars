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
    private val items: MutableList<ChatMessage>,
    private val listener: OnMessageActionListener? = null
) : RecyclerView.Adapter<MessagingMessagesAdapter.MessageViewHolder>() {

    interface OnMessageActionListener {
        fun onSaveFile(fileName: String, localPath: String)
    }

    companion object {
        private const val TYPE_IN = 0
        private const val TYPE_OUT = 1
    }

    abstract class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val body: TextView = view.findViewById(R.id.tvMessageBody)
        val time: TextView = view.findViewById(R.id.tvMessageTime)
        val btnCopy: View = view.findViewById(R.id.btnCopyMessage)
        val btnSave: View? = try { view.findViewById(R.id.btnDownloadMessage) } catch(_: Exception) { null }
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
        
        if (item.body.startsWith("BT_FILE|")) {
            val parts = item.body.split("|")
            if (parts.size >= 3) {
                val fileName = parts[1]
                val localPath = parts[2]
                holder.body.text = "File Received: $fileName"
                holder.btnSave?.visibility = View.VISIBLE
                holder.btnSave?.setOnClickListener {
                    listener?.onSaveFile(fileName, localPath)
                }
            } else {
                holder.body.text = item.body
                holder.btnSave?.visibility = View.GONE
            }
        } else {
            holder.body.text = item.body
            holder.btnSave?.visibility = View.GONE
        }

        holder.time.text = formatTime(item.sentAt)
        holder.btnCopy.setOnClickListener {
            val context = it.context
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            if (clipboard != null) {
                val textToCopy = if (item.body.startsWith("BT_FILE|")) {
                    item.body.split("|").getOrNull(1) ?: item.body
                } else {
                    item.body
                }
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Message", textToCopy))
                android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
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
