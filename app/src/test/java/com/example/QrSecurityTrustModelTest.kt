package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.CardCryptoUtils
import com.example.crypto.CardDateUtils
import com.example.crypto.SoftwareCardSigner
import com.example.crypto.TrustedIssuerRegistry
import com.example.data.MockStudentRepository
import com.example.data.RoomStudentRepository
import com.example.data.local.AppDatabase
import com.example.data.local.CardEntity
import com.example.data.local.StudentEntity
import com.example.model.AccessStatus
import com.example.model.CardStatus
import com.example.model.DayScholarStatus
import com.example.model.FeeStatus
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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPair
import java.util.UUID

/**
 * Executable security audit tests for the Lira Town College (LTC)
 * Student QR Identity System (Protocol V2 Authenticated ECDSA P-256 Trust Model).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QrSecurityTrustModelTest {

    private lateinit var database: AppDatabase
    private lateinit var roomRepository: RoomStudentRepository
    private lateinit var mockRepository: MockStudentRepository
    private lateinit var testKeyPair: KeyPair
    private lateinit var testSigner: SoftwareCardSigner

    private val studentId1 = "stu-test-001"
    private val studentNum1 = "LTC-2026-0001"
    private lateinit var cardId1: String
    private lateinit var validPayload1: String
    private val validFrom = "2026-01-01"
    private val validUntil = "2026-12-31"

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TrustedIssuerRegistry.initialize(context)
        TrustedIssuerRegistry.clearAll()
        CardCryptoManager.resetForTesting()

        database = AppDatabase.createInMemory(context)
        roomRepository = RoomStudentRepository(database)
        mockRepository = MockStudentRepository.getInstance()

        // Generate isolated test-only P-256 key pair and configure test signer
        testKeyPair = CardCryptoManager.generateKeyPair()
        testSigner = SoftwareCardSigner(testKeyPair)
        CardCryptoManager.setActiveSigner(testSigner)
        TrustedIssuerRegistry.registerTrustedKey(testSigner.publicKey, "Primary Test Issuer Authority")

        runBlocking {
            mockRepository.resetToSampleData()
            mockRepository.clearScanLogs()

            val now = System.currentTimeMillis()
            cardId1 = CardCryptoManager.generateSecureRandomCardId()
            validPayload1 = CardCryptoManager.signCardPayload(
                cardId = cardId1,
                validFrom = validFrom,
                validUntil = validUntil,
                signer = testSigner
            )

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
        CardCryptoManager.resetForTesting()
        TrustedIssuerRegistry.clearAll()
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
        assertEquals(testSigner.keyId, valid.kid)
        assertEquals(validFrom, valid.validFrom)
        assertEquals(validUntil, valid.validUntil)

        // Cryptographic check
        assertTrue(
            "Cryptographic signature must verify against trusted public key",
            CardCryptoManager.verifyCardSignature(valid)
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
        assertEquals(7, parts.size)
        val kid = parts[2]
        val from = parts[4]
        val until = parts[5]
        val signature = parts[6]
        val tamperedCardId = "CRD-TAMPERED99999999999999999999"
        val tamperedPayload = "LTC:V2:$kid:$tamperedCardId:$from:$until:$signature"

        // Cryptographic check
        assertFalse(
            "Altered cardId must fail ECDSA P-256 signature verification",
            CardCryptoManager.verifyCardSignature(
                kid = kid,
                cardId = tamperedCardId,
                validFrom = from,
                validUntil = until,
                signatureBase64Url = signature
            )
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
        val parts = validPayload1.split(":")
        val kid = parts[2]
        val from = parts[4]
        val until = parts[5]
        val signature = parts[6]

        val canonicalBytes = CardCryptoManager.getCanonicalMessageBytes(kid, cardId1, from, until)
        val modifiedCanonical = "LTC-V2|$kid|${cardId1}_EXTRA|$from|$until".toByteArray(Charsets.UTF_8)
        assertFalse("Canonical byte representation must be deterministic and sensitive to alteration",
            canonicalBytes.contentEquals(modifiedCanonical))

        // Alter single byte of cardId
        val slightlyModifiedCardId = cardId1.dropLast(1) + if (cardId1.last() == 'A') 'B' else 'A'
        assertFalse(
            "Single character modification in cardId must break signature",
            CardCryptoManager.verifyCardSignature(
                kid = kid,
                cardId = slightlyModifiedCardId,
                validFrom = from,
                validUntil = until,
                signatureBase64Url = signature
            )
        )

        // Alter validFrom date
        assertFalse(
            "Modification in validFrom date must break signature",
            CardCryptoManager.verifyCardSignature(
                kid = kid,
                cardId = cardId1,
                validFrom = "2025-01-01",
                validUntil = until,
                signatureBase64Url = signature
            )
        )

        // Alter validUntil date
        assertFalse(
            "Modification in validUntil date must break signature",
            CardCryptoManager.verifyCardSignature(
                kid = kid,
                cardId = cardId1,
                validFrom = from,
                validUntil = "2027-12-31",
                signatureBase64Url = signature
            )
        )

        // Alter kid
        val fakeKid = "ffffffffffffffff"
        assertFalse(
            "Modification in kid must break signature",
            CardCryptoManager.verifyCardSignature(
                kid = fakeKid,
                cardId = cardId1,
                validFrom = from,
                validUntil = until,
                signatureBase64Url = signature,
                publicKey = testSigner.publicKey
            )
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
        val fakeSigned = "LTC:V2:${testSigner.keyId}:$randomCardId:$validFrom:$validUntil:notarealsignature"
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
    // 9. A valid signature for Card A cannot be transferred to Card B.
    // =========================================================================
    @Test
    fun test9_ValidSignatureForCardACannotBeTransferredToCardB() = runBlocking {
        val cardBId = CardCryptoManager.generateSecureRandomCardId()
        val parts = validPayload1.split(":")
        val signatureA = parts[6]

        // Attacker attempts signature reuse: "LTC:V2:<kid>:<cardBId>:<from>:<until>:<signatureA>"
        val forgedCardBPayload = "LTC:V2:${parts[2]}:$cardBId:${parts[4]}:${parts[5]}:$signatureA"

        assertFalse(
            "Signature for Card A must fail verification when paired with Card B",
            CardCryptoManager.verifyCardSignature(
                kid = parts[2],
                cardId = cardBId,
                validFrom = parts[4],
                validUntil = parts[5],
                signatureBase64Url = signatureA
            )
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
        val validPayloadForUnknownCard = CardCryptoManager.signCardPayload(
            cardId = unknownCardId,
            validFrom = validFrom,
            validUntil = validUntil,
            signer = testSigner
        )

        // Signature is cryptographically authentic, but card does NOT exist in Room
        val scanResult = roomRepository.verifyStudentByQr(validPayloadForUnknownCard)
        assertTrue(
            "Cryptographically authentic but unregistered card must yield StudentNotFound",
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
            "LTC:V2:${testSigner.keyId}:$cardId1:$validFrom:$validUntil:!!!NOT_BASE_64!!!",
            "LTC:V2:${testSigner.keyId}:$cardId1:$validFrom:$validUntil:",
            "LTC:V2:${testSigner.keyId}:$cardId1:$validFrom:$validUntil:AAAA",
            "LTC:V2:${testSigner.keyId}:$cardId1:$validFrom:$validUntil:${"A".repeat(200)}",
            "LTC:V2:${testSigner.keyId}:$cardId1:$validFrom:$validUntil:====="
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
            qrPayload = CardCryptoManager.signCardPayload(cardId2, validFrom, validUntil, testSigner),
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
        val unknownSigned = CardCryptoManager.signCardPayload(unknownCardId, validFrom, validUntil, testSigner)
        val scanResultUnknown = roomRepository.verifyStudentByQr(unknownSigned)
        assertTrue("Unknown card must yield StudentNotFound and not fall back to active student card",
            scanResultUnknown is StudentScanResult.StudentNotFound)
    }

    // =========================================================================
    // 16. An expired card is rejected.
    // =========================================================================
    @Test
    fun test16_ExpiredCardIsRejected() = runBlocking {
        val expiredCardId = CardCryptoManager.generateSecureRandomCardId()
        val expiredPayload = CardCryptoManager.signCardPayload(
            cardId = expiredCardId,
            validFrom = "2020-01-01",
            validUntil = "2020-12-31",
            signer = testSigner
        )

        // Seed in database
        val now = System.currentTimeMillis()
        val card = CardEntity(
            id = "crd-db-expired",
            cardIdentifier = expiredCardId,
            studentId = studentId1,
            studentNumber = studentNum1,
            qrPayload = expiredPayload,
            status = CardStatus.ACTIVE.name,
            issueDate = now,
            activationDate = now,
            deactivationDate = null,
            replacedByCardId = null,
            reason = "Expired card",
            notes = "Test expired",
            updatedAt = now,
            isDeleted = false
        )
        database.cardDao().insertOrUpdateCard(card)

        val scanResult = roomRepository.verifyStudentByQr(expiredPayload)
        assertTrue("Expired card must be rejected as InvalidQr", scanResult is StudentScanResult.InvalidQr)
        val invalid = scanResult as StudentScanResult.InvalidQr
        assertTrue("Error message must mention expiration", invalid.errorReason.contains("expired", ignoreCase = true))
    }

    // =========================================================================
    // 17. A card that is not yet valid is rejected.
    // =========================================================================
    @Test
    fun test17_NotYetValidCardIsRejected() = runBlocking {
        val futureCardId = CardCryptoManager.generateSecureRandomCardId()
        val futurePayload = CardCryptoManager.signCardPayload(
            cardId = futureCardId,
            validFrom = "2099-01-01",
            validUntil = "2099-12-31",
            signer = testSigner
        )

        val now = System.currentTimeMillis()
        val card = CardEntity(
            id = "crd-db-future",
            cardIdentifier = futureCardId,
            studentId = studentId1,
            studentNumber = studentNum1,
            qrPayload = futurePayload,
            status = CardStatus.ACTIVE.name,
            issueDate = now,
            activationDate = now,
            deactivationDate = null,
            replacedByCardId = null,
            reason = "Future card",
            notes = "Test future",
            updatedAt = now,
            isDeleted = false
        )
        database.cardDao().insertOrUpdateCard(card)

        val scanResult = roomRepository.verifyStudentByQr(futurePayload)
        assertTrue("Future card must be rejected as InvalidQr", scanResult is StudentScanResult.InvalidQr)
        val invalid = scanResult as StudentScanResult.InvalidQr
        assertTrue("Error message must indicate card not yet valid", invalid.errorReason.contains("not yet valid", ignoreCase = true))
    }

    // =========================================================================
    // 18. An untrusted issuer key is rejected.
    // =========================================================================
    @Test
    fun test18_UntrustedIssuerKeyIsRejected() = runBlocking {
        // Generate an untrusted / rogue issuer key pair that is NOT in TrustedIssuerRegistry
        val rogueKeyPair = CardCryptoManager.generateKeyPair()
        val rogueSigner = SoftwareCardSigner(rogueKeyPair)

        val rogueCardId = CardCryptoManager.generateSecureRandomCardId()
        val roguePayload = CardCryptoManager.signCardPayload(
            cardId = rogueCardId,
            validFrom = validFrom,
            validUntil = validUntil,
            signer = rogueSigner
        )

        assertFalse(
            "Rogue issuer key must not be trusted",
            TrustedIssuerRegistry.isTrusted(rogueSigner.keyId)
        )

        val scanResult = roomRepository.verifyStudentByQr(roguePayload)
        assertTrue("Card with untrusted issuer key must be rejected as InvalidQr", scanResult is StudentScanResult.InvalidQr)
        val invalid = scanResult as StudentScanResult.InvalidQr
        assertTrue(invalid.errorReason.contains("Untrusted issuer", ignoreCase = true))
    }

    // =========================================================================
    // 19. A revoked issuer key is rejected.
    // =========================================================================
    @Test
    fun test19_RevokedIssuerKeyIsRejected() = runBlocking {
        // Valid card verifies initially
        val initialScan = roomRepository.verifyStudentByQr(validPayload1)
        assertTrue("Card must verify before key revocation", initialScan is StudentScanResult.Success)

        // Revoke the issuer key in TrustedIssuerRegistry
        TrustedIssuerRegistry.revokeIssuer(testSigner.keyId)
        assertFalse("Key must now report as not trusted", TrustedIssuerRegistry.isTrusted(testSigner.keyId))

        // Scanning the same card now fails immediately
        val revokedScan = roomRepository.verifyStudentByQr(validPayload1)
        assertTrue("Card from revoked issuer must be rejected as InvalidQr", revokedScan is StudentScanResult.InvalidQr)
        val invalid = revokedScan as StudentScanResult.InvalidQr
        assertTrue(invalid.errorReason.contains("Untrusted issuer", ignoreCase = true) ||
                   invalid.errorReason.contains("revoked", ignoreCase = true))
    }

    // =========================================================================
    // 20. Key rotation: multiple concurrent issuer keys without invalidating existing cards.
    // =========================================================================
    @Test
    fun test20_KeyRotationSupportsMultipleConcurrentIssuersWithoutInvalidatingExistingCards() = runBlocking {
        // Issuer 1 issued Card 1 (seeded in setup)
        val scan1 = roomRepository.verifyStudentByQr(validPayload1)
        assertTrue("Card 1 must verify with Issuer 1 key", scan1 is StudentScanResult.Success)

        // Introduce Issuer 2 (Key Rotation)
        val issuer2KeyPair = CardCryptoManager.generateKeyPair()
        val issuer2Signer = SoftwareCardSigner(issuer2KeyPair)
        TrustedIssuerRegistry.registerTrustedKey(issuer2Signer.publicKey, "Rotated Issuer Authority #2")

        // Issuer 2 issues Card 2
        val cardId2 = CardCryptoManager.generateSecureRandomCardId()
        val payload2 = CardCryptoManager.signCardPayload(
            cardId = cardId2,
            validFrom = validFrom,
            validUntil = validUntil,
            signer = issuer2Signer
        )

        val now = System.currentTimeMillis()
        val c2 = CardEntity(
            id = "crd-db-rot-2",
            cardIdentifier = cardId2,
            studentId = studentId1,
            studentNumber = studentNum1,
            qrPayload = payload2,
            status = CardStatus.ACTIVE.name,
            issueDate = now,
            activationDate = now,
            deactivationDate = null,
            replacedByCardId = null,
            reason = "Rotated key card",
            notes = "Issuer 2 card",
            updatedAt = now,
            isDeleted = false
        )
        database.cardDao().insertOrUpdateCard(c2)

        // Verify BOTH cards concurrently!
        val scanCard1Again = roomRepository.verifyStudentByQr(validPayload1)
        assertTrue("Card 1 from Issuer 1 must still verify after rotation", scanCard1Again is StudentScanResult.Success)

        val scanCard2 = roomRepository.verifyStudentByQr(payload2)
        assertTrue("Card 2 from Issuer 2 must verify successfully", scanCard2 is StudentScanResult.Success)
    }

    // =========================================================================
    // 21. Gate devices operate without any private keys.
    // =========================================================================
    @Test
    fun test21_GateDeviceHasZeroPrivateKeysAndVerifiesFullyOffline() = runBlocking {
        // Gate terminal hardening: remove all active private signing keys
        CardCryptoManager.clearIssuerPrivateKey()
        assertFalse("Gate terminal must report zero private keys", CardCryptoManager.hasIssuerPrivateKey())

        // Gate terminal attempts to sign a card -> MUST FAIL with IllegalStateException
        try {
            CardCryptoManager.signCardPayload("CRD-NEW-ATTEMPT")
            fail("Gate device must not be able to sign card payloads")
        } catch (e: IllegalStateException) {
            assertTrue("Exception message must indicate Gate / Verifier mode",
                e.message!!.contains("Gate / Verifier mode", ignoreCase = true))
        }

        // Gate terminal can STILL fully verify authentic cards using public keys in registry!
        val scanResult = roomRepository.verifyStudentByQr(validPayload1)
        assertTrue("Gate device without private key must verify valid cards offline", scanResult is StudentScanResult.Success)
    }
}
