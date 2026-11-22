package com.example

import com.example.util.RosterImportParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RosterImportParserTest {

    @Test
    fun csvImportMapsSupportedFieldsAndPreservesUnsupportedFields() {
        val csv = """admission_number,first_name,last_name,other_names,gender,date_of_birth,class_name,stream,student_phone,guardian_name,guardian_phone,email,status,house
LTC-001,Alice,Okello,"Grace, Mary",Female,2009-04-12,Senior 3,A,0700000001,John Okello,0770000001,alice@example.com,ACTIVE,Pakwach
"""
        val preview = RosterImportParser.parse(
            ByteArrayInputStream(csv.toByteArray()),
            "students.csv",
            "text/csv"
        )

        assertEquals(1, preview.validCount)
        assertTrue(preview.errors.isEmpty())
        val student = preview.records.single().toStudent()
        assertEquals("LTC-001", student.studentNumber)
        assertEquals("Alice Grace, Mary", student.firstName)
        assertEquals("Senior 3-A", student.gradeClass)
        assertEquals("Female", student.gender)
        assertEquals("John Okello", student.guardianName)
        assertTrue(student.notes.contains("date_of_birth"))
        assertTrue(student.notes.contains("0700000001"))
        assertTrue(student.notes.contains("alice@example.com"))
        assertTrue(student.notes.contains("ACTIVE"))
        assertTrue(student.notes.contains("Pakwach"))
        assertTrue(student.notes.contains("not used to grant gate access"))
    }

    @Test
    fun csvImportRejectsDuplicateAdmissionNumbersAndMissingRequiredValues() {
        val csv = """admission_number,first_name,last_name,class_name
LTC-001,Alice,Okello,Senior 3
LTC-001,Peter,Otto,Senior 4
LTC-003,,Auma,Senior 2
"""
        val preview = RosterImportParser.parse(
            ByteArrayInputStream(csv.toByteArray()),
            "students.csv",
            "text/csv"
        )

        assertEquals(1, preview.validCount)
        assertEquals(2, preview.errorCount)
        assertTrue(preview.errors.any { it.contains("duplicate admission_number") })
        assertTrue(preview.errors.any { it.contains("first_name") })
    }

    @Test
    fun xlsxImportReadsFirstWorksheetAndSharedStrings() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zip.write("""<?xml version="1.0" encoding="UTF-8"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>admission_number</t></si><si><t>first_name</t></si><si><t>last_name</t></si><si><t>class_name</t></si><si><t>LTC-009</t></si><si><t>Mary</t></si><si><t>Atim</t></si><si><t>Senior 5</t></si></sst>""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c><c r="D1" t="s"><v>3</v></c></row><row r="2"><c r="A2" t="s"><v>4</v></c><c r="B2" t="s"><v>5</v></c><c r="C2" t="s"><v>6</v></c><c r="D2" t="s"><v>7</v></c></row></sheetData></worksheet>""".toByteArray())
            zip.closeEntry()
        }

        val preview = RosterImportParser.parse(
            ByteArrayInputStream(output.toByteArray()),
            "students.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )

        assertEquals(1, preview.validCount)
        assertEquals("LTC-009", preview.records.single().admissionNumber)
        assertEquals("Mary", preview.records.single().firstName)
        assertEquals("Senior 5", preview.records.single().className)
    }
}
