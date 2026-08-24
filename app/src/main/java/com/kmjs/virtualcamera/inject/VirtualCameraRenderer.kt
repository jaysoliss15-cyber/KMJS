package com.kmjs.virtualcamera.inject

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.view.Surface
import com.kmjs.virtualcamera.frame.KmjsFrameDiagnostics
import com.kmjs.virtualcamera.frame.KmjsFrameManager
import com.kmjs.virtualcamera.frame.TestPatternGenerator
import com.kmjs.virtualcamera.frame.VideoFrame
import com.kmjs.virtualcamera.ipc.KmjsIpcClient
import com.kmjs.virtualcamera.util.KmjsLog
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Dedicated rendering thread attached to an intercepted camera preview/capture Surface.
 * Substitutes physical camera frames with continuous RTSP video frames or fallback test frames
 * delivered from the KMJS pipeline.
 */
class VirtualCameraRenderer(
    private val targetSurface: Surface,
    private val ipcClient: KmjsIpcClient? = null,
    private val surfaceName: String = "Camera2Surface"
) : Thread("KMJS-Renderer-$surfaceName") {

    private val isRunning = AtomicBoolean(true)
    private val matrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val debugPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        textSize = 28f
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }

    private val successfulFrames = AtomicLong(0)
    private val failedFrames = AtomicLong(0)
    private var hasLoggedFirstDelivery = false
    private val fallbackGenerator = TestPatternGenerator(1280, 720)

    val surfaceId: Int = System.identityHashCode(targetSurface)
    val isSurfaceValid: Boolean get() = targetSurface.isValid

    override fun run() {
        KmjsLog.event(
            KmjsLog.TAG_INJECT,
            "INJECTION_RENDERER_START",
            "Surface=$surfaceName (id=$surfaceId), targetValid=${targetSurface.isValid}"
        )

        val targetIntervalMs = 33L // ~30 FPS

        while (isRunning.get() && targetSurface.isValid) {
            val startTime = System.currentTimeMillis()

            try {
                // 1. Try local FrameManager first (if running in same process e.g. test target)
                // 2. Fall back to IPC client (if running inside separate target process)
                var frame: VideoFrame? = KmjsFrameManager.getLatestFrame()
                if (frame?.bitmap == null && ipcClient != null) {
                    frame = ipcClient.fetchLatestFrame()
                }

                // 3. If still no active frame from RTSP, generate a fallback standby test pattern
                if (frame?.bitmap == null) {
                    frame = fallbackGenerator.generateFrame("KMJS Standby (Awaiting RTSP Stream)")
                }

                if (frame.bitmap != null) {
                    KmjsFrameDiagnostics.recordSubmitted()
                    val delivered = renderFrameToSurface(frame.bitmap, frame.width, frame.height)
                    if (delivered) {
                        successfulFrames.incrementAndGet()
                        KmjsFrameDiagnostics.recordSuccess()

                        if (!hasLoggedFirstDelivery) {
                            hasLoggedFirstDelivery = true
                            KmjsLog.event(
                                KmjsLog.TAG_FRAME,
                                "FIRST_FRAME_DELIVERED",
                                "Surface=$surfaceName (id=$surfaceId), res=${frame.width}x${frame.height}"
                            )
                        }
                    } else {
                        failedFrames.incrementAndGet()
                        KmjsFrameDiagnostics.recordFailure()
                    }
                } else {
                    KmjsFrameDiagnostics.recordDropped()
                }
            } catch (t: Throwable) {
                failedFrames.incrementAndGet()
                KmjsFrameDiagnostics.recordFailure()
                KmjsLog.event(
                    KmjsLog.TAG_ERROR,
                    "FRAME_DELIVERY_ERROR",
                    "Surface=$surfaceName, error=${t.message}"
                )
            }

            val elapsed = System.currentTimeMillis() - startTime
            val sleepTime = (targetIntervalMs - elapsed).coerceAtLeast(5L)
            try {
                sleep(sleepTime)
            } catch (e: InterruptedException) {
                break
            }
        }

        KmjsLog.event(
            KmjsLog.TAG_INJECT,
            "INJECTION_RENDERER_STOP",
            "Surface=$surfaceName (id=$surfaceId), success=${successfulFrames.get()}, failed=${failedFrames.get()}"
        )
    }

    /**
     * Renders a frame to the target surface.
     * Returns true ONLY IF lock and unlockCanvasAndPost completed without error.
     */
    private fun renderFrameToSurface(bitmap: Bitmap, frameWidth: Int, frameHeight: Int): Boolean {
        if (!targetSurface.isValid) return false

        var canvas: Canvas? = null
        try {
            canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    targetSurface.lockHardwareCanvas()
                } catch (e: Exception) {
                    targetSurface.lockCanvas(null)
                }
            } else {
                targetSurface.lockCanvas(null)
            }

            if (canvas != null) {
                val canvasWidth = canvas.width
                val canvasHeight = canvas.height

                // Calculate aspect ratio scale matrix with letterboxing/pillarboxing
                matrix.reset()
                val scaleX = canvasWidth.toFloat() / frameWidth.coerceAtLeast(1)
                val scaleY = canvasHeight.toFloat() / frameHeight.coerceAtLeast(1)
                val maxScale = Math.max(scaleX, scaleY)

                val scaledW = frameWidth * maxScale
                val scaledH = frameHeight * maxScale
                val dx = (canvasWidth - scaledW) / 2f
                val dy = (canvasHeight - scaledH) / 2f

                matrix.setScale(maxScale, maxScale)
                matrix.postTranslate(dx, dy)

                canvas.drawColor(Color.BLACK)
                canvas.drawBitmap(bitmap, matrix, paint)

                if (KmjsLog.isDebugEnabled) {
                    canvas.drawText("KMJS Virtual Feed | Frame: ${successfulFrames.get()}", 30f, 60f, debugPaint)
                }

                targetSurface.unlockCanvasAndPost(canvas)
                canvas = null
                return true
            }
            return false
        } catch (e: Exception) {
            KmjsLog.d(KmjsLog.TAG_CAMERA, "Surface render exception on $surfaceName: ${e.message}")
            return false
        } finally {
            if (canvas != null) {
                try {
                    targetSurface.unlockCanvasAndPost(canvas)
                } catch (e: Exception) {
                    // Ignored in cleanup
                }
            }
        }
    }

    fun stopRenderer() {
        isRunning.set(false)
        interrupt()
    }
}
