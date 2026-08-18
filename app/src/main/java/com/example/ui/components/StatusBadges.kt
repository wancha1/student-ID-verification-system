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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Person
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
import com.example.model.DayScholarStatus
import com.example.model.FeeStatus
import com.example.model.Student
import com.example.ui.theme.ApprovedGreen
import com.example.ui.theme.ApprovedGreenDark
import com.example.ui.theme.ApprovedGreenLight
import com.example.ui.theme.RejectedRed
import com.example.ui.theme.RejectedRedDark
import com.example.ui.theme.RejectedRedLight

@Composable
fun FeeStatusBadge(
    status: FeeStatus,
    modifier: Modifier = Modifier,
    isLarge: Boolean = false
) {
    val isCleared = status == FeeStatus.CLEARED
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
