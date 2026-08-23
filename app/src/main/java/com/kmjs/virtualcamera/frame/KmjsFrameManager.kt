package com.kmjs.virtualcamera.frame

import android.graphics.Bitmap
import com.kmjs.virtualcamera.util.KmjsLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe singleton implementation of FrameProvider.
 * Decouples RTSP stream decoding from UI presentation and target app camera interception.
 */
object KmjsFrameManager : FrameProvider {

    private val consumers = CopyOnWriteArraySet<FrameConsumer>()

    @Volatile
    private var latestFrame: VideoFrame? = null

    private val _statsFlow = MutableStateFlow(FrameStats())
    override val statsFlow: StateFlow<FrameStats> = _statsFlow.asStateFlow()
    override val currentStats: FrameStats
        get() = _statsFlow.value

    private val totalFrames = AtomicLong(0)
    private val droppedFrames = AtomicLong(0)

    // FPS calculation tracking
    private var fpsCounter = 0
    private var lastFpsCalculationTimeMs = System.currentTimeMillis()
    private var currentFps = 0f

    // Test pattern generator background loop if enabled
    private var testPatternJob: Job? = null
    private val testPatternScope = CoroutineScope(Dispatchers.Default)
    private val testGenerator = TestPatternGenerator()

    override fun getLatestFrame(): VideoFrame? = latestFrame

    override fun registerConsumer(consumer: FrameConsumer) {
        consumers.add(consumer)
        KmjsLog.d(KmjsLog.TAG_FRAME, "Consumer registered. Total active consumers: ${consumers.size}")
        // Immediately feed current latest frame if available
        latestFrame?.let { frame ->
            try {
                consumer.onFrameAvailable(frame)
            } catch (e: Exception) {
                KmjsLog.w(KmjsLog.TAG_FRAME, "Error delivering initial frame to consumer", e)
            }
        }
    }

    override fun unregisterConsumer(consumer: FrameConsumer) {
        consumers.remove(consumer)
        KmjsLog.d(KmjsLog.TAG_FRAME, "Consumer unregistered. Remaining active consumers: ${consumers.size}")
    }

    override fun publishFrame(frame: VideoFrame) {
        val count = totalFrames.incrementAndGet()
        latestFrame = frame

        // Calculate FPS window
        fpsCounter++
        val nowMs = System.currentTimeMillis()
        val elapsed = nowMs - lastFpsCalculationTimeMs
        if (elapsed >= 1000) {
            currentFps = (fpsCounter * 1000f) / elapsed
            fpsCounter = 0
            lastFpsCalculationTimeMs = nowMs

            _statsFlow.value = FrameStats(
                fps = currentFps,
                width = frame.width,
                height = frame.height,
                totalFramesDecoded = count,
                droppedFrames = droppedFrames.get(),
                lastFrameTimestampNs = frame.timestampNs,
                sourceDescription = "RTSP Active (${frame.width}x${frame.height} @ ${String.format("%.1f", currentFps)} fps)"
            )
        }

        // Notify consumers
        for (consumer in consumers) {
            try {
                consumer.onFrameAvailable(frame)
            } catch (t: Throwable) {
                droppedFrames.incrementAndGet()
                KmjsLog.w(KmjsLog.TAG_FRAME, "Failed to deliver frame to consumer: ${t.message}")
            }
        }
    }

    fun startTestPatternGenerator(fps: Int = 30, subtitle: String = "KMJS Virtual Camera (Test Pattern)") {
        stopTestPatternGenerator()
        KmjsLog.i(KmjsLog.TAG_FRAME, "Starting internal test pattern generator at $fps FPS")
        testPatternJob = testPatternScope.launch {
            val delayIntervalMs = 1000L / fps
            while (isActive) {
                val frame = testGenerator.generateFrame(subtitle)
                publishFrame(frame)
                delay(delayIntervalMs)
            }
        }
    }

    fun stopTestPatternGenerator() {
        testPatternJob?.cancel()
        testPatternJob = null
    }

    override fun reset() {
        latestFrame = null
        totalFrames.set(0)
        droppedFrames.set(0)
        fpsCounter = 0
        currentFps = 0f
        _statsFlow.value = FrameStats()
        KmjsLog.i(KmjsLog.TAG_FRAME, "FrameProvider state reset")
    }
}
