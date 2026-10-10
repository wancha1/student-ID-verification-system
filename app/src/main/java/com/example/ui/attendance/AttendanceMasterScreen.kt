package com.example.ui.attendance

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AttendanceRecord
import com.example.model.AuthUser
import com.example.ui.theme.ApprovedGreenLight
import com.example.ui.theme.ApprovedGreenText
import com.example.ui.theme.SchoolPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceMasterScreen(
    user: AuthUser,
    attendanceRecords: List<AttendanceRecord>,
    selectedSubject: String,
    selectedSession: String,
    onSubjectChange: (String) -> Unit,
    onSessionChange: (String) -> Unit,
    onOpenScanner: () -> Unit,
    onManualLookup: (String) -> Unit,
    onCorrectAttendance: (recordId: String, note: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val subjects = listOf("Mathematics", "English Language", "Physics", "Chemistry", "Biology", "History", "Geography")
    val sessions = listOf("Morning Lesson 1", "Morning Lesson 2", "Mid-day Lesson 3", "Afternoon Lesson 4", "Evening Study")

    var manualStudentNumber by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Classroom Attendance", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${user.name} • ${user.role.title}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SchoolPrimary,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenScanner,
                containerColor = SchoolPrimary,
                contentColor = Color.White
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Badge")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan Student Card", fontWeight = FontWeight.Bold)
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Context selectors
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "ACADEMIC LESSON CONTEXT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SchoolPrimary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(text = "Subject: $selectedSubject", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    ScrollableTabRow(
                        selectedTabIndex = subjects.indexOf(selectedSubject).coerceAtLeast(0),
                        edgePadding = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        subjects.forEach { subj ->
                            Tab(
                                selected = subj == selectedSubject,
                                onClick = { onSubjectChange(subj) },
                                text = { Text(subj, fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Session: $selectedSession", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    ScrollableTabRow(
                        selectedTabIndex = sessions.indexOf(selectedSession).coerceAtLeast(0),
                        edgePadding = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        sessions.forEach { sess ->
                            Tab(
                                selected = sess == selectedSession,
                                onClick = { onSessionChange(sess) },
                                text = { Text(sess, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            // Quick manual entry
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = manualStudentNumber,
                    onValueChange = { manualStudentNumber = it },
                    label = { Text("Manual Student Reg No") },
                    placeholder = { Text("LTC-2026-0001") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (manualStudentNumber.isNotBlank()) {
                            onManualLookup(manualStudentNumber.trim())
                            manualStudentNumber = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SchoolPrimary)
                ) {
                    Text("Lookup")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Attendance list
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Present Students (${attendanceRecords.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (attendanceRecords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No students scanned yet for $selectedSubject ($selectedSession).\nTap 'Scan Student Card' to record roll-call.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(attendanceRecords, key = { it.id }) { record ->
                        AttendanceRecordRow(
                            record = record,
                            onCorrect = { note -> onCorrectAttendance(record.id, note) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceRecordRow(
    record: AttendanceRecord,
    onCorrect: (String) -> Unit
) {
    val timeStr = SimpleDateFormat("HH:mm", Locale.US).format(Date(record.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(ApprovedGreenLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ApprovedGreenText)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = record.studentName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(text = "${record.studentNumber} • ${record.gradeClass}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "${record.subject} • ${record.sessionName} @ $timeStr", fontSize = 11.sp, color = SchoolPrimary)
                if (record.isCorrection) {
                    Text(text = "Corrected by ${record.correctedBy ?: "Teacher"}: ${record.notes}", fontSize = 11.sp, color = ApprovedGreenText)
                }
            }
        }
    }
}
