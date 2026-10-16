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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
abstract class FirestoreEmulatorTestBase {

  protected lateinit var firestore: FirebaseFirestore
  protected lateinit var auth: FirebaseAuth
  protected lateinit var databaseId: String

  @Before
  open fun setUpFirebase() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val resId = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
    databaseId = if (resId != 0) context.getString(resId) else "demo-database"

    // Use a dedicated Firebase app so a google-services.json app initialized by
    // Robolectric cannot silently send emulator tests to a real Firebase project.
    val app = FirebaseApp.getApps(context).firstOrNull { it.name == TEST_APP_NAME }
      ?: FirebaseApp.initializeApp(
        context,
        FirebaseOptions.Builder()
          .setApplicationId("com.aistudio.studentaccess.xpkz")
          .setProjectId(PROJECT_ID)
          .setApiKey("fake-api-key-for-emulator")
          .build(),
        TEST_APP_NAME
      )

    synchronized(FirebaseEmulatorTestBase::class.java) {
      if (!emulatorConfigured) {
        val emulatorFirestore = FirebaseFirestore.getInstance(app, databaseId)
        emulatorFirestore.useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
        emulatorFirestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
          .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
          .build()

        FirebaseAuth.getInstance(app).useEmulator(EMULATOR_HOST, AUTH_PORT)
        emulatorConfigured = true
      }
    }

    firestore = FirebaseFirestore.getInstance(app, databaseId)
    auth = FirebaseAuth.getInstance(app)
  }

  @After
  open fun tearDownFirebase() {
    auth.signOut()
  }

  /**
   * Provisions staff records through the Firestore Emulator's owner bypass.
   * This helper is test-only; production clients must never self-provision staff roles.
   */
  protected fun provisionStaffForTest(uid: String, role: String, name: String) {
    val safeRole = role.replace("\\", "\\\\").replace("\"", "\\\"")
    val safeName = name.replace("\\", "\\\\").replace("\"", "\\\"")
    val safeUid = uid.replace("\\", "\\\\").replace("\"", "\\\"")
    val payload = """{"fields":{"role":{"stringValue":"$safeRole"},"name":{"stringValue":"$safeName"},"userId":{"stringValue":"$safeUid"},"active":{"booleanValue":true}}}"""
    val url = URL("http://127.0.0.1:8085/v1/projects/demo-no-project/databases/$databaseId/documents/schools/ltc_school_01/staff/$uid")
    val request = Request.Builder()
      .url(url)
      .header("Authorization", "Bearer owner")
      .patch(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
      .build()

    OkHttpClient().newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        val detail = response.body?.string() ?: "no response body"
        throw IllegalStateException("Test staff provisioning failed (HTTP ${response.code}): $detail")
      }
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
    const val TEST_APP_NAME = "ltc_emulator_test"
    const val DEFAULT_PASSWORD = "password123"
    const val AUTH_TIMEOUT_MS = 5000L
    @Volatile var emulatorConfigured = false
  }
}
