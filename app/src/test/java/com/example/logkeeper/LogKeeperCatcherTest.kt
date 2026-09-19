package com.example.logkeeper

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LogKeeperCatcherTest {

  @Test
  fun testLogEntryFormattedTimeAndLine() {
    val entry = LogEntry(
      timestampEpochMs = 1700000000000L,
      tag = "TestComponent",
      message = "Test message",
      isError = false
    )
    assertNotNull(entry.formattedTime)
    assertTrue(entry.toExportLine().contains("[INFO]"))
    assertTrue(entry.toExportLine().contains("[TestComponent]"))
    assertTrue(entry.toExportLine().contains("Test message"))
  }

  @Test
  fun testCatcherMasterSwitchAndLogging() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val catcher = LogKeeperCatcher.getInstance(context)

    catcher.setEnabled(true)
    assertTrue(catcher.isEnabled.value)

    catcher.log("UnitTester", "Testing event 1")
    val entries = catcher.entries.value
    assertTrue(entries.isNotEmpty())
    assertEquals("UnitTester", entries.first().tag)
    assertEquals("Testing event 1", entries.first().message)

    // Verify disabling stops interception
    catcher.setEnabled(false)
    assertFalse(catcher.isEnabled.value)
    val sizeBefore = catcher.entries.value.size
    catcher.log("IgnoredTester", "Should not be logged")
    assertEquals(sizeBefore, catcher.entries.value.size)

    // Re-enable
    catcher.setEnabled(true)
  }
}
