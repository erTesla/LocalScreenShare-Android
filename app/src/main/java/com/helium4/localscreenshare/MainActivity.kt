package com.helium4.localscreenshare

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.random.Random

private const val LOGTAG = "LocalScreenShare"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { MainScreen() } }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current

    var showPcDialog by remember { mutableStateOf(false) }
    var ip by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    // 🪟 Compose AlertDialog for PC mirror
    if (showPcDialog) {
        AlertDialog(
            onDismissRequest = { showPcDialog = false },
            title = { Text("Mirror PC Screen") },
            text = {
                Text(
                    "Connect from your PC using the Python sender script.\n\n" +
                            "🔹 IP Address: $ip\n" +
                            "🔹 PIN: $pin\n\n" +
                            "Make sure both devices are on the same Wi-Fi or hotspot."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(context, ReceiverActivity::class.java).apply {
                        putExtra("MODE", "PC")
                        putExtra("DEVICE_IP", ip)
                        putExtra("DEVICE_PIN", pin)
                    }
                    context.startActivity(intent)
                    showPcDialog = false
                }) { Text("Start Receiver") }
            },
            dismissButton = {
                TextButton(onClick = { showPcDialog = false }) { Text("Cancel") }
            }
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { context.startActivity(Intent(context, SenderActivity::class.java)) },
                modifier = Modifier.padding(16.dp)
            ) { Text("Send Screen (This Phone)") }

            Button(
                onClick = { context.startActivity(Intent(context, DeviceListActivity::class.java)) },
                modifier = Modifier.padding(16.dp)
            ) { Text("View Screen (From Another Phone)") }

            Button(
                onClick = {
                    ip = getLocalIpAddress(context)
                    pin = Random.nextInt(100000, 999999).toString()
                    showPcDialog = true
                },
                modifier = Modifier.padding(16.dp)
            ) { Text("Mirror PC Screen (Beta)") }
        }
    }
}

private fun getLocalIpAddress(context: Context): String {
    return try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
    } catch (e: Exception) {
        Log.e(LOGTAG, "Failed to get IP", e)
        "0.0.0.0"
    }
}
