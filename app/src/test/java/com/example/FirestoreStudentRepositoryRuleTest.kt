package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.FirestoreStudentRepository
import com.example.data.model.FirestoreScanLog
import com.example.data.model.FirestoreStudent
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Regression tests for the retired /users/{uid} Firestore schema.
 * Production data is school-scoped under /schools/{schoolId}; authenticated users
 * must not be able to keep using the old per-user path.
 */
class FirestoreStudentRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun saveUserProfile_legacySchemaRejectedForAuthenticatedUser() = runBlocking {
    signInTestUser("staff_" + UUID.randomUUID().toString().take(8) + "@ltc.ac.ug")
    val repo = FirestoreStudentRepository(firestore, auth)
    val result = repo.saveUserProfile(
      displayName = "Staff Officer",
      email = "staff@ltc.ac.ug",
      role = "GATE_OFFICER"
    )
    assertTrue("Legacy user-profile writes must be denied", result.isFailure)
  }

  @Test
  fun addStudent_legacySchemaRejectedForAuthenticatedUser() = runBlocking {
    val uid = signInTestUser("alice_" + UUID.randomUUID().toString().take(8) + "@ltc.ac.ug")
    val repo = FirestoreStudentRepository(firestore, auth)
    val student = FirestoreStudent(
      id = UUID.randomUUID().toString(),
      userId = uid,
      studentNumber = "LTC-2026-0001",
      firstName = "Michael",
      lastName = "Kasule",
      gradeClass = "Senior 4-A",
      isDayScholar = true,
      feesStatus = "CLEARED",
      outstandingAmount = 0.0
    )
    assertTrue("Legacy student writes must be denied", repo.addStudent(student).isFailure)
  }

  @Test
  fun recordScanLog_legacySchemaRejectedForAuthenticatedUser() = runBlocking {
    val uid = signInTestUser("guard_" + UUID.randomUUID().toString().take(8) + "@ltc.ac.ug")
    val repo = FirestoreStudentRepository(firestore, auth)
    val log = FirestoreScanLog(
      id = UUID.randomUUID().toString(),
      userId = uid,
      studentNumber = "LTC-2026-0001",
      studentName = "Michael Kasule",
      decision = "APPROVED",
      gatePoint = "Main Gate"
    )
    assertTrue("Legacy scan-log writes must be denied", repo.recordScanLog(log).isFailure)
  }

  @Test
  fun legacyStudentPathsAreDeniedAcrossUsers() = runBlocking {
    val aliceUid = signInTestUser("alice_owner_" + UUID.randomUUID().toString().take(8) + "@ltc.ac.ug")
    val repo = FirestoreStudentRepository(firestore, auth)
    val student = FirestoreStudent(
      id = UUID.randomUUID().toString(),
      userId = aliceUid,
      studentNumber = "LTC-2026-0099",
      firstName = "Secret",
      lastName = "Student",
      gradeClass = "Senior 1",
      isDayScholar = true,
      feesStatus = "CLEARED",
      outstandingAmount = 0.0
    )
    assertTrue(repo.addStudent(student).isFailure)

    signInTestUser("bob_attacker_" + UUID.randomUUID().toString().take(8) + "@ltc.ac.ug")
    var denied = false
    try {
      firestore.collection("users").document(aliceUid)
        .collection("students").document(student.id).get().await()
    } catch (error: Exception) {
      val cause = error.cause as? FirebaseFirestoreException ?: error as? FirebaseFirestoreException
      denied = cause?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
        error.message?.contains("PERMISSION_DENIED") == true
    }
    assertTrue("Legacy student paths must be denied to every client", denied)
  }

  @Test
  fun unauthenticatedUser_accessIsRejected() = runBlocking {
    auth.signOut()
    var denied = false
    try {
      firestore.collection("users").document("someone").collection("students").get().await()
    } catch (error: Exception) {
      val cause = error.cause as? FirebaseFirestoreException ?: error as? FirebaseFirestoreException
      denied = cause?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
        error.message?.contains("PERMISSION_DENIED") == true
    }
    assertTrue("Unauthenticated legacy access must be rejected", denied)
  }
}
