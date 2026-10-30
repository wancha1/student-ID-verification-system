package com.example.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AttendanceRecord
import com.example.model.AuthUser
import com.example.model.MealRecord
import com.example.model.ScanLog
import com.example.model.Student
import com.example.model.StudentRequirement

/**
 * Leadership overview. It is intentionally read-only: the Head Teacher reviews
 * school operations while operational staff retain responsibility for data entry.
 */
@Composable
fun HeadTeacherDashboardScreen(
    user: AuthUser,
    students: List<Student>,
    attendanceRecords: List<AttendanceRecord>,
    mealRecords: List<MealRecord>,
    requirements: List<StudentRequirement>,
    gateLogs: List<ScanLog>,
    onOpenAttendance: () -> Unit,
    onOpenRequirements: () -> Unit,
    onOpenGateLogs: () -> Unit,
    onOpenMeals: () -> Unit,
    onOpenExeat: () -> Unit,
    onOpenGuardianAlerts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clearedRequirements = requirements.count { it.isComplete }
    val pendingRequirements = (requirements.size - clearedRequirements).coerceAtLeast(0)

    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 18.dp, 16.dp, 100.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF14263D))
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("SCHOOL LEADERSHIP", color = Color(0xFFE8C77A), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("Good day, ${user.name}", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    Text("Operational overview • Lira Town College", color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Text("School at a glance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LeadershipMetric("Students", students.size.toString(), Icons.Default.School, Modifier.weight(1f))
                LeadershipMetric("Attendance", attendanceRecords.size.toString(), Icons.Default.MenuBook, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LeadershipMetric("Meal records", mealRecords.size.toString(), Icons.Default.Restaurant, Modifier.weight(1f))
                LeadershipMetric("Gate records", gateLogs.size.toString(), Icons.Default.History, Modifier.weight(1f))
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Requirements oversight", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("$clearedRequirements cleared  •  $pendingRequirements pending", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Based on the requirement records currently available on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Text("Leadership workspaces", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            LeadershipAction("Classroom attendance", "Review lesson attendance records", Icons.Default.MenuBook, onOpenAttendance)
        }
        item {
            LeadershipAction("Student requirements", "Review clearance and outstanding items", Icons.Default.FactCheck, onOpenRequirements)
        }
        item {
            LeadershipAction("Gate activity", "Review access decisions and exceptions", Icons.Default.History, onOpenGateLogs)
        }
        item {
            LeadershipAction("Meals oversight", "Review recorded meal service activity", Icons.Default.Restaurant, onOpenMeals)
        }
        item {
            LeadershipAction("Exeat and leave passes", "Review student movement records", Icons.Default.Assessment, onOpenExeat)
        }
        item {
            LeadershipAction("Guardian alert records", "Review recorded guardian contact notices", Icons.Default.SupervisorAccount, onOpenGuardianAlerts)
        }
    }
}

@Composable
private fun LeadershipMetric(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = Color(0xFF52769A), modifier = Modifier.size(22.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LeadershipAction(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF52769A).copy(alpha = 0.12f)) {
                Icon(icon, contentDescription = null, tint = Color(0xFF52769A), modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
        }
    }
}
