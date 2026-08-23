package com.kmjs.virtualcamera.ipc

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import com.kmjs.virtualcamera.frame.KmjsFrameManager
import com.kmjs.virtualcamera.util.KmjsLog
import java.io.ByteArrayOutputStream

/**
 * Android ContentProvider IPC endpoint.
 * Enables the injected Xposed / LSPatch / NPatch module running inside the target app process
 * to fetch stream state, configuration, and decoded frames across Android process boundaries.
 */
class KmjsFrameContentProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        KmjsLog.i(KmjsLog.TAG_SERVICE, "KmjsFrameContentProvider initialized for IPC")
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val response = Bundle()

        when (method) {
            KmjsIpcConstants.METHOD_GET_STATUS -> {
                val stats = KmjsFrameManager.currentStats
                val frame = KmjsFrameManager.getLatestFrame()
                response.putBoolean(KmjsIpcConstants.KEY_IS_CONNECTED, frame != null)
                response.putInt(KmjsIpcConstants.KEY_WIDTH, stats.width.takeIf { it > 0 } ?: 1280)
                response.putInt(KmjsIpcConstants.KEY_HEIGHT, stats.height.takeIf { it > 0 } ?: 720)
                response.putFloat(KmjsIpcConstants.KEY_FPS, stats.fps)
                response.putLong(KmjsIpcConstants.KEY_TIMESTAMP, stats.lastFrameTimestampNs)
                response.putString(KmjsIpcConstants.KEY_STATE, stats.sourceDescription)
                return response
            }

            KmjsIpcConstants.METHOD_GET_LATEST_FRAME -> {
                val frame = KmjsFrameManager.getLatestFrame()
                if (frame != null && frame.bitmap != null) {
                    response.putBoolean(KmjsIpcConstants.KEY_IS_CONNECTED, true)
                    response.putInt(KmjsIpcConstants.KEY_WIDTH, frame.width)
                    response.putInt(KmjsIpcConstants.KEY_HEIGHT, frame.height)
                    response.putLong(KmjsIpcConstants.KEY_TIMESTAMP, frame.timestampNs)

                    // Compress JPEG buffer for cross-process IPC parceling
                    val stream = ByteArrayOutputStream()
                    frame.bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    response.putByteArray(KmjsIpcConstants.KEY_FRAME_BYTES, stream.toByteArray())
                } else {
                    response.putBoolean(KmjsIpcConstants.KEY_IS_CONNECTED, false)
                }
                return response
            }

            KmjsIpcConstants.METHOD_HEARTBEAT -> {
                val targetPkg = extras?.getString(KmjsIpcConstants.KEY_TARGET_PKG) ?: "Unknown"
                KmjsLog.i(KmjsLog.TAG_INJECT, "Received heartbeat from injected target process: $targetPkg")
                response.putBoolean("acknowledged", true)
                return response
            }
        }

        return response
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = "vnd.android.cursor.dir/vnd.com.kmjs.virtualcamera.frame"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
