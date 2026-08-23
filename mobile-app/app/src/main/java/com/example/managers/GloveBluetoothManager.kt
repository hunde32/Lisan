package com.example.managers

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

// Standard SPP UUID for Bluetooth Classic (HC-05 / ATmega32 standard)
private val UUID_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

class GloveBluetoothManager(private val context: Context, private val coroutineScope: CoroutineScope) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _latencyMs = MutableStateFlow(0)
    val latencyMs: StateFlow<Int> = _latencyMs.asStateFlow()

    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _receivedText = MutableStateFlow("")
    val receivedText: StateFlow<String> = _receivedText.asStateFlow()

    @SuppressLint("MissingPermission")
    fun connectToDevice(macAddress: String, isDemoMode: Boolean) {
        if (isDemoMode) {
            startMockSimulation()
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(macAddress)
                socket = device?.createRfcommSocketToServiceRecord(UUID_SPP)
                bluetoothAdapter?.cancelDiscovery()
                socket?.connect()
                
                inputStream = socket?.inputStream
                outputStream = socket?.outputStream
                
                _isConnected.value = true
                startListening()
            } catch (e: Exception) {
                _isConnected.value = false
                socket?.close()
                // Do NOT start mock simulation if real connection fails
            }
        }
    }

    private fun startListening() {
        coroutineScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(1024)
            var bytes: Int
            while (isActive && _isConnected.value) {
                try {
                    bytes = inputStream?.read(buffer) ?: 0
                    if (bytes > 0) {
                        val text = String(buffer, 0, bytes).trim()
                        if (text.isNotEmpty()) {
                            _receivedText.value = text
                        }
                    }
                } catch (e: Exception) {
                    _isConnected.value = false
                    break
                }
            }
        }
    }

    private fun startMockSimulation() {
        // Fallback for prototype if no real hardware is paired
        _isConnected.value = true
        coroutineScope.launch(Dispatchers.IO) {
            simulateMetrics()
            while(isActive && _isConnected.value) {
                delay(15000)
                _receivedText.value = "Hello from Glove"
            }
        }
    }

    private fun simulateMetrics() {
        coroutineScope.launch(Dispatchers.IO) {
            while (isActive && _isConnected.value) {
                _latencyMs.value = (15..45).random()
                _batteryLevel.value = (_batteryLevel.value - 1).coerceAtLeast(10)
                delay(3000)
            }
        }
    }

    fun disconnect() {
        try {
            socket?.close()
        } catch (e: Exception) {}
        _isConnected.value = false
    }
}
