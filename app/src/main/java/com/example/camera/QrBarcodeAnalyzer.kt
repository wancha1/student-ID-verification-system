package com.example.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class QrBarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )
    private val isScanningEnabled = AtomicBoolean(true)
    private val isClosed = AtomicBoolean(false)
    private val lastDetectedAtMs = AtomicLong(0L)

    fun setScanningEnabled(enabled: Boolean) {
        isScanningEnabled.set(enabled)
    }

    fun close() {
        if (isClosed.compareAndSet(false, true)) {
            runCatching { scanner.close() }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || isClosed.get() || !isScanningEnabled.get()) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (isClosed.get() || !isScanningEnabled.get()) return@addOnSuccessListener
                val now = System.currentTimeMillis()
                if (now - lastDetectedAtMs.get() < SCAN_COOLDOWN_MS) return@addOnSuccessListener

                for (barcode in barcodes) {
                    val rawValue = barcode.rawValue ?: barcode.displayValue
                    if (!rawValue.isNullOrBlank() && lastDetectedAtMs.compareAndSet(lastDetectedAtMs.get(), now)) {
                        isScanningEnabled.set(false)
                        onBarcodeDetected(rawValue)
                        break
                    }
                }
            }
            .addOnFailureListener {
                // Ignore transient analysis frame failures
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private companion object {
        const val SCAN_COOLDOWN_MS = 1200L
    }
}
