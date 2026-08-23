package com.example.data

import com.example.model.Card
import com.example.model.CardStatus
import com.example.model.DayScholarStatus
import com.example.model.FeeStatus
import com.example.model.GateVerificationDecision
import com.example.model.ScanLog
import com.example.model.Student

object StudentDataSamples {

    fun createInitialStudents(): List<Student> = listOf(
        Student(
            id = "c7b2-4f11-9a3d-0001",
            studentNumber = "OAK-2026-0001",
            firstName = "Michael",
            lastName = "Adeyemi",
            gradeClass = "Senior 4-A",
            isDayScholar = true,
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_BUS,
            transportRoute = "School Bus #4 (Kampala Central • Route A)",
            feesStatus = FeeStatus.CLEARED,
            outstandingAmount = 0.0,
            gender = "Male",
            avatarColorSeed = 0xFF1D4ED8,
            photoUrl = null,
            guardianName = "Dr. Samuel Adeyemi",
            guardianPhone = "+256 772 234890",
            emergencyContact = "+256 701 234890",
            homeroomTeacher = "Mr. Kenneth Ross",
            academicYear = "2025/2026",
            notes = "Honor student, science club lead.",
            updatedAt = 1735689600000L
        ),
        Student(
            id = "c7b2-4f11-9a3d-0002",
            studentNumber = "OAK-2026-0002",
            firstName = "Sophia",
            lastName = "Chen",
            gradeClass = "Senior 3-B",
            isDayScholar = true,
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_PRIVATE,
            transportRoute = "Parent Drop-off (Gate 1 Carpool)",
            feesStatus = FeeStatus.OUTSTANDING,
            outstandingAmount = 480000.00,
            gender = "Female",
            avatarColorSeed = 0xFFDC2626,
            photoUrl = null,
            guardianName = "Mrs. Mei Chen",
            guardianPhone = "+256 782 345671",
            emergencyContact = "+256 752 345671",
            homeroomTeacher = "Ms. Lauren Parker",
            notes = "Outstanding 2nd installment for Term 1.",
            updatedAt = 1735689600000L
        ),
        Student(
            id = "c7b2-4f11-9a3d-0003",
            studentNumber = "OAK-2026-0003",
            firstName = "Liam",
            lastName = "O'Connor",
            gradeClass = "Senior 6-Sciences",
            isDayScholar = true,
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_BUS,
            transportRoute = "School Bus #2 (Entebbe Road)",
            feesStatus = FeeStatus.CLEARED,
            outstandingAmount = 0.0,
            gender = "Male",
            avatarColorSeed = 0xFF059669,
            photoUrl = null,
            guardianName = "Patrick O'Connor",
            guardianPhone = "+256 703 456782",
            emergencyContact = "+256 773 456789",
            homeroomTeacher = "Dr. Arthur Vance",
            notes = "Varsity basketball captain.",
            updatedAt = 1735689600000L
        ),
        Student(
            id = "c7b2-4f11-9a3d-0004",
            studentNumber = "OAK-2026-0004",
            firstName = "Amina",
            lastName = "Al-Mansoor",
            gradeClass = "Senior 1-Gold",
            isDayScholar = true,
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_WALK,
            transportRoute = "Walking / Local Transit (North Gate)",
            feesStatus = FeeStatus.OUTSTANDING,
            outstandingAmount = 620000.00,
            gender = "Female",
            avatarColorSeed = 0xFFD97706,
            photoUrl = null,
            guardianName = "Fatima Al-Mansoor",
            guardianPhone = "+256 754 567893",
            emergencyContact = "+256 704 567890",
            homeroomTeacher = "Mrs. Clara Higgins",
            notes = "Awaiting bursary confirmation letter.",
            updatedAt = 1735689600000L
        ),
        Student(
            id = "c7b2-4f11-9a3d-0005",
            studentNumber = "OAK-2026-0005",
            firstName = "Lucas",
            lastName = "Silva",
            gradeClass = "Senior 4-B",
            isDayScholar = true,
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_BUS,
            transportRoute = "School Bus #4 (Kira • Naalya)",
            feesStatus = FeeStatus.CLEARED,
            outstandingAmount = 0.0,
            gender = "Male",
            avatarColorSeed = 0xFF7C3AED,
            photoUrl = null,
            guardianName = "Elena Silva",
            guardianPhone = "+256 775 678904",
            emergencyContact = "+256 705 678900",
            homeroomTeacher = "Mr. Kenneth Ross",
            notes = "Replaced lost card on 15 Feb.",
            updatedAt = 1735689600000L
        ),
        Student(
            id = "c7b2-4f11-9a3d-0006",
            studentNumber = "OAK-2026-0006",
            firstName = "Emily",
            lastName = "Watson",
            gradeClass = "Senior 2-A",
            isDayScholar = true,
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_PRIVATE,
            transportRoute = "Parent Drop-off",
            feesStatus = FeeStatus.CLEARED,
            outstandingAmount = 0.0,
            gender = "Female",
            avatarColorSeed = 0xFFDB2777,
            photoUrl = null,
            guardianName = "James & Sarah Watson",
            guardianPhone = "+256 786 789015",
            emergencyContact = "+256 756 789010",
            homeroomTeacher = "Ms. Lauren Parker",
            notes = "All fees cleared on Term 1 intake.",
            updatedAt = 1735689600000L
        ),
        Student(
            id = "c7b2-4f11-9a3d-0007",
            studentNumber = "OAK-2026-0007",
            firstName = "Tariq",
            lastName = "Johnson",
            gradeClass = "Senior 5-Arts",
            isDayScholar = true,
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_BUS,
            transportRoute = "School Bus #1 (Lugogo • Nakawa)",
            feesStatus = FeeStatus.OUTSTANDING,
            outstandingAmount = 350000.00,
            gender = "Male",
            avatarColorSeed = 0xFFE11D48,
            photoUrl = null,
            guardianName = "Marcus Johnson",
            guardianPhone = "+256 707 890126",
            emergencyContact = "+256 777 890120",
            homeroomTeacher = "Dr. Arthur Vance",
            notes = "Card deactivated pending Bursar interview.",
            updatedAt = 1735689600000L
        ),
        Student(
            id = "c7b2-4f11-9a3d-0008",
            studentNumber = "OAK-2026-0008",
            firstName = "Chloe",
            lastName = "Dubois",
            gradeClass = "Senior 3-C",
            isDayScholar = true,
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_BUS,
            transportRoute = "School Bus #3 (Bukoto • Ntinda)",
            feesStatus = FeeStatus.CLEARED,
            outstandingAmount = 0.0,
            gender = "Female",
            avatarColorSeed = 0xFF0891B2,
            photoUrl = null,
            guardianName = "Genevieve Dubois",
            guardianPhone = "+256 788 901237",
            emergencyContact = "+256 758 901230",
            homeroomTeacher = "Ms. Lauren Parker",
            notes = "Art exhibition showcase contributor.",
            updatedAt = 1735689600000L
        ),
        Student(
            id = "c7b2-4f11-9a3d-0009",
            studentNumber = "OAK-2026-0009",
            firstName = "Noah",
            lastName = "Patel",
            gradeClass = "Senior 1-Silver",
            isDayScholar = true,
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_WALK,
            transportRoute = "Walking / Local Transit (South Gate)",
            feesStatus = FeeStatus.CLEARED,
            outstandingAmount = 0.0,
            gender = "Male",
            avatarColorSeed = 0xFF4F46E5,
            photoUrl = null,
            guardianName = "Aarav Patel",
            guardianPhone = "+256 709 012348",
            emergencyContact = "+256 779 012340",
            homeroomTeacher = "Mrs. Clara Higgins",
            notes = "Chess champion.",
            updatedAt = 1735689600000L
        ),
        Student(
            id = "c7b2-4f11-9a3d-0010",
            studentNumber = "OAK-2026-0010",
            firstName = "Isabella",
            lastName = "Martinez",
            gradeClass = "Senior 4-A",
            isDayScholar = true,
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_PRIVATE,
            transportRoute = "Parent Drop-off",
            feesStatus = FeeStatus.OUTSTANDING,
            outstandingAmount = 520000.00,
            gender = "Female",
            avatarColorSeed = 0xFFB91C1C,
            photoUrl = null,
            guardianName = "Carlos Martinez",
            guardianPhone = "+256 780 123459",
            emergencyContact = "+256 750 123450",
            homeroomTeacher = "Mr. Kenneth Ross",
            notes = "Direct to Bursar building Rm 104.",
            updatedAt = 1735689600000L
        )
    )

