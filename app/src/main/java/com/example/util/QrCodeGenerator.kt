package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

object QrCodeGenerator {

    /**
     * Generates a square QR Code Bitmap with optional centered LTC monogram.
     * Uses Level H error correction (30% Reed-Solomon redundancy). The central LTC
     * emblem occupies approximately 2.5% of the surface area, well within Level H tolerance,
     * ensuring 100% reliable camera and hardware scanner readability.
     */
    fun generateQrBitmap(
        content: String,
        size: Int = 600,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE,
        addLtcLogo: Boolean = true
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.MARGIN, 1) // 1 module quiet border
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
            }

            val bitMatrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size,
                hints
            )

            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) foregroundColor else backgroundColor
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

            if (!addLtcLogo) {
                return bitmap
            }

            // Draw centered LTC emblem
            val canvas = Canvas(bitmap)
            val emblemRadius = width * 0.09f // Diameter = 18% of QR width
            val centerX = width / 2f
            val centerY = height / 2f

            // 1. Solid white background circle
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawCircle(centerX, centerY, emblemRadius, bgPaint)

            // 2. High-contrast Navy ring border
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42) // Deep Slate / Navy
                style = Paint.Style.STROKE
                strokeWidth = width * 0.014f
            }
            canvas.drawCircle(centerX, centerY, emblemRadius - (ringPaint.strokeWidth / 2f), ringPaint)

            // 3. Inner Gold Accent Circle
            val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(245, 158, 11) // Gold
                style = Paint.Style.FILL
            }
            canvas.drawCircle(centerX, centerY, emblemRadius * 0.76f, goldPaint)

            // 4. Centered bold high-contrast "LTC" monogram
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42) // Navy text on gold
                textSize = emblemRadius * 0.70f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            val fontMetrics = textPaint.fontMetrics
            val baseline = centerY - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText("LTC", centerX, baseline, textPaint)

            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
