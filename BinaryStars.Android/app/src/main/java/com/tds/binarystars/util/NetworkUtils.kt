package com.tds.binarystars.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Network utility helpers.
 */
object NetworkUtils {
    private const val TAG = "NetworkUtils"

    /**
     * Returns true when a network path is available.
     *
     * We intentionally do not require NET_CAPABILITY_VALIDATED because local/LAN API
     * deployments (hotspot, isolated Wi-Fi, emulator bridge) may be reachable without
     * public internet validation.
     */
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork
        if (network == null) {
            com.tds.binarystars.util.NativeLogging.d(TAG, "isOnline: No active network")
            return false
        }
        val capabilities = manager.getNetworkCapabilities(network)
        if (capabilities == null) {
            com.tds.binarystars.util.NativeLogging.d(TAG, "isOnline: No capabilities for active network")
            return false
        }
        
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val hasCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val hasEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        
        val online = hasInternet || hasWifi || hasCellular || hasEthernet
        com.tds.binarystars.util.NativeLogging.d(TAG, "isOnline: $online (Internet: $hasInternet, WiFi: $hasWifi, Cell: $hasCellular, Eth: $hasEthernet)")
        
        return online
    }

    /** Returns true when active validated network transport is Wi-Fi. */
    fun isWifiConnected(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        val result = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        com.tds.binarystars.util.NativeLogging.d(TAG, "isWifiConnected: $result")
        return result
    }
}
