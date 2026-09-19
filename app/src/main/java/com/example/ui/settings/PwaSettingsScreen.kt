package com.example.ui.settings

import android.webkit.WebStorage
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.data.AppDatabase
import com.example.data.PwaEntity
import com.example.logkeeper.LogKeeperCatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dedicated Per-PWA Settings page.
 * Keeps "Clear App Data" separate from the in-game Player view per project rules.
 * Completely uncolored system bars and respectful of edge-to-edge insets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PwaSettingsScreen(
  pwa: PwaEntity,
  onBack: () -> Unit,
  onPwaDeleted: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val db = remember { AppDatabase.getInstance(context) }
  val catcher = remember { LogKeeperCatcher.getInstance(context) }

  var showClearDataDialog by remember { mutableStateOf(false) }
  var showDeleteDialog by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface,
          titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("pwa_settings_back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back"
            )
          }
        },
        title = {
          Text(
            text = "${pwa.title} Settings",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
          )
        }
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Info Card
      Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1E88E5))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Package Information", fontWeight = FontWeight.Bold, fontSize = 16.sp)
          }
          Spacer(modifier = Modifier.height(12.dp))
          InfoRow(label = "Title", value = pwa.title)
          InfoRow(label = "Virtual Host", value = pwa.virtualHost)
          InfoRow(label = "Launch Entry", value = pwa.entryHtmlPath)
          InfoRow(label = "Package ID", value = pwa.id)
        }
      }

      // Storage & Save Data Card
      Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CleaningServices, contentDescription = null, tint = Color(0xFFE65100))
            Spacer(modifier = Modifier.width(8.dp))
            Text("PWA Save Data", fontWeight = FontWeight.Bold, fontSize = 16.sp)
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Clearing app data wipes localStorage, cookies, and IndexedDB save files for this specific PWA only without touching other apps.",
            fontSize = 13.sp,
            color = Color(0xFF64748B)
          )
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedButton(
            onClick = { showClearDataDialog = true },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("clear_pwa_data_button")
          ) {
            Icon(Icons.Default.CleaningServices, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Clear App Data (Reset Saves)")
          }
        }
      }

      // Destructive Actions Card
      Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFC62828))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Uninstall PWA", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFC62828))
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Permanently deletes this PWA, all extracted source files, and database records from this device.",
            fontSize = 13.sp,
            color = Color(0xFF5A1A1A)
          )
          Spacer(modifier = Modifier.height(12.dp))
          Button(
            onClick = { showDeleteDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("delete_pwa_from_settings_button")
          ) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete PWA Completely")
          }
        }
      }
    }

    // Dialog: Clear App Data
    if (showClearDataDialog) {
      AlertDialog(
        onDismissRequest = { showClearDataDialog = false },
        title = { Text("Reset PWA Saves?") },
        text = { Text("This will reset all game progress, offline saves, and settings for '${pwa.title}'.") },
        confirmButton = {
          TextButton(
            onClick = {
              try {
                // Delete WebStorage origin
                val originUrl = "https://${pwa.virtualHost}"
                WebStorage.getInstance().deleteOrigin(originUrl)
                catcher.log("Settings", "Cleared WebStorage for origin: $originUrl")
                Toast.makeText(context, "Saves cleared for ${pwa.title}", Toast.LENGTH_SHORT).show()
              } catch (e: Exception) {
                catcher.log("Settings", "Failed to clear WebStorage: ${e.message}", isError = true)
                Toast.makeText(context, "Clear failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
              }
              showClearDataDialog = false
            }
          ) {
            Text("Clear Data", color = Color(0xFFE65100))
          }
        },
        dismissButton = {
          TextButton(onClick = { showClearDataDialog = false }) {
            Text("Cancel")
          }
        }
      )
    }

    // Dialog: Delete PWA
    if (showDeleteDialog) {
      AlertDialog(
        onDismissRequest = { showDeleteDialog = false },
        title = { Text("Delete '${pwa.title}'?") },
        text = { Text("This action cannot be undone. All files and saves will be deleted immediately.") },
        confirmButton = {
          TextButton(
            onClick = {
              scope.launch {
                val pwaDir = File(context.filesDir, pwa.relativeRootDir).parentFile ?: File(context.filesDir, "pwas/${pwa.id}")
                withContext(Dispatchers.IO) {
                  pwaDir.deleteRecursively()
                }
                WebStorage.getInstance().deleteOrigin("https://${pwa.virtualHost}")
                db.pwaDao().deletePwa(pwa)
                catcher.log("Settings", "Deleted PWA: ${pwa.title}")
                showDeleteDialog = false
                onPwaDeleted()
              }
            }
          ) {
            Text("Delete", color = MaterialTheme.colorScheme.error)
          }
        },
        dismissButton = {
          TextButton(onClick = { showDeleteDialog = false }) {
            Text("Cancel")
          }
        }
      )
    }
  }
}

@Composable
private fun InfoRow(label: String, value: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(text = label, fontSize = 13.sp, color = Color(0xFF64748B))
    Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A))
  }
}
