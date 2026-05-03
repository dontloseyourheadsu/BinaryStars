package com.tds.binarystars.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import com.tds.binarystars.util.NativeLogging as L

/**
 * Modern Bluetooth Manager using pure RFCOMM/SPP with identity handshake.
 */
object BluetoothManager {
    private const val SERVER_UUID = "00001101-0000-1000-8000-00805f9b34fb"
    private var socket: BluetoothSocket? = null
    private const val TAG = "BinaryStarsBT"

    var connectedDeviceId: String? = null
        private set

    @SuppressLint("MissingPermission")
    suspend fun connect(context: Context, macAddress: String, myDeviceId: String, allowedDeviceIds: List<String>): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                disconnect()
                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = bluetoothManager.adapter ?: return@withContext Result.failure(Exception("Bluetooth not supported"))
                
                val device = adapter.getRemoteDevice(macAddress)
                val newSocket = device.createRfcommSocketToServiceRecord(UUID.fromString(SERVER_UUID))
                newSocket.connect()
                
                val output = newSocket.outputStream
                val input = BufferedReader(InputStreamReader(newSocket.inputStream))
                
                // 1. Identify ourselves
                val identifyMsg = "IDENTIFY|$myDeviceId\n"
                output.write(identifyMsg.toByteArray())
                output.flush()
                
                // 2. Wait for identification from the other side
                val response = input.readLine() ?: throw Exception("Connection closed during handshake")
                if (response.startsWith("IDENTIFIED|")) {
                    val peerId = response.split("|")[1]
                    if (allowedDeviceIds.contains(peerId)) {
                        socket = newSocket
                        connectedDeviceId = peerId
                        L.i(TAG, "Connected and identified with $peerId")
                        Result.success(peerId)
                    } else {
                        newSocket.close()
                        L.w(TAG, "Rejected connection from unknown device $peerId")
                        Result.failure(Exception("Untrusted device: $peerId"))
                    }
                } else {
                    newSocket.close()
                    L.w(TAG, "Handshake failed: $response")
                    Result.failure(Exception("Handshake failed: $response"))
                }
            } catch (e: Exception) {
                L.e(TAG, "Failed to connect to $macAddress", e)
                Result.failure(e)
            }
        }
    }

    suspend fun send(message: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val s = socket ?: return@withContext Result.failure(Exception("Not connected"))
                val payload = message.replace("\n", "") + "\n"
                s.outputStream.write(payload.toByteArray())
                s.outputStream.flush()
                Result.success(Unit)
            } catch (e: Exception) {
                L.e(TAG, "Failed to send message", e)
                disconnect()
                Result.failure(e)
            }
        }
    }

    suspend fun sendFile(fileName: String, base64Data: String): Result<Unit> {
        return send("FILE|$fileName|$base64Data")
    }

    fun receive(): Flow<String> = flow {
        val s = socket ?: return@flow
        val reader = BufferedReader(InputStreamReader(s.inputStream))
        while (true) {
            try {
                val line = reader.readLine() ?: break
                emit(line)
            } catch (e: Exception) {
                break
            }
        }
        disconnect()
    }.flowOn(Dispatchers.IO)

    fun disconnect() {
        try { socket?.close() } catch (e: Exception) {}
        socket = null
        connectedDeviceId = null
    }
    
    fun isConnected(): Boolean = socket?.isConnected == true
}
