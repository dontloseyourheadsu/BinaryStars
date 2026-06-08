package com.tds.binarystars

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tds.binarystars.data.repository.BluetoothRepositoryImpl
import com.tds.binarystars.domain.repository.ConnectionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceInfoCommandTest {

    @Test
    fun testSelfDeviceInfoCommand() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = BluetoothRepositoryImpl(appContext)

        // Run the !device-info --self command
        val result = repository.sendMessage("!device-info --self")
        assertTrue("Command sending should succeed", result.isSuccess)

        // Wait a short time for the background info retrieval to complete and write to db
        delay(500)

        // Fetch messages from the flow
        val messagesList = repository.getMessages().first()
        
        println("=== Instrumented Test Messages ===")
        messagesList.forEach { msg ->
            println("${msg.senderDeviceId}: ${msg.body}")
        }
        println("==================================")

        // Verify we have the command message and the system response
        assertTrue("Should have at least 2 messages in session", messagesList.size >= 2)
        
        val cmdMsg = messagesList[messagesList.size - 2]
        val replyMsg = messagesList[messagesList.size - 1]

        assertEquals("!device-info --self", cmdMsg.body)
        assertEquals("System", replyMsg.senderDeviceId)
        
        val info = replyMsg.body
        assertTrue("Response should contain header", info.contains("--- Device Info (Android) ---"))
        assertTrue("Response should contain MAC", info.contains("MAC:"))
        assertTrue("Response should contain IP", info.contains("IP:"))
        assertTrue("Response should contain WiFi", info.contains("WiFi:"))
        assertTrue("Response should contain Occupied Storage", info.contains("Occupied Storage:"))
        assertTrue("Response should contain Battery", info.contains("Battery:"))
        assertTrue("Response should contain Occupied RAM", info.contains("Occupied RAM:"))
        assertTrue("Response should contain Occupied CPU", info.contains("Occupied CPU:"))
    }
}
