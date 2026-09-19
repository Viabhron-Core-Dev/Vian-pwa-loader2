package com.example.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.example.data.PwaEntity
import com.example.logkeeper.LogKeeperCatcher
import java.io.File

/**
 * On-demand PWA Player screen.
 * Strictly respects:
 * - Uncolored system status bar and bottom navigation bar
 * - Topbar with PWA name and 3-dots menu with Exit (and Reload/Settings)
 * - NO Clear Data button in 3-dots menu (relegated exclusively to PwaSettingsScreen)
 * - Leaves controls completely to the PWA (no virtual buttons)
 * - Immediate, aggressive WebView destruction on exit for Android Go low-RAM optimization
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PwaPlayerScreen(
  pwa: PwaEntity,
  onExit: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val catcher = remember { LogKeeperCatcher.getInstance(context) }
  var menuExpanded by remember { mutableStateOf(false) }
  var webViewRef by remember { mutableStateOf<WebView?>(null) }

  BackHandler {
    if (webViewRef?.canGoBack() == true) {
      webViewRef?.goBack()
    } else {
      catcher.log("Player", "Exited player via back button: ${pwa.title}")
      onExit()
    }
  }

  // Ensure teardown on dispose
  DisposableEffect(pwa.id) {
    catcher.log("Player", "Loaded player for '${pwa.title}' with virtualHost: ${pwa.virtualHost}")
    onDispose {
      webViewRef?.let { wv ->
        catcher.log("Player", "Destroying WebView instance for '${pwa.title}'")
        try {
          wv.stopLoading()
          wv.loadUrl("about:blank")
          wv.clearHistory()
          wv.pauseTimers()
          (wv.parent as? ViewGroup)?.removeView(wv)
          wv.destroy()
        } catch (e: Exception) {
          catcher.log("Player", "Error cleaning WebView: ${e.message}", isError = true)
        }
      }
      webViewRef = null
    }
  }

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
            onClick = onExit,
            modifier = Modifier.testTag("player_exit_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Exit"
            )
          }
        },
        title = {
          Text(
            text = pwa.title,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
          )
        },
        actions = {
          Box {
            IconButton(
              onClick = { menuExpanded = true },
              modifier = Modifier.testTag("player_3dots_menu")
            ) {
              Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Menu"
              )
            }

            DropdownMenu(
              expanded = menuExpanded,
              onDismissRequest = { menuExpanded = false }
            ) {
              DropdownMenuItem(
                text = { Text("Reload") },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                onClick = {
                  menuExpanded = false
                  webViewRef?.reload()
                  catcher.log("Player", "Reloaded ${pwa.title}")
                }
              )
              DropdownMenuItem(
                text = { Text("App Settings") },
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                onClick = {
                  menuExpanded = false
                  onOpenSettings()
                }
              )
              DropdownMenuItem(
                text = { Text("Exit Game") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                onClick = {
                  menuExpanded = false
                  onExit()
                }
              )
            }
          }
        }
      )
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(Color.Black)
    ) {
      AndroidView(
        factory = { ctx ->
          createConfiguredWebView(ctx, pwa, catcher).also { wv ->
            webViewRef = wv
          }
        },
        modifier = Modifier.fillMaxSize()
      )
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
  context: Context,
  pwa: PwaEntity,
  catcher: LogKeeperCatcher
): WebView {
  return WebView(context).apply {
    layoutParams = ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )

    settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      databaseEnabled = true
      allowFileAccess = false
      allowContentAccess = false
      mediaPlaybackRequiresUserGesture = false
      useWideViewPort = true
      loadWithOverviewMode = true
      cacheMode = WebSettings.LOAD_DEFAULT
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        safeBrowsingEnabled = true
      }
    }

    // Set up WebViewAssetLoader mapping to the extracted local PWA directory
    val pwaDir = File(context.filesDir, pwa.relativeRootDir)
    val assetLoader = WebViewAssetLoader.Builder()
      .setDomain(pwa.virtualHost)
      .addPathHandler("/", WebViewAssetLoader.InternalStoragePathHandler(context, pwaDir))
      .build()

    webViewClient = object : WebViewClient() {
      override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
      ): WebResourceResponse? {
        val uri = request?.url ?: return null
        return assetLoader.shouldInterceptRequest(uri)
      }

      override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        catcher.log("Player", "Page loaded: $url")
      }
    }

    val startUrl = "https://${pwa.virtualHost}/${pwa.entryHtmlPath}"
    catcher.log("Player", "Starting URL: $startUrl")
    loadUrl(startUrl)
  }
}
