package com.tds.binarystars.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import com.tds.binarystars.R
import com.tds.binarystars.activities.NoteDetailActivity
import com.tds.binarystars.activities.CreateNoteActivity
import com.tds.binarystars.adapter.NotesAdapter
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.NoteResponse
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tds.binarystars.MainActivity
import com.tds.binarystars.storage.NotesStorage
import com.tds.binarystars.util.NetworkUtils

class NotesFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateText: TextView
    private lateinit var createNoteBtn: Button
    private lateinit var adapter: NotesAdapter
    private lateinit var contentView: View
    private lateinit var noConnectionView: View
    private lateinit var retryButton: Button
    private var notes: MutableList<NoteResponse> = mutableListOf()
    private companion object {
        private const val TABLET_MIN_WIDTH_DP = 600
    }

    private fun isTabletLayout(): Boolean {
        return resources.configuration.smallestScreenWidthDp >= TABLET_MIN_WIDTH_DP
    }

    /**
     * Inflates the notes list UI.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notes, container, false)
    }

    /**
     * Initializes UI, adapters, and loads notes.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.ivMenu)?.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        recyclerView = view.findViewById(R.id.rvNotes)
        progressBar = view.findViewById(R.id.pbLoading)
        emptyStateText = view.findViewById(R.id.tvEmptyState)
        createNoteBtn = view.findViewById(R.id.btnCreateNote)
        contentView = view.findViewById(R.id.viewContent)
        noConnectionView = view.findViewById(R.id.viewNoConnection)
        retryButton = view.findViewById(R.id.btnRetry)

        adapter = NotesAdapter(notes) { note ->
            val intent = Intent(requireContext(), NoteDetailActivity::class.java)
            intent.putExtra("noteId", note.id)
            intent.putExtra("noteName", note.name)
            intent.putExtra("deviceId", note.signedByDeviceId)
            intent.putExtra("deviceName", note.signedByDeviceName ?: "")
            intent.putExtra("contentType", note.contentType.ordinal)
            intent.putExtra("content", note.content)
            intent.putExtra("createdAt", note.createdAt)
            intent.putExtra("updatedAt", note.updatedAt)
            startActivity(intent)
        }

        recyclerView.layoutManager = if (isTabletLayout()) {
            GridLayoutManager(requireContext(), 2)
        } else {
            LinearLayoutManager(requireContext())
        }
        recyclerView.adapter = adapter

        createNoteBtn.setOnClickListener {
            startActivity(Intent(requireContext(), CreateNoteActivity::class.java))
        }

        retryButton.setOnClickListener {
            loadNotes()
        }

        loadNotes()
    }

    /**
     * Loads notes from the API or cache and updates the UI.
     */
    private fun loadNotes() {
        viewLifecycleOwner.lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val isOnline = NetworkUtils.isOnline(requireContext())
            if (!isOnline) {
                createNoteBtn.isEnabled = false
                val cached = withContext(Dispatchers.IO) { NotesStorage.getNotes() }
                progressBar.visibility = View.GONE
                notes.clear()
                notes.addAll(cached)
                adapter.notifyDataSetChanged()
                if (notes.isEmpty()) {
                    setNoConnection(true)
                } else {
                    setNoConnection(false)
                    updateEmptyState()
                }
                return@launch
            }

            createNoteBtn.isEnabled = true

            try {
                val response = withContext(Dispatchers.IO) { ApiClient.apiService.getNotes() }
                progressBar.visibility = View.GONE

                if (response.isSuccessful && response.body() != null) {
                    notes.clear()
                    notes.addAll(response.body()!!)
                    adapter.notifyDataSetChanged()
                    withContext(Dispatchers.IO) { NotesStorage.upsertNotes(notes.toList()) }
                    setNoConnection(false)
                    updateEmptyState()
                } else {
                    val cached = withContext(Dispatchers.IO) { NotesStorage.getNotes() }
                    notes.clear()
                    notes.addAll(cached)
                    adapter.notifyDataSetChanged()
                    setNoConnection(false)
                    if (notes.isEmpty()) {
                        emptyStateText.text = "Failed to load notes"
                        emptyStateText.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        updateEmptyState()
                    }
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                val cached = withContext(Dispatchers.IO) { NotesStorage.getNotes() }
                notes.clear()
                notes.addAll(cached)
                adapter.notifyDataSetChanged()
                setNoConnection(false)
                if (notes.isEmpty()) {
                    emptyStateText.text = "Error: ${e.message}"
                    emptyStateText.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    updateEmptyState()
                }
            }
        }
    }

    /**
     * Toggles offline state UI panels.
     */
    private fun setNoConnection(show: Boolean) {
        noConnectionView.visibility = if (show) View.VISIBLE else View.GONE
        contentView.visibility = if (show) View.GONE else View.VISIBLE
    }

    /**
     * Updates the empty state UI based on list contents.
     */
    private fun updateEmptyState() {
        if (notes.isEmpty()) {
            emptyStateText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyStateText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        loadNotes()
    }
}