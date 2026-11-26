package com.example.camera

import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

@Composable
fun CameraPreviewView(
    modifier: Modifier = Modifier,
    isFlashlightOn: Boolean = false,
    useFrontCamera: Boolean = false,
    isScanningActive: Boolean = true,
    onBarcodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBarcodeScanned by rememberUpdatedState(onBarcodeScanned)
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember {
        QrBarcodeAnalyzer { rawText ->
            currentOnBarcodeScanned(rawText)
        }
    }

    analyzer.setScanningEnabled(isScanningActive)

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(context) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                cameraProvider = future.get()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(cameraProvider, previewView, lifecycleOwner, useFrontCamera) {
        val provider = cameraProvider ?: return@LaunchedEffect
        val view = previewView ?: return@LaunchedEffect
        runCatching {
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = view.surfaceProvider
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, analyzer)
                }
            val selector = if (useFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            provider.unbindAll()
            val bound = provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                imageAnalysis
            )
            activeCamera = bound
            if (bound.cameraInfo.hasFlashUnit()) {
                bound.cameraControl.enableTorch(isFlashlightOn)
            }
        }
    }

    LaunchedEffect(activeCamera, isFlashlightOn) {
        val cam = activeCamera ?: return@LaunchedEffect
        if (cam.cameraInfo.hasFlashUnit()) {
            runCatching { cam.cameraControl.enableTorch(isFlashlightOn) }
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }.also { created ->
                previewView = created
            }
        },
        modifier = modifier.fillMaxSize()
    )

    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProvider?.unbindAll() }
            analyzer.close()
            cameraExecutor.shutdown()
        }
    }
}
