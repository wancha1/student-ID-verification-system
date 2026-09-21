package com.example.util

import android.content.Context
import android.content.Intent
import com.example.model.GateVerificationDecision
import com.example.model.ScanLog
import com.example.model.Student
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportUtils {

    private val dateTimeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Generates a standard RFC-4180 compliant CSV string for gate scan logs.
     */
    fun generateGateLogsCsv(logs: List<ScanLog>): String {
        val sb = StringBuilder()
        // Header
        sb.append("Log ID,Student Number,Student Name,Grade / Class,Decision,Approval Status,Timestamp,Date,Time,Guard Name,Gate Terminal,Card ID,Reason,Day Scholar,Fee Status\n")

        for (log in logs) {
            val dateStr = dateFormatter.format(Date(log.timestamp))
            val timeStr = timeFormatter.format(Date(log.timestamp))
            val isApprovedStr = if (log.decision == GateVerificationDecision.APPROVED) "APPROVED" else "DENIED"

            sb.append(escapeCsv(log.id)).append(",")
            sb.append(escapeCsv(log.studentNumber ?: log.studentId ?: "N/A")).append(",")
            sb.append(escapeCsv(log.studentName)).append(",")
            sb.append(escapeCsv(log.gradeClass)).append(",")
            sb.append(escapeCsv(log.decision.name)).append(",")
            sb.append(escapeCsv(isApprovedStr)).append(",")
            sb.append(log.timestamp).append(",")
            sb.append(escapeCsv(dateStr)).append(",")
            sb.append(escapeCsv(timeStr)).append(",")
            sb.append(escapeCsv(log.guardName)).append(",")
            sb.append(escapeCsv(log.gateLocation)).append(",")
            sb.append(escapeCsv(log.cardIdentifier ?: "N/A")).append(",")
            sb.append(escapeCsv(log.reason)).append(",")
            sb.append(if (log.isDayScholar) "YES" else "NO").append(",")
            sb.append(escapeCsv(log.feeStatus?.name ?: "N/A")).append("\n")
        }

        return sb.toString()
    }

    /**
     * Generates a comprehensive attendance summary report.
     */
    fun generateAttendanceSummaryReport(
        allStudents: List<Student>,
        scanLogs: List<ScanLog>
    ): String {
        val totalStudents = allStudents.size
        val dayScholars = allStudents.count { it.isDayScholar }
        val boarders = totalStudents - dayScholars
        val totalScans = scanLogs.size
        val approvedScans = scanLogs.count { it.decision == GateVerificationDecision.APPROVED }
        val deniedScans = scanLogs.count { it.decision != GateVerificationDecision.APPROVED }
        val uniqueStudentsScanned = scanLogs.mapNotNull { it.studentNumber ?: it.studentId }.distinct().size

        val reportDate = SimpleDateFormat("EEEE, MMMM d, yyyy • HH:mm", Locale.getDefault()).format(Date())

        val sb = StringBuilder()
        sb.append("========================================\n")
        sb.append("OAKRIDGE HIGH SCHOOL • GATE ACCESS REPORT\n")
        sb.append("Generated: $reportDate\n")
        sb.append("========================================\n\n")

        sb.append("📊 EXECUTIVE SUMMARY:\n")
        sb.append("• Total Registered Students: $totalStudents (Day Scholars: $dayScholars, Boarders: $boarders)\n")
        sb.append("• Total Gate Scans Recorded: $totalScans\n")
        sb.append("• Unique Students Verified: $uniqueStudentsScanned\n")
        sb.append("• Access Approved: $approvedScans (${if (totalScans > 0) (approvedScans * 100 / totalScans) else 0}%)\n")
        sb.append("• Access Denied / Flagged: $deniedScans (${if (totalScans > 0) (deniedScans * 100 / totalScans) else 0}%)\n\n")

        sb.append("📋 RECENT GATE ACTIVITY HIGHLIGHTS (LAST 10):\n")
        scanLogs.take(10).forEachIndexed { index, log ->
            val time = timeFormatter.format(Date(log.timestamp))
            val outcome = if (log.decision == GateVerificationDecision.APPROVED) "✅ [APPROVED]" else "❌ [DENIED - ${log.decision.name}]"
            sb.append("${index + 1}. $time - ${log.studentName} (${log.studentNumber ?: "ID"}) -> $outcome\n   Reason: ${log.reason}\n")
        }

        sb.append("\n========================================\n")
        sb.append("Official Gate Security System • Oakridge High School\n")
        return sb.toString()
    }

    /**
     * Triggers Android's native share sheet with the CSV log data or text report.
     */
    fun shareData(
        context: Context,
        content: String,
        subject: String = "Oakridge Gate Access Report",
        isCsv: Boolean = false
    ) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (isCsv) "text/csv" else "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, content)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val chooser = Intent.createChooser(sendIntent, "Share Gate Access Report").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(chooser)
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
