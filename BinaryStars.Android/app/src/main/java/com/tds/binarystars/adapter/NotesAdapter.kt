package com.tds.binarystars.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.R
import com.tds.binarystars.api.NoteResponse
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class NotesAdapter(
    private val notes: List<NoteResponse>,
    private val onNoteClick: (NoteResponse) -> Unit
) : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {

    inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val noteName: TextView = itemView.findViewById(R.id.tvNoteName)
        private val deviceName: TextView = itemView.findViewById(R.id.tvDeviceName)
        private val createdDate: TextView = itemView.findViewById(R.id.tvCreatedDate)
        private val noteTypeTag: TextView = itemView.findViewById(R.id.tvNoteType)

        fun bind(note: NoteResponse) {
            noteName.text = note.name
            val deviceLabel = note.signedByDeviceName ?: ""
            val displayDevice = if (deviceLabel.isNotBlank()) deviceLabel else note.signedByDeviceId
            deviceName.text = "Device: $displayDevice"
            noteTypeTag.text = note.contentType.name

            createdDate.text = formatDate(note.createdAt)

            itemView.setOnClickListener {
                onNoteClick(note)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(notes[position])
    }

    override fun getItemCount() = notes.size

    private fun formatDate(isoDate: String): String {
        return try {
            val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm", Locale.getDefault())
            OffsetDateTime.parse(isoDate).format(formatter)
        } catch (e: Exception) {
            try {
                val legacyFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                legacyFormat.format(Date(isoDate.toLong()))
            } catch (ignored: Exception) {
                isoDate
            }
        }
    }
}
