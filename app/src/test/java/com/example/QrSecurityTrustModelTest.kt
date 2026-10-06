package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.MockStudentRepository
import com.example.data.RoomStudentRepository
import com.example.data.local.AppDatabase
import com.example.data.local.CardEntity
import com.example.data.local.StudentEntity
import com.example.model.AccessStatus
import com.example.model.Card
import com.example.model.CardStatus
import com.example.model.DayScholarStatus
import com.example.model.FeeStatus
import com.example.model.Student
import com.example.model.StudentScanResult
import com.example.util.CardCryptoManager
import com.example.util.QrCodeUtils
import com.example.util.QrParseResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPair
import java.util.UUID

/**
 * Executable security audit tests for the Lira Town College (LTC)
 * Student QR Identity System (Protocol V2 Authenticated Trust Model).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QrSecurityTrustModelTest {

    private lateinit var database: AppDatabase
    private lateinit var roomRepository: RoomStudentRepository
    private lateinit var mockRepository: MockStudentRepository
    private lateinit var testKeyPair: KeyPair

    private val studentId1 = "stu-test-001"
    private val studentNum1 = "LTC-2026-0001"
    private lateinit var cardId1: String
    private lateinit var validPayload1: String

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.createInMemory(context)
        roomRepository = RoomStudentRepository(database)
        mockRepository = MockStudentRepository.getInstance()

        // Generate isolated test-only key pair for cryptographic key management testing
        testKeyPair = CardCryptoManager.generateKeyPair()
        CardCryptoManager.configureTestKeyPair(testKeyPair)

        runBlocking {
            mockRepository.resetToSampleData()
            mockRepository.clearScanLogs()

            val now = System.currentTimeMillis()
            cardId1 = CardCryptoManager.generateSecureRandomCardId()
            validPayload1 = CardCryptoManager.signCardPayload(cardId1, testKeyPair.private)

            // Seed active student 1 in Room
            val s1 = StudentEntity(
                id = studentId1,
                studentNumber = studentNum1,
                name = "Julius Okot",
                firstName = "Julius",
                lastName = "Okot",
                photoUrl = null,
                accessStatus = AccessStatus.APPROVED.name,
                gradeClass = "Senior 4-A",
                isDayScholar = true,
                dayScholarType = DayScholarStatus.DAY_SCHOLAR_WALK.name,
                transportRoute = "Main Gate Line",
                feesStatus = FeeStatus.CLEARED.name,
                outstandingAmount = 0.0,
                gender = "Male",
                avatarColorSeed = 100L,
                guardianName = "Okot Denis",
                guardianPhone = "+256 772 111222",
                emergencyContact = "+256 772 111222",
                homeroomTeacher = "Mr. Obua Denis",
                academicYear = "2026",
                notes = "Security test student",
                qrToken = "TOK-TEST-01",
                updatedAt = now,
                isDeleted = false
            )
            database.studentDao().insertOrUpdateStudent(s1)

            // Seed active card 1 in Room
            val c1 = CardEntity(
                id = "crd-db-001",
                cardIdentifier = cardId1,
                studentId = studentId1,
                studentNumber = studentNum1,
                qrPayload = validPayload1,
                status = CardStatus.ACTIVE.name,
                issueDate = now,
                activationDate = now,
                deactivationDate = null,
                replacedByCardId = null,
                reason = "Primary test card",
                notes = "Authentic test card",
                updatedAt = now,
                isDeleted = false
            )
            database.cardDao().insertOrUpdateCard(c1)
        }
    }

    @After
    fun tearDown() {
        database.close()
        // Reset authority keys to clean state
        CardCryptoManager.resetForTesting()
    }

    // =========================================================================
    // 1. A genuinely signed V2 QR verifies successfully.
    // =========================================================================
    @Test
    fun test1_GenuinelySignedV2QrVerifiesSuccessfully() = runBlocking {
        val parseResult = QrCodeUtils.parseQrCode(validPayload1)
        assertTrue("Parsing must yield ValidV2Card", parseResult is QrParseResult.ValidV2Card)
        val valid = parseResult as QrParseResult.ValidV2Card
        assertEquals(cardId1, valid.cardId)

        // Cryptographic check
        assertTrue(
            "Cryptographic signature must verify against public key",
            CardCryptoManager.verifyCardSignature(valid.cardId, valid.signature)
        )

        // End-to-end Room verification
        val scanResult = roomRepository.verifyStudentByQr(validPayload1)
        assertTrue("Scan result must be Success", scanResult is StudentScanResult.Success)
        val success = scanResult as StudentScanResult.Success
        assertTrue("Eligible student with cleared fees must be approved", success.isApproved)
        assertEquals("Julius Okot", success.student.fullName)
        assertEquals(cardId1, success.card?.cardIdentifier)
    }

    // =========================================================================
    // 2. Modifying the cardId causes signature verification to fail.
    // =========================================================================
    @Test
    fun test2_ModifyingCardIdCausesSignatureVerificationToFail() = runBlocking {
        val parts = validPayload1.split(":")
        val signature = parts[3]
        val tamperedCardId = "CRD-TAMPERED99999999999999999999"
        val tamperedPayload = "LTC:V2:$tamperedCardId:$signature"

        // Cryptographic check
        assertFalse(
            "Altered cardId must fail Ed25519 signature verification",
            CardCryptoManager.verifyCardSignature(tamperedCardId, signature)
        )

        // End-to-end verification must reject
        val scanResult = roomRepository.verifyStudentByQr(tamperedPayload)
        assertTrue("Tampered cardId payload must be rejected as InvalidQr", scanResult is StudentScanResult.InvalidQr)
    }

    // =========================================================================
    // 3. Modifying any signed field causes verification to fail.
    // =========================================================================
    @Test
    fun test3_ModifyingAnySignedFieldCausesVerificationToFail() {
        val canonicalBytes = CardCryptoManager.getCanonicalMessageBytes(cardId1)
        val modifiedCanonical = "LTC:V2:${cardId1}_EXTRA".toByteArray(Charsets.UTF_8)
        assertFalse("Byte representation must be deterministic and sensitive to alteration",
            canonicalBytes.contentEquals(modifiedCanonical))

        val parts = validPayload1.split(":")
        val signature = parts[3]

        // Alter single byte of cardId
        val slightlyModifiedCardId = cardId1.dropLast(1) + if (cardId1.last() == 'A') 'B' else 'A'
        assertFalse(
            "Single character modification in cardId must break signature",
            CardCryptoManager.verifyCardSignature(slightlyModifiedCardId, signature)
        )
    }

    // =========================================================================
    // 4. A random unsigned cardId is rejected.
    // =========================================================================
    @Test
    fun test4_RandomUnsignedCardIdIsRejected() = runBlocking {
        val randomCardId = CardCryptoManager.generateSecureRandomCardId()

        // 1. Bare random card identifier
        val parseBare = QrCodeUtils.parseQrCode(randomCardId)
        assertTrue("Bare card identifier must be rejected", parseBare is QrParseResult.Invalid)

        val scanBare = roomRepository.verifyStudentByQr(randomCardId)
        assertTrue("Bare random cardId must be rejected", scanBare is StudentScanResult.InvalidQr)

        // 2. Unsigned formatted string
        val unsignedV2 = "LTC:V2:$randomCardId"
        val parseUnsigned = QrCodeUtils.parseQrCode(unsignedV2)
        assertTrue("Unsigned LTC:V2 must be rejected", parseUnsigned is QrParseResult.Invalid)

        // 3. Fake dummy signature
        val fakeSigned = "LTC:V2:$randomCardId:notarealsignature"
        val scanFake = roomRepository.verifyStudentByQr(fakeSigned)
        assertTrue("Fake signature must be rejected", scanFake is StudentScanResult.InvalidQr)
    }

    // =========================================================================
    // 5. A validly signed cardId whose card status is LOST is rejected.
    // =========================================================================
    @Test
    fun test5_ValidlySignedCardIdWithStatusLostIsRejected() = runBlocking {
        val now = System.currentTimeMillis()
        database.cardDao().updateCardStatus(
            cardId = "crd-db-001",
            newStatus = CardStatus.LOST.name,
            deactivationDate = now,
            replacedByCardId = null,
            reason = "Wallet lost in transit",
            updatedAt = now
        )

        // Signature itself is authentic, but Room revocation makes it inactive
        val scanResult = roomRepository.verifyStudentByQr(validPayload1)
        assertTrue("LOST card must yield CardInactive result", scanResult is StudentScanResult.CardInactive)
        val inactive = scanResult as StudentScanResult.CardInactive
        assertEquals(CardStatus.LOST, inactive.cardStatus)
        assertTrue(inactive.reason.contains("LOST"))
    }

    // =========================================================================
    // 6. A validly signed cardId whose card status is REPLACED is rejected.
    // =========================================================================
    @Test
    fun test6_ValidlySignedCardIdWithStatusReplacedIsRejected() = runBlocking {
        val now = System.currentTimeMillis()
        database.cardDao().updateCardStatus(
            cardId = "crd-db-001",
            newStatus = CardStatus.REPLACED.name,
            deactivationDate = now,
            replacedByCardId = "crd-db-002-new",
            reason = "Replaced by newly issued card",
            updatedAt = now
        )

        val scanResult = roomRepository.verifyStudentByQr(validPayload1)
        assertTrue("REPLACED card must yield CardInactive result", scanResult is StudentScanResult.CardInactive)
        val inactive = scanResult as StudentScanResult.CardInactive
        assertEquals(CardStatus.REPLACED, inactive.cardStatus)
        assertTrue(inactive.reason.contains("REPLACED"))
    }

    // =========================================================================
    // 7. A validly signed cardId whose card status is DEACTIVATED is rejected.
    // =========================================================================
    @Test
    fun test7_ValidlySignedCardIdWithStatusDeactivatedIsRejected() = runBlocking {
        val now = System.currentTimeMillis()
        database.cardDao().updateCardStatus(
            cardId = "crd-db-001",
            newStatus = CardStatus.DEACTIVATED.name,
            deactivationDate = now,
            replacedByCardId = null,
            reason = "Administrative disciplinary hold",
            updatedAt = now
        )

        val scanResult = roomRepository.verifyStudentByQr(validPayload1)
        assertTrue("DEACTIVATED card must yield CardInactive result", scanResult is StudentScanResult.CardInactive)
        val inactive = scanResult as StudentScanResult.CardInactive
        assertEquals(CardStatus.DEACTIVATED, inactive.cardStatus)
        assertTrue(inactive.reason.contains("DEACTIVATED"))
    }

    // =========================================================================
    // 8. A validly signed cardId for a deleted/inactive student is rejected.
    // =========================================================================
    @Test
    fun test8_ValidlySignedCardIdForDeletedStudentIsRejected() = runBlocking {
        // Soft delete student
        val s = database.studentDao().getStudentById(studentId1)!!
        database.studentDao().insertOrUpdateStudent(s.copy(isDeleted = true))

        val scanResult = roomRepository.verifyStudentByQr(validPayload1)
        assertTrue(
            "Scan for deleted student must yield StudentNotFound",
            scanResult is StudentScanResult.StudentNotFound
        )
    }

    // =========================================================================
    // 9. A valid signature for Card A cannot be changed to Card B by editing QR text.
    // =========================================================================
    @Test
    fun test9_ValidSignatureForCardACannotBeTransferredToCardB() = runBlocking {
        val cardBId = CardCryptoManager.generateSecureRandomCardId()
        val signatureA = validPayload1.split(":")[3]

        // Attacker attempts signature reuse: "LTC:V2:<cardBId>:<signatureA>"
        val forgedCardBPayload = "LTC:V2:$cardBId:$signatureA"

        assertFalse(
            "Signature for Card A must fail verification when paired with Card B",
            CardCryptoManager.verifyCardSignature(cardBId, signatureA)
        )

        val scanResult = roomRepository.verifyStudentByQr(forgedCardBPayload)
        assertTrue("Forged payload must be rejected as InvalidQr", scanResult is StudentScanResult.InvalidQr)
    }

    // =========================================================================
    // 10. An unknown but correctly formatted cardId is rejected.
    // =========================================================================
    @Test
    fun test10_UnknownCardIdWithValidSignatureIsRejected() = runBlocking {
        val unknownCardId = CardCryptoManager.generateSecureRandomCardId()
        val validPayloadForUnknownCard = CardCryptoManager.signCardPayload(unknownCardId, testKeyPair.private)

        // Signature is cryptographically authentic, but card does NOT exist in Room
        val scanResult = roomRepository.verifyStudentByQr(validPayloadForUnknownCard)
        assertTrue(
            "Cryptographically authentic but unregistered card must yield StudentNotFound / Unregistered",
            scanResult is StudentScanResult.StudentNotFound
        )
        val notFound = scanResult as StudentScanResult.StudentNotFound
        assertEquals(unknownCardId, notFound.parsedIdentifier)
    }

    // =========================================================================
    // 11. A malformed signature is rejected without crashing.
    // =========================================================================
    @Test
    fun test11_MalformedSignatureIsRejectedWithoutCrashing() = runBlocking {
        val malformedSignatures = listOf(
            "LTC:V2:$cardId1:!!!NOT_BASE_64!!!",
            "LTC:V2:$cardId1:",
            "LTC:V2:$cardId1:AAAA", // Too short for 64-byte Ed25519 signature
            "LTC:V2:$cardId1:${"A".repeat(200)}", // Wrong length
            "LTC:V2:$cardId1:====="
        )

        for (badPayload in malformedSignatures) {
            val scanResult = roomRepository.verifyStudentByQr(badPayload)
            assertTrue("Malformed signature '$badPayload' must return InvalidQr without throwing exception",
                scanResult is StudentScanResult.InvalidQr)
        }
    }

    // =========================================================================
    // 12. A bare student number is rejected.
    // =========================================================================
    @Test
    fun test12_BareStudentNumberIsRejected() = runBlocking {
        val bareNumbers = listOf(
            "LTC-2026-0001",
            "OAK-2026-0001",
            "STU-2026-0001",
            "ltc-2026-0042"
        )

        for (bare in bareNumbers) {
            val parseResult = QrCodeUtils.parseQrCode(bare)
            assertTrue("Bare student number '$bare' must be rejected during parse",
                parseResult is QrParseResult.Invalid)

            val scanResult = roomRepository.verifyStudentByQr(bare)
            assertTrue("Bare student number '$bare' must be rejected during scan",
                scanResult is StudentScanResult.InvalidQr)
        }
    }

    // =========================================================================
    // 13. A bare UUID is rejected.
    // =========================================================================
    @Test
    fun test13_BareUuidIsRejected() = runBlocking {
        val bareUuid = UUID.randomUUID().toString()

        val parseResult = QrCodeUtils.parseQrCode(bareUuid)
        assertTrue("Bare UUID must be rejected during parse", parseResult is QrParseResult.Invalid)

        val scanResult = roomRepository.verifyStudentByQr(bareUuid)
        assertTrue("Bare UUID must be rejected during scan", scanResult is StudentScanResult.InvalidQr)
    }

    // =========================================================================
    // 14. OAKRIDGE:* is rejected from the V2 production verification path.
    // =========================================================================
    @Test
    fun test14_OakridgeLegacyFormatIsRejectedFromProductionVerificationPath() = runBlocking {
        val legacyOakridgeList = listOf(
            "OAKRIDGE:STU:OAK-2026-0001",
            "OAKRIDGE:ID:12345678-1234-1234-1234-123456789abc",
            "oakridge:stu:oak-2026-0002"
        )

        for (legacy in legacyOakridgeList) {
            val parseResult = QrCodeUtils.parseQrCode(legacy)
            assertTrue("Legacy Oakridge format '$legacy' must be rejected", parseResult is QrParseResult.Invalid)

            val scanResult = roomRepository.verifyStudentByQr(legacy)
            assertTrue("Legacy Oakridge format '$legacy' must be rejected from gate scan",
                scanResult is StudentScanResult.InvalidQr)
        }
    }

    // =========================================================================
    // 15. There is no fallback to another active card when the scanned cardId is unknown or inactive.
    // =========================================================================
    @Test
    fun test15_NoFallbackToAnotherActiveCardWhenScannedCardIsUnknownOrInactive() = runBlocking {
        val now = System.currentTimeMillis()

        // Student 1 has a second ACTIVE card registered in the database
        val cardId2 = CardCryptoManager.generateSecureRandomCardId()
        val c2 = CardEntity(
            id = "crd-db-002-active",
            cardIdentifier = cardId2,
            studentId = studentId1,
            studentNumber = studentNum1,
            qrPayload = CardCryptoManager.signCardPayload(cardId2, testKeyPair.private),
            status = CardStatus.ACTIVE.name,
            issueDate = now + 1000,
            activationDate = now + 1000,
            deactivationDate = null,
            replacedByCardId = null,
            reason = "Second active card",
            notes = "Test active card",
            updatedAt = now + 1000,
            isDeleted = false
        )
        database.cardDao().insertOrUpdateCard(c2)

        // Mark card 1 as LOST
        database.cardDao().updateCardStatus(
            cardId = "crd-db-001",
            newStatus = CardStatus.LOST.name,
            deactivationDate = now,
            replacedByCardId = null,
            reason = "Reported lost",
            updatedAt = now
        )

        // When scanning card 1 (LOST), system MUST return CardInactive and MUST NOT fall back to card 2 (ACTIVE)!
        val scanResultCard1 = roomRepository.verifyStudentByQr(validPayload1)
        assertTrue("Must NOT fall back to second active card; must report LOST card",
            scanResultCard1 is StudentScanResult.CardInactive)
        val cardInactive = scanResultCard1 as StudentScanResult.CardInactive
        assertEquals(cardId1, cardInactive.card.cardIdentifier)
        assertEquals(CardStatus.LOST, cardInactive.cardStatus)

        // Scanning an unknown cardId must NOT fall back to any active card for the student
        val unknownCardId = CardCryptoManager.generateSecureRandomCardId()
        val unknownSigned = CardCryptoManager.signCardPayload(unknownCardId, testKeyPair.private)
        val scanResultUnknown = roomRepository.verifyStudentByQr(unknownSigned)
        assertTrue("Unknown card must yield StudentNotFound and not fall back to active student card",
            scanResultUnknown is StudentScanResult.StudentNotFound)
    }

    // =========================================================================
    // KEY MANAGEMENT TESTING
    // =========================================================================
    @Test
    fun testKeyManagement_SignatureVerificationAndTamperResistance() {
        val authorityKeyPair = CardCryptoManager.generateKeyPair()
        val attackerKeyPair = CardCryptoManager.generateKeyPair()

        // Configure system with authority public key
        CardCryptoManager.setVerificationPublicKey(authorityKeyPair.public)
        CardCryptoManager.setIssuerPrivateKey(authorityKeyPair.private)

        val cardId = CardCryptoManager.generateSecureRandomCardId()

        // 1. Authoritative signature verifies with public key
        val legitimatePayload = CardCryptoManager.signCardPayload(cardId, authorityKeyPair.private)
        val validParse = QrCodeUtils.parseQrCode(legitimatePayload) as QrParseResult.ValidV2Card
        assertTrue(
            "Trusted public key must verify legitimate signature",
            CardCryptoManager.verifyCardSignature(validParse.cardId, validParse.signature, authorityKeyPair.public)
        )

        // 2. Attacker signature generated with attacker private key fails verification
        val forgedPayload = CardCryptoManager.signCardPayload(cardId, attackerKeyPair.private)
        val forgedParse = QrCodeUtils.parseQrCode(forgedPayload) as QrParseResult.ValidV2Card
        assertFalse(
            "Attacker private key cannot produce valid signature against authority public key",
            CardCryptoManager.verifyCardSignature(forgedParse.cardId, forgedParse.signature, authorityKeyPair.public)
        )

        // 3. Gate device hardening: Clearing private key retains full offline verification capability
        CardCryptoManager.clearIssuerPrivateKey()
        assertFalse("Device without issuer private key cannot sign", CardCryptoManager.hasIssuerPrivateKey())
        assertTrue(
            "Device without issuer private key can still verify valid cards offline",
            CardCryptoManager.verifyCardSignature(validParse.cardId, validParse.signature)
        )
    }

    @Test
    fun testKeyExportAndImportRoundTrip() {
        val originalKeyPair = CardCryptoManager.generateKeyPair()

        val pubBase64 = CardCryptoManager.exportPublicKeyBase64(originalKeyPair.public)
        val privBase64 = CardCryptoManager.exportPrivateKeyBase64(originalKeyPair.private)

        val restoredPublic = CardCryptoManager.importPublicKeyBase64(pubBase64)
        val restoredPrivate = CardCryptoManager.importPrivateKeyBase64(privBase64)

        val cardId = CardCryptoManager.generateSecureRandomCardId()
        val signedPayload = CardCryptoManager.signCardPayload(cardId, restoredPrivate)
        val parseResult = QrCodeUtils.parseQrCode(signedPayload) as QrParseResult.ValidV2Card

        assertTrue(
            "Imported public key must verify signatures produced with imported private key",
            CardCryptoManager.verifyCardSignature(parseResult.cardId, parseResult.signature, restoredPublic)
        )
    }
}
