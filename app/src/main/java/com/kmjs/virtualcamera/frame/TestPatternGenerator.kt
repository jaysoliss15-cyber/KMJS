package com.kmjs.virtualcamera.frame

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates dynamic SMPTE color bar test frames with live timestamp and KMJS status watermark.
 * Used during stream initialization, fallback, or zero-camera testing mode.
 */
class TestPatternGenerator(
    private val width: Int = 1280,
    private val height: Int = 720
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        style = Paint.Style.FILL
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 20, 20, 30)
        style = Paint.Style.FILL
    }

    private val colors = intArrayOf(
        Color.rgb(255, 255, 255), // White
        Color.rgb(255, 255, 0),   // Yellow
        Color.rgb(0, 255, 255),   // Cyan
        Color.rgb(0, 255, 0),     // Green
        Color.rgb(255, 0, 255),   // Magenta
        Color.rgb(255, 0, 0),     // Red
        Color.rgb(0, 0, 255)      // Blue
    )

    private var frameCount = 0L

    fun generateFrame(customSubtitle: String = "KMJS Virtual Camera Active"): VideoFrame {
        frameCount++
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw top 7 color bars (75% height)
        val barWidth = width.toFloat() / colors.size
        val topBarHeight = height * 0.70f

        for (i in colors.indices) {
            paint.color = colors[i]
            canvas.drawRect(i * barWidth, 0f, (i + 1) * barWidth, topBarHeight, paint)
        }

        // Draw bottom section (Darker / Gray / Blue / Black)
        val bottomHeight = height - topBarHeight
        val bottomBarWidth = width.toFloat() / 5
        val bottomColors = intArrayOf(
            Color.rgb(0, 33, 71),    // Deep Navy
            Color.rgb(255, 255, 255),// White
            Color.rgb(50, 0, 106),   // Purple
            Color.rgb(19, 19, 19),   // Black
            Color.rgb(60, 60, 60)    // Dark Gray
        )
        for (i in bottomColors.indices) {
            paint.color = bottomColors[i]
            canvas.drawRect(i * bottomBarWidth, topBarHeight, (i + 1) * bottomBarWidth, height.toFloat(), paint)
        }

        // Draw moving scanline / animation indicator
        val scanX = (frameCount * 8) % width
        paint.color = Color.argb(160, 0, 255, 200)
        paint.strokeWidth = 6f
        canvas.drawLine(scanX.toFloat(), 0f, scanX.toFloat(), height.toFloat(), paint)

        // Draw center info card
        val cardWidth = width * 0.65f
        val cardHeight = 160f
        val cardLeft = (width - cardWidth) / 2f
        val cardTop = (height - cardHeight) / 2f
        val cardRect = Rect(cardLeft.toInt(), cardTop.toInt(), (cardLeft + cardWidth).toInt(), (cardTop + cardHeight).toInt())
        canvas.drawRoundRect(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight, 16f, 16f, badgePaint)

        // Draw text info
        textPaint.textSize = 38f
        textPaint.isFakeBoldText = true
        textPaint.color = Color.rgb(0, 255, 220)
        canvas.drawText("KMJS VIRTUAL CAMERA FEED", cardLeft + 24f, cardTop + 50f, textPaint)

        textPaint.textSize = 28f
        textPaint.isFakeBoldText = false
        textPaint.color = Color.WHITE
        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        canvas.drawText("Time: $timeStr | Frame #$frameCount", cardLeft + 24f, cardTop + 95f, textPaint)

        textPaint.color = Color.rgb(255, 215, 0)
        canvas.drawText(customSubtitle, cardLeft + 24f, cardTop + 135f, textPaint)

        return VideoFrame(
            bitmap = bitmap,
            width = width,
            height = height,
            timestampNs = System.nanoTime(),
            rotationDegrees = 0,
            sequenceNumber = frameCount
        )
    }
}
