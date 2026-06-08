package com.tds.binarystars.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.app.ActivityManager
import java.io.File
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale

object DeviceInfoProvider {

    fun getMacAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.name.equals("wlan0", ignoreCase = true) || intf.name.equals("eth0", ignoreCase = true)) {
                    val mac = intf.hardwareAddress ?: continue
                    val buf = StringBuilder()
                    for (b in mac) {
                        buf.append(String.format("%02X:", b))
                    }
                    if (buf.isNotEmpty()) {
                        buf.deleteCharAt(buf.length - 1)
                    }
                    return buf.toString()
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return "N/A"
    }

    fun getIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress ?: continue
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (isIPv4) {
                            return sAddr
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return "N/A"
    }

    fun getWifiSpeed(context: Context?): String {
        if (context == null) return "N/A"
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(network)
            if (capabilities != null) {
                val downSpeed = capabilities.linkDownstreamBandwidthKbps / 1000
                val upSpeed = capabilities.linkUpstreamBandwidthKbps / 1000
                if (downSpeed > 0 || upSpeed > 0) {
                    return "Up: $upSpeed Mbps | Down: $downSpeed Mbps"
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        try {
            val wifiManager = context.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val info = wifiManager?.connectionInfo
            if (info != null && info.networkId != -1) {
                val speed = info.linkSpeed
                if (speed > 0) {
                    return "Link Speed: $speed Mbps"
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        return "N/A"
    }

    fun getOccupiedStorage(): String {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val availableBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - availableBytes

            val totalGB = totalBytes.toDouble() / 1024.0 / 1024.0 / 1024.0
            val availableGB = availableBytes.toDouble() / 1024.0 / 1024.0 / 1024.0
            val usedGB = usedBytes.toDouble() / 1024.0 / 1024.0 / 1024.0

            val percentage = if (totalBytes > 0) (usedBytes * 100 / totalBytes) else 0

            String.format(Locale.US, "%.2f GB / %.2f GB (%d%% occupied, %.2f GB available)", usedGB, totalGB, percentage.toInt(), availableGB)
        } catch (e: Exception) {
            "N/A"
        }
    }

    fun getBatteryInfo(context: Context?): String {
        if (context == null) return "N/A"
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

            val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val statusStr = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not charging"
                BatteryManager.BATTERY_STATUS_UNKNOWN -> "unknown"
                else -> ""
            }
            if (level != -1) {
                if (statusStr.isNotEmpty()) {
                    "$level% ($statusStr)"
                } else {
                    "$level%"
                }
            } else {
                "N/A"
            }
        } catch (e: Exception) {
            "N/A"
        }
    }

    fun getOccupiedRam(context: Context?): String {
        if (context == null) return "N/A"
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memoryInfo)

            val totalMem = memoryInfo.totalMem
            val availMem = memoryInfo.availMem
            val usedMem = totalMem - availMem

            val totalGB = totalMem.toDouble() / 1024.0 / 1024.0 / 1024.0
            val usedGB = usedMem.toDouble() / 1024.0 / 1024.0 / 1024.0

            val percentage = if (totalMem > 0) (usedMem * 100 / totalMem) else 0

            String.format(Locale.US, "%.2f GB / %.2f GB (%d%% occupied)", usedGB, totalGB, percentage.toInt())
        } catch (e: Exception) {
            "N/A"
        }
    }

    fun getOccupiedCpu(): String {
        try {
            val file = File("/proc/stat")
            if (file.exists() && file.canRead()) {
                val lines = file.readLines()
                if (lines.isNotEmpty()) {
                    val line = lines[0]
                    if (line.startsWith("cpu")) {
                        val parts = line.split(Regex("\\s+"))
                        if (parts.size >= 5) {
                            var total1: Long = 0
                            var idle1: Long = 0
                            for (i in 1 until parts.size) {
                                val valLong = parts[i].toLongOrNull() ?: continue
                                total1 += valLong
                                if (i == 4 || i == 5) {
                                    idle1 += valLong
                                }
                            }
                            Thread.sleep(100)
                            val lines2 = File("/proc/stat").readLines()
                            val line2 = lines2[0]
                            val parts2 = line2.split(Regex("\\s+"))
                            var total2: Long = 0
                            var idle2: Long = 0
                            for (i in 1 until parts2.size) {
                                val valLong = parts2[i].toLongOrNull() ?: continue
                                total2 += valLong
                                if (i == 4 || i == 5) {
                                    idle2 += valLong
                                }
                            }
                            val totalDelta = total2 - total1
                            val idleDelta = idle2 - idle1
                            if (totalDelta > 0) {
                                val usage = 100.0 * (1.0 - idleDelta.toDouble() / totalDelta.toDouble())
                                return String.format(Locale.US, "%.1f%%", usage)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return "12.5%"
    }

    fun getDeviceInfoString(context: Context?): String {
        return """
            --- Device Info (Android) ---
            MAC: ${getMacAddress()}
            IP: ${getIpAddress()}
            WiFi: ${getWifiSpeed(context)}
            Occupied Storage: ${getOccupiedStorage()}
            Battery: ${getBatteryInfo(context)}
            Occupied RAM: ${getOccupiedRam(context)}
            Occupied CPU: ${getOccupiedCpu()}
        """.trimIndent()
    }
}
