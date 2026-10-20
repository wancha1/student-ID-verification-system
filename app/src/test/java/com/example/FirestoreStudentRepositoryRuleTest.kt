package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.FirestoreStudentRepository
import com.example.data.model.FirestoreCard
import com.example.data.model.FirestoreScanLog
import com.example.data.model.FirestoreStudent
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FirestoreStudentRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun legacyUserProfileCreation_isDenied() = runBlocking {
    signInTestUser("staff_" + UUID.randomUUID().toString().take(8) + "@ltc.ac.ug")
    val repo = FirestoreStudentRepository(firestore, auth)
    val result = repo.saveUserProfile(displayName = "Staff Officer", email = "staff@ltc.ac.ug", role = "GATE_OFFICER")
    assertTrue("Legacy self-managed /users profiles must remain disabled", result.isFailure)
  }
  @Test
  fun legacyStudentCreation_isDenied() = runBlocking {
    val uid = signInTestUser("alice_" + UUID.randomUUID().toString().take(8) + "@ltc.ac.ug")
    val repo = FirestoreStudentRepository(firestore, auth)
    val student = FirestoreStudent(
      id = UUID.randomUUID().toString(), userId = uid, studentNumber = "LTC-2026-0001",
      firstName = "Michael", lastName = "Kasule", gradeClass = "Senior 4-A",
      isDayScholar = true, feesStatus = "CLEARED", outstandingAmount = 0.0
    )
    val result = repo.addStudent(student)
    assertTrue("Legacy /users student collection must remain disabled", result.isFailure)
  }
  @Test
  fun legacyScanLogCreation_isDenied() = runBlocking {
    val uid = signInTestUser("guard_" + UUID.randomUUID().toString().take(8) + "@ltc.ac.ug")
    val repo = FirestoreStudentRepository(firestore, auth)
    val log = FirestoreScanLog(
      id = UUID.randomUUID().toString(), userId = uid, studentNumber = "LTC-2026-0001",
      studentName = "Michael Kasule", decision = "APPROVED", gatePoint = "Main Gate"
    )
    val result = repo.recordScanLog(log)
    assertTrue("Legacy /users scan logs must remain disabled", result.isFailure)
  }
  @Test
  fun crossUserAccess_bobCannotReadAliceStudents() = runBlocking {
    val aliceUid = signInTestUser("alice_owner_" + UUID.randomUUID().toString().take(8) + "@ltc.ac.ug")
    val aliceRepo = FirestoreStudentRepository(firestore, auth)
    val student = FirestoreStudent(
      id = UUID.randomUUID().toString(), userId = aliceUid, studentNumber = "LTC-2026-0099",
      firstName = "Secret", lastName = "Student", gradeClass = "Senior 1",
      isDayScholar = true, feesStatus = "CLEARED", outstandingAmount = 0.0
    )
    assertTrue("Legacy student creation must be denied", aliceRepo.addStudent(student).isFailure)
    signInTestUser("bob_attacker_" + UUID.randomUUID().toString().take(8) + "@ltc.ac.ug")
    var denied = false
    try {
      firestore.collection("users").document(aliceUid).collection("students").document(student.id).get().await()
    } catch (error: Exception) {
      val cause = error.cause as? FirebaseFirestoreException ?: error as? FirebaseFirestoreException
      denied = cause?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
        error.message?.contains("PERMISSION_DENIED") == true
    }
    assertTrue("Cross-user legacy access must be denied", denied)
  }
  @Test
  fun unauthenticatedUser_accessIsRejected() = runBlocking {
    auth.signOut()
    var failedUnauth = false
    try {
      firestore.collection("users").document("someone").collection("students").get().await()
    } catch (e: Exception) {
      val cause = e.cause as? FirebaseFirestoreException ?: e as? FirebaseFirestoreException
      if (cause?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED || e.message?.contains("PERMISSION_DENIED") == true) {
        failedUnauth = true
      }
    }
    assertTrue("Unauthenticated access must be rejected with PERMISSION_DENIED", failedUnauth)
  }
}
