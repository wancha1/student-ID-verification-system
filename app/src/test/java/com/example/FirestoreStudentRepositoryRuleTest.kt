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
  fun saveUserProfile_authenticatedUser_success() = runBlocking {
    val uid = signInTestUser("staff@oakridge.ac.ug")
    val repo = FirestoreStudentRepository(firestore, auth)

    val res = repo.saveUserProfile(displayName = "Staff Officer", email = "staff@oakridge.ac.ug", role = "GATE_OFFICER")
    assertTrue(res.isSuccess)

    val doc = firestore.collection("users").document(uid).get().await()
    assertTrue(doc.exists())
    assertEquals(uid, doc.getString("userId"))
    assertEquals("GATE_OFFICER", doc.getString("role"))
  }

  @Test
  fun addStudentAndObserve_authenticatedOwner_success() = runBlocking {
    val uid = signInTestUser("alice@oakridge.ac.ug")
    val repo = FirestoreStudentRepository(firestore, auth)

    val studentId = UUID.randomUUID().toString()
    val student = FirestoreStudent(
      id = studentId,
      userId = uid,
      studentNumber = "LTC-2026-0001",
      firstName = "Michael",
      lastName = "Kasule",
      gradeClass = "Senior 4-A",
      isDayScholar = true,
      feesStatus = "CLEARED",
      outstandingAmount = 0.0
    )

    val addRes = repo.addStudent(student)
    assertTrue(addRes.isSuccess)

    val observed = withTimeout(5000L) {
      repo.observeStudents().first { list -> list.any { it.studentNumber == "LTC-2026-0001" } }
    }
    assertNotNull(observed)
    assertTrue(observed.any { it.firstName == "Michael" })
  }

  @Test
  fun recordScanLog_appendOnly_success() = runBlocking {
    val uid = signInTestUser("guard@oakridge.ac.ug")
    val repo = FirestoreStudentRepository(firestore, auth)

    val logId = UUID.randomUUID().toString()
    val log = FirestoreScanLog(
      id = logId,
      userId = uid,
      studentNumber = "LTC-2026-0001",
      studentName = "Michael Kasule",
      decision = "APPROVED",
      gatePoint = "Main Gate"
    )

    val logRes = repo.recordScanLog(log)
    assertTrue(logRes.isSuccess)

    val observed = withTimeout(5000L) {
      repo.observeScanLogs().first { list -> list.any { it.id == logId } }
    }
    assertTrue(observed.any { it.studentNumber == "LTC-2026-0001" })
  }

  @Test
  fun crossUserAccess_bobCannotReadAliceStudents() = runBlocking {
    val aliceUid = signInTestUser("alice_owner@oakridge.ac.ug")
    val aliceRepo = FirestoreStudentRepository(firestore, auth)

    val studentId = UUID.randomUUID().toString()
    val student = FirestoreStudent(
      id = studentId,
      userId = aliceUid,
      studentNumber = "LTC-2026-0099",
      firstName = "Secret",
      lastName = "Student",
      gradeClass = "Senior 1",
      isDayScholar = true,
      feesStatus = "CLEARED",
      outstandingAmount = 0.0
    )
    val addRes = aliceRepo.addStudent(student)
    assertTrue(addRes.isSuccess)

    // Bob signs in and attempts direct access to Alice's student document
    signInTestUser("bob_attacker@oakridge.ac.ug")
    var failedWithPermissionDenied = false
    try {
      firestore.collection("users").document(aliceUid).collection("students").document(studentId).get().await()
    } catch (e: Exception) {
      val cause = e.cause as? FirebaseFirestoreException ?: e as? FirebaseFirestoreException
      if (cause?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED || e.message?.contains("PERMISSION_DENIED") == true) {
        failedWithPermissionDenied = true
      }
    }
    assertTrue("Cross-user access must be rejected with PERMISSION_DENIED", failedWithPermissionDenied)
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
