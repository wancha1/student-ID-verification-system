package com.example.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AuthUser
import com.example.model.AttendanceRecord
import com.example.model.MealRecord
import com.example.model.MealServingStatus
import com.example.model.ScanLog
import com.example.model.Student
import com.example.ui.theme.LocalRoleTheme
import com.example.ui.theme.roleGlassBackground
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Head Teacher operational workspace.
 * It provides live oversight from the records already available to this role;
 * it deliberately does not expose finance data or pretend to offer unimplemented approvals.
 */
@Composable
fun HeadTeacherDashboardScreen(
    user: AuthUser,
    students: List<Student>,
    mealRecords: List<MealRecord>,
    gateLogs: List<ScanLog>,
    attendanceRecords: List<AttendanceRecord>,
    onOpenGateLogs: () -> Unit,
    onOpenMeals: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenRequirements: () -> Unit,
    onOpenExeat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val roleTheme = LocalRoleTheme.current
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val activeStudents = remember(students) { students.filterNot { it.isDeleted } }
    val todayMeals = remember(mealRecords, today) {
        mealRecords.filter { it.mealDate == today && it.status == MealServingStatus.SERVED }
    }
    val todayGate = remember(gateLogs, today) {
        gateLogs.filter {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it.timestamp)) == today
        }
    }
    val approvedGate = todayGate.count { it.isApproved }
    val blockedGate = todayGate.size - approvedGate
    val todayAttendance = attendanceRecords.filter { it.lessonDate == today }
    val presentToday = todayAttendance.count { it.isPresent }
    val absentToday = todayAttendance.size - presentToday
    val classCounts = remember(activeStudents) {
        activeStudents.groupingBy { it.gradeClass.ifBlank { "Unassigned class" } }.eachCount()
            .toList().sortedByDescending { it.second }
    }
    var studentSearch by remember { mutableStateOf("") }
    val matchingStudents = remember(activeStudents, studentSearch) {
        if (studentSearch.isBlank()) emptyList()
        else activeStudents.filter {
            it.fullName.contains(studentSearch, true) ||
                it.studentNumber.contains(studentSearch, true) ||
                it.gradeClass.contains(studentSearch, true)
        }.take(12)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().roleGlassBackground(roleTheme.primaryColor),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 108.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF14263D)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.12f)) {
                            Icon(Icons.Default.School, null, tint = Color(0xFFE8C77A), modifier = Modifier.padding(9.dp).size(23.dp))
                        }
                        Spacer(Modifier.size(10.dp))
                        Text("HEAD TEACHER WORKSPACE", color = Color(0xFFE8C77A), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                    }
                    Text("Good day, ${user.name}", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("School operations overview • Lira Town College", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date()),
                        color = Color.White.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        item {
            Text("Today's operations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OverviewMetric("Students", activeStudents.size.toString(), Icons.Default.School, Modifier.weight(1f))
                OverviewMetric("Meals served", todayMeals.size.toString(), Icons.Default.Restaurant, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OverviewMetric("Gate checks", todayGate.size.toString(), Icons.Default.Security, Modifier.weight(1f))
                OverviewMetric("Entry approved", approvedGate.toString(), Icons.Default.TrendingUp, Modifier.weight(1f))
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OverviewMetric("Attendance marked", todayAttendance.size.toString(), Icons.Default.TrendingUp, Modifier.weight(1f))
                OverviewMetric("Present", presentToday.toString(), Icons.Default.School, Modifier.weight(1f))
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gate exceptions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Approved entries", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(approvedGate.toString(), color = Color(0xFF16834A), fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Denied / flagged", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(blockedGate.toString(), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                    Text("Counts are based on gate records currently available on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    WorkspaceAction("Review gate activity", "Inspect entry decisions and recent verification records", Icons.Default.History, onOpenGateLogs)
                }
            }
        }

        item {
            WorkspaceAction("Meals oversight", "Review meal service activity and serving history", Icons.Default.Restaurant, onOpenMeals)
        }
        item {
            WorkspaceAction("Class attendance", "Review class, subject and session attendance records", Icons.Default.TrendingUp, onOpenAttendance)
        }
        item {
            WorkspaceAction("Student requirements", "Review clearance checklists and outstanding requirements", Icons.Default.School, onOpenRequirements)
        }
        item {
            WorkspaceAction("Exeat passes", "Review student leave requests and used / outstanding passes", Icons.Default.History, onOpenExeat)
        }

        item {
            Text("Student directory", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Search the synced roster by student name, number or class.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = studentSearch,
                onValueChange = { studentSearch = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Find a student") },
                placeholder = { Text("Name, admission number or class") }
            )
        }

        if (studentSearch.isNotBlank()) {
            if (matchingStudents.isEmpty()) {
                item {
                    Text("No matching students in the currently synced roster.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                items(matchingStudents, key = { it.id }) { student ->
                    StudentDirectoryRow(student)
                }
            }
        } else {
            item {
                Text("Class distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (classCounts.isEmpty()) {
                item {
                    Text("No student records are available yet. The directory will populate after the approved roster is synced.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(classCounts.take(8), key = { it.first }) { (className, count) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(className, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(count.toString(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewMetric(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WorkspaceAction(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(10.dp).size(21.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
        }
    }
}

@Composable
private fun StudentDirectoryRow(student: Student) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(student.fullName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${student.studentNumber} • ${student.gradeClass}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (student.isDayScholar) "Day scholar" else "Boarding student", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
