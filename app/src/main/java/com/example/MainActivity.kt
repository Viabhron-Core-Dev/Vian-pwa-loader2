package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.data.PwaEntity
import com.example.logkeeper.LogKeeperCatcher
import com.example.logkeeper.ui.LogKeeperScreen
import com.example.ui.library.MainLibraryScreen
import com.example.ui.player.PwaPlayerScreen
import com.example.ui.settings.PwaSettingsScreen
import com.example.ui.theme.PwaLoaderTheme

sealed class Screen {
  data object Library : Screen()
  data object LogKeeper : Screen()
  data class Player(val pwa: PwaEntity) : Screen()
  data class Settings(val pwa: PwaEntity) : Screen()
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val catcher = LogKeeperCatcher.getInstance(this)
    catcher.log("Navigation", "MainActivity launched")

    setContent {
      PwaLoaderTheme {
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Library) }

        when (val screen = currentScreen) {
          is Screen.Library -> {
            MainLibraryScreen(
              onOpenPwa = { selectedPwa ->
                catcher.log("Navigation", "Launched PWA Player: ${selectedPwa.title}")
                currentScreen = Screen.Player(selectedPwa)
              },
              onOpenPwaSettings = { targetPwa ->
                catcher.log("Navigation", "Opened PWA Settings: ${targetPwa.title}")
                currentScreen = Screen.Settings(targetPwa)
              },
              onOpenLogKeeper = {
                catcher.log("Navigation", "Navigated to: Log Keeper")
                currentScreen = Screen.LogKeeper
              }
            )
          }

          is Screen.Player -> {
            PwaPlayerScreen(
              pwa = screen.pwa,
              onExit = {
                catcher.log("Navigation", "Exited player to Library")
                currentScreen = Screen.Library
              },
              onOpenSettings = {
                currentScreen = Screen.Settings(screen.pwa)
              }
            )
          }

          is Screen.Settings -> {
            BackHandler {
              currentScreen = Screen.Library
            }
            PwaSettingsScreen(
              pwa = screen.pwa,
              onBack = { currentScreen = Screen.Library },
              onPwaDeleted = { currentScreen = Screen.Library }
            )
          }

          is Screen.LogKeeper -> {
            BackHandler {
              catcher.log("Navigation", "Navigated back from Log Keeper")
              currentScreen = Screen.Library
            }
            LogKeeperScreen(
              onBack = {
                catcher.log("Navigation", "Navigated back from Log Keeper")
                currentScreen = Screen.Library
              }
            )
          }
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
  PwaLoaderTheme {
    MainLibraryScreen(
      onOpenPwa = {},
      onOpenPwaSettings = {},
      onOpenLogKeeper = {}
    )
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  androidx.compose.material3.Text(text = "Hello $name!", modifier = modifier)
}
