package com.example.ui.exams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.model.ExamException
import com.example.model.ExamExceptionDecisionStatus

@Composable
fun ExamExceptionReviewScreen(
    exceptions: List<ExamException>,
    onBack: () -> Unit,
    onApprove: (studentId: String, examCode: String) -> Unit,
    onReject: (studentId: String, examCode: String, reason: String) -> Unit
) {
    var rejectionTarget by remember { mutableStateOf<ExamException?>(null) }
    var rejectionReason by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Exam exception requests", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Review requests and record a traceable leadership decision.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onBack) { Text("Back") }
        }

        if (exceptions.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("No exam exception requests have been synchronized.", modifier = Modifier.padding(16.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(exceptions, key = { it.id }) { request ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(request.studentName, style = MaterialTheme.typography.titleMedium)
                            Text("${request.studentNumber} • ${request.examCode}", style = MaterialTheme.typography.bodyMedium)
                            Text("Requested by: ${request.requestedByBursar}", style = MaterialTheme.typography.bodySmall)
                            Text("Reason: ${request.recommendationReason}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Status: ${request.decisionStatus.name}",
                                style = MaterialTheme.typography.labelLarge,
                                color = when (request.decisionStatus) {
                                    ExamExceptionDecisionStatus.APPROVED -> MaterialTheme.colorScheme.primary
                                    ExamExceptionDecisionStatus.REJECTED -> MaterialTheme.colorScheme.error
                                    ExamExceptionDecisionStatus.PENDING -> MaterialTheme.colorScheme.tertiary
                                }
                            )
                            request.decisionReason?.let {
                                Text("Decision reason: $it", style = MaterialTheme.typography.bodySmall)
                            }
                            if (request.decisionStatus == ExamExceptionDecisionStatus.PENDING) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        modifier = Modifier.weight(1f),
                                        onClick = { onApprove(request.studentId, request.examCode) }
                                    ) { Text("Approve") }
                                    OutlinedButton(
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            rejectionTarget = request
                                            rejectionReason = ""
                                        }
                                    ) { Text("Reject") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    rejectionTarget?.let { request ->
        AlertDialog(
            onDismissRequest = { rejectionTarget = null },
            title = { Text("Reject exam exception") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A reason is required. The decision will be recorded in the audit history.")
                    OutlinedTextField(
                        value = rejectionReason,
                        onValueChange = { rejectionReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Rejection reason") },
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = rejectionReason.isNotBlank(),
                    onClick = {
                        onReject(request.studentId, request.examCode, rejectionReason.trim())
                        rejectionTarget = null
                    }
                ) { Text("Confirm rejection") }
            },
            dismissButton = {
                TextButton(onClick = { rejectionTarget = null }) { Text("Cancel") }
            }
        )
    }
}
