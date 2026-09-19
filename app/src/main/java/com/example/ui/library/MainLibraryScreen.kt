package com.example.ui.library

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDatabase
import com.example.data.PwaEntity
import com.example.importer.ImportResult
import com.example.importer.PwaZipImporter
import com.example.logkeeper.LogKeeperCatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLibraryScreen(
  onOpenPwa: (PwaEntity) -> Unit,
  onOpenPwaSettings: (PwaEntity) -> Unit,
  onOpenLogKeeper: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val db = remember { AppDatabase.getInstance(context) }
  val catcher = remember { LogKeeperCatcher.getInstance(context) }
  val importer = remember { PwaZipImporter(context) }

  val pwas by db.pwaDao().getAllPwas().collectAsState(initial = emptyList())

  var isImporting by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var isSearchActive by remember { mutableStateOf("") }
  var showSearchField by remember { mutableStateOf(false) }
  var pwaToDelete by remember { mutableStateOf<PwaEntity?>(null) }

  val safPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      scope.launch {
        isImporting = true
        catcher.log("Library", "Importing PWA from SAF picker")
        when (val result = importer.importFromUri(uri)) {
          is ImportResult.Success -> {
            db.pwaDao().insertPwa(result.pwa)
            catcher.log("Library", "Saved ${result.pwa.title} to local database")
            snackbarHostState.showSnackbar("Installed: ${result.pwa.title}")
          }
          is ImportResult.Error -> {
            snackbarHostState.showSnackbar("Import Error: ${result.message}")
          }
        }
        isImporting = false
      }
    }
  }

  val filteredPwas = remember(pwas, searchQuery) {
    if (searchQuery.isBlank()) pwas
    else pwas.filter { it.title.contains(searchQuery, ignoreCase = true) }
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
        title = {
          if (showSearchField) {
            OutlinedTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              placeholder = { Text("Search installed PWAs...") },
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("library_search_input")
            )
          } else {
            Text(
              text = "vian pwa loader",
              fontWeight = FontWeight.Bold,
              fontSize = 20.sp
            )
          }
        },
        actions = {
          IconButton(
            onClick = {
              showSearchField = !showSearchField
              if (!showSearchField) searchQuery = ""
            },
            modifier = Modifier.testTag("toggle_search_button")
          ) {
            Icon(
              imageVector = Icons.Default.Search,
              contentDescription = "Search PWAs"
            )
          }

          IconButton(
            onClick = onOpenLogKeeper,
            modifier = Modifier.testTag("open_log_keeper_button")
          ) {
            Icon(
              imageVector = Icons.Default.BugReport,
              contentDescription = "Open Log Keeper"
            )
          }
        }
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          safPickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed"))
        },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("import_pwa_fab")
      ) {
        Icon(Icons.Default.Add, contentDescription = "Import PWA ZIP")
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      if (filteredPwas.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = Icons.Default.Gamepad,
              contentDescription = null,
              modifier = Modifier.size(72.dp),
              tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
              text = if (searchQuery.isBlank()) "No PWAs Installed" else "No matching PWAs",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = if (searchQuery.isBlank()) "Tap the + button to import a PWA dist ZIP archive from your device." else "Try a different search term.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(filteredPwas, key = { it.id }) { pwa ->
            PwaCardItem(
              pwa = pwa,
              onPlay = { onOpenPwa(pwa) },
              onSettings = { onOpenPwaSettings(pwa) },
              onDelete = { pwaToDelete = pwa }
            )
          }
        }
      }

      // Loading indicator during import
      if (isImporting) {
        Surface(
          color = Color.Black.copy(alpha = 0.5f),
          modifier = Modifier.fillMaxSize()
        ) {
          Box(contentAlignment = Alignment.Center) {
            Card(
              shape = RoundedCornerShape(12.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              modifier = Modifier.padding(24.dp)
            ) {
              Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                  text = "Extracting & validating PWA package...",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Medium
                )
              }
            }
          }
        }
      }
    }

    // Delete Confirmation Dialog
    pwaToDelete?.let { targetPwa ->
      AlertDialog(
        onDismissRequest = { pwaToDelete = null },
        title = { Text("Delete PWA?") },
        text = { Text("Are you sure you want to delete '${targetPwa.title}'? All saved data, files, and settings will be permanently removed.") },
        confirmButton = {
          TextButton(
            onClick = {
              scope.launch {
                val pwaDir = File(context.filesDir, targetPwa.relativeRootDir).parentFile ?: File(context.filesDir, "pwas/${targetPwa.id}")
                withContext(Dispatchers.IO) {
                  pwaDir.deleteRecursively()
                }
                db.pwaDao().deletePwa(targetPwa)
                catcher.log("Library", "Deleted PWA: ${targetPwa.title}")
                snackbarHostState.showSnackbar("Deleted ${targetPwa.title}")
                pwaToDelete = null
              }
            },
            modifier = Modifier.testTag("confirm_delete_pwa_button")
          ) {
            Text("Delete", color = MaterialTheme.colorScheme.error)
          }
        },
        dismissButton = {
          TextButton(onClick = { pwaToDelete = null }) {
            Text("Cancel")
          }
        }
      )
    }
  }
}

