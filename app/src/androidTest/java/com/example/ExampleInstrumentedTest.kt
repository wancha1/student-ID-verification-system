package com.example

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Device-level smoke checks for the installed LTC application configuration. */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
  @Test
  fun applicationIdIsCorrect() {
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)

  }

  @Test
  fun mainActivityLaunchesWithoutCrashing() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        assertNotNull(activity.window)
      }
    }
  }
}
