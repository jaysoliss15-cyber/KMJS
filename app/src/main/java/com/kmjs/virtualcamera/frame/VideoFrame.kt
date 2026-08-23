package com.kmjs.virtualcamera.frame

import android.graphics.Bitmap
import java.nio.ByteBuffer

/**
 * Represents a single decoded video frame produced by the RTSP pipeline
 * or test pattern generator, consumable by the virtual camera injection layer.
 */
data class VideoFrame(
    val bitmap: Bitmap? = null,
    val rawBuffer: ByteBuffer? = null,
    val width: Int,
    val height: Int,
    val timestampNs: Long = System.nanoTime(),
    val rotationDegrees: Int = 0,
    val format: FrameFormat = FrameFormat.ARGB_8888,
    val sequenceNumber: Long = 0L
) {
    enum class FrameFormat {
        ARGB_8888,
        NV21,
        YUV_420_888,
        RGB_565
    }
}

/**
 * Frame production and consumption metrics.
 */
data class FrameStats(
    val fps: Float = 0f,
    val width: Int = 0,
    val height: Int = 0,
    val totalFramesDecoded: Long = 0L,
    val droppedFrames: Long = 0L,
    val lastFrameTimestampNs: Long = 0L,
    val bitrateKbps: Long = 0L,
    val codecName: String = "H.264/AVC",
    val sourceDescription: String = "Inactive"
)

/**
 * Interface for components consuming video frames (e.g. Surface renderer, Preview UI, IPC Socket).
 */
fun interface FrameConsumer {
    fun onFrameAvailable(frame: VideoFrame)
}