@Composable
fun PwaCardItem(
  pwa: PwaEntity,
  onPlay: () -> Unit,
  onSettings: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier
) {
  var menuExpanded by remember { mutableStateOf(false) }

  Card(
    shape = RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
    modifier = modifier
      .fillMaxWidth()
      .clickable { onPlay() }
      .testTag("pwa_card_${pwa.id}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Icon thumbnail or fallback game icon
      Box(
        modifier = Modifier
          .size(52.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(Color(0xFFE2E8F0)),
        contentAlignment = Alignment.Center
      ) {
        val bitmap = remember(pwa.iconPath) {
          pwa.iconPath?.let { path ->
            val file = File(path)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
          }
        }

        if (bitmap != null) {
          Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = pwa.title,
            modifier = Modifier.fillMaxSize()
          )
        } else {
          Icon(
            imageVector = Icons.Default.Gamepad,
            contentDescription = null,
            tint = Color(0xFF475569),
            modifier = Modifier.size(32.dp)
          )
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      // Title and size details
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = pwa.title,
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp,
          color = Color(0xFF0F172A),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          val sizeFormatted = remember(pwa.sizeBytes) { formatBytes(pwa.sizeBytes) }
          val dateFormatted = remember(pwa.createdAt) {
            SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(pwa.createdAt))
          }
          Text(
            text = "$sizeFormatted • $dateFormatted",
            fontSize = 12.sp,
            color = Color(0xFF64748B)
          )
        }
      }

      // Quick play button
      IconButton(
        onClick = onPlay,
        modifier = Modifier.testTag("play_pwa_${pwa.id}")
      ) {
        Icon(
          imageVector = Icons.Default.PlayArrow,
          contentDescription = "Play PWA",
          tint = Color(0xFF1E88E5),
          modifier = Modifier.size(28.dp)
        )
      }

      // 3-dot options menu
      Box {
        IconButton(
          onClick = { menuExpanded = true },
          modifier = Modifier.testTag("pwa_menu_${pwa.id}")
        ) {
          Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "Options",
            tint = Color(0xFF64748B)
          )
        }

        DropdownMenu(
          expanded = menuExpanded,
          onDismissRequest = { menuExpanded = false }
        ) {
          DropdownMenuItem(
            text = { Text("Settings") },
            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
            onClick = {
              menuExpanded = false
              onSettings()
            }
          )
          DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            onClick = {
              menuExpanded = false
              onDelete()
            }
          )
        }
      }
    }
  }
}

private fun formatBytes(bytes: Long): String {
  if (bytes <= 0) return "0 B"
  val kb = bytes / 1024.0
  val mb = kb / 1024.0
  return if (mb >= 1.0) {
    String.format(Locale.US, "%.1f MB", mb)
  } else {
    String.format(Locale.US, "%.0f KB", kb)
  }
}
