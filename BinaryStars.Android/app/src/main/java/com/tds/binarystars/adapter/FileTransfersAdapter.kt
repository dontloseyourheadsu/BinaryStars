package com.tds.binarystars.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.R

class FileTransfersAdapter(
    private val items: List<TransferUiItem>,
    private val onActionClick: (TransferUiItem, TransferAction) -> Unit
) : RecyclerView.Adapter<FileTransfersAdapter.TransferViewHolder>() {

    class TransferViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivTransferIcon)
        val fileName: TextView = view.findViewById(R.id.tvTransferFileName)
        val meta: TextView = view.findViewById(R.id.tvTransferMeta)
        val status: TextView = view.findViewById(R.id.tvTransferStatus)
        val statusIcon: ImageView = view.findViewById(R.id.ivStatusIcon)
        val progress: ProgressBar = view.findViewById(R.id.pbTransfer)
        val primary: Button = view.findViewById(R.id.btnPrimaryAction)
        val secondary: Button = view.findViewById(R.id.btnSecondaryAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransferViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file_transfer, parent, false)
        return TransferViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransferViewHolder, position: Int) {
        val item = items[position]
        holder.fileName.text = item.fileName
        holder.meta.text = item.metaText
        holder.status.text = item.statusText

        holder.statusIcon.visibility = if (item.showSuccessIcon) View.VISIBLE else View.GONE
        holder.progress.visibility = if (item.showProgress) View.VISIBLE else View.GONE

        if (item.primaryAction != null) {
            holder.primary.visibility = View.VISIBLE
            holder.primary.text = item.primaryAction.label
            holder.primary.setOnClickListener { onActionClick(item, item.primaryAction) }
        } else {
            holder.primary.visibility = View.GONE
        }

        if (item.secondaryAction != null) {
            holder.secondary.visibility = View.VISIBLE
            holder.secondary.text = item.secondaryAction.label
            holder.secondary.setOnClickListener { onActionClick(item, item.secondaryAction) }
        } else {
            holder.secondary.visibility = View.GONE
        }
    }

    override fun getItemCount() = items.size
}

data class TransferUiItem(
    val id: String,
    val fileName: String,
    val contentType: String,
    val metaText: String,
    val statusText: String,
    val isSender: Boolean,
    val showSuccessIcon: Boolean,
    val showProgress: Boolean,
    val primaryAction: TransferAction?,
    val secondaryAction: TransferAction?,
    val localPath: String?
)

enum class TransferAction(val label: String) {
    Download("Download"),
    Resend("Resend"),
    Move("Move"),
    Open("Open"),
    Reject("Reject")
}
