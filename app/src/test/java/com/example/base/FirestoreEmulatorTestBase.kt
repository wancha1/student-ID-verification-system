package com.example.base

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
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
    testFirebaseApp = FirebaseApp.initializeApp(
      context,
      FirebaseOptions.Builder()
        .setApplicationId("com.aistudio.studentaccess.xpkz")
        .setProjectId(PROJECT_ID)
        .setApiKey("fake-api-key-for-emulator")
        .build(),
      appName
    ) ?: throw IllegalStateException("Unable to initialize isolated Firebase test app.")

    firestore = FirebaseFirestore.getInstance(testFirebaseApp, databaseId)
    firestore.useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
    firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
      .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
      .build()

    auth = FirebaseAuth.getInstance(testFirebaseApp)
    auth.useEmulator(EMULATOR_HOST, AUTH_PORT)
  }

  @After
  open fun tearDownFirebase() {
    try {
      if (::auth.isInitialized) auth.signOut()
    } finally {
      if (::testFirebaseApp.isInitialized) testFirebaseApp.delete()
    }
  }

  /**
   * Provisions staff records through the Firestore Emulator's owner bypass.
   * This helper is test-only; production clients must never self-provision staff roles.
   */
  protected fun provisionStaffForTest(uid: String, role: String, name: String) {
    val safeRole = role.replace("\\", "\\\\").replace("\"", "\\\"")
    val safeName = name.replace("\\", "\\\\").replace("\"", "\\\"")
    val safeUid = uid.replace("\\", "\\\\").replace("\"", "\\\"")
    val payload = """{"fields":{"role":{"stringValue":"$safeRole"},"name":{"stringValue":"$safeName"},"userId":{"stringValue":"$safeUid"}}}"""
    val url = URL("http://127.0.0.1:8085/v1/projects/demo-no-project/databases/$databaseId/documents/schools/ltc_school_01/staff/$uid")
    val connection = (url.openConnection() as HttpURLConnection).apply {
      requestMethod = "PATCH"
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
      val result = try {
        auth.signInWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
      } catch (unused: FirebaseAuthInvalidUserException) {
        auth.createUserWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
      }
      checkNotNull(result.user?.uid) { "User auth failed" }
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
