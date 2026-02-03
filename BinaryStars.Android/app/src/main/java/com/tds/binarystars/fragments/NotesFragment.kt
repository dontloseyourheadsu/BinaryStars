package com.tds.binarystars.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.R
import com.tds.binarystars.activities.NoteDetailActivity
import com.tds.binarystars.activities.CreateNoteActivity
import com.tds.binarystars.adapter.NotesAdapter
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.NoteResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotesFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateText: TextView
    private lateinit var createNoteBtn: Button
    private lateinit var adapter: NotesAdapter
    private var notes: MutableList<NoteResponse> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.rvNotes)
        progressBar = view.findViewById(R.id.pbLoading)
        emptyStateText = view.findViewById(R.id.tvEmptyState)
        createNoteBtn = view.findViewById(R.id.btnCreateNote)

        adapter = NotesAdapter(notes) { note ->
            val intent = Intent(requireContext(), NoteDetailActivity::class.java)
            intent.putExtra("noteId", note.id)
            intent.putExtra("noteName", note.name)
            intent.putExtra("deviceId", note.signedByDeviceId)
            intent.putExtra("contentType", note.contentType.ordinal)
            intent.putExtra("content", note.content)
            intent.putExtra("createdAt", note.createdAt)
            intent.putExtra("updatedAt", note.updatedAt)
            startActivity(intent)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        createNoteBtn.setOnClickListener {
            startActivity(Intent(requireContext(), CreateNoteActivity::class.java))
        }

        loadNotes()
    }

    private fun loadNotes() {
        GlobalScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                val response = ApiClient.apiService.getNotes()

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE

                    if (response.isSuccessful && response.body() != null) {
                        notes.clear()
                        notes.addAll(response.body()!!)
                        adapter.notifyDataSetChanged()

                        if (notes.isEmpty()) {
                            emptyStateText.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                        } else {
                            emptyStateText.visibility = View.GONE
                            recyclerView.visibility = View.VISIBLE
                        }
                    } else {
                        emptyStateText.text = "Failed to load notes"
                        emptyStateText.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    emptyStateText.text = "Error: ${e.message}"
                    emptyStateText.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadNotes()
    }
}