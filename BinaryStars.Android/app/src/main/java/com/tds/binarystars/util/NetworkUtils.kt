package com.tds.binarystars.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Network utility helpers.
 */
object NetworkUtils {
    /**
     * Returns true when a network path is available.
     *
     * We intentionally do not require NET_CAPABILITY_VALIDATED because local/LAN API
     * deployments (hotspot, isolated Wi-Fi, emulator bridge) may be reachable without
     * public internet validation.
     */
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /** Returns true when active validated network transport is Wi-Fi. */
    fun isWifiConnected(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
