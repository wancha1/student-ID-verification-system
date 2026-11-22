package com.example.util

import android.util.Xml
import com.example.model.Student
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

/**
 * Loss-aware parser for the Scholink 13-column student roster.
 * CSV and the first worksheet of modern Excel (.xlsx) workbooks are supported.
 */
data class RosterImportRecord(
    val rowNumber: Int,
    val admissionNumber: String,
    val firstName: String,
    val lastName: String,
    val otherNames: String,
    val gender: String,
    val dateOfBirth: String,
    val className: String,
    val stream: String,
    val studentPhone: String,
    val guardianName: String,
    val guardianPhone: String,
    val email: String,
    val rosterStatus: String,
    val sourceFields: Map<String, String>
) {
    fun toStudent(): Student {
        val preserved = linkedMapOf<String, String>()
        preserved["other_names"] = otherNames
        preserved["date_of_birth"] = dateOfBirth
        preserved["student_phone"] = studentPhone
        preserved["email"] = email
        preserved["status"] = rosterStatus
        sourceFields.forEach { (key, value) ->
            if (key !in SUPPORTED_ROSTER_COLUMNS && value.isNotBlank()) preserved[key] = value
        }
        val metadata = preserved.filterValues { it.isNotBlank() }
            .entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
                "${jsonString(key)}:${jsonString(value)}"
            }

        return Student(
            studentNumber = admissionNumber.trim().uppercase(Locale.ROOT),
            firstName = listOf(firstName, otherNames).filter { it.isNotBlank() }.joinToString(" ").trim(),
            lastName = lastName.trim(),
            gradeClass = listOf(className, stream).filter { it.isNotBlank() }.joinToString("-"),
            gender = gender.ifBlank { "Unspecified" },
            guardianName = guardianName.ifBlank { "Guardian" },
            guardianPhone = guardianPhone,
            notes = if (metadata == "{}") "" else
                "ROSTER_IMPORT_METADATA_V1 (preserved source values; not used to grant gate access): $metadata"
        )
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        append('"')
    }

    companion object {
        private val SUPPORTED_ROSTER_COLUMNS = setOf(
            "admission_number", "first_name", "last_name", "gender",
            "class_name", "stream", "guardian_name", "guardian_phone"
        )
    }
}

data class RosterImportPreview(
    val records: List<RosterImportRecord>,
    val errors: List<String>,
    val headers: List<String>
) {
    val validCount: Int get() = records.size
    val errorCount: Int get() = errors.size
}

object RosterImportParser {
    private const val MAX_INPUT_BYTES = 20 * 1024 * 1024
    private val requiredColumns = setOf("admission_number", "first_name", "last_name", "class_name")

    fun parse(input: InputStream, fileName: String, mimeType: String? = null): RosterImportPreview {
        val bytes = input.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_INPUT_BYTES) { "Roster file exceeds the 20 MB import limit." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val lowerName = fileName.lowercase(Locale.ROOT)
        val rows = when {
            lowerName.endsWith(".csv") || mimeType == "text/csv" ->
                parseCsv(String(bytes, Charsets.UTF_8).removePrefix("\uFEFF"))
            lowerName.endsWith(".xlsx") ||
                mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
                parseXlsx(bytes)
            else -> throw IllegalArgumentException("Unsupported file. Choose a .csv or .xlsx roster.")
        }
        return validateRows(rows)
    }

    private fun validateRows(rows: List<List<String>>): RosterImportPreview {
        if (rows.isEmpty()) return RosterImportPreview(emptyList(), listOf("The selected file is empty."), emptyList())
        val headers = rows.first().map(::normalizeHeader)
        val missing = requiredColumns - headers.toSet()
        if (missing.isNotEmpty()) {
            return RosterImportPreview(
                emptyList(),
                listOf("Missing required column(s): ${missing.sorted().joinToString()}."),
                rows.first()
            )
        }

        val index = headers.withIndex().associate { it.value to it.index }
        val seen = mutableSetOf<String>()
        val records = mutableListOf<RosterImportRecord>()
        val errors = mutableListOf<String>()

        rows.drop(1).forEachIndexed { offset, row ->
            val rowNumber = offset + 2
            if (row.all { it.isBlank() }) return@forEachIndexed
            fun value(column: String): String = index[column]?.let { row.getOrNull(it)?.trim().orEmpty() }.orEmpty()
            val admission = value("admission_number")
            val first = value("first_name")
            val last = value("last_name")
            val className = value("class_name")
            val missingValues = buildList {
                if (admission.isBlank()) add("admission_number")
                if (first.isBlank()) add("first_name")
                if (last.isBlank()) add("last_name")
                if (className.isBlank()) add("class_name")
            }
            if (missingValues.isNotEmpty()) {
                errors += "Row $rowNumber: missing ${missingValues.joinToString()}."
                return@forEachIndexed
            }
            val normalizedAdmission = admission.uppercase(Locale.ROOT)
            if (!seen.add(normalizedAdmission)) {
                errors += "Row $rowNumber: duplicate admission_number '$admission' within this file."
                return@forEachIndexed
            }
            val fields = headers.mapIndexedNotNull { columnIndex, header ->
                header.takeIf { it.isNotBlank() }?.let { it to row.getOrNull(columnIndex).orEmpty().trim() }
            }.toMap()
            records += RosterImportRecord(
                rowNumber = rowNumber,
                admissionNumber = admission,
                firstName = first,
                lastName = last,
                otherNames = value("other_names"),
                gender = value("gender"),
                dateOfBirth = value("date_of_birth"),
                className = className,
                stream = value("stream"),
                studentPhone = value("student_phone"),
                guardianName = value("guardian_name"),
                guardianPhone = value("guardian_phone"),
                email = value("email"),
                rosterStatus = value("status"),
                sourceFields = fields
            )
        }
        return RosterImportPreview(records, errors, rows.first())
    }

