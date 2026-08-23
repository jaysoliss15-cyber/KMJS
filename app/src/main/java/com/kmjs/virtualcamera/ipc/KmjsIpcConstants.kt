package com.kmjs.virtualcamera.ipc

import android.net.Uri

object KmjsIpcConstants {
    const val AUTHORITY = "com.kmjs.virtualcamera.provider"
    val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/frame")

    const val METHOD_GET_STATUS = "getStatus"
    const val METHOD_GET_LATEST_FRAME = "getLatestFrame"
    const val METHOD_HEARTBEAT = "heartbeat"

    const val KEY_IS_CONNECTED = "isConnected"
    const val KEY_STATE = "state"
    const val KEY_WIDTH = "width"
    const val KEY_HEIGHT = "height"
    const val KEY_FPS = "fps"
    const val KEY_TIMESTAMP = "timestamp"
    const val KEY_FRAME_BYTES = "frameBytes"
    const val KEY_TARGET_PKG = "targetPkg"
    const val KEY_HOOK_INSTALLED = "hookInstalled"

    const val SOCKET_NAME = "kmjs_virtual_camera_ipc_sock"
    const val ACTION_START_RTSP = "com.kmjs.virtualcamera.ACTION_START_RTSP"
    const val ACTION_STOP_RTSP = "com.kmjs.virtualcamera.ACTION_STOP_RTSP"
    const val EXTRA_RTSP_URL = "extra_rtsp_url"
}
