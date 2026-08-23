package com.kmjs.virtualcamera.frame

import kotlinx.coroutines.flow.StateFlow

/**
 * Clean abstraction decoupling the RTSP decoding pipeline from the Activity lifecycle
 * and the injected camera substitution layer.
 */
interface FrameProvider {
    /**
     * Retrieves the latest decoded video frame, if available.
     */
    fun getLatestFrame(): VideoFrame?

    /**
     * Subscribes a consumer to receive frames as they are decoded.
     */
    fun registerConsumer(consumer: FrameConsumer)

    /**
     * Unregisters a previously registered consumer.
     */
    fun unregisterConsumer(consumer: FrameConsumer)

    /**
     * Publishes a new frame into the pipeline.
     */
    fun publishFrame(frame: VideoFrame)

    /**
     * Reactive stream of frame statistics (FPS, resolution, dropped count, etc.).
     */
    val statsFlow: StateFlow<FrameStats>

    /**
     * Current frame statistics.
     */
    val currentStats: FrameStats

    /**
     * Clears cached frames and resets buffers.
     */
    fun reset()
}