    private fun normalizeHeader(value: String): String {
        val cleaned = value.trim().lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        return when (cleaned) {
            "admission_no", "admission_number", "student_number", "registration_number" -> "admission_number"
            "firstname", "first_name" -> "first_name"
            "lastname", "last_name", "surname" -> "last_name"
            "other_name", "other_names", "middle_name", "middle_names" -> "other_names"
            "class", "class_name", "grade", "grade_class" -> "class_name"
            "guardian", "parent_name", "guardian_name" -> "guardian_name"
            "parent_phone", "guardian_contact", "guardian_phone" -> "guardian_phone"
            "student_contact", "student_phone", "phone" -> "student_phone"
            "dob", "date_of_birth", "birth_date" -> "date_of_birth"
            "student_email", "email_address", "email" -> "email"
            "student_status", "status" -> "status"
            else -> cleaned
        }
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char == '"' && quoted && index + 1 < text.length && text[index + 1] == '"' -> {
                    cell.append('"'); index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    row += cell.toString(); cell.clear()
                }
                (char == '\n' || char == '\r') && !quoted -> {
                    row += cell.toString(); cell.clear()
                    if (row.any { it.isNotBlank() }) rows += row.toList()
                    row.clear()
                    if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                }
                else -> cell.append(char)
            }
            index++
        }
        if (quoted) throw IllegalArgumentException("CSV contains an unterminated quoted field.")
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            if (row.any { it.isNotBlank() }) rows += row.toList()
        }
        return rows
    }

    private fun parseXlsx(bytes: ByteArray): List<List<String>> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && (entry.name == "xl/sharedStrings.xml" ||
                    entry.name == "xl/worksheets/sheet1.xml")) {
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    var total = 0
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_INPUT_BYTES) { "Excel worksheet is too large to import." }
                        output.write(buffer, 0, count)
                    }
                    entries[entry.name] = output.toByteArray()
                }
            }
        }
        val worksheet = entries["xl/worksheets/sheet1.xml"]
            ?: throw IllegalArgumentException("Excel workbook has no first worksheet.")
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        return parseWorksheet(worksheet, sharedStrings)
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val parser = Xml.newPullParser()
        parser.setInput(xml.inputStream(), "UTF-8")
        val strings = mutableListOf<String>()
        var inItem = false
        val value = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { inItem = true; value.clear() }
                    "t" -> if (inItem) value.append(parser.nextText())
                }
                XmlPullParser.END_TAG -> if (parser.name == "si" && inItem) {
                    strings += value.toString()
                    inItem = false
                }
            }
            event = parser.next()
        }
        return strings
    }

    private fun parseWorksheet(xml: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val parser = Xml.newPullParser()
        parser.setInput(xml.inputStream(), "UTF-8")
        val rows = mutableListOf<List<String>>()
        var currentRow: MutableMap<Int, String>? = null
        var cellIndex = -1
        var cellType = ""
        var cellValue = ""
        var inCell = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> currentRow = mutableMapOf()
                    "c" -> {
                        inCell = true
                        cellType = parser.getAttributeValue(null, "t").orEmpty()
                        cellIndex = columnIndex(parser.getAttributeValue(null, "r").orEmpty())
                        cellValue = ""
                    }
                    "v", "t" -> if (inCell) cellValue += parser.nextText()
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "c" -> {
                        val resolved = if (cellType == "s") {
                            sharedStrings.getOrNull(cellValue.toIntOrNull() ?: -1).orEmpty()
                        } else cellValue
                        if (cellIndex >= 0) currentRow?.set(cellIndex, resolved)
                        inCell = false
                    }
                    "row" -> {
                        val values = currentRow.orEmpty()
                        val max = values.keys.maxOrNull() ?: -1
                        rows += (0..max).map { values[it].orEmpty() }
                        currentRow = null
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun columnIndex(reference: String): Int {
        val letters = reference.takeWhile { it.isLetter() }.uppercase(Locale.ROOT)
        if (letters.isEmpty()) return -1
        var result = 0
        letters.forEach { result = result * 26 + (it - 'A' + 1) }
        return result - 1
    }
}
