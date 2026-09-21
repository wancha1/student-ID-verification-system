package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.model.GateVerificationDecision
import com.example.model.MealRecord
import com.example.model.ScanLog
import com.example.model.Student
import com.example.model.StudentRequirement
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat(
    val extension: String,
    val mimeType: String,
    val label: String,
    val description: String
) {
    EXCEL_XLS(
        extension = "xls",
        mimeType = "application/vnd.ms-excel",
        label = "Microsoft Excel (.xls)",
        description = "Styled spreadsheet with formatted headers and status colors"
    ),
    EXCEL_CSV(
        extension = "csv",
        mimeType = "text/csv",
        label = "Excel CSV (.csv)",
        description = "Universal spreadsheet format with UTF-8 BOM encoding"
    ),
    WORD_DOC(
        extension = "doc",
        mimeType = "application/msword",
        label = "Microsoft Word (.doc)",
        description = "Official formatted school report document with letterhead"
    ),
    TEXT_REPORT(
        extension = "txt",
        mimeType = "text/plain",
        label = "Printable Text (.txt)",
        description = "Structured text table with summary and audit lines"
    )
}

object ExportManager {

    private const val TAG = "ExportManager"
    private val dateTimeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.US)

    // ==========================================
    // 1. STUDENT REGISTRY EXPORTS
    // ==========================================

    fun generateStudentsExport(students: List<Student>, format: ExportFormat): String {
        return when (format) {
            ExportFormat.EXCEL_XLS -> generateStudentsHtmlXls(students)
            ExportFormat.EXCEL_CSV -> generateStudentsCsv(students)
            ExportFormat.WORD_DOC -> generateStudentsWordDoc(students)
            ExportFormat.TEXT_REPORT -> generateStudentsTextReport(students)
        }
    }

    private fun generateStudentsCsv(students: List<Student>): String {
        val sb = StringBuilder()
        sb.append("\uFEFF") // UTF-8 BOM for Excel
        sb.append("Student Number,Full Name,Class,Transport Route,Fees Status,Outstanding (UGX),Access Status,Guardian Name,Guardian Phone,Homeroom Teacher,Unique QR Code\n")
        for (s in students) {
            sb.append(escapeCsv(s.studentNumber)).append(",")
            sb.append(escapeCsv(s.fullName)).append(",")
            sb.append(escapeCsv(s.gradeClass)).append(",")
            sb.append(escapeCsv(s.transportRoute)).append(",")
            sb.append(escapeCsv(s.feesStatus.name)).append(",")
            sb.append(s.outstandingAmount).append(",")
            sb.append(escapeCsv(s.accessStatus.name)).append(",")
            sb.append(escapeCsv(s.guardianName)).append(",")
            sb.append(escapeCsv(s.guardianPhone)).append(",")
            sb.append(escapeCsv(s.homeroomTeacher)).append(",")
            sb.append(escapeCsv(s.uniqueQrCode)).append("\n")
        }
        return sb.toString()
    }

    private fun generateStudentsHtmlXls(students: List<Student>): String {
        val generatedAt = dateTimeFormatter.format(Date())
        val sb = StringBuilder()
        sb.append("""
            <html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns="http://www.w3.org/TR/REC-html40">
            <head>
            <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
            <style>
                body { font-family: Arial, sans-serif; }
                table { border-collapse: collapse; width: 100%; }
                th { background-color: #1E3A8A; color: #FFFFFF; font-weight: bold; padding: 8px; border: 1px solid #CBD5E1; }
                td { padding: 6px 8px; border: 1px solid #E2E8F0; font-size: 11pt; }
                .cleared { color: #047857; font-weight: bold; }
                .pending { color: #B45309; font-weight: bold; }
                .title { font-size: 16pt; font-weight: bold; color: #1E3A8A; margin-bottom: 4px; }
            </style>
            </head>
            <body>
            <div class="title">Oakridge High School — Student Registry Directory</div>
            <p>Generated: $generatedAt | Total Registered Students: ${students.size}</p>
            <table>
                <tr>
                    <th>#</th>
                    <th>Student Number</th>
                    <th>Full Name</th>
                    <th>Class</th>
                    <th>Fees Status</th>
                    <th>Outstanding</th>
                    <th>Transport Route</th>
                    <th>Guardian Name</th>
                    <th>Guardian Phone</th>
                    <th>Unique QR Payload</th>
                </tr>
        """.trimIndent())

        students.forEachIndexed { i, s ->
            val feeClass = if (s.feesStatus.name == "CLEARED") "cleared" else "pending"
            sb.append("""
                <tr>
                    <td>${i + 1}</td>
                    <td><b>${escapeHtml(s.studentNumber)}</b></td>
                    <td>${escapeHtml(s.fullName)}</td>
                    <td>${escapeHtml(s.gradeClass)}</td>
                    <td class="$feeClass">${s.feesStatus.name}</td>
                    <td>UGX ${String.format(Locale.US, "%,.0f", s.outstandingAmount)}</td>
                    <td>${escapeHtml(s.transportRoute)}</td>
                    <td>${escapeHtml(s.guardianName)}</td>
                    <td>${escapeHtml(s.guardianPhone)}</td>
                    <td><code>${escapeHtml(s.uniqueQrCode)}</code></td>
                </tr>
            """.trimIndent())
        }

        sb.append("</table></body></html>")
        return sb.toString()
    }

    private fun generateStudentsWordDoc(students: List<Student>): String {
        val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.append("""
            <html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:w="urn:schemas-microsoft-com:office:word" xmlns="http://www.w3.org/TR/REC-html40">
            <head>
            <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
            <title>Oakridge Academy Student Registry</title>
            <style>
                body { font-family: 'Calibri', Arial, sans-serif; margin: 20px; }
                h1 { color: #1E3A8A; font-size: 20pt; margin-bottom: 2px; text-align: center; }
                .subtitle { text-align: center; color: #475569; font-size: 11pt; margin-bottom: 20px; }
                table { border-collapse: collapse; width: 100%; margin-top: 10px; }
                th { background-color: #1E3A8A; color: white; padding: 8px; font-size: 10pt; text-align: left; border: 1px solid #94A3B8; }
                td { padding: 6px 8px; font-size: 9.5pt; border: 1px solid #CBD5E1; }
                .footer { margin-top: 30px; border-top: 1px solid #CBD5E1; padding-top: 10px; font-size: 9pt; color: #64748B; text-align: center; }
            </style>
            </head>
            <body>
            <h1>OAKRIDGE ACADEMY</h1>
            <div class="subtitle">Bursar & Academic Registry — Official Student Directory<br>Date of Issue: $dateStr</div>
            <p><b>Executive Summary:</b> Total Day Scholars: ${students.count { it.isDayScholar }} | Fees Cleared: ${students.count { it.feesStatus.name == "CLEARED" }}</p>
            <table>
                <tr>
                    <th>#</th>
                    <th>Student ID</th>
                    <th>Student Name</th>
                    <th>Class</th>
                    <th>Fee Status</th>
                    <th>Transport Route</th>
                    <th>Guardian Contact</th>
                    <th>Unique QR Token</th>
                </tr>
        """.trimIndent())

        students.forEachIndexed { idx, s ->
            sb.append("""
                <tr>
                    <td>${idx + 1}</td>
                    <td><b>${escapeHtml(s.studentNumber)}</b></td>
                    <td>${escapeHtml(s.fullName)}</td>
                    <td>${escapeHtml(s.gradeClass)}</td>
                    <td>${s.feesStatus.name}</td>
                    <td>${escapeHtml(s.transportRoute)}</td>
                    <td>${escapeHtml(s.guardianPhone)}</td>
                    <td>${escapeHtml(s.qrToken)}</td>
                </tr>
            """.trimIndent())
        }

        sb.append("""
            </table>
            <div class="footer">Official Student Record • Oakridge Academy Day Scholar Access Management System</div>
            </body></html>
        """.trimIndent())
        return sb.toString()
    }

    private fun generateStudentsTextReport(students: List<Student>): String {
        val sb = StringBuilder()
        sb.append("========================================================================================\n")
        sb.append("OAKRIDGE HIGH SCHOOL • STUDENT DIRECTORY REPORT\n")
        sb.append("Generated: ${dateTimeFormatter.format(Date())} | Total Students: ${students.size}\n")
        sb.append("========================================================================================\n\n")
        sb.append(String.format(Locale.US, "%-4s %-16s %-24s %-12s %-12s %-22s\n", "#", "STUDENT ID", "NAME", "CLASS", "FEES", "GUARDIAN"))
        sb.append("----------------------------------------------------------------------------------------\n")
        students.forEachIndexed { i, s ->
            sb.append(String.format(Locale.US, "%-4d %-16s %-24s %-12s %-12s %-22s\n",
                i + 1,
                s.studentNumber.take(15),
                s.fullName.take(23),
                s.gradeClass.take(11),
                s.feesStatus.name.take(11),
                s.guardianPhone.take(21)
            ))
        }
        sb.append("\nTotal Registered: ${students.size} | Cleared: ${students.count { it.feesStatus.name == "CLEARED" }}\n")
        return sb.toString()
    }

    // ==========================================
    // 2. GATE VERIFICATION LOGS EXPORTS
    // ==========================================

    fun generateGateLogsExport(logs: List<ScanLog>, format: ExportFormat): String {
        return when (format) {
            ExportFormat.EXCEL_XLS -> generateGateLogsHtmlXls(logs)
            ExportFormat.EXCEL_CSV -> ExportUtils.generateGateLogsCsv(logs)
            ExportFormat.WORD_DOC -> generateGateLogsWordDoc(logs)
            ExportFormat.TEXT_REPORT -> ExportUtils.generateAttendanceSummaryReport(emptyList(), logs)
        }
    }

    private fun generateGateLogsHtmlXls(logs: List<ScanLog>): String {
        val generatedAt = dateTimeFormatter.format(Date())
        val sb = StringBuilder()
        sb.append("""
            <html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns="http://www.w3.org/TR/REC-html40">
            <head>
            <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
            <style>
                body { font-family: Arial, sans-serif; }
                table { border-collapse: collapse; width: 100%; }
                th { background-color: #1E3A8A; color: white; padding: 8px; border: 1px solid #CBD5E1; }
                td { padding: 6px 8px; border: 1px solid #E2E8F0; font-size: 11pt; }
                .approved { color: #047857; font-weight: bold; }
                .denied { color: #DC2626; font-weight: bold; }
            </style>
            </head>
            <body>
            <h2>Oakridge High School — Gate Access Verification Logs</h2>
            <p>Generated: $generatedAt | Total Scans: ${logs.size}</p>
            <table>
                <tr>
                    <th>#</th>
                    <th>Time</th>
                    <th>Student ID</th>
                    <th>Student Name</th>
                    <th>Class</th>
                    <th>Gate Decision</th>
                    <th>Reason</th>
                    <th>Guard Name</th>
                    <th>Gate Terminal</th>
                </tr>
        """.trimIndent())

        logs.forEachIndexed { i, log ->
            val isApp = log.decision == GateVerificationDecision.APPROVED
            val decClass = if (isApp) "approved" else "denied"
            val timeStr = dateTimeFormatter.format(Date(log.timestamp))
            sb.append("""
                <tr>
                    <td>${i + 1}</td>
                    <td>$timeStr</td>
                    <td><b>${escapeHtml(log.studentNumber ?: log.studentId ?: "N/A")}</b></td>
                    <td>${escapeHtml(log.studentName)}</td>
                    <td>${escapeHtml(log.gradeClass)}</td>
                    <td class="$decClass">${log.decision.name}</td>
                    <td>${escapeHtml(log.reason)}</td>
                    <td>${escapeHtml(log.guardName)}</td>
                    <td>${escapeHtml(log.gateLocation)}</td>
                </tr>
            """.trimIndent())
        }

        sb.append("</table></body></html>")
        return sb.toString()
    }

    private fun generateGateLogsWordDoc(logs: List<ScanLog>): String {
        val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.append("""
            <html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:w="urn:schemas-microsoft-com:office:word">
            <head>
            <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
            <style>
                body { font-family: 'Calibri', Arial, sans-serif; margin: 20px; }
                h1 { color: #1E3A8A; font-size: 18pt; text-align: center; margin-bottom: 2px; }
                .sub { text-align: center; color: #475569; font-size: 10pt; margin-bottom: 16px; }
                table { border-collapse: collapse; width: 100%; font-size: 9pt; }
                th { background-color: #1E3A8A; color: white; padding: 6px; border: 1px solid #94A3B8; }
                td { padding: 5px; border: 1px solid #CBD5E1; }
            </style>
            </head>
            <body>
            <h1>OAKRIDGE HIGH SCHOOL • SECURITY GATE VERIFICATION AUDIT</h1>
            <div class="sub">Generated on $dateStr | Official Guard Station Log</div>
            <table>
                <tr>
                    <th>Time</th>
                    <th>Student ID</th>
                    <th>Student Name</th>
                    <th>Class</th>
                    <th>Decision</th>
                    <th>Reason / Flag</th>
                    <th>Gate Officer</th>
                </tr>
        """.trimIndent())

        logs.forEach { log ->
            val time = timeFormatter.format(Date(log.timestamp))
            sb.append("""
                <tr>
                    <td>$time</td>
                    <td><b>${escapeHtml(log.studentNumber ?: "ID")}</b></td>
                    <td>${escapeHtml(log.studentName)}</td>
                    <td>${escapeHtml(log.gradeClass)}</td>
                    <td><b>${log.decision.name}</b></td>
                    <td>${escapeHtml(log.reason)}</td>
                    <td>${escapeHtml(log.guardName)}</td>
                </tr>
            """.trimIndent())
        }

        sb.append("</table></body></html>")
        return sb.toString()
    }

    // ==========================================
    // 3. MEALS & CAFETERIA LOGS EXPORTS
    // ==========================================

    fun generateMealsExport(records: List<MealRecord>, format: ExportFormat): String {
        return when (format) {
            ExportFormat.EXCEL_XLS -> generateMealsHtmlXls(records)
            ExportFormat.EXCEL_CSV -> generateMealsCsv(records)
            ExportFormat.WORD_DOC -> generateMealsWordDoc(records)
            ExportFormat.TEXT_REPORT -> generateMealsTextReport(records)
        }
    }

    private fun generateMealsCsv(records: List<MealRecord>): String {
        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.append("Log ID,Student Number,Student Name,Class,Meal Session,Date,Time,Status,Catering Server,Notes\n")
        for (r in records) {
            val dateStr = r.mealDate
            val timeStr = timeFormatter.format(Date(r.timestamp))
            sb.append(escapeCsv(r.id)).append(",")
            sb.append(escapeCsv(r.studentNumber)).append(",")
            sb.append(escapeCsv(r.studentName)).append(",")
            sb.append(escapeCsv(r.gradeClass)).append(",")
            sb.append(escapeCsv(r.mealType.displayName)).append(",")
            sb.append(escapeCsv(dateStr)).append(",")
            sb.append(escapeCsv(timeStr)).append(",")
            sb.append(escapeCsv(r.status.name)).append(",")
            sb.append(escapeCsv(r.serverName)).append(",")
            sb.append(escapeCsv(r.notes)).append("\n")
        }
        return sb.toString()
    }

    private fun generateMealsHtmlXls(records: List<MealRecord>): String {
        val sb = StringBuilder()
        sb.append("""
            <html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel">
            <head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
            <style>
                body { font-family: Arial, sans-serif; }
                table { border-collapse: collapse; width: 100%; }
                th { background-color: #0D9488; color: white; padding: 8px; border: 1px solid #CBD5E1; }
                td { padding: 6px 8px; border: 1px solid #E2E8F0; font-size: 11pt; }
                .served { color: #047857; font-weight: bold; }
                .blocked { color: #DC2626; font-weight: bold; }
            </style>
            </head><body>
            <h2>Oakridge High School — Dining Hall & Cafeteria Attendance Sheet</h2>
            <p>Generated: ${dateTimeFormatter.format(Date())} | Total Entries: ${records.size}</p>
            <table>
                <tr>
                    <th>#</th>
                    <th>Time</th>
                    <th>Meal Session</th>
                    <th>Student ID</th>
                    <th>Student Name</th>
                    <th>Class</th>
                    <th>Status</th>
                    <th>Server</th>
                </tr>
        """.trimIndent())

        records.forEachIndexed { i, r ->
            val statusClass = if (r.status.name == "SERVED") "served" else "blocked"
            val time = dateTimeFormatter.format(Date(r.timestamp))
            sb.append("""
                <tr>
                    <td>${i + 1}</td>
                    <td>$time</td>
                    <td><b>${r.mealType.displayName}</b></td>
                    <td>${escapeHtml(r.studentNumber)}</td>
                    <td>${escapeHtml(r.studentName)}</td>
                    <td>${escapeHtml(r.gradeClass)}</td>
                    <td class="$statusClass">${r.status.name}</td>
                    <td>${escapeHtml(r.serverName)}</td>
                </tr>
            """.trimIndent())
        }

        sb.append("</table></body></html>")
        return sb.toString()
    }

    private fun generateMealsWordDoc(records: List<MealRecord>): String {
        val sb = StringBuilder()
        sb.append("""
            <html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:w="urn:schemas-microsoft-com:office:word">
            <head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
            <style>
                body { font-family: Calibri, sans-serif; margin: 20px; }
                h1 { color: #0D9488; text-align: center; }
                table { border-collapse: collapse; width: 100%; font-size: 10pt; }
                th { background-color: #0D9488; color: white; padding: 6px; }
                td { padding: 5px; border: 1px solid #CBD5E1; }
            </style>
            </head><body>
            <h1>OAKRIDGE DINING HALL ACCESS RECORD</h1>
            <p>Date: ${SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(Date())} | Total Servings Logged: ${records.size}</p>
            <table>
                <tr>
                    <th>Time</th>
                    <th>Meal</th>
                    <th>Student ID</th>
                    <th>Student Name</th>
                    <th>Class</th>
                    <th>Result</th>
                    <th>Server</th>
                </tr>
        """.trimIndent())

        records.forEach { r ->
            val time = timeFormatter.format(Date(r.timestamp))
            sb.append("""
                <tr>
                    <td>$time</td>
                    <td>${r.mealType.displayName}</td>
                    <td>${escapeHtml(r.studentNumber)}</td>
                    <td>${escapeHtml(r.studentName)}</td>
                    <td>${escapeHtml(r.gradeClass)}</td>
                    <td><b>${r.status.name}</b></td>
                    <td>${escapeHtml(r.serverName)}</td>
                </tr>
            """.trimIndent())
        }

        sb.append("</table></body></html>")
        return sb.toString()
    }

    private fun generateMealsTextReport(records: List<MealRecord>): String {
        val sb = StringBuilder()
        sb.append("===============================================================================\n")
        sb.append("OAKRIDGE HIGH SCHOOL • DINING HALL ATTENDANCE LOG\n")
        sb.append("Generated: ${dateTimeFormatter.format(Date())} | Total Servings: ${records.size}\n")
        sb.append("===============================================================================\n\n")
        sb.append(String.format(Locale.US, "%-8s %-12s %-16s %-22s %-12s %-14s\n", "TIME", "MEAL", "STUDENT ID", "NAME", "CLASS", "STATUS"))
        sb.append("-------------------------------------------------------------------------------\n")
        records.forEach { r ->
            val time = timeFormatter.format(Date(r.timestamp))
            sb.append(String.format(Locale.US, "%-8s %-12s %-16s %-22s %-12s %-14s\n",
                time,
                r.mealType.displayName.take(11),
                r.studentNumber.take(15),
                r.studentName.take(21),
                r.gradeClass.take(11),
                r.status.name.take(13)
            ))
        }
        return sb.toString()
    }

    // ==========================================
    // 4. REQUIREMENTS COMPLIANCE EXPORTS
    // ==========================================

    fun generateRequirementsExport(requirements: List<StudentRequirement>, format: ExportFormat): String {
        return when (format) {
            ExportFormat.EXCEL_XLS -> generateRequirementsHtmlXls(requirements)
            ExportFormat.EXCEL_CSV -> generateRequirementsCsv(requirements)
            ExportFormat.WORD_DOC -> generateRequirementsWordDoc(requirements)
            ExportFormat.TEXT_REPORT -> generateRequirementsTextReport(requirements)
        }
    }

    private fun generateRequirementsCsv(list: List<StudentRequirement>): String {
        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.append("Student Number,Student Name,Class,Uniform,Sports Kit,Textbooks,Medical Form,ID Card,Rules Agreement,Bus Pass,Compliance %,Status,Notes\n")
        for (r in list) {
            sb.append(escapeCsv(r.studentNumber)).append(",")
            sb.append(escapeCsv(r.studentName)).append(",")
            sb.append(escapeCsv(r.gradeClass)).append(",")
            sb.append(if (r.uniformComplete) "CLEARED" else "PENDING").append(",")
            sb.append(if (r.sportsKitComplete) "CLEARED" else "PENDING").append(",")
            sb.append(if (r.textbooksSubmitted) "CLEARED" else "PENDING").append(",")
            sb.append(if (r.medicalFormSigned) "CLEARED" else "PENDING").append(",")
            sb.append(if (r.schoolIdIssued) "CLEARED" else "PENDING").append(",")
            sb.append(if (r.rulesAgreementSigned) "CLEARED" else "PENDING").append(",")
            sb.append(if (r.busPassCleared) "CLEARED" else "PENDING").append(",")
            sb.append("${r.compliancePercentage}%").append(",")
            sb.append(if (r.isFullyCleared) "FULLY CLEARED" else "PENDING").append(",")
            sb.append(escapeCsv(r.notes)).append("\n")
        }
        return sb.toString()
    }

    private fun generateRequirementsHtmlXls(list: List<StudentRequirement>): String {
        val sb = StringBuilder()
        sb.append("""
            <html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel">
            <head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
            <style>
                body { font-family: Arial, sans-serif; }
                table { border-collapse: collapse; width: 100%; }
                th { background-color: #6366F1; color: white; padding: 8px; border: 1px solid #CBD5E1; }
                td { padding: 6px 8px; border: 1px solid #E2E8F0; font-size: 10pt; }
                .cleared { color: #047857; font-weight: bold; }
                .pending { color: #DC2626; font-weight: bold; }
            </style>
            </head><body>
            <h2>Oakridge Academy — Student Requirements Clearance Sheet</h2>
            <p>Generated: ${dateTimeFormatter.format(Date())} | Total Students: ${list.size}</p>
            <table>
                <tr>
                    <th>#</th>
                    <th>Student ID</th>
                    <th>Student Name</th>
                    <th>Class</th>
                    <th>Uniform</th>
                    <th>Textbooks</th>
                    <th>Medical Form</th>
                    <th>ID Card</th>
                    <th>Compliance</th>
                    <th>Notes</th>
                </tr>
        """.trimIndent())

        list.forEachIndexed { i, r ->
            val statClass = if (r.isFullyCleared) "cleared" else "pending"
            sb.append("""
                <tr>
                    <td>${i + 1}</td>
                    <td><b>${escapeHtml(r.studentNumber)}</b></td>
                    <td>${escapeHtml(r.studentName)}</td>
                    <td>${escapeHtml(r.gradeClass)}</td>
                    <td>${if (r.uniformComplete) "YES" else "NO"}</td>
                    <td>${if (r.textbooksSubmitted) "YES" else "NO"}</td>
                    <td>${if (r.medicalFormSigned) "YES" else "NO"}</td>
                    <td>${if (r.schoolIdIssued) "YES" else "NO"}</td>
                    <td class="$statClass"><b>${r.compliancePercentage}%</b></td>
                    <td>${escapeHtml(r.notes)}</td>
                </tr>
            """.trimIndent())
        }

        sb.append("</table></body></html>")
        return sb.toString()
    }

    private fun generateRequirementsWordDoc(list: List<StudentRequirement>): String {
        val sb = StringBuilder()
        sb.append("""
            <html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:w="urn:schemas-microsoft-com:office:word">
            <head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
            <style>
                body { font-family: Calibri, sans-serif; margin: 20px; }
                h1 { color: #6366F1; text-align: center; }
                table { border-collapse: collapse; width: 100%; font-size: 9.5pt; }
                th { background-color: #6366F1; color: white; padding: 6px; }
                td { padding: 5px; border: 1px solid #CBD5E1; }
            </style>
            </head><body>
            <h1>OAKRIDGE ACADEMY • REQUIREMENTS COMPLIANCE AUDIT</h1>
            <p>Office of the Requirements Master | Date: ${SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(Date())}</p>
            <table>
                <tr>
                    <th>Student ID</th>
                    <th>Name</th>
                    <th>Class</th>
                    <th>Uniform</th>
                    <th>Textbooks</th>
                    <th>Medical</th>
                    <th>ID Badge</th>
                    <th>Compliance</th>
                    <th>Notes</th>
                </tr>
        """.trimIndent())

        list.forEach { r ->
            sb.append("""
                <tr>
                    <td><b>${escapeHtml(r.studentNumber)}</b></td>
                    <td>${escapeHtml(r.studentName)}</td>
                    <td>${escapeHtml(r.gradeClass)}</td>
                    <td>${if (r.uniformComplete) "Cleared" else "Pending"}</td>
                    <td>${if (r.textbooksSubmitted) "Cleared" else "Pending"}</td>
                    <td>${if (r.medicalFormSigned) "Cleared" else "Pending"}</td>
                    <td>${if (r.schoolIdIssued) "Issued" else "Pending"}</td>
                    <td><b>${r.compliancePercentage}%</b></td>
                    <td>${escapeHtml(r.notes)}</td>
                </tr>
            """.trimIndent())
        }

        sb.append("</table></body></html>")
        return sb.toString()
    }

    private fun generateRequirementsTextReport(list: List<StudentRequirement>): String {
        val sb = StringBuilder()
        sb.append("===============================================================================\n")
        sb.append("OAKRIDGE HIGH SCHOOL • STUDENT REQUIREMENTS STATUS REPORT\n")
        sb.append("Generated: ${dateTimeFormatter.format(Date())} | Total Students: ${list.size}\n")
        sb.append("===============================================================================\n\n")
        sb.append(String.format(Locale.US, "%-15s %-22s %-10s %-12s %-14s\n", "STUDENT ID", "NAME", "CLASS", "COMPLIANCE", "STATUS"))
        sb.append("-------------------------------------------------------------------------------\n")
        list.forEach { r ->
            sb.append(String.format(Locale.US, "%-15s %-22s %-10s %-12s %-14s\n",
                r.studentNumber.take(14),
                r.studentName.take(21),
                r.gradeClass.take(9),
                "${r.compliancePercentage}%",
                if (r.isFullyCleared) "CLEARED" else "PENDING"
            ))
        }
        return sb.toString()
    }

    // ==========================================
    // 5. DOWNLOAD & SHARE HANDLER
    // ==========================================

    fun downloadAndShare(
        context: Context,
        content: String,
        baseFileName: String,
        format: ExportFormat,
        subject: String = "Oakridge High School Report"
    ) {
        try {
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fullFileName = "${baseFileName}_$timestamp.${format.extension}"
            val exportFile = File(exportDir, fullFileName)

            FileOutputStream(exportFile).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                exportFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = format.mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TITLE, fullFileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Download or Open with... ($fullFileName)").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(chooser)
            Toast.makeText(context, "Export ready: $fullFileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download and share export file", e)
            Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
