package com.example.logkeeper

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Immutable log record captured by LogKeeper.
 * Strictly holds non-PII, technical diagnostic event details.
 */
data class LogEntry(
  val id: Long = System.nanoTime(),
  val timestampEpochMs: Long = System.currentTimeMillis(),
  val tag: String,
  val message: String,
  val isError: Boolean = false,
  val stackTrace: String? = null
) {
  val formattedTime: String
    get() {
      val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
      return sdf.format(Date(timestampEpochMs))
    }

  fun toExportLine(): String {
    val prefix = if (isError) "[ERROR]" else "[INFO]"
    val base = "$formattedTime $prefix [$tag] $message"
    return if (stackTrace != null) "$base\n$stackTrace" else base
  }
}
