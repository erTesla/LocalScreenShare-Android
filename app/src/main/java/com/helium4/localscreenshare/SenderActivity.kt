package com.helium4.localscreenshare

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private const val LOGTAG = "LocalScreenShareq"

class SenderActivity : AppCompatActivity() {

    private val REQUEST_CODE_CAPTURE = 100
    private val REQUEST_CODE_NOTIF = 200
    private lateinit var projectionManager: MediaProjectionManager
    private var isSharing = false
    private var overlayLayout: LinearLayout? = null
    private var selectedFps = 24
    private var pairingPin: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(LOGTAG, "[SenderActivity] onCreate()")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_NOTIF
            )
            return
        }
        showFpsSelectionDialog()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSharing) showEndSessionDialog() else finish()
            }
        })
    }

    private fun showFpsSelectionDialog() {
        val options = arrayOf("10 fps", "15 fps", "24 fps", "30 fps", "45 fps", "60 fps")
        AlertDialog.Builder(this)
            .setTitle("Choose Frame Rate")
            .setItems(options) { _, which ->
                selectedFps = arrayOf(10, 15, 24, 30, 45, 60)[which]
                startProjection()
            }.setCancelable(false).show()
    }

    private fun startProjection() {
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE_CAPTURE)
    }

    @Deprecated("Legacy OK")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CAPTURE && resultCode == Activity.RESULT_OK && data != null) {
            pairingPin = (100000..999999).random().toString()
            ProjectionHolder.resultCode = resultCode
            ProjectionHolder.data = data
            val svc = Intent(this, ScreenShareService::class.java).apply {
                putExtra("TARGET_FPS", selectedFps)
                putExtra("PAIR_PIN", pairingPin)
            }
            ContextCompat.startForegroundService(this, svc)
            isSharing = true
            showOverlay()
        } else finish()
    }

    private fun showOverlay() {
        overlayLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xCC000000.toInt())
            setPadding(40, 100, 40, 100)
        }
        val pinText = TextView(this).apply {
            text = "🔐 PIN: $pairingPin"
            setTextColor(0xFFFFFFFF.toInt()); textSize = 24f; gravity = Gravity.CENTER
        }
        val fpsText = TextView(this).apply {
            text = "🎥 FPS: $selectedFps"
            setTextColor(0xFFFFFFFF.toInt()); textSize = 20f; gravity = Gravity.CENTER
        }
        val statusText = TextView(this).apply {
            text = "🔴 Screen is being shared"
            setTextColor(0xFFFFFFFF.toInt()); textSize = 22f; gravity = Gravity.CENTER
        }
        val stopBtn = Button(this).apply {
            text = "Stop Sharing"
            setBackgroundColor(0xFFFF4444.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { showEndSessionDialog() }
        }
        overlayLayout!!.apply { addView(pinText); addView(fpsText); addView(statusText); addView(stopBtn) }
        setContentView(overlayLayout)
    }

    private fun showEndSessionDialog() {
        AlertDialog.Builder(this)
            .setTitle("End Screen Sharing?")
            .setMessage("Stop sharing your screen?")
            .setPositiveButton("Yes") { _, _ -> stopSharing() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun stopSharing() {
        stopService(Intent(this, ScreenShareService::class.java))
        isSharing = false; overlayLayout?.removeAllViews(); finish()
    }
}
