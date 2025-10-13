package com.helium4.localscreenshare

import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class DeviceListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val nsdManager = getSystemService(NSD_SERVICE) as NsdManager
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { DeviceListScreen(nsdManager) } }
    }
}

@Composable
fun DeviceListScreen(nsdManager: NsdManager) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf(listOf<Pair<String, String>>()) }

    DisposableEffect(Unit) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String?) {}
            override fun onDiscoveryStopped(serviceType: String?) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.contains("_localscreenshare._tcp.")) {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            val host = resolved.host.hostAddress ?: return
                            devices = devices + (resolved.serviceName to host)
                        }
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
                    })
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                devices = devices.filterNot { it.first == serviceInfo.serviceName }
            }
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
        }
        nsdManager.discoverServices("_localscreenshare._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
        onDispose { nsdManager.stopServiceDiscovery(listener) }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Available Devices", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            devices.forEach { (name, ip) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable {
                            val input = EditText(context).apply {
                                hint = "Enter 6-digit PIN"
                                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                            }
                            AlertDialog.Builder(context)
                                .setTitle("Connect to $name")
                                .setMessage("Enter the pairing PIN shown on sender’s screen.")
                                .setView(input)
                                .setPositiveButton("Connect") { _, _ ->
                                    val pin = input.text.toString().trim()
                                    if (pin.length == 6) {
                                        val intent = Intent(context, ReceiverActivity::class.java)
                                        intent.putExtra("DEVICE_IP", ip)
                                        intent.putExtra("DEVICE_PIN", pin)
                                        context.startActivity(intent)
                                    } else {
                                        Toast.makeText(context, "Invalid PIN", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Device: $name")
                        Text("IP: $ip")
                    }
                }
            }
        }
    }
}
