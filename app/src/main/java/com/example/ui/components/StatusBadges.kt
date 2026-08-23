package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CardStatus
import com.example.model.DayScholarStatus
import com.example.model.FeeStatus
import com.example.model.GateVerificationDecision
import com.example.model.Student
import com.example.ui.theme.ApprovedGreen
import com.example.ui.theme.ApprovedGreenDark
import com.example.ui.theme.ApprovedGreenLight
import com.example.ui.theme.RejectedRed
import com.example.ui.theme.RejectedRedDark
import com.example.ui.theme.RejectedRedLight

@Composable
fun FeeStatusBadge(
    status: FeeStatus = FeeStatus.CLEARED,
    feeStatus: FeeStatus = status,
    modifier: Modifier = Modifier,
    isLarge: Boolean = false
) {
    val activeStatus = if (feeStatus != FeeStatus.CLEARED && status == FeeStatus.CLEARED) feeStatus else (if (status != FeeStatus.CLEARED) status else feeStatus)
    val isCleared = activeStatus == FeeStatus.CLEARED
    val bgColor = if (isCleared) ApprovedGreenLight else RejectedRedLight
    val borderColor = if (isCleared) ApprovedGreen else RejectedRed
    val contentColor = if (isCleared) ApprovedGreenDark else RejectedRedDark
    val icon = if (isCleared) Icons.Default.CheckCircle else Icons.Default.Error
    val label = if (isCleared) "FEES CLEARED" else "FEES OUTSTANDING"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .testTag(if (isCleared) "badge_fees_cleared" else "badge_fees_outstanding")
            .clip(RoundedCornerShape(if (isLarge) 12.dp else 8.dp))
            .background(bgColor)
            .border(if (isLarge) 2.dp else 1.dp, borderColor, RoundedCornerShape(if (isLarge) 12.dp else 8.dp))
            .padding(
                horizontal = if (isLarge) 16.dp else 10.dp,
                vertical = if (isLarge) 8.dp else 5.dp
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(if (isLarge) 22.dp else 16.dp)
        )
        Spacer(modifier = Modifier.width(if (isLarge) 8.dp else 6.dp))
        Text(
            text = label,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = if (isLarge) 15.sp else 12.sp,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun CardStatusBadge(
    status: CardStatus,
    modifier: Modifier = Modifier,
    isLarge: Boolean = false
) {
    val (bgColor, borderColor, contentColor, icon, text) = when (status) {
        CardStatus.ACTIVE -> Tuple5(
            ApprovedGreenLight,
            ApprovedGreen,
            ApprovedGreenDark,
            Icons.Default.CheckCircle,
            "CARD ACTIVE"
        )
        CardStatus.LOST -> Tuple5(
            Color(0xFFFFE4E6),
            Color(0xFFE11D48),
            Color(0xFF9F1239),
            Icons.Default.Cancel,
            "REPORTED LOST"
        )
        CardStatus.REPLACED -> Tuple5(
            Color(0xFFFEF3C7),
            Color(0xFFD97706),
            Color(0xFF92400E),
            Icons.Default.SyncAlt,
            "REPLACED"
        )
        CardStatus.DEACTIVATED -> Tuple5(
            Color(0xFFF1F5F9),
            Color(0xFF64748B),
            Color(0xFF334155),
            Icons.Default.Lock,
            "DEACTIVATED"
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .testTag("badge_card_${status.name.lowercase()}")
            .clip(RoundedCornerShape(if (isLarge) 12.dp else 8.dp))
            .background(bgColor)
            .border(if (isLarge) 2.dp else 1.dp, borderColor, RoundedCornerShape(if (isLarge) 12.dp else 8.dp))
            .padding(
                horizontal = if (isLarge) 14.dp else 8.dp,
                vertical = if (isLarge) 6.dp else 4.dp
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = contentColor,
            modifier = Modifier.size(if (isLarge) 18.dp else 14.dp)
        )
        Spacer(modifier = Modifier.width(if (isLarge) 6.dp else 4.dp))
        Text(
            text = text,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = if (isLarge) 13.sp else 11.sp,
            letterSpacing = 0.4.sp
        )
    }
}

private data class Tuple5<A, B, C, D, E>(
    val a: A, val b: B, val c: C, val d: D, val e: E
)

@Composable
fun GateDecisionBadge(
    decision: GateVerificationDecision,
    modifier: Modifier = Modifier,
    isLarge: Boolean = false
) {
    val (bgColor, borderColor, contentColor, icon, text) = when (decision) {
        GateVerificationDecision.APPROVED -> Tuple5(
            ApprovedGreenLight,
            ApprovedGreen,
            ApprovedGreenDark,
            Icons.Default.CheckCircle,
            "APPROVED"
        )
        GateVerificationDecision.NOT_APPROVED -> Tuple5(
            RejectedRedLight,
            RejectedRed,
            RejectedRedDark,
            Icons.Default.Error,
            "NOT APPROVED"
        )
        GateVerificationDecision.CARD_INACTIVE -> Tuple5(
            Color(0xFFFEF3C7),
            Color(0xFFD97706),
            Color(0xFFB45309),
            Icons.Default.Block,
            "CARD INACTIVE"
        )
        GateVerificationDecision.STUDENT_NOT_FOUND -> Tuple5(
            Color(0xFFFFFBEB),
            Color(0xFFF59E0B),
            Color(0xFF92400E),
            Icons.Default.Help,
            "NOT FOUND"
        )
        GateVerificationDecision.INVALID_QR -> Tuple5(
            Color(0xFFF3F4F6),
            Color(0xFF6B7280),
            Color(0xFF1F2937),
            Icons.Default.QrCodeScanner,
            "INVALID QR"
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .testTag("badge_decision_${decision.name.lowercase()}")
            .clip(RoundedCornerShape(if (isLarge) 10.dp else 6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(if (isLarge) 10.dp else 6.dp))
            .padding(
                horizontal = if (isLarge) 12.dp else 8.dp,
                vertical = if (isLarge) 6.dp else 3.dp
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = contentColor,
            modifier = Modifier.size(if (isLarge) 16.dp else 12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = if (isLarge) 12.sp else 10.sp
        )
    }
}

@Composable
fun DayScholarBadge(
    status: DayScholarStatus = DayScholarStatus.DAY_SCHOLAR_BUS,
    route: String = "",
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Icon(
            imageVector = Icons.Default.DirectionsBus,
            contentDescription = "Day Scholar",
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (route.isNotBlank()) "Day Scholar • $route" else status.label,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
    }
}

@Composable
fun StudentAvatar(
    student: Student,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    val initials = "${student.firstName.firstOrNull() ?: ""}${student.lastName.firstOrNull() ?: ""}".uppercase()
    val bgColor = Color(student.avatarColorSeed)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor)
            .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
    ) {
        Text(
            text = initials.ifBlank { "ST" },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.38).sp
        )
    }
}
