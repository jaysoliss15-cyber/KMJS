package com.kmjs.virtualcamera.ipc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import com.kmjs.virtualcamera.frame.VideoFrame
import com.kmjs.virtualcamera.util.KmjsLog

/**
 * IPC Client running inside the hooked target process (or within KMJS).
 * Communicates with the KMJS Foreground Service via ContentProvider IPC
 * to retrieve virtual camera frames for camera feed substitution.
 */
class KmjsIpcClient(private val context: Context) {

    private val providerUri: Uri = KmjsIpcConstants.CONTENT_URI

    fun sendHeartbeat(targetPkg: String): Boolean {
        return try {
            val extras = Bundle().apply {
                putString(KmjsIpcConstants.KEY_TARGET_PKG, targetPkg)
            }
            val res = context.contentResolver.call(
                providerUri,
                KmjsIpcConstants.METHOD_HEARTBEAT,
                null,
                extras
            )
            res?.getBoolean("acknowledged", false) ?: false
        } catch (e: Exception) {
            KmjsLog.w(KmjsLog.TAG_INJECT, "Heartbeat to KMJS service failed: ${e.message}")
            false
        }
    }

    fun fetchLatestFrame(): VideoFrame? {
        return try {
            val res = context.contentResolver.call(
                providerUri,
                KmjsIpcConstants.METHOD_GET_LATEST_FRAME,
                null,
                null
            ) ?: return null

            val isConnected = res.getBoolean(KmjsIpcConstants.KEY_IS_CONNECTED, false)
            if (!isConnected) return null

            val width = res.getInt(KmjsIpcConstants.KEY_WIDTH, 1280)
            val height = res.getInt(KmjsIpcConstants.KEY_HEIGHT, 720)
            val timestamp = res.getLong(KmjsIpcConstants.KEY_TIMESTAMP, System.nanoTime())
            val frameBytes = res.getByteArray(KmjsIpcConstants.KEY_FRAME_BYTES)

            var bitmap: Bitmap? = null
            if (frameBytes != null && frameBytes.isNotEmpty()) {
                bitmap = BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.size)
            }

            VideoFrame(
                bitmap = bitmap,
                width = width,
                height = height,
                timestampNs = timestamp
            )
        } catch (e: Exception) {
            KmjsLog.w(KmjsLog.TAG_INJECT, "Failed to fetch frame from KMJS service IPC: ${e.message}")
            null
        }
    }
}
