package com.printbridge

import android.hardware.usb.UsbDevice
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.printbridge.engine.LabelConfig
import com.printbridge.engine.TsplCommandBuilder
import com.printbridge.transport.PrinterError
import com.printbridge.transport.UsbPrinterConnection
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Home() } }
    }

    @Composable
    fun Home() {
        val scope = rememberCoroutineScope()
        var devices by remember { mutableStateOf(UsbPrinterConnection.listPrinters(this)) }
        var selected by remember { mutableStateOf<UsbDevice?>(null) }
        var conn by remember { mutableStateOf<UsbPrinterConnection?>(null) }
        var status by remember { mutableStateOf("Not connected") }
        var progress by remember { mutableStateOf(0f) }
        var w by remember { mutableStateOf("75") }
        var h by remember { mutableStateOf("75") }
        var density by remember { mutableStateOf("8") }

        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("PrintBridge", style = MaterialTheme.typography.headlineMedium)
            Text("Status: $status")
            Button(onClick = { devices = UsbPrinterConnection.listPrinters(this@MainActivity) }, Modifier.fillMaxWidth()) { Text("Scan USB printers") }
            if (devices.isEmpty()) Text("No USB printer detected (use OTG cable)")
            devices.forEach { d ->
                OutlinedCard(Modifier.fillMaxWidth(), onClick = { selected = d }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(d.productName ?: "USB device")
                        Text("${d.manufacturerName ?: "?"}  VID 0x%04X  PID 0x%04X".format(d.vendorId, d.productId))
                        if (selected == d) Text("Selected")
                    }
                }
            }
            Button(enabled = selected != null, modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    try {
                        conn?.disconnect()
                        val c = UsbPrinterConnection(this@MainActivity, selected!!)
                        c.connect(); conn = c; status = "Connected (USB)"
                    } catch (e: PrinterError) { status = e.message ?: "Error" }
                }
            }) { Text("Connect USB") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(w, { w = it }, label = { Text("Width mm") }, modifier = Modifier.weight(1f))
                OutlinedTextField(h, { h = it }, label = { Text("Height mm") }, modifier = Modifier.weight(1f))
                OutlinedTextField(density, { density = it }, label = { Text("Density") }, modifier = Modifier.weight(1f))
            }
            Button(enabled = conn?.isConnected == true, modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    try {
                        val cfg = LabelConfig(w.toFloatOrNull() ?: 75f, h.toFloatOrNull() ?: 75f, density = density.toIntOrNull() ?: 8)
                        val now = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date())
                        val data = TsplCommandBuilder(cfg).begin()
                            .text(20, 20, "PRINTBRIDGE TEST", "3", 2, 2)
                            .text(20, 80, "USB  ${cfg.widthMm}x${cfg.heightMm} mm")
                            .text(20, 110, now)
                            .barcode(20, 150, "PB123456")
                            .qr(20, 270, "PRINTBRIDGE", 5)
                            .print(1).build()
                        status = "Printing..."
                        conn!!.write(data) { progress = it }
                        status = "Print sent"
                    } catch (e: PrinterError) { status = e.message ?: "Error" }
                      catch (e: IllegalArgumentException) { status = "Invalid label size" }
                }
            }) { Text("Test Print (TSPL)") }
            LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth())
        }
    }
}
