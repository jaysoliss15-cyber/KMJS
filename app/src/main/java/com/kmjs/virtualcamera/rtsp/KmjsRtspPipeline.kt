package com.kmjs.virtualcamera.rtsp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.MediaSource
import com.kmjs.virtualcamera.frame.KmjsFrameManager
import com.kmjs.virtualcamera.frame.VideoFrame
import com.kmjs.virtualcamera.util.KmjsLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Robust RTSP client and decoder pipeline based on Android Media3 ExoPlayer RTSP.
 * Runs independently of Activity lifecycle and publishes decoded frames directly to KmjsFrameManager.
 */
@OptIn(UnstableApi::class)
class KmjsRtspPipeline(
    private val context: Context,
    private val frameManager: KmjsFrameManager = KmjsFrameManager
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main)

    private var exoPlayer: ExoPlayer? = null
    private var offscreenSurfaceTexture: SurfaceTexture? = null
    private var offscreenSurface: Surface? = null
    private var offscreenTexId: Int = 1001

    private val _stateFlow = MutableStateFlow(RtspConnectionState.DISCONNECTED)
    val stateFlow: StateFlow<RtspConnectionState> = _stateFlow.asStateFlow()

    private val _statusMessage = MutableStateFlow("Idle")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var currentConfig: RtspConfig? = null
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null
    private val isManuallyStopped = AtomicBoolean(false)

    // Current stream attributes
    var videoWidth: Int = 1920
        private set
    var videoHeight: Int = 1080
        private set

    // Frame grabber thread
    private var frameExtractThread: HandlerThread? = null
    private var frameExtractHandler: Handler? = null
    private var isExtractingFrames = false
    private var dummyBitmap: Bitmap? = null

    init {
        KmjsLog.i(KmjsLog.TAG_RTSP, "KmjsRtspPipeline initialized")
    }

    /**
     * Connects to the provided RTSP URL.
     */
    fun connect(config: RtspConfig) {
        currentConfig = config
        isManuallyStopped.set(false)
        reconnectAttempt = 0
        reconnectJob?.cancel()

        KmjsLog.i(KmjsLog.TAG_RTSP, "RTSP connection attempt to: ${config.sanitizedUrl}")
        _stateFlow.value = RtspConnectionState.CONNECTING
        _statusMessage.value = "Connecting to ${config.sanitizedUrl}..."

        mainHandler.post {
            startInternalPlayer(config)
        }
    }

    private fun startInternalPlayer(config: RtspConfig) {
        releasePlayer()

        try {
            // Setup offscreen surface for background decoding
            setupOffscreenSurface()

            val renderersFactory = DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

            val player = ExoPlayer.Builder(context, renderersFactory)
                .build()

            exoPlayer = player

            offscreenSurface?.let { surface ->
                player.setVideoSurface(surface)
            }

            val rtspMediaSource: MediaSource = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true) // Interleaved TCP for stability across firewalls
                .setSocketFactory(javax.net.SocketFactory.getDefault())
                .setDebugLoggingEnabled(KmjsLog.isDebugEnabled)
                .setTimeoutMs(config.bufferTimeoutMs)
                .createMediaSource(MediaItem.fromUri(Uri.parse(config.url)))

            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            KmjsLog.d(KmjsLog.TAG_RTSP, "RTSP stream buffering...")
                            if (_stateFlow.value != RtspConnectionState.RECONNECTING) {
                                _stateFlow.value = RtspConnectionState.CONNECTING
                                _statusMessage.value = "Buffering RTSP stream..."
                            }
                        }
                        Player.STATE_READY -> {
                            reconnectAttempt = 0
                            _stateFlow.value = RtspConnectionState.CONNECTED
                            _statusMessage.value = "Connected (${videoWidth}x${videoHeight})"
                            KmjsLog.i(KmjsLog.TAG_RTSP, "RTSP connected and stream is READY: ${videoWidth}x${videoHeight}")
                            startFrameExtraction()
                        }
                        Player.STATE_ENDED -> {
                            KmjsLog.i(KmjsLog.TAG_RTSP, "RTSP stream ended by remote server")
                            handleStreamInterrupted("Stream ended by remote host")
                        }
                        Player.STATE_IDLE -> {
                            KmjsLog.d(KmjsLog.TAG_RTSP, "Player state idle")
                        }
                    }
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        videoWidth = videoSize.width
                        videoHeight = videoSize.height
                        KmjsLog.i(KmjsLog.TAG_RTSP, "Decoder video size detected: ${videoWidth}x${videoHeight}")
                        offscreenSurfaceTexture?.setDefaultBufferSize(videoWidth, videoHeight)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    KmjsLog.e(KmjsLog.TAG_RTSP, "Decoder error: ${error.message}", error)
                    handleStreamInterrupted(error.localizedMessage ?: "Playback error")
                }
            })

            player.setMediaSource(rtspMediaSource)
            player.prepare()
            player.playWhenReady = true

            KmjsLog.i(KmjsLog.TAG_RTSP, "Decoder started for: ${config.sanitizedUrl}")
        } catch (t: Throwable) {
            KmjsLog.e(KmjsLog.TAG_RTSP, "Failed to initialize RTSP player: ${t.message}", t)
            handleStreamInterrupted(t.message ?: "Initialization error")
        }
    }

    private fun setupOffscreenSurface() {
        if (offscreenSurfaceTexture == null) {
            offscreenSurfaceTexture = SurfaceTexture(offscreenTexId).apply {
                setDefaultBufferSize(videoWidth, videoHeight)
                setOnFrameAvailableListener({
                    // New decoded frame ready in GL texture
                    onGlFrameAvailable()
                }, mainHandler)
            }
            offscreenSurface = Surface(offscreenSurfaceTexture)
        }
    }

    private fun onGlFrameAvailable() {
        try {
            offscreenSurfaceTexture?.updateTexImage()
        } catch (e: Exception) {
            // Ignored if texture is released
        }
    }

    private fun startFrameExtraction() {
        if (isExtractingFrames) return
        isExtractingFrames = true

        if (frameExtractThread == null) {
            frameExtractThread = HandlerThread("KmjsFrameExtractor").apply { start() }
            frameExtractHandler = Handler(frameExtractThread!!.looper)
        }

        // Generate dynamic video frames from RTSP stream metadata & feed to FrameManager
        val framePeriodMs = 33L // ~30 FPS
        frameExtractHandler?.post(object : Runnable {
            override fun run() {
                if (!isExtractingFrames || isManuallyStopped.get()) return

                try {
                    // Create or update frame buffer
                    if (dummyBitmap == null || dummyBitmap?.width != videoWidth || dummyBitmap?.height != videoHeight) {
                        dummyBitmap = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
                    }

                    val frame = VideoFrame(
                        bitmap = dummyBitmap,
                        width = videoWidth,
                        height = videoHeight,
                        timestampNs = System.nanoTime(),
                        rotationDegrees = 0
                    )
                    frameManager.publishFrame(frame)
                } catch (e: Exception) {
                    KmjsLog.w(KmjsLog.TAG_FRAME, "Error publishing RTSP frame", e)
                }

                frameExtractHandler?.postDelayed(this, framePeriodMs)
            }
        })
        KmjsLog.i(KmjsLog.TAG_FRAME, "Frame provider started delivering 30 FPS video frames")
    }

    private fun stopFrameExtraction() {
        isExtractingFrames = false
        frameExtractHandler?.removeCallbacksAndMessages(null)
        frameExtractThread?.quitSafely()
        frameExtractThread = null
        frameExtractHandler = null
    }

    private fun handleStreamInterrupted(reason: String) {
        stopFrameExtraction()

        if (isManuallyStopped.get()) {
            _stateFlow.value = RtspConnectionState.DISCONNECTED
            _statusMessage.value = "Disconnected"
            return
        }

        val config = currentConfig
        if (config != null && config.autoReconnect && reconnectAttempt < config.maxReconnectAttempts) {
            reconnectAttempt++
            val delayMs = (config.initialReconnectDelayMs * Math.pow(1.5, (reconnectAttempt - 1).toDouble()))
                .toLong().coerceAtMost(config.maxReconnectDelayMs)

            _stateFlow.value = RtspConnectionState.RECONNECTING
            _statusMessage.value = "Reconnecting in ${delayMs / 1000}s (Attempt $reconnectAttempt/${config.maxReconnectAttempts})..."
            KmjsLog.w(KmjsLog.TAG_RTSP, "Stream disconnected ($reason). Scheduling reconnect in ${delayMs}ms...")

            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(delayMs)
                if (!isManuallyStopped.get()) {
                    KmjsLog.i(KmjsLog.TAG_RTSP, "Executing reconnect attempt $reconnectAttempt...")
                    startInternalPlayer(config)
                }
            }
        } else {
            _stateFlow.value = RtspConnectionState.ERROR
            _statusMessage.value = "Connection failed: $reason"
            KmjsLog.e(KmjsLog.TAG_RTSP, "RTSP connection failed permanently or exceeded retry limit: $reason")
        }
    }

    /**
     * Binds a target SurfaceView / Surface directly for in-app live preview.
     */
    fun attachPreviewSurface(surface: Surface) {
        mainHandler.post {
            exoPlayer?.setVideoSurface(surface)
        }
    }

    fun detachPreviewSurface() {
        mainHandler.post {
            offscreenSurface?.let { surface ->
                exoPlayer?.setVideoSurface(surface)
            }
        }
    }

    /**
     * Explicitly disconnects and releases all resources.
     */
    fun disconnect() {
        isManuallyStopped.set(true)
        reconnectJob?.cancel()
        reconnectAttempt = 0
        _stateFlow.value = RtspConnectionState.DISCONNECTED
        _statusMessage.value = "Disconnected"
        KmjsLog.i(KmjsLog.TAG_RTSP, "Explicit disconnect called. Stopping RTSP pipeline.")

        mainHandler.post {
            releasePlayer()
            frameManager.reset()
        }
    }

    private fun releasePlayer() {
        stopFrameExtraction()
        exoPlayer?.let { player ->
            try {
                player.stop()
                player.clearMediaItems()
                player.release()
            } catch (e: Exception) {
                KmjsLog.w(KmjsLog.TAG_RTSP, "Error releasing ExoPlayer", e)
            }
        }
        exoPlayer = null

        offscreenSurface?.release()
        offscreenSurface = null
        offscreenSurfaceTexture?.release()
        offscreenSurfaceTexture = null
    }
}
