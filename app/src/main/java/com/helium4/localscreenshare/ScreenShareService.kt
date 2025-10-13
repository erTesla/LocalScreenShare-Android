package com.helium4.localscreenshare

import android.app.*
import android.content.*
import android.graphics.*
import android.hardware.display.DisplayManager
import android.media.*
import android.media.projection.*
import android.net.nsd.*
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import kotlin.math.max

private const val LOGTAG = "LocalScreenShareq"

class ScreenShareService : Service() {

    companion object {
        private const val CHANNEL_ID = "screen_share"
        private const val PORT = 5000
        private const val SERVICE_TYPE = "_localscreenshare._tcp."
        private const val ACTION_STOP = "com.helium4.localscreenshare.STOP_SERVICE"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var out: DataOutputStream? = null
    private val jpegQueue = ArrayBlockingQueue<ByteArray>(1)
    private var nsdManager: NsdManager? = null
    private var nsdListener: NsdManager.RegistrationListener? = null
    private var registered = false
    private var pinUsed = false
    private var pairingCode = ""
    private var handlerThread: HandlerThread? = null
    private var targetFps = 20
    private var frameIntervalMs = 1000L / targetFps
    private var localIp: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())
        registerReceiver(stopReceiver, IntentFilter(ACTION_STOP), Context.RECEIVER_NOT_EXPORTED)
        Log.i(LOGTAG, "[Service] Created and running foreground")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        pairingCode = intent?.getStringExtra("PAIR_PIN") ?: (100000..999999).random().toString()
        targetFps = intent?.getIntExtra("TARGET_FPS", 20)?.coerceIn(5, 60) ?: 20
        frameIntervalMs = 1000L / max(1, targetFps)
        localIp = getLocalIpAddress()
        startForeground(1, buildNotification())

        Log.i(LOGTAG, "[Service] onStartCommand pairingCode=$pairingCode targetFps=$targetFps ip=$localIp")

        val resultCode = ProjectionHolder.resultCode
        val data = ProjectionHolder.data
        if (resultCode == Activity.RESULT_OK && data != null) {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = mgr.getMediaProjection(resultCode, data)
            startSharing()
            registerNsdService()
        } else stopSelf()
        return START_STICKY
    }

    private fun startSharing() {
        scope.launch {
            try {
                Log.i(LOGTAG, "[Service] Opening server socket on port $PORT")
                val server = ServerSocket(PORT)
                val client = server.accept()
                Log.i(LOGTAG, "[Network] Client connected: ${client.inetAddress.hostAddress}")

                val input = client.getInputStream()
                val output = DataOutputStream(client.getOutputStream())
                val buffer = ByteArray(6)
                input.read(buffer)
                val receivedPin = String(buffer).trim()
                Log.i(LOGTAG, "[Auth] Received PIN='$receivedPin' expected='$pairingCode'")

                if (receivedPin != pairingCode || pinUsed) {
                    Log.w(LOGTAG, "[Auth] Invalid or reused PIN, closing sockets")
                    client.close(); server.close(); stopSelf(); return@launch
                }

                pinUsed = true
                out = output
                startVirtualDisplay()

                var frameCount = 0
                var lastFrameTime = 0L
                while (isActive) {
                    val frame = jpegQueue.take()
                    val now = System.currentTimeMillis()
                    val delta = now - lastFrameTime
                    if (delta < frameIntervalMs) delay(frameIntervalMs - delta)
                    lastFrameTime = now
                    try {
                        out?.writeInt(frame.size)
                        out?.write(frame)
                        out?.flush()
                        frameCount++
                        if (frameCount % 10 == 0)
                            Log.d(LOGTAG, "[Stream] Sent $frameCount frames, last=${frame.size} bytes")
                    } catch (e: Exception) {
                        Log.e(LOGTAG, "[Stream] Write error ${e.message}", e)
                        break
                    }
                }
                client.close(); server.close()
                stopSelf()
            } catch (e: Exception) {
                Log.e(LOGTAG, "[Network] Server failed ${e.message}", e)
                stopSelf()
            }
        }
    }

    private fun startVirtualDisplay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        Log.i(LOGTAG, "[Projection] Display metrics w=$width h=$height d=$density")

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(LOGTAG, "[Projection] MediaProjection stopped by system")
                imageReader?.close()
                stopSelf()
            }
        }, null)

        projection?.createVirtualDisplay(
            "ScreenCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )

        handlerThread = HandlerThread("ImageReaderThread").apply { start() }
        val handler = Handler(handlerThread!!.looper)
        imageReader!!.setOnImageAvailableListener({ reader ->
            captureFrame(reader, width, height)
        }, handler)
        Log.i(LOGTAG, "[Projection] Virtual display created")
    }

    private fun captureFrame(reader: ImageReader, width: Int, height: Int) {
        try {
            reader.acquireLatestImage()?.use { image ->
                val plane = image.planes.firstOrNull() ?: return@use
                val buffer: ByteBuffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width

                val tmp = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                tmp.copyPixelsFromBuffer(buffer)
                val bmp = Bitmap.createBitmap(tmp, 0, 0, width, height)
                val baos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 60, baos)
                val bytes = baos.toByteArray()
                if (!jpegQueue.offer(bytes)) Log.d(LOGTAG, "[Frame] Dropping frame, queue full")
            }
        } catch (e: Exception) {
            Log.e(LOGTAG, "[Frame] Capture error ${e.message}", e)
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress
                        if (!ip.startsWith("169.254") && !ip.startsWith("0.")) return ip
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(LOGTAG, "[Network] IP lookup failed ${e.message}")
        }
        return null
    }

    private fun registerNsdService() {
        try {
            nsdManager = getSystemService(NSD_SERVICE) as NsdManager
            val info = NsdServiceInfo().apply {
                serviceName = "LocalScreenShare"
                serviceType = SERVICE_TYPE
                port = PORT
            }
            nsdListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo?) {
                    registered = true
                    Log.i(LOGTAG, "[NSD] Registered ${info?.serviceName}")
                }
                override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                    Log.e(LOGTAG, "[NSD] Registration failed $errorCode")
                }
                override fun onServiceUnregistered(info: NsdServiceInfo?) {
                    Log.i(LOGTAG, "[NSD] Unregistered")
                }
                override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                    Log.e(LOGTAG, "[NSD] Unregister failed $errorCode")
                }
            }
            nsdManager?.registerService(info, NsdManager.PROTOCOL_DNS_SD, nsdListener!!)
        } catch (e: Exception) {
            Log.e(LOGTAG, "[NSD] Exception ${e.message}", e)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(ACTION_STOP).apply { setPackage(packageName) }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Screen Sharing Active")
            .setContentText("IP: $localIp • PIN: $pairingCode • FPS: $targetFps")
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.i(LOGTAG, "[Service] Stop action received")
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Screen Share", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(LOGTAG, "[Service] Destroying, cleaning up")
        try { unregisterReceiver(stopReceiver) } catch (_: Exception) {}
        if (registered && nsdListener != null) {
            try { nsdManager?.unregisterService(nsdListener!!) } catch (_: Exception) {}
        }
        imageReader?.close(); projection?.stop(); handlerThread?.quitSafely(); scope.cancel()
        Log.i(LOGTAG, "[Service] Cleanup complete")
    }
}
