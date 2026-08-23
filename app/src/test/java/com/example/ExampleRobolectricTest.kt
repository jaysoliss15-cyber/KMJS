package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kmjs.virtualcamera.frame.KmjsFrameManager
import com.kmjs.virtualcamera.frame.TestPatternGenerator
import com.kmjs.virtualcamera.frame.VideoFrame
import com.kmjs.virtualcamera.rtsp.RtspConfig
import org.junit.Assert.assertEquals
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
}

