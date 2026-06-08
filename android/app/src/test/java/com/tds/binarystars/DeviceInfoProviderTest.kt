package com.tds.binarystars.data.repository

import android.content.Context
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class DeviceInfoProviderTest {

    @Test
    fun testGetDeviceInfoString() {
        val info = DeviceInfoProvider.getDeviceInfoString(null)
        
        println("=== Device Info Output (Android Test) ===")
        println(info)
        println("=========================================")

        assertNotNull(info)
        assertTrue(info.contains("--- Device Info (Android) ---"))
        assertTrue(info.contains("MAC:"))
        assertTrue(info.contains("IP:"))
        assertTrue(info.contains("WiFi:"))
        assertTrue(info.contains("Occupied Storage:"))
        assertTrue(info.contains("Battery:"))
        assertTrue(info.contains("Occupied RAM:"))
        assertTrue(info.contains("Occupied CPU:"))
    }
}
