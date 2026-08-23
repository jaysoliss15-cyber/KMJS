package com.kmjs.virtualcamera.rtsp

enum class RtspConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

data class RtspConfig(
    val url: String = "rtsp://192.168.1.38:8554/live/obs",
    val autoReconnect: Boolean = true,
    val maxReconnectAttempts: Int = 10,
    val initialReconnectDelayMs: Long = 2000L,
    val maxReconnectDelayMs: Long = 10000L,
    val bufferTimeoutMs: Long = 8000L
) {
    /**
     * Extracts sanitized display URL (masking password if present).
     */
    val sanitizedUrl: String
        get() {
            return try {
                if (url.contains("@") && url.startsWith("rtsp://")) {
                    val atIndex = url.indexOf("@")
                    val protocolEnd = "rtsp://".length
                    val userPass = url.substring(protocolEnd, atIndex)
                    if (userPass.contains(":")) {
                        val user = userPass.substringBefore(":")
                        "rtsp://$user:***@${url.substring(atIndex + 1)}"
                    } else {
                        url
                    }
                } else {
                    url
                }
            } catch (e: Exception) {
                url
            }
        }
}
