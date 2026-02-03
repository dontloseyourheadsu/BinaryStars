package com.tds.binarystars.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.R
import com.tds.binarystars.api.NoteResponse
import java.text.SimpleDateFormat
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
            deviceName.text = "Device: ${note.signedByDeviceId}"
            noteTypeTag.text = note.contentType.name
            
            try {
                val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                val date = formatter.format(Date(note.createdAt.toLong()))
                createdDate.text = date
            } catch (e: Exception) {
                createdDate.text = note.createdAt
            }

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
}
