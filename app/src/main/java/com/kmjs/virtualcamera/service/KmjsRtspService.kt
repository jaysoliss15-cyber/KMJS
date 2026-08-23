package com.kmjs.virtualcamera.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.R
import com.kmjs.virtualcamera.MainActivity
import com.kmjs.virtualcamera.frame.KmjsFrameManager
import com.kmjs.virtualcamera.ipc.KmjsIpcConstants
import com.kmjs.virtualcamera.rtsp.KmjsRtspPipeline
import com.kmjs.virtualcamera.rtsp.RtspConfig
import com.kmjs.virtualcamera.rtsp.RtspConnectionState
import com.kmjs.virtualcamera.util.KmjsLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android Foreground Service managing the RTSP pipeline and FrameProvider.
 * Continues running seamlessly when the KMJS Activity is minimized or destroyed.
 */
class KmjsRtspService : Service() {

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Main)
    private var stateObserverJob: Job? = null

    private var rtspPipeline: KmjsRtspPipeline? = null
    private var wakeLock: PowerManager.WakeLock? = null

    inner class LocalBinder : Binder() {
        fun getService(): KmjsRtspService = this@KmjsRtspService
        val pipeline: KmjsRtspPipeline?
            get() = rtspPipeline
    }

    companion object {
        const val CHANNEL_ID = "kmjs_rtsp_service_channel"
        const val NOTIFICATION_ID = 10086

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _activeUrl = MutableStateFlow("rtsp://192.168.1.38:8554/live/obs")
        val activeUrl: StateFlow<String> = _activeUrl.asStateFlow()

        fun start(context: Context, url: String) {
            val intent = Intent(context, KmjsRtspService::class.java).apply {
                action = KmjsIpcConstants.ACTION_START_RTSP
                putExtra(KmjsIpcConstants.EXTRA_RTSP_URL, url)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, KmjsRtspService::class.java).apply {
                action = KmjsIpcConstants.ACTION_STOP_RTSP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        KmjsLog.i(KmjsLog.TAG_SERVICE, "KmjsRtspService onCreate")
        _isServiceRunning.value = true

        createNotificationChannel()
        acquireWakeLock()

        rtspPipeline = KmjsRtspPipeline(applicationContext, KmjsFrameManager)

        // Observe RTSP connection state to update notification
        stateObserverJob = scope.launch {
            rtspPipeline?.stateFlow?.collect { state ->
                updateNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: KmjsIpcConstants.ACTION_START_RTSP
        KmjsLog.i(KmjsLog.TAG_SERVICE, "KmjsRtspService onStartCommand action=$action")

        when (action) {
            KmjsIpcConstants.ACTION_START_RTSP -> {
                val url = intent?.getStringExtra(KmjsIpcConstants.EXTRA_RTSP_URL)
                    ?: _activeUrl.value
                _activeUrl.value = url

                val notification = buildNotification(RtspConnectionState.CONNECTING)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                rtspPipeline?.connect(RtspConfig(url = url))
            }

            KmjsIpcConstants.ACTION_STOP_RTSP -> {
                KmjsLog.i(KmjsLog.TAG_SERVICE, "Stopping RTSP service requested by action")
                rtspPipeline?.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "KMJS Virtual Camera RTSP Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background streaming and frame provider for camera substitution"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: RtspConnectionState): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, KmjsRtspService::class.java).apply {
                action = KmjsIpcConstants.ACTION_STOP_RTSP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val statusText = when (state) {
            RtspConnectionState.CONNECTED -> "Streaming Live (${rtspPipeline?.videoWidth}x${rtspPipeline?.videoHeight})"
            RtspConnectionState.CONNECTING -> "Connecting to RTSP..."
            RtspConnectionState.RECONNECTING -> "Reconnecting to stream..."
            RtspConnectionState.ERROR -> "Connection Error"
            RtspConnectionState.DISCONNECTED -> "Disconnected"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KMJS Virtual Camera Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Disconnect", disconnectIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(state: RtspConnectionState) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "KMJS::RtspServiceWakeLock"
            )?.apply {
                acquire(24 * 60 * 60 * 1000L) // 24 hours max
            }
            KmjsLog.i(KmjsLog.TAG_SERVICE, "WakeLock acquired for background RTSP streaming")
        } catch (e: Exception) {
            KmjsLog.w(KmjsLog.TAG_SERVICE, "Could not acquire WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    KmjsLog.i(KmjsLog.TAG_SERVICE, "WakeLock released")
                }
            }
        } catch (e: Exception) {
            KmjsLog.w(KmjsLog.TAG_SERVICE, "Error releasing WakeLock", e)
        }
        wakeLock = null
    }

    override fun onDestroy() {
        super.onDestroy()
        KmjsLog.i(KmjsLog.TAG_SERVICE, "KmjsRtspService onDestroy")
        _isServiceRunning.value = false
        stateObserverJob?.cancel()
        rtspPipeline?.disconnect()
        releaseWakeLock()
    }
}
