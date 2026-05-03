package com.tds.binarystars.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import com.tds.binarystars.R
import com.tds.binarystars.MainActivity
import com.tds.binarystars.activities.MessagingChatActivity
import com.tds.binarystars.adapter.ChatListItem
import com.tds.binarystars.adapter.MessagingChatsAdapter
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.DeviceActionResultDto
import com.tds.binarystars.api.LocationUpdateEventDto
import com.tds.binarystars.messaging.MessagingEventListener
import com.tds.binarystars.messaging.MessagingSocketManager
import com.tds.binarystars.storage.ChatStorage
import com.tds.binarystars.storage.DeviceCacheStorage
import com.tds.binarystars.util.NetworkUtils
import com.tds.binarystars.model.ChatSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.SystemClock
import java.time.OffsetDateTime

class MessagingFragment : Fragment(), MessagingEventListener {
    private lateinit var rvChats: RecyclerView
    private lateinit var tvMessagingEmptyState: TextView
    private lateinit var pbMessagingLoading: ProgressBar
    private lateinit var btnNewChat: Button
    private lateinit var adapter: MessagingChatsAdapter
    private lateinit var viewContent: View
    private lateinit var viewNoConnection: View
    private lateinit var btnRetry: Button
    private val items = mutableListOf<ChatListItem>()
    private var isLoadingChats = false
    private var lastRemoteLoadAtMs = 0L
    private companion object {
        private const val TABLET_MIN_WIDTH_DP = 600
        private const val MIN_REMOTE_LOAD_INTERVAL_MS = 1500L
    }

    private fun isTabletLayout(): Boolean = resources.configuration.smallestScreenWidthDp >= TABLET_MIN_WIDTH_DP

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_messaging, container, false)
    }

    @SuppressLint("HardwareIds")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<ImageView>(R.id.ivMenu)?.setOnClickListener { (activity as? MainActivity)?.openDrawer() }
        rvChats = view.findViewById(R.id.rvChats)
        tvMessagingEmptyState = view.findViewById(R.id.tvMessagingEmptyState)
        pbMessagingLoading = view.findViewById(R.id.pbMessagingLoading)
        btnNewChat = view.findViewById(R.id.btnNewChat)
        viewContent = view.findViewById(R.id.viewContent)
        viewNoConnection = view.findViewById(R.id.viewNoConnection)
        btnRetry = view.findViewById(R.id.btnRetry)

        adapter = MessagingChatsAdapter(items) { item -> openChat(item.deviceId, item.deviceName) }
        rvChats.layoutManager = if (isTabletLayout()) GridLayoutManager(requireContext(), 2) else LinearLayoutManager(requireContext())
        rvChats.adapter = adapter
        btnNewChat.setOnClickListener { pickDeviceToChat() }
        btnRetry.setOnClickListener { loadChats() }
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

    override fun onChatUpdated(deviceId: String) { loadChats(allowRemote = false) }
    override fun onDeviceRemoved(deviceId: String, isSelf: Boolean) { loadChats(allowRemote = false) }
    override fun onConnectionStateChanged(isConnected: Boolean) { loadChats(allowRemote = false) }
    override fun onDevicePresenceChanged(deviceId: String, isOnline: Boolean, lastSeen: String) { loadChats(allowRemote = false) }
    override fun onLocationUpdated(event: LocationUpdateEventDto) {}
    override fun onActionResult(result: DeviceActionResultDto) {}

    private fun loadChats(allowRemote: Boolean = true) {
        if (isLoadingChats) return
        viewLifecycleOwner.lifecycleScope.launch {
            isLoadingChats = true
            pbMessagingLoading.visibility = View.VISIBLE
            val isOnline = NetworkUtils.isOnline(requireContext())
            if (!isOnline) { pbMessagingLoading.visibility = View.GONE; refreshFromCache(); return@launch }

            try {
                val now = SystemClock.elapsedRealtime()
                if (allowRemote && (now - lastRemoteLoadAtMs >= MIN_REMOTE_LOAD_INTERVAL_MS)) {
                    lastRemoteLoadAtMs = now
                    val resp = withContext(Dispatchers.IO) { ApiClient.apiService.getMessagingChats(getCurrentDeviceId()) }
                    if (resp.isSuccessful) {
                        val remote = resp.body().orEmpty().map { ChatSummary(it.deviceId, it.lastMessage, OffsetDateTime.parse(it.lastSentAt).toInstant().toEpochMilli()) }
                        withContext(Dispatchers.IO) { remote.forEach { ChatStorage.upsertChatSummary(it) } }
                    }
                }
                refreshFromCache()
            } catch (_: Exception) {
                refreshFromCache()
            } finally {
                isLoadingChats = false
                pbMessagingLoading.visibility = View.GONE
            }
        }
    }

    private suspend fun refreshFromCache() {
        val summaries = withContext(Dispatchers.IO) { ChatStorage.getChats() }
        val accountDevices = withContext(Dispatchers.IO) { DeviceCacheStorage.getDevices() }
        val lookup = accountDevices.associateBy({ it.id }, { it.name })
        items.clear()
        summaries.forEach { s -> items.add(ChatListItem(s.deviceId, lookup[s.deviceId] ?: s.deviceId, s.lastMessage, s.lastSentAt, false)) }
        adapter.notifyDataSetChanged()
        tvMessagingEmptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        setNoConnection(items.isEmpty() && !NetworkUtils.isOnline(requireContext()))
    }

    private fun setNoConnection(v: Boolean) { viewNoConnection.visibility = if (v) View.VISIBLE else View.GONE; viewContent.visibility = if (v) View.GONE else View.VISIBLE }

    private fun pickDeviceToChat() {
        viewLifecycleOwner.lifecycleScope.launch {
            val devices = withContext(Dispatchers.IO) { DeviceCacheStorage.getDevices() }.filter { it.id != getCurrentDeviceId() }
            if (devices.isEmpty()) return@launch
            val names = devices.map { it.name }.toTypedArray()
            AlertDialog.Builder(requireContext()).setTitle("Start chat").setItems(names) { _, i -> openChat(devices[i].id, devices[i].name) }.show()
        }
    }

    private fun openChat(id: String, name: String) {
        val intent = Intent(requireContext(), MessagingChatActivity::class.java).apply { putExtra(MessagingChatActivity.EXTRA_DEVICE_ID, id); putExtra(MessagingChatActivity.EXTRA_DEVICE_NAME, name) }
        startActivity(intent)
    }

    private fun getCurrentDeviceId(): String = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
}