    fun createInitialCards(students: List<Student> = createInitialStudents()): List<Card> {
        val cards = mutableListOf<Card>()
        students.forEach { student ->
            when (student.studentNumber) {
                "OAK-2026-0005" -> {
                    // Lucas Silva: Has historical LOST card #1, and ACTIVE replacement card #2
                    val card1Id = "crd-card-0005-01"
                    val card2Id = "crd-card-0005-02"

                    cards.add(
                        Card(
                            id = card1Id,
                            cardIdentifier = "CRD-2026-0005-01",
                            studentId = student.id,
                            studentNumber = student.studentNumber,
                            qrPayload = "OAKRIDGE:STU:${student.studentNumber}",
                            status = CardStatus.LOST,
                            issueDate = 1736928000000L, // Jan 15
                            activationDate = 1736928000000L,
                            deactivationDate = 1739520000000L, // Feb 14
                            replacedByCardId = card2Id,
                            reason = "Lost on public transit route",
                            notes = "Reported by parent Elena Silva"
                        )
                    )

                    cards.add(
                        Card(
                            id = card2Id,
                            cardIdentifier = "CRD-2026-0005-02",
                            studentId = student.id,
                            studentNumber = student.studentNumber,
                            qrPayload = "OAKRIDGE:STU:${student.studentNumber}",
                            status = CardStatus.ACTIVE,
                            issueDate = 1739606400000L, // Feb 15
                            activationDate = 1739606400000L,
                            deactivationDate = null,
                            replacedByCardId = null,
                            reason = "Replacement card issued",
                            notes = "Active physical PVC card"
                        )
                    )
                }

                "OAK-2026-0007" -> {
                    // Tariq Johnson: Deactivated card
                    cards.add(
                        Card(
                            id = "crd-card-0007-01",
                            cardIdentifier = "CRD-2026-0007-01",
                            studentId = student.id,
                            studentNumber = student.studentNumber,
                            qrPayload = "OAKRIDGE:STU:${student.studentNumber}",
                            status = CardStatus.DEACTIVATED,
                            issueDate = 1736928000000L,
                            activationDate = 1736928000000L,
                            deactivationDate = 1738368000000L,
                            replacedByCardId = null,
                            reason = "Administrative temporary suspension pending bursar clearance",
                            notes = "Deactivated by Administrator"
                        )
                    )
                }

                else -> {
                    // Standard single ACTIVE card
                    cards.add(
                        Card(
                            id = "crd-card-${student.studentNumber.takeLast(4)}-01",
                            cardIdentifier = "CRD-${student.studentNumber.removePrefix("OAK-")}-01",
                            studentId = student.id,
                            studentNumber = student.studentNumber,
                            qrPayload = "OAKRIDGE:STU:${student.studentNumber}",
                            status = CardStatus.ACTIVE,
                            issueDate = 1736928000000L,
                            activationDate = 1736928000000L,
                            deactivationDate = null,
                            replacedByCardId = null,
                            reason = "Standard enrollment issuance",
                            notes = "Primary digital/printed badge"
                        )
                    )
                }
            }
        }
        return cards
    }

