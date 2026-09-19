package com.example.logkeeper

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Singleton lightweight catcher and logger engine for Log Keeper.
 * Strictly enforces:
 * - Master On/Off switch (persisted in SharedPreferences)
 * - Thread-safe in-memory ring buffer for low overhead
 * - Drop-on-crash uncaught exception handling
 * - 2 MB log file threshold detection & automatic drop to Downloads folder
 * - Zero PII / zero credentials policy
 */
class LogKeeperCatcher private constructor(private val appContext: Context) {

  private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val writeMutex = Mutex()

  private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
  val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

  private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
  val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

  // Signal for UI notifications if manual SAF save is needed
  private val _autoDropNotification = MutableStateFlow<String?>(null)
  val autoDropNotification: StateFlow<String?> = _autoDropNotification.asStateFlow()

  init {
    loadCachedEntries()
  }

  fun setEnabled(enabled: Boolean) {
    _isEnabled.value = enabled
    prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    if (enabled) {
      log("System", "LogKeeper enabled")
    }
  }

  fun log(tag: String, message: String, isError: Boolean = false, throwable: Throwable? = null) {
    if (!_isEnabled.value) return

    val stackTraceString = throwable?.let {
      val sw = StringWriter()
      it.printStackTrace(PrintWriter(sw))
      sw.toString()
    }

    val entry = LogEntry(
      tag = tag,
      message = message,
      isError = isError,
      stackTrace = stackTraceString
    )

    // Update in-memory state
    synchronized(this) {
      val current = _entries.value
      val updated = if (current.size >= MAX_IN_MEMORY_ENTRIES) {
        listOf(entry) + current.take(MAX_IN_MEMORY_ENTRIES - 1)
      } else {
        listOf(entry) + current
      }
      _entries.value = updated
    }

    // Persist asynchronously
    scope.launch {
      persistEntry(entry)
    }
  }

  private suspend fun persistEntry(entry: LogEntry) {
    writeMutex.withLock {
      try {
        val logFile = getActiveLogFile()
        FileWriter(logFile, true).use { writer ->
          writer.append(entry.toExportLine()).append("\n")
        }

        // Check 2MB boundary
        if (logFile.length() >= MAX_FILE_SIZE_BYTES) {
          dropLogFileToDownloads(logFile)
        }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to persist log entry", e)
      }
    }
  }

  /**
   * Drops the log file to the user's standard Downloads folder via MediaStore.
   * On Android 10+ (including Android 15 Go), MediaStore.Downloads requires zero runtime permissions.
   */
  fun dropLogFileToDownloads(sourceFile: File): Uri? {
    try {
      val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
      val fileName = "LogKeeper_$timeStamp.txt"
      val mimeType = "text/plain"

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentValues = ContentValues().apply {
          put(MediaStore.Downloads.DISPLAY_NAME, fileName)
          put(MediaStore.Downloads.MIME_TYPE, mimeType)
          put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = appContext.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
          resolver.openOutputStream(uri)?.use { out ->
            sourceFile.inputStream().use { input ->
              input.copyTo(out)
            }
          }
          // Reset current file
          sourceFile.delete()
          _autoDropNotification.value = "Log reached 2MB and was saved to Downloads: $fileName"
          return uri
        }
      } else {
        // Pre-Android 10 fallback
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir.exists() || downloadsDir.mkdirs()) {
          val destFile = File(downloadsDir, fileName)
          sourceFile.copyTo(destFile, overwrite = true)
          sourceFile.delete()
          _autoDropNotification.value = "Log reached 2MB and was saved to Downloads: $fileName"
          return Uri.fromFile(destFile)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Automated drop to Downloads failed", e)
      _autoDropNotification.value = "Auto-export failed: ${e.localizedMessage}. Manual export required."
    }
    return null
  }

  /**
   * Performs a crash dump synchronously upon fatal uncaught exception.
   */
  fun dropOnCrash(thread: Thread, throwable: Throwable) {
    try {
      val crashEntry = LogEntry(
        tag = "CrashReporter",
        message = "FATAL EXCEPTION on thread [${thread.name}]",
        isError = true,
        stackTrace = throwable.stackTraceToString()
      )

      val crashFile = File(appContext.filesDir, "crash_${System.currentTimeMillis()}.log")
      FileWriter(crashFile, false).use { writer ->
        writer.append("=== PWA LOADER CRASH REPORT ===\n")
        writer.append(crashEntry.toExportLine()).append("\n")
        writer.append("\n--- RECENT LOG ENTRIES ---\n")
        _entries.value.take(100).forEach {
          writer.append(it.toExportLine()).append("\n")
        }
      }

      // Also attempt immediate drop to Downloads
      dropLogFileToDownloads(crashFile)
    } catch (e: Exception) {
      Log.e(TAG, "Failed during dropOnCrash", e)
    }
  }

  fun clearNotification() {
    _autoDropNotification.value = null
  }

  fun clearLogs() {
    _entries.value = emptyList()
    scope.launch {
      writeMutex.withLock {
        try {
          getActiveLogFile().delete()
        } catch (e: Exception) {
          Log.e(TAG, "Failed to clear log file", e)
        }
      }
    }
  }

  fun getAllLogsFormatted(filterMs: Long? = null): String {
    val now = System.currentTimeMillis()
    val list = if (filterMs != null) {
      _entries.value.filter { (now - it.timestampEpochMs) <= filterMs }
    } else {
      _entries.value
    }
    return list.reversed().joinToString("\n") { it.toExportLine() }
  }

  private fun loadCachedEntries() {
    val logFile = getActiveLogFile()
    if (!logFile.exists()) return

    try {
      val lines = logFile.readLines().takeLast(MAX_IN_MEMORY_ENTRIES)
      val loaded = mutableListOf<LogEntry>()
      lines.forEach { line ->
        if (line.isNotBlank()) {
          loaded.add(LogEntry(tag = "Archive", message = line))
        }
      }
      _entries.value = loaded.reversed()
    } catch (e: Exception) {
      Log.e(TAG, "Failed loading cached entries", e)
    }
  }

  private fun getActiveLogFile(): File {
    val logDir = File(appContext.filesDir, "logkeeper")
    if (!logDir.exists()) {
      logDir.mkdirs()
    }
    return File(logDir, "active_log.txt")
  }

  companion object {
    private const val TAG = "LogKeeperCatcher"
    private const val PREFS_NAME = "pwa_loader_logkeeper_prefs"
    private const val KEY_ENABLED = "key_logkeeper_enabled"
    const val MAX_IN_MEMORY_ENTRIES = 500
    const val MAX_FILE_SIZE_BYTES = 2L * 1024L * 1024L // 2 Megabytes

    @Volatile
    private var INSTANCE: LogKeeperCatcher? = null

    fun getInstance(context: Context): LogKeeperCatcher {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: LogKeeperCatcher(context.applicationContext).also { INSTANCE = it }
      }
    }

    /**
     * Initializes LogKeeper on application startup. Installs the crash drop uncaught exception handler.
     */
    fun install(context: Context): LogKeeperCatcher {
      val instance = getInstance(context)
      val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
      Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        instance.dropOnCrash(thread, throwable)
        defaultHandler?.uncaughtException(thread, throwable)
      }
      instance.log("System", "LogKeeper initialized")
      return instance
    }
  }
}
