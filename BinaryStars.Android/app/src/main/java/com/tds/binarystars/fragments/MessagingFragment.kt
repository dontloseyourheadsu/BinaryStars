package com.tds.binarystars.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tds.binarystars.R
import com.tds.binarystars.activities.MessagingChatActivity
import com.tds.binarystars.adapter.ChatListItem
import com.tds.binarystars.adapter.MessagingChatsAdapter
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.messaging.MessagingEventListener
import com.tds.binarystars.messaging.MessagingSocketManager
import com.tds.binarystars.storage.ChatStorage
import com.tds.binarystars.MainActivity
import com.tds.binarystars.util.NetworkUtils
import com.tds.binarystars.model.ChatSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessagingFragment : Fragment(), MessagingEventListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var newChatButton: Button
    private lateinit var adapter: MessagingChatsAdapter
    private lateinit var contentView: View
    private lateinit var noConnectionView: View
    private lateinit var retryButton: Button
    private val items = mutableListOf<ChatListItem>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_messaging, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.ivMenu)?.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        recyclerView = view.findViewById(R.id.rvChats)
        emptyState = view.findViewById(R.id.tvMessagingEmptyState)
        progressBar = view.findViewById(R.id.pbMessagingLoading)
        newChatButton = view.findViewById(R.id.btnNewChat)
        contentView = view.findViewById(R.id.viewContent)
        noConnectionView = view.findViewById(R.id.viewNoConnection)
        retryButton = view.findViewById(R.id.btnRetry)

        adapter = MessagingChatsAdapter(items) { item ->
            openChat(item.deviceId, item.deviceName)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        newChatButton.setOnClickListener {
            pickDeviceToChat()
        }

        retryButton.setOnClickListener {
            loadChats()
        }

        loadChats()
    }

    override fun onResume() {
        super.onResume()
        MessagingSocketManager.addListener(this)
        loadChats()
    }

    override fun onPause() {
        super.onPause()
        MessagingSocketManager.removeListener(this)
    }

    override fun onChatUpdated(deviceId: String) {
        loadChats()
    }

    override fun onDeviceRemoved(deviceId: String, isSelf: Boolean) {
        loadChats()
    }

    override fun onConnectionStateChanged(isConnected: Boolean) {
        // No-op for now
    }

    private fun loadChats() {
        viewLifecycleOwner.lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val summaries = withContext(Dispatchers.IO) { ChatStorage.getChats() }
            val isOnline = NetworkUtils.isOnline(requireContext())
            if (!isOnline) {
                progressBar.visibility = View.GONE
                newChatButton.isEnabled = false
                updateItems(summaries, emptyMap())
                if (items.isEmpty()) {
                    setNoConnection(true)
                } else {
                    setNoConnection(false)
                }
                return@launch
            }

            newChatButton.isEnabled = true
            val devicesResponse = withContext(Dispatchers.IO) { ApiClient.apiService.getDevices() }
            progressBar.visibility = View.GONE

            val nameLookup = if (devicesResponse.isSuccessful) {
                devicesResponse.body().orEmpty().associateBy({ it.id }, { it.name })
            } else {
                emptyMap()
            }

            updateItems(summaries, nameLookup)
            setNoConnection(false)
        }
    }

    private fun updateItems(summaries: List<ChatSummary>, nameLookup: Map<String, String>) {
        items.clear()
        summaries.forEach { summary ->
            val name = nameLookup[summary.deviceId] ?: summary.deviceId
            items.add(
                ChatListItem(
                    deviceId = summary.deviceId,
                    deviceName = name,
                    lastMessage = summary.lastMessage,
                    lastSentAt = summary.lastSentAt
                )
            )
        }
        adapter.notifyDataSetChanged()
        emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setNoConnection(show: Boolean) {
        noConnectionView.visibility = if (show) View.VISIBLE else View.GONE
        contentView.visibility = if (show) View.GONE else View.VISIBLE
    }

    @SuppressLint("HardwareIds")
    private fun pickDeviceToChat() {
        viewLifecycleOwner.lifecycleScope.launch {
            val devicesResponse = withContext(Dispatchers.IO) { ApiClient.apiService.getDevices() }
            val devices = devicesResponse.body().orEmpty()
            val currentDeviceId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
            val availableDevices = devices.filter { it.id != currentDeviceId }
            if (availableDevices.isEmpty()) {
                return@launch
            }

            val names = availableDevices.map { it.name }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Start chat")
                .setItems(names) { _, index ->
                    val device = availableDevices[index]
                    openChat(device.id, device.name)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun openChat(deviceId: String, deviceName: String) {
        val intent = Intent(requireContext(), MessagingChatActivity::class.java)
        intent.putExtra(MessagingChatActivity.EXTRA_DEVICE_ID, deviceId)
        intent.putExtra(MessagingChatActivity.EXTRA_DEVICE_NAME, deviceName)
        startActivity(intent)
    }
}
