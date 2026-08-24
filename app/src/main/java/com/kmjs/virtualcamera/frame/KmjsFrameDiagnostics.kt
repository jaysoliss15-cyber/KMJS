package com.kmjs.virtualcamera.frame

import com.kmjs.virtualcamera.util.KmjsLog
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe diagnostics tracking for frame pipeline metrics:
 * decodedFrames, convertedFrames, submittedFrames, successfulFrames, failedFrames, droppedFrames.
 */
object KmjsFrameDiagnostics {

    private val _decodedFrames = AtomicLong(0)
    private val _convertedFrames = AtomicLong(0)
    private val _submittedFrames = AtomicLong(0)
    private val _successfulFrames = AtomicLong(0)
    private val _failedFrames = AtomicLong(0)
    private val _droppedFrames = AtomicLong(0)

    val decodedFrames: Long get() = _decodedFrames.get()
    val convertedFrames: Long get() = _convertedFrames.get()
    val submittedFrames: Long get() = _submittedFrames.get()
    val successfulFrames: Long get() = _successfulFrames.get()
    val failedFrames: Long get() = _failedFrames.get()
    val droppedFrames: Long get() = _droppedFrames.get()

    fun recordDecoded(): Long = _decodedFrames.incrementAndGet()
    fun recordConverted(): Long = _convertedFrames.incrementAndGet()
    fun recordSubmitted(): Long = _submittedFrames.incrementAndGet()
    fun recordSuccess(): Long = _successfulFrames.incrementAndGet()
    fun recordFailure(): Long = _failedFrames.incrementAndGet()
    fun recordDropped(): Long = _droppedFrames.incrementAndGet()

    fun reset() {
        _decodedFrames.set(0)
        _convertedFrames.set(0)
        _submittedFrames.set(0)
        _successfulFrames.set(0)
        _failedFrames.set(0)
        _droppedFrames.set(0)
    }

    /**
     * Formats diagnostics string matching:
     * [FRAME] decoded=X converted=Y submitted=Z success=S failed=F dropped=D
     */
    fun toDiagnosticString(): String {
        return "[FRAME] decoded=$decodedFrames converted=$convertedFrames submitted=$submittedFrames success=$successfulFrames failed=$failedFrames dropped=$droppedFrames"
    }

    fun logSummary(tag: String = KmjsLog.TAG_FRAME) {
        KmjsLog.i(tag, toDiagnosticString())
    }
}
