package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kmjs.virtualcamera.frame.KmjsFrameDiagnostics
import com.kmjs.virtualcamera.frame.KmjsFrameManager
import com.kmjs.virtualcamera.frame.TestPatternGenerator
import com.kmjs.virtualcamera.frame.VideoFrame
import com.kmjs.virtualcamera.inject.CameraApiDetector
import com.kmjs.virtualcamera.inject.CameraApiType
import com.kmjs.virtualcamera.inject.SupportedTargetRegistry
import com.kmjs.virtualcamera.inject.TargetAppConfig
import com.kmjs.virtualcamera.inject.TargetProcessDetector
import com.kmjs.virtualcamera.rtsp.RtspConfig
import com.kmjs.virtualcamera.util.KmjsLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("KMJS", appName)
    }

    @Test
    fun `test pattern generator creates valid frame`() {
        val generator = TestPatternGenerator(1280, 720)
        val frame = generator.generateFrame("Test Frame")
        assertNotNull(frame.bitmap)
        assertEquals(1280, frame.width)
        assertEquals(720, frame.height)
        assertTrue(frame.sequenceNumber > 0)
    }

    @Test
    fun `frame manager registers and delivers frames to consumer`() {
        var receivedFrame: VideoFrame? = null
        val consumer = com.kmjs.virtualcamera.frame.FrameConsumer { frame ->
            receivedFrame = frame
        }

        KmjsFrameManager.registerConsumer(consumer)
        val testFrame = VideoFrame(width = 1920, height = 1080, timestampNs = 12345L)
        KmjsFrameManager.publishFrame(testFrame)

        assertNotNull(receivedFrame)
        assertEquals(1920, receivedFrame?.width)
        assertEquals(1080, receivedFrame?.height)

        KmjsFrameManager.unregisterConsumer(consumer)
    }

    @Test
    fun `rtsp config sanitizes user passwords in display url`() {
        val config = RtspConfig(url = "rtsp://admin:secretPass123@192.168.1.50:8554/live")
        assertEquals("rtsp://admin:***@192.168.1.50:8554/live", config.sanitizedUrl)
    }

    @Test
    fun `target process detector correctly identifies registered and wildcard targets`() {
        // Main process of registered photo camera
        val photoResult = TargetProcessDetector.inspect("com.photo.android.camera", "com.photo.android.camera")
        assertTrue(photoResult.shouldInject)
        assertTrue(photoResult.isMainProcess)
        assertFalse(photoResult.isAuxiliaryProcess)

        // Auxiliary process should be filtered
        val pushResult = TargetProcessDetector.inspect("com.photo.android.camera", "com.photo.android.camera:push")
        assertFalse(pushResult.shouldInject)
        assertTrue(pushResult.isAuxiliaryProcess)

        // Wildcard target matching
        SupportedTargetRegistry.isWildcardModeEnabled = true
        val randomResult = TargetProcessDetector.inspect("com.custom.app", "com.custom.app")
        assertTrue(randomResult.shouldInject)
    }

    @Test
    fun `supported target registry allows dynamic target configuration`() {
        val customTarget = TargetAppConfig(
            packageName = "com.sample.scanner",
            displayName = "Barcode Scanner",
            preferredApi = CameraApiType.CAMERA2
        )
        SupportedTargetRegistry.register(customTarget)

        val match = SupportedTargetRegistry.findMatchingTarget("com.sample.scanner")
        assertNotNull(match)
        assertEquals("Barcode Scanner", match?.displayName)
    }

    @Test
    fun `frame diagnostics accurately formats diagnostic string`() {
        KmjsFrameDiagnostics.reset()
        KmjsFrameDiagnostics.recordDecoded()
        KmjsFrameDiagnostics.recordConverted()
        KmjsFrameDiagnostics.recordSubmitted()
        KmjsFrameDiagnostics.recordSuccess()

        val diag = KmjsFrameDiagnostics.toDiagnosticString()
        assertTrue(diag.contains("decoded=1"))
        assertTrue(diag.contains("converted=1"))
        assertTrue(diag.contains("submitted=1"))
        assertTrue(diag.contains("success=1"))
        assertTrue(diag.contains("failed=0"))
        assertTrue(diag.contains("dropped=0"))
    }

    @Test
    fun `supported target registry retrieves all default targets`() {
        val targets = SupportedTargetRegistry.getAllTargets()
        assertTrue(targets.isNotEmpty())
        val photoTarget = SupportedTargetRegistry.findMatchingTarget("com.photo.android.camera")
        assertNotNull(photoTarget)
        assertEquals("Android Photo Camera", photoTarget?.displayName)
    }

    @Test
    fun `target process detector filters non-matching target when wildcard disabled`() {
        SupportedTargetRegistry.isWildcardModeEnabled = false
        val nonTarget = TargetProcessDetector.inspect("com.random.unregistered.app", "com.random.unregistered.app")
        assertFalse(nonTarget.shouldInject)
        assertTrue(nonTarget.skipReason?.contains("not registered", ignoreCase = true) == true)
    }

    @Test
    fun `kmjs log entries can be recorded and cleared`() {
        KmjsLog.clear()
        KmjsLog.event(KmjsLog.TAG_INJECT, "TEST_EVENT", "Testing log system")
        val logs = KmjsLog.logsFlow.value
        assertTrue(logs.any { it.tag == KmjsLog.TAG_INJECT && it.message.contains("TEST_EVENT") })
        KmjsLog.clear()
        assertEquals(0, KmjsLog.logsFlow.value.size)
    }
}
