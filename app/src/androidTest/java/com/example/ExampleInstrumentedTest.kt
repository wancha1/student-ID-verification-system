package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Device-level smoke checks for the installed LTC application configuration. */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
  @Test
  fun applicationIdAndFirebaseConfigurationAreCorrect() {
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)

    // FirebaseInitProvider must initialize the default app from packaged resources.
    val firebaseApp = FirebaseApp.getInstance()
    assertNotNull(firebaseApp.options)
    assertEquals("gen-lang-client-0207053136", firebaseApp.options.projectId)
    assertEquals(
      "1:863997008927:android:87c2ce91560390f176892a",
      firebaseApp.options.applicationId
    )
  }
}
