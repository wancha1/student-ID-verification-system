package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.Card as StudentCard
import com.example.model.CardStatus
import com.example.model.Student
import com.example.util.QrCodeGenerator
import com.example.util.QrCodeUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * High-fidelity Digital Student ID Card generator with authentic school branding,
 * security guilloche pattern, student avatar, barcode/QR visual, and metadata.
 */
@Composable
fun PrintableStudentIdCard(
    student: Student,
    card: StudentCard?,
    modifier: Modifier = Modifier
) {
    val issueDateStr = remember(card?.issueDate) {
        val ts = card?.issueDate ?: student.updatedAt
        SimpleDateFormat("MMM yyyy", Locale.US).format(Date(ts))
    }
    val cardIdStr = card?.cardIdentifier ?: "CRD-${student.studentNumber.removePrefix("LTC-").removePrefix("OAK-")}-01"
    val cardStatus = card?.status ?: CardStatus.ACTIVE
    val effectiveQrPayload = remember(card?.qrPayload, student.studentNumber, cardIdStr) {
        if (card != null && card.qrPayload.isNotBlank()) card.qrPayload
        else student.uniqueQrCode
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier
            .testTag("printable_student_id_card")
            .fillMaxWidth()
            .border(1.5.dp, Color(0xFF1E3A8A), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // TOP HEADER BAND (NAVY & GOLD ACCENT)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF0F172A), Color(0xFF1E3A8A), Color(0xFF1E40AF))
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "LIRA TOWN COLLEGE",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "LIRA, UGANDA • OFFICIAL STUDENT ID",
                                color = Color(0xFFFCD34D),
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.5.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Card Year Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFF59E0B)
                    ) {
                        Text(
                            text = "2026",
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // CARD BODY WITH SECURITY WATERMARK
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFAFAFA))
                    .padding(14.dp)
            ) {
                // Background subtle security guilloche lines
                SecurityGuillochePattern(modifier = Modifier.matchParentSize())

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Student Passport Avatar
                        Box(
                            modifier = Modifier
                                .size(width = 82.dp, height = 102.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(2.dp, Color(0xFF1E3A8A), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            StudentAvatar(
                                student = student,
                                size = 80.dp
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Student Metadata
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = student.fullName.uppercase(),
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = Color(0xFF0F172A),
                                maxLines = 1
                            )
                            Text(
                                text = "CLASS: ${student.gradeClass.uppercase()}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF1E3A8A)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "STUDENT NO: ${student.studentNumber}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFF475569)
                            )
                            Text(
                                text = "CARD ID: $cardIdStr",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Large Scannable QR Code with centered LTC monogram
                        Box(
                            modifier = Modifier
                                .size(104.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(1.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            VisualQrMatrix(payload = effectiveQrPayload)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Footer Attributes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DirectionsBus,
                                contentDescription = null,
                                tint = Color(0xFF1E3A8A),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (student.isDayScholar) "DAY SCHOLAR • ${student.transportRoute}" else "BOARDER",
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = Color(0xFF334155)
                            )
                        }

                        CardStatusBadge(status = cardStatus)
                    }
                }
            }

            // BOTTOM BARCODE & SECURITY STRIP
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VALID THRU: 12/2026",
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "ISSUED: $issueDateStr",
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "LTC GATE ACCESS VERIFICATION",
                        color = Color(0xFFF59E0B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.5.sp
                    )
                }
            }
        }
    }
}

/**
 * Authentic ZXing QR Matrix representation with centered LTC monogram.
 * Uses Level H error correction for 100% reliable camera and hardware scanner readability.
 */
@Composable
fun VisualQrMatrix(
    payload: String,
    modifier: Modifier = Modifier.fillMaxSize(),
    addLtcLogo: Boolean = true
) {
    val bitmap = remember(payload, addLtcLogo) {
        QrCodeGenerator.generateQrBitmap(
            content = payload,
            size = 500,
            addLtcLogo = addLtcLogo
        )
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QR Code for $payload",
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text("QR ERROR", fontSize = 10.sp, color = Color.Red)
        }
    }
}

@Composable
private fun SecurityGuillochePattern(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val step = 16f
        val stroke = Stroke(width = 1f)
        val color = Color(0xFF1E3A8A).copy(alpha = 0.05f)

        for (i in 0..(size.width + size.height).toInt() step step.toInt()) {
            drawLine(
                color = color,
                start = Offset(0f, i.toFloat()),
                end = Offset(i.toFloat(), 0f),
                strokeWidth = 1f
            )
        }
    }
}

/**
 * Full Dialog preview with print / export / test scan affordances.
 */
@Composable
fun DigitalIdCardDialog(
    student: Student,
    card: StudentCard?,
    onDismiss: () -> Unit,
    onTestScan: (String) -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .testTag("dialog_digital_id_card")
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Dialog Title Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Digital ID Card Generator",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(
                    text = "Official printable identity card for ${student.fullName}. Gate terminals scan the embedded QR code offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Official Card View
                PrintableStudentIdCard(
                    student = student,
                    card = card
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Card Actions Grid
                Text(
                    text = "ID Badge Actions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            // Test scan this student's active card QR payload
                            val testPayload = card?.qrPayload?.takeIf { it.isNotBlank() } ?: student.uniqueQrCode
                            onTestScan(testPayload)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("button_test_scan_card"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simulate Gate Scan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close Preview")
                }
            }
        }
    }
}
