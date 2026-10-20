package com.example.base

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
abstract class FirestoreEmulatorTestBase {

  protected lateinit var firestore: FirebaseFirestore
  protected lateinit var auth: FirebaseAuth
  protected lateinit var databaseId: String
  private lateinit var testFirebaseApp: FirebaseApp

  @Before
  open fun setUpFirebase() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val resId = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
    databaseId = if (resId != 0) context.getString(resId) else "demo-database"

    // Give every test its own FirebaseApp. Reusing a previously initialized app meant
    // the emulator endpoints were configured only for the first test, causing later
    // tests to contact the real Firebase endpoints and fail before exercising rules.
    val appName = "ltc-test-${java.util.UUID.randomUUID()}"
    try {
      testFirebaseApp = FirebaseApp.initializeApp(
      context,
      FirebaseOptions.Builder()
        .setApplicationId("1:1234567890:android:abcdef0123456789")
        .setProjectId(PROJECT_ID)
        .setApiKey("fake-api-key-for-emulator")
        .build(),
      appName
    ) ?: throw IllegalStateException("Unable to initialize isolated Firebase test app.")

    firestore = FirebaseFirestore.getInstance(testFirebaseApp, databaseId)
    // Configure settings before connecting the client to the emulator; Firestore
    // locks its settings once the client starts.
    firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
      .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
      .build()
    firestore.useEmulator(EMULATOR_HOST, FIRESTORE_PORT)

      auth = FirebaseAuth.getInstance(testFirebaseApp)
      auth.useEmulator(EMULATOR_HOST, AUTH_PORT)
    } catch (error: Throwable) {
      System.err.println("Firebase emulator test setup failed: ${error.stackTraceToString()}")
      throw error
    }
  }

  @After
  open fun tearDownFirebase() {
    try {
      if (::auth.isInitialized) auth.signOut()
    } finally {
      // Do not delete the FirebaseApp in Robolectric tests. Firebase Auth keeps
      // process-level client state; deleting an app leaves cached Auth instances
      // pointing at a deleted FirebaseApp, breaking emulator setup in later tests.
      // Each test uses a unique app name, and the JVM exits after the suite.
    }
  }

  /**
   * Provisions staff records through the Firestore Emulator's owner bypass.
   * This helper is test-only; production clients must never self-provision staff roles.
   */
  private fun markPrivacyMigrationCompleteForTest() {
    val url = URL("http://127.0.0.1:8085/v1/projects/demo-no-project/databases/$databaseId/documents:commit")
    val payload = """{"writes":[{"update":{"name":"projects/demo-no-project/databases/$databaseId/documents/schools/ltc_school_01","fields":{"studentPrivacyMigrationComplete":{"booleanValue":true}}}}]}"""
    val connection = (url.openConnection() as HttpURLConnection).apply {
      requestMethod = "POST"
      setRequestProperty("Authorization", "Bearer owner")
      setRequestProperty("Content-Type", "application/json")
      doOutput = true
    }
    try {
      connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
      val code = connection.responseCode
      if (code !in 200..299) {
        val detail = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "no response body"
        throw IllegalStateException("Test school metadata provisioning failed (HTTP $code): $detail")
      }
    } finally {
      connection.disconnect()
    }
  }

  protected fun provisionStaffForTest(uid: String, role: String, name: String) {
    markPrivacyMigrationCompleteForTest()
    val safeRole = role.replace("\\", "\\\\").replace("\"", "\\\"")
    val safeName = name.replace("\\", "\\\\").replace("\"", "\\\"")
    val safeUid = uid.replace("\\", "\\\\").replace("\"", "\\\"")
    val payload = """{"fields":{"role":{"stringValue":"$safeRole"},"name":{"stringValue":"$safeName"},"userId":{"stringValue":"$safeUid"},"active":{"booleanValue":true}}}"""
    // Create the document through the collection endpoint. HttpURLConnection on
    // the JVM does not support PATCH as a request method, so use POST + documentId.
    val url = URL("http://127.0.0.1:8085/v1/projects/demo-no-project/databases/$databaseId/documents/schools/ltc_school_01/staff?documentId=$uid")
    val connection = (url.openConnection() as HttpURLConnection).apply {
      requestMethod = "POST"
      setRequestProperty("Authorization", "Bearer owner")
      setRequestProperty("Content-Type", "application/json")
      doOutput = true
    }
    try {
      connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
      val code = connection.responseCode
      if (code !in 200..299) {
        val detail = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "no response body"
        throw IllegalStateException("Test staff provisioning failed (HTTP $code): $detail")
      }
    } finally {
      connection.disconnect()
    }
  }

  protected suspend fun signInTestUser(email: String): String = withContext(Dispatchers.IO) {
    withTimeout(AUTH_TIMEOUT_MS) {
      // Each test has an isolated FirebaseApp, so create its test account directly.
      // Auth emulator error types for a missing user vary; sign-in-first is brittle.
      val result = auth.createUserWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
      checkNotNull(result.user?.uid) { "User creation failed for test account $email" }
    }
  }

  private companion object {
    const val EMULATOR_HOST = "127.0.0.1"
    const val FIRESTORE_PORT = 8085
    const val AUTH_PORT = 9099
    const val PROJECT_ID = "demo-no-project"
    const val DEFAULT_PASSWORD = "password123"
    const val AUTH_TIMEOUT_MS = 5000L
  }
}
