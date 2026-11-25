package com.example.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FeeStatus
import com.example.model.GateVerificationDecision
import com.example.model.ScanLog
import com.example.model.Student
import com.example.ui.theme.ApprovedGreen
import com.example.ui.theme.ApprovedGreenLight
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.PendingAmberLight
import com.example.ui.theme.RejectedRed
import java.util.Calendar

@Composable
fun GateAnalyticsCard(
    students: List<Student>,
    scanLogs: List<ScanLog>,
    modifier: Modifier = Modifier
) {
    val totalStudents = students.size
    val clearedCount = students.count { it.feesStatus == FeeStatus.CLEARED }
    val clearancePct = if (totalStudents > 0) (clearedCount * 100 / totalStudents) else 0
    val clearanceDisplay = if (totalStudents > 0) "$clearancePct%" else "--"
    val clearanceSubtitle = if (totalStudents > 0) "$clearedCount/$totalStudents cleared" else "0 students"

    val totalScans = scanLogs.size
    val approvedScans = scanLogs.count { it.decision == GateVerificationDecision.APPROVED }
    val deniedScans = scanLogs.count { it.decision != GateVerificationDecision.APPROVED }
    val approvalPct = if (totalScans > 0) (approvedScans * 100 / totalScans) else 0
    val approvalDisplay = if (totalScans > 0) "$approvalPct%" else "--"
    val approvalSubtitle = if (totalScans > 0) "$approvedScans/$totalScans passed" else "0 scans"

    val hourlyData = remember(scanLogs) {
        val buckets = IntArray(13) { 0 }
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
    val hasTraffic = hourlyData.any { it > 0 }
    val peakHourIndex = if (hasTraffic) hourlyData.indices.maxByOrNull { hourlyData[it] } ?: -1 else -1
    val peakHourDisplay = if (hasTraffic) "${peakHourIndex + 6}:00" else "--"
    val peakHourSubtitle = if (hasTraffic) "Peak hour" else "No scans"

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_gate_analytics")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Gate & Fee Pulse",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Today's entry compliance",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricPill(
                    title = "Pass Rate",
                    value = approvalDisplay,
                    subtitle = approvalSubtitle,
                    icon = Icons.Default.CheckCircle,
                    tint = ApprovedGreen,
                    containerColor = ApprovedGreenLight.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f)
                )
                MetricPill(
                    title = "Cleared",
                    value = clearanceDisplay,
                    subtitle = clearanceSubtitle,
                    icon = Icons.Default.CreditCard,
                    tint = PendingAmber,
                    containerColor = PendingAmberLight.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f)
                )
                MetricPill(
                    title = "Peak",
                    value = peakHourDisplay,
                    subtitle = peakHourSubtitle,
                    icon = Icons.Default.Schedule,
                    tint = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Hourly gate activity (06:00 - 18:00)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                val hourLabels = listOf("6a", "7a", "8a", "9a", "10a", "11a", "12p", "1p", "2p", "3p", "4p", "5p", "6p")
                val primary = MaterialTheme.colorScheme.primary
                val emptyBarColor = MaterialTheme.colorScheme.outlineVariant
                hourlyData.forEachIndexed { index, count ->
                    val heightRatio = if (maxHourlyCount > 0) (count.toFloat() / maxHourlyCount.toFloat()).coerceIn(0.14f, 1.0f) else 0.14f
                    val isPeak = index == peakHourIndex && count > 0

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (count > 0) {
                            Text(
                                text = "$count",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPeak) primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .height((32 * heightRatio).dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(
                                    when {
                                        isPeak -> Brush.verticalGradient(listOf(GoldAccent, primary))
                                        count > 0 -> Brush.verticalGradient(listOf(primary.copy(alpha = 0.7f), primary))
                                        else -> Brush.verticalGradient(listOf(emptyBarColor, emptyBarColor))
                                    }
                                )
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = hourLabels.getOrElse(index) { "" },
                            fontSize = 7.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = if (totalScans > 0) {
                    "$approvedScans approved • $deniedScans denied"
                } else {
                    "No gate scans logged yet"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            ) {
                if (totalScans == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                } else {
                    val approvedWeight = (approvedScans.toFloat() / totalScans.toFloat()).coerceAtLeast(0.01f)
                    val deniedWeight = (deniedScans.toFloat() / totalScans.toFloat()).coerceAtLeast(0.001f)

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
}

@Composable
private fun MetricPill(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
