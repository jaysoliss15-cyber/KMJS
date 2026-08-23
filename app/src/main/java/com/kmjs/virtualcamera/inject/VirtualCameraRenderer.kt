package com.kmjs.virtualcamera.inject

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.view.Surface
import com.kmjs.virtualcamera.frame.KmjsFrameManager
import com.kmjs.virtualcamera.frame.VideoFrame
import com.kmjs.virtualcamera.ipc.KmjsIpcClient
import com.kmjs.virtualcamera.util.KmjsLog
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Dedicated rendering thread attached to an intercepted camera preview/capture Surface.
 * Substitutes the physical camera frames with continuous RTSP frames delivered from KMJS.
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

    private var framesRendered = 0L

    override fun run() {
        KmjsLog.i(KmjsLog.TAG_CAMERA, "VirtualCameraRenderer started for surface: $surfaceName")
        KmjsLog.i(KmjsLog.TAG_INJECT, "Frame substitution started for surface: $surfaceName")

        val targetIntervalMs = 33L // ~30 FPS

        while (isRunning.get() && targetSurface.isValid) {
            val startTime = System.currentTimeMillis()

            try {
                // 1. Try local FrameManager first (if same process e.g. test target)
                // 2. Fall back to IPC client (if separate target process under LSPatch/NPatch)
                val frame: VideoFrame? = KmjsFrameManager.getLatestFrame()
                    ?: ipcClient?.fetchLatestFrame()

                if (frame?.bitmap != null) {
                    renderFrameToSurface(frame.bitmap, frame.width, frame.height)
                    framesRendered++
                }
            } catch (t: Throwable) {
                KmjsLog.w(KmjsLog.TAG_CAMERA, "Error rendering frame to surface: ${t.message}")
            }

            val elapsed = System.currentTimeMillis() - startTime
            val sleepTime = (targetIntervalMs - elapsed).coerceAtLeast(5L)
            try {
                sleep(sleepTime)
            } catch (e: InterruptedException) {
                break
            }
        }

        KmjsLog.i(KmjsLog.TAG_INJECT, "Frame substitution stopped for surface: $surfaceName (Total rendered: $framesRendered)")
    }

    private fun renderFrameToSurface(bitmap: Bitmap, frameWidth: Int, frameHeight: Int) {
        if (!targetSurface.isValid) return

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

                // Calculate aspect ratio scale matrix
                matrix.reset()
                val scaleX = canvasWidth.toFloat() / frameWidth
                val scaleY = canvasHeight.toFloat() / frameHeight
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
                    canvas.drawText("KMJS Virtual Feed | Frame: $framesRendered", 30f, 60f, debugPaint)
                }
            }
        } finally {
            if (canvas != null) {
                try {
                    targetSurface.unlockCanvasAndPost(canvas)
                } catch (e: Exception) {
                    KmjsLog.w(KmjsLog.TAG_CAMERA, "Failed to unlockCanvasAndPost: ${e.message}")
                }
            }
        }
    }

    fun stopRenderer() {
        isRunning.set(false)
        interrupt()
    }
}