    fun createInitialScanLogs(): List<ScanLog> {
        val now = System.currentTimeMillis()
        return listOf(
            ScanLog(
                id = "log-init-001",
                studentId = "c7b2-4f11-9a3d-0001",
                studentNumber = "OAK-2026-0001",
                studentName = "Michael Adeyemi",
                gradeClass = "Senior 4-A",
                cardId = "crd-card-0001-01",
                cardIdentifier = "CRD-2026-0001-01",
                qrPayload = "OAKRIDGE:STU:OAK-2026-0001",
                timestamp = now - 3600000L * 2,
                decision = GateVerificationDecision.APPROVED,
                feeStatus = FeeStatus.CLEARED,
                cardStatus = CardStatus.ACTIVE,
                isDayScholar = true,
                isApproved = true,
                reason = "Entry Approved: Fees Cleared & Card Active",
                isOfflineDecision = false,
                dataSyncTimestampAtScan = now - 3600000L * 3,
                guardName = "Peter (Main Gate)",
                deviceIdentifier = "GateTerminal-01",
                gateLocation = "Gate 1 (Main Entrance)",
                isSyncedToCloud = true
            ),
            ScanLog(
                id = "log-init-002",
                studentId = "c7b2-4f11-9a3d-0002",
                studentNumber = "OAK-2026-0002",
                studentName = "Sophia Chen",
                gradeClass = "Senior 3-B",
                cardId = "crd-card-0002-01",
                cardIdentifier = "CRD-2026-0002-01",
                qrPayload = "OAKRIDGE:STU:OAK-2026-0002",
                timestamp = now - 3600000L * 1,
                decision = GateVerificationDecision.NOT_APPROVED,
                feeStatus = FeeStatus.OUTSTANDING,
                cardStatus = CardStatus.ACTIVE,
                isDayScholar = true,
                isApproved = false,
                reason = "Entry Not Approved: Fees Outstanding (UGX 480,000) • Direct to Bursar",
                isOfflineDecision = true,
                dataSyncTimestampAtScan = now - 3600000L * 2,
                guardName = "Peter (Main Gate)",
                deviceIdentifier = "GateTerminal-01",
                gateLocation = "Gate 1 (Main Entrance)",
                isSyncedToCloud = false
            ),
            ScanLog(
                id = "log-init-003",
                studentId = "c7b2-4f11-9a3d-0007",
                studentNumber = "OAK-2026-0007",
                studentName = "Tariq Johnson",
                gradeClass = "Senior 5-Arts",
                cardId = "crd-card-0007-01",
                cardIdentifier = "CRD-2026-0007-01",
                qrPayload = "OAKRIDGE:STU:OAK-2026-0007",
                timestamp = now - 1800000L,
                decision = GateVerificationDecision.CARD_INACTIVE,
                feeStatus = FeeStatus.OUTSTANDING,
                cardStatus = CardStatus.DEACTIVATED,
                isDayScholar = true,
                isApproved = false,
                reason = "Card Inactive: Card CRD-2026-0007-01 was DEACTIVATED by Administrator",
                isOfflineDecision = false,
                dataSyncTimestampAtScan = now - 3600000L,
                guardName = "Daniel (North Gate)",
                deviceIdentifier = "GateTerminal-02",
                gateLocation = "North Gate (Bus Bay)",
                isSyncedToCloud = true
            )
        )
    }
}
