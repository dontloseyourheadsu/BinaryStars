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

    @Test
    fun testRemoteDeviceInfoCommand() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = BluetoothRepositoryImpl(appContext)

        // Set up the repository connection state to Connected via reflection
        val connectionStateField = repository.javaClass.getDeclaredField("_connectionState")
        connectionStateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = connectionStateField.get(repository) as kotlinx.coroutines.flow.MutableStateFlow<ConnectionState>
        stateFlow.value = ConnectionState.Connected("RemotePeer", "00:11:22:33:44:55")

        // Set up mock writer to capture outgoing responses
        val outputStream = java.io.ByteArrayOutputStream()
        val writerField = repository.javaClass.getDeclaredField("writer")
        writerField.isAccessible = true
        writerField.set(repository, outputStream)

        // Set up mock reader with the incoming !device-info command
        val incomingCommand = "!device-info\n"
        val bufferedReader = java.io.BufferedReader(java.io.StringReader(incomingCommand))
        val readerField = repository.javaClass.getDeclaredField("reader")
        readerField.isAccessible = true
        readerField.set(repository, bufferedReader)

        // Invoke startReading(peerId) via reflection
        val startReadingMethod = repository.javaClass.getDeclaredMethod("startReading", String::class.java)
        startReadingMethod.isAccessible = true
        startReadingMethod.invoke(repository, "RemotePeer")

        // Wait for reading loop and response transmission to complete
        delay(600)

        // Verify that the command was processed and an automated reply was generated
        val writtenBytes = outputStream.toByteArray()
        val writtenString = String(writtenBytes)

        println("=== Instrumented Test Captured Output ===")
        println(writtenString)
        println("=========================================")

        assertTrue("Output should contain the device info header", writtenString.contains("--- Device Info (Android) ---"))
        assertTrue("Output should contain MAC address field", writtenString.contains("MAC:"))
        assertTrue("Output should contain IP address field", writtenString.contains("IP:"))
        assertTrue("Output should contain Battery field", writtenString.contains("Battery:"))
    }
}
