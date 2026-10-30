package com.printbridge.transport

sealed class PrinterError(message: String) : Exception(message) {
    object NotFound : PrinterError("USB printer not detected")
    object PermissionDenied : PrinterError("USB permission denied")
    object NoEndpoint : PrinterError("No bulk OUT endpoint found on this device")
    object NotConnected : PrinterError("Printer not connected")
    object Disconnected : PrinterError("Printer disconnected during printing")
    object Timeout : PrinterError("USB transfer timeout")
    class Failed(msg: String) : PrinterError(msg)
}

interface PrinterConnection {
    val isConnected: Boolean
    suspend fun connect()
    suspend fun write(data: ByteArray, onProgress: (Float) -> Unit = {})
    suspend fun read(maxBytes: Int = 64, timeoutMs: Int = 500): ByteArray
    fun disconnect()
}
