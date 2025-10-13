package com.helium4.localscreenshare

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.chrisbanes.photoview.PhotoView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.OutputStream
import java.net.Socket

private const val LOGTAG = "LocalScreenShareq"

class ReceiverActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageView = PhotoView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true
        }
        setContentView(imageView)

        val ip = intent.getStringExtra("DEVICE_IP") ?: return
        val pin = intent.getStringExtra("DEVICE_PIN") ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.i(LOGTAG, "[Receiver] Connecting to $ip:5000 with PIN=$pin")
                val socket = Socket(ip, 5000)
                Log.i(LOGTAG, "[Receiver] Connected, sending PIN")
                val out: OutputStream = socket.getOutputStream()
                out.write(pin.toByteArray())
                out.flush()

                val input = DataInputStream(socket.getInputStream())
                Log.i(LOGTAG, "[Receiver] Waiting for frames...")
                while (true) {
                    val size = input.readInt()
                    Log.d(LOGTAG, "[Receiver] Next frame size=$size")
                    val bytes = ByteArray(size)
                    input.readFully(bytes)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    withContext(Dispatchers.Main) { imageView.setImageBitmap(bmp) }
                }
            } catch (e: Exception) {
                Log.e(LOGTAG, "[Receiver] Connection lost or failed ${e.message}", e)
            }
        }
    }
}
