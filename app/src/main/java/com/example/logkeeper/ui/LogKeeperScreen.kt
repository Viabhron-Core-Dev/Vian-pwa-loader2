package com.example.logkeeper.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.logkeeper.LogEntry
import com.example.logkeeper.LogKeeperCatcher
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogTimeFilter(val label: String, val durationMs: Long?) {
  SIX_HOURS("6h", 6 * 60 * 60 * 1000L),
  TWELVE_HOURS("12h", 12 * 60 * 60 * 1000L),
  TWENTY_FOUR_HOURS("24h", 24 * 60 * 60 * 1000L),
  ALL("All", null)
}

/**
 * Log Keeper UI matching the user's reference screenshot:
 * - Clean top app bar with Back arrow, "Log Keeper" title, Master switch, Copy button, Download button
 * - Time filter tab bar (6h, 12h, 24h, All)
 * - Soft rounded cards displaying Timestamp, Component/Tag, and Message
 * - Fully respects system bars without artificial background coloring
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogKeeperScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  catcher: LogKeeperCatcher = LogKeeperCatcher.getInstance(LocalContext.current)
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  val isEnabled by catcher.isEnabled.collectAsState()
  val allEntries by catcher.entries.collectAsState()
  val autoDropNotice by catcher.autoDropNotification.collectAsState()

  var selectedFilterIndex by remember { mutableIntStateOf(3) } // Default to "All"
  val activeFilter = LogTimeFilter.entries[selectedFilterIndex]

  // Filter entries based on active tab
  val now = System.currentTimeMillis()
  val filteredEntries = remember(allEntries, activeFilter) {
    val duration = activeFilter.durationMs
    if (duration != null) {
      allEntries.filter { (now - it.timestampEpochMs) <= duration }
    } else {
      allEntries
    }
  }

  // Activity launcher for manual SAF download if needed
  val exportFileLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("text/plain")
  ) { uri: Uri? ->
    if (uri != null) {
      scope.launch {
        try {
          context.contentResolver.openOutputStream(uri)?.use { output ->
            val content = catcher.getAllLogsFormatted(activeFilter.durationMs)
            output.write(content.toByteArray())
          }
          Toast.makeText(context, "Logs successfully exported!", Toast.LENGTH_SHORT).show()
          catcher.log("System", "Logs exported via SAF")
        } catch (e: Exception) {
          Toast.makeText(context, "Failed to export logs: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  LaunchedEffect(autoDropNotice) {
    autoDropNotice?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      catcher.clearNotification()
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface,
          titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("log_keeper_back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back"
            )
          }
        },
        title = {
          Text(
            text = "Log Keeper",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
          )
        },
        actions = {
          // Master On/Off Switch
          Switch(
            checked = isEnabled,
            onCheckedChange = { catcher.setEnabled(it) },
            modifier = Modifier.testTag("log_keeper_master_switch"),
            colors = SwitchDefaults.colors(
              checkedThumbColor = Color.White,
              checkedTrackColor = Color(0xFF1E88E5)
            )
          )

          Spacer(modifier = Modifier.width(4.dp))

          // Copy button
          IconButton(
            onClick = {
              val formattedLogs = catcher.getAllLogsFormatted(activeFilter.durationMs)
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              val clip = ClipData.newPlainText("LogKeeper_Logs", formattedLogs)
              clipboard.setPrimaryClip(clip)
              Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
              catcher.log("System", "Logs copied to clipboard")
            },
            modifier = Modifier.testTag("log_keeper_copy_button")
          ) {
            Icon(
              imageVector = Icons.Default.ContentCopy,
              contentDescription = "Copy logs"
            )
          }

          // Download / Export button
          IconButton(
            onClick = {
              val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
              exportFileLauncher.launch("LogKeeper_$timeStamp.txt")
            },
            modifier = Modifier.testTag("log_keeper_export_button")
          ) {
            Icon(
              imageVector = Icons.Default.FileDownload,
              contentDescription = "Download logs"
            )
          }
        }
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      // Time Filter Tabs: 6h, 12h, 24h, All
      TabRow(
        selectedTabIndex = selectedFilterIndex,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        indicator = { tabPositions ->
          TabRowDefaults.SecondaryIndicator(
            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedFilterIndex]),
            height = 3.dp,
            color = Color(0xFF1E88E5)
          )
        },
        divider = {}
      ) {
        LogTimeFilter.entries.forEachIndexed { index, filter ->
          val isSelected = selectedFilterIndex == index
          Tab(
            selected = isSelected,
            onClick = { selectedFilterIndex = index },
            text = {
              Text(
                text = filter.label,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 16.sp,
                color = if (isSelected) Color(0xFF1E88E5) else MaterialTheme.colorScheme.onSurfaceVariant
              )
            },
            modifier = Modifier.testTag("log_filter_${filter.label.lowercase()}")
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Logs List
      if (filteredEntries.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = if (isEnabled) "No log entries found for this time window." else "LogKeeper is turned OFF.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(filteredEntries, key = { it.id }) { entry ->
            LogEntryCard(entry = entry)
          }
          item {
            Spacer(modifier = Modifier.height(16.dp))
          }
        }
      }
    }
  }
}

@Composable
fun LogEntryCard(entry: LogEntry, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp)),
    colors = CardDefaults.cardColors(
      containerColor = if (entry.isError) Color(0xFFFFEBEE) else Color(0xFFEEF3F8)
    ),
    shape = RoundedCornerShape(8.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = entry.formattedTime,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1E293B)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = entry.tag,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (entry.isError) Color(0xFFC62828) else Color(0xFF0F172A)
          )
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = entry.message,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFF1E293B)
      )

      if (entry.stackTrace != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = entry.stackTrace,
          fontSize = 11.sp,
          fontWeight = FontWeight.Normal,
          color = Color(0xFFB71C1C),
          lineHeight = 14.sp
        )
      }
    }
  }
}
