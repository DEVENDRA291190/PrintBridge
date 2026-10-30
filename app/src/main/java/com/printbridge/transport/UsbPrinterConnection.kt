package com.printbridge.transport

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.*
import android.os.Build
import kotlinx.coroutines.*
import kotlin.coroutines.resume

class UsbPrinterConnection(
    private val context: Context,
    private val device: UsbDevice,
) : PrinterConnection {

    private val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var outEp: UsbEndpoint? = null
    private var inEp: UsbEndpoint? = null

    override val isConnected get() = connection != null

    companion object {
        const val ACTION_PERMISSION = "com.printbridge.USB_PERMISSION"
        private const val CHUNK = 16 * 1024
        private const val WRITE_TIMEOUT_MS = 5000

        fun listPrinters(context: Context): List<UsbDevice> {
            val m = context.getSystemService(Context.USB_SERVICE) as UsbManager
            return m.deviceList.values.filter { d ->
                d.vendorId == 0x1203 ||
                (0 until d.interfaceCount).any { d.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_PRINTER }
            }
        }
    }

    private suspend fun ensurePermission() {
        if (manager.hasPermission(device)) return
        val granted = suspendCancellableCoroutine<Boolean> { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    context.unregisterReceiver(this)
                    if (cont.isActive) cont.resume(i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                }
            }
            val filter = IntentFilter(ACTION_PERMISSION)
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else context.registerReceiver(receiver, filter)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(context, 0, Intent(ACTION_PERMISSION).setPackage(context.packageName), flags)
            manager.requestPermission(device, pi)
            cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
        }
        if (!granted) throw PrinterError.PermissionDenied
    }

    override suspend fun connect() = withContext(Dispatchers.IO) {
        ensurePermission()
        val candidates = (0 until device.interfaceCount).map { device.getInterface(it) }
        val iface = candidates.firstOrNull { i ->
            i.interfaceClass == UsbConstants.USB_CLASS_PRINTER && findBulk(i, UsbConstants.USB_DIR_OUT) != null
        } ?: candidates.firstOrNull { findBulk(it, UsbConstants.USB_DIR_OUT) != null }
            ?: throw PrinterError.NoEndpoint

        val conn = manager.openDevice(device) ?: throw PrinterError.Failed("Unable to open USB device")
        if (!conn.claimInterface(iface, true)) { conn.close(); throw PrinterError.Failed("Unable to claim USB interface") }
        usbInterface = iface; connection = conn
        outEp = findBulk(iface, UsbConstants.USB_DIR_OUT)
        inEp = findBulk(iface, UsbConstants.USB_DIR_IN)
    }

    private fun findBulk(i: UsbInterface, dir: Int): UsbEndpoint? =
        (0 until i.endpointCount).map { i.getEndpoint(it) }
            .firstOrNull { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == dir }

    override suspend fun write(data: ByteArray, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val conn = connection ?: throw PrinterError.NotConnected
        val ep = outEp ?: throw PrinterError.NoEndpoint
        var offset = 0
        while (offset < data.size) {
            ensureActive()
            if (!manager.deviceList.containsValue(device)) { disconnect(); throw PrinterError.Disconnected }
            val len = minOf(CHUNK, data.size - offset)
            val sent = conn.bulkTransfer(ep, data, offset, len, WRITE_TIMEOUT_MS)
            if (sent < 0) {
                if (!manager.deviceList.containsValue(device)) { disconnect(); throw PrinterError.Disconnected }
                throw PrinterError.Timeout
            }
            offset += sent
            onProgress(offset.toFloat() / data.size)
        }
    }

    override suspend fun read(maxBytes: Int, timeoutMs: Int): ByteArray = withContext(Dispatchers.IO) {
        val conn = connection ?: throw PrinterError.NotConnected
        val ep = inEp ?: return@withContext ByteArray(0)
        val buf = ByteArray(maxBytes)
        val n = conn.bulkTransfer(ep, buf, buf.size, timeoutMs)
        if (n > 0) buf.copyOf(n) else ByteArray(0)
    }

    override fun disconnect() {
        runCatching { usbInterface?.let { connection?.releaseInterface(it) } }
        runCatching { connection?.close() }
        connection = null; usbInterface = null; outEp = null; inEp = null
    }
}
