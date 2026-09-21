package com.example.ui.admin

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FeeStatus
import com.example.model.GateVerificationDecision
import com.example.model.ScanLog
import com.example.model.Student
import com.example.ui.theme.ApprovedGreen
import com.example.ui.theme.ApprovedGreenLight
import com.example.ui.theme.ApprovedGreenText
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.RejectedRed
import com.example.ui.theme.RejectedRedLight
import com.example.ui.theme.SchoolPrimary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun GateAnalyticsCard(
    students: List<Student>,
    scanLogs: List<ScanLog>,
    modifier: Modifier = Modifier
) {
    val totalStudents = students.size
    val clearedCount = students.count { it.feesStatus == FeeStatus.CLEARED }
    val clearancePct = if (totalStudents > 0) (clearedCount * 100 / totalStudents) else 0

    val totalScans = scanLogs.size
    val approvedScans = scanLogs.count { it.decision == GateVerificationDecision.APPROVED }
    val deniedScans = scanLogs.count { it.decision != GateVerificationDecision.APPROVED }
    val approvalPct = if (totalScans > 0) (approvedScans * 100 / totalScans) else 100

    // Hourly scan buckets (6 AM to 18 PM)
    val hourlyData = remember(scanLogs) {
        val buckets = IntArray(13) { 0 } // 6 AM (index 0) to 18 PM (index 12)
        val cal = Calendar.getInstance()
        for (log in scanLogs) {
            cal.timeInMillis = log.timestamp
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            if (hour in 6..18) {
                buckets[hour - 6]++
            }
        }
        buckets
    }

    val maxHourlyCount = hourlyData.maxOrNull()?.coerceAtLeast(1) ?: 1
    val peakHourIndex = hourlyData.indices.maxByOrNull { hourlyData[it] } ?: 1
    val peakHourLabel = "${peakHourIndex + 6}:00 - ${peakHourIndex + 7}:00"

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_gate_analytics")
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(20.dp)
            )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SchoolPrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = SchoolPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Gate Access & Security Analytics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Real-time compliance & entry metrics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = SchoolPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Live Pulse",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp,
                            color = SchoolPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3 Quick Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricPill(
                    title = "Pass Rate",
                    value = "$approvalPct%",
                    subtitle = "$approvedScans / $totalScans passed",
                    icon = Icons.Default.CheckCircle,
                    tint = ApprovedGreen,
                    containerColor = ApprovedGreenLight.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )

                MetricPill(
                    title = "Fee Clearance",
                    value = "$clearancePct%",
                    subtitle = "$clearedCount / $totalStudents cleared",
                    icon = Icons.Default.CreditCard,
                    tint = GoldAccent,
                    containerColor = GoldAccent.copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f)
                )

                MetricPill(
                    title = "Peak Traffic",
                    value = peakHourLabel.split(" - ").firstOrNull() ?: "07:00",
                    subtitle = "Rush Hour",
                    icon = Icons.Default.Schedule,
                    tint = SchoolPrimary,
                    containerColor = SchoolPrimary.copy(alpha = 0.1f),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Hourly Distribution Bar Graph
            Text(
                text = "Gate Traffic Distribution by Hour (06:00 - 18:00)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Visual bar chart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                val hourLabels = listOf("6a", "7a", "8a", "9a", "10a", "11a", "12p", "1p", "2p", "3p", "4p", "5p", "6p")
                hourlyData.forEachIndexed { index, count ->
                    val heightRatio = if (maxHourlyCount > 0) (count.toFloat() / maxHourlyCount.toFloat()).coerceIn(0.12f, 1.0f) else 0.12f
                    val isPeak = index == peakHourIndex && count > 0

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (count > 0) {
                            Text(
                                text = "$count",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPeak) SchoolPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .height((36 * heightRatio).dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (isPeak)
                                        Brush.verticalGradient(listOf(GoldAccent, SchoolPrimary))
                                    else if (count > 0)
                                        Brush.verticalGradient(listOf(SchoolPrimary.copy(alpha = 0.7f), SchoolPrimary))
                                    else
                                        Brush.verticalGradient(listOf(Color.LightGray.copy(alpha = 0.3f), Color.LightGray.copy(alpha = 0.4f)))
                                )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = hourLabels.getOrElse(index) { "" },
                            fontSize = 7.5.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Access Verification Ratios Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gate Access Integrity: $approvedScans Approved • $deniedScans Denied / Flagged",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                val approvedWeight = if (totalScans > 0) (approvedScans.toFloat() / totalScans.toFloat()).coerceAtLeast(0.01f) else 1f
                val deniedWeight = if (totalScans > 0) (deniedScans.toFloat() / totalScans.toFloat()).coerceAtLeast(0.001f) else 0f

                Box(
                    modifier = Modifier
                        .weight(approvedWeight)
                        .fillMaxHeight()
                        .background(ApprovedGreen)
                )
                if (deniedScans > 0) {
                    Box(
                        modifier = Modifier
                            .weight(deniedWeight)
                            .fillMaxHeight()
                            .background(RejectedRed)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricPill(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 9.5.sp,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1
            )
        }
    }
}
