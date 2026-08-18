package com.example.ui.guard

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.camera.CameraPreviewView
import com.example.model.FeeStatus
import com.example.model.Student
import com.example.ui.theme.ApprovedGreen
import com.example.ui.theme.ApprovedGreenLight
import com.example.ui.theme.ApprovedGreenText
import com.example.ui.theme.RejectedRed
import com.example.ui.theme.RejectedRedLight
import com.example.ui.theme.RejectedRedText
import com.example.ui.theme.SchoolPrimary

@Composable
fun GuardScannerScreen(
    sampleStudents: List<Student>,
    onBarcodeDetected: (String) -> Unit,
    onCloseScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isFlashlightOn by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var manualStudentId by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            // Live CameraX Feed
            CameraPreviewView(
                modifier = Modifier.fillMaxSize(),
                isFlashlightOn = isFlashlightOn,
                useFrontCamera = useFrontCamera,
                isScanningActive = true,
                onBarcodeScanned = { rawCode ->
                    onBarcodeDetected(rawCode)
                }
            )

            // Viewfinder Target Overlay
            ScannerViewfinderOverlay()
        } else {
            // Camera Permission Request Fallback
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "The QR scanner requires camera access to verify student badges at the gate.",
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.testTag("button_request_camera_permission")
                ) {
                    Text("Grant Camera Permission")
                }
            }
        }

        // Top Scanner Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCloseScanner,
                modifier = Modifier
                    .testTag("button_close_scanner")
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Scanner",
                    tint = Color.White
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ALIGN QR IN FRAME",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Flashlight toggle
                IconButton(
                    onClick = { isFlashlightOn = !isFlashlightOn },
                    modifier = Modifier
                        .testTag("button_toggle_flashlight")
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isFlashlightOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flashlight",
                        tint = if (isFlashlightOn) Color(0xFFFBBF24) else Color.White
                    )
                }

                // Camera Switch
                IconButton(
                    onClick = { useFrontCamera = !useFrontCamera },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.White
                    )
                }
            }
        }

        // Bottom Controls: Quick Test Barcodes & Manual Entry
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(top = 12.dp, bottom = 24.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FAST TEST BARCODES",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                TextButton(
                    onClick = { showManualDialog = true },
                    modifier = Modifier.testTag("button_scanner_manual_code")
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = null,
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Type Code",
                        color = Color(0xFF60A5FA),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick demo pills
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(sampleStudents) { student ->
                    val isCleared = student.feesStatus == FeeStatus.CLEARED
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCleared) ApprovedGreen.copy(alpha = 0.9f) else RejectedRed.copy(alpha = 0.9f),
                        modifier = Modifier
                            .testTag("scanner_pill_${student.id}")
                            .clickable {
                                onBarcodeDetected(student.id)
                            }
                    ) {
                        Text(
                            text = "${student.firstName} (${student.id})",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }

    // Manual Code Dialog
    if (showManualDialog) {
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text("Manual QR Code Lookup", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = manualStudentId,
                    onValueChange = { manualStudentId = it.uppercase() },
                    placeholder = { Text("e.g. STU-2026-0001") },
                    singleLine = true,
                    modifier = Modifier
                        .testTag("input_scanner_manual_code")
                        .fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualStudentId.isNotBlank()) {
                            onBarcodeDetected(manualStudentId.trim())
                            showManualDialog = false
                        }
                    }
                ) {
                    Text("Verify")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ScannerViewfinderOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "laser_animation")
    val laserPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_line"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val frameSize = size.width * 0.72f
        val left = (size.width - frameSize) / 2f
        val top = (size.height - frameSize) / 2.3f
        val cornerLength = 36f
        val strokeWidth = 8f

        // Transparent Viewfinder Target Frame
        drawRoundRect(
            color = Color.White.copy(alpha = 0.4f),
            topLeft = Offset(left, top),
            size = Size(frameSize, frameSize),
            cornerRadius = CornerRadius(16f, 16f),
            style = Stroke(width = 2f)
        )

        // Corner 1: Top-Left
        drawLine(Color(0xFF3B82F6), Offset(left, top), Offset(left + cornerLength, top), strokeWidth)
        drawLine(Color(0xFF3B82F6), Offset(left, top), Offset(left, top + cornerLength), strokeWidth)

        // Corner 2: Top-Right
        drawLine(Color(0xFF3B82F6), Offset(left + frameSize, top), Offset(left + frameSize - cornerLength, top), strokeWidth)
        drawLine(Color(0xFF3B82F6), Offset(left + frameSize, top), Offset(left + frameSize, top + cornerLength), strokeWidth)

        // Corner 3: Bottom-Left
        drawLine(Color(0xFF3B82F6), Offset(left, top + frameSize), Offset(left + cornerLength, top + frameSize), strokeWidth)
        drawLine(Color(0xFF3B82F6), Offset(left, top + frameSize), Offset(left, top + frameSize - cornerLength), strokeWidth)

        // Corner 4: Bottom-Right
        drawLine(Color(0xFF3B82F6), Offset(left + frameSize, top + frameSize), Offset(left + frameSize - cornerLength, top + frameSize), strokeWidth)
        drawLine(Color(0xFF3B82F6), Offset(left + frameSize, top + frameSize), Offset(left + frameSize, top + frameSize - cornerLength), strokeWidth)

        // Laser Scan Line
        val currentLaserY = top + (frameSize * laserPosition)
        drawLine(
            color = Color(0xFFEF4444),
            start = Offset(left + 8f, currentLaserY),
            end = Offset(left + frameSize - 8f, currentLaserY),
            strokeWidth = 4f
        )
    }
}
