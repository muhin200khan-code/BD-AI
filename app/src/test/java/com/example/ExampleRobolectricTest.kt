package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("BD AI", appName)
  }

  @Test
  fun `system instruction includes creator identity and multilingual directive`() {
    val repo = com.example.data.repository.BdAiGenerativeRepository()
    val prompt = repo.buildSystemInstruction(
      profile = com.example.data.model.UserProfile(),
      memories = emptyList()
    )
    org.junit.Assert.assertTrue(prompt.contains("Muhin Hasan Khan"))
    org.junit.Assert.assertTrue(prompt.contains("মুহিন হাসান খান"))
  }
}
