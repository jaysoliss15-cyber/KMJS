package com.kmjs.virtualcamera.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

object KmjsLog {
    const val TAG_GENERAL = "KMJS"
    const val TAG_RTSP = "KMJS-RTSP"
    const val TAG_SERVICE = "KMJS-SERVICE"
    const val TAG_FRAME = "KMJS-FRAME"
    const val TAG_INJECT = "KMJS-INJECT"
    const val TAG_CAMERA = "KMJS-CAMERA"

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val tag: String,
        val level: String,
        val message: String,
        val throwable: Throwable? = null
    ) {
        val formattedTime: String
            get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
    }

    private const val MAX_LOGS = 300
    private val logBuffer = ConcurrentLinkedDeque<LogEntry>()
    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    var isDebugEnabled: Boolean = true

    private fun addEntry(tag: String, level: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(tag = tag, level = level, message = message, throwable = throwable)
        logBuffer.addLast(entry)
        while (logBuffer.size > MAX_LOGS) {
            logBuffer.pollFirst()
        }
        _logsFlow.value = logBuffer.toList()
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        addEntry(tag, "I", message)
    }

    fun d(tag: String, message: String) {
        if (isDebugEnabled) {
            Log.d(tag, message)
            addEntry(tag, "D", message)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
        addEntry(tag, "W", message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
        addEntry(tag, "E", message, throwable)
    }

    fun clear() {
        logBuffer.clear()
        _logsFlow.value = emptyList()
    }
}
