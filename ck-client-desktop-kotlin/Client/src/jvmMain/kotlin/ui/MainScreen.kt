package ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState

@Composable
fun MainScreen() {
    var showOutlineWindow by remember { mutableStateOf(false) }
    var showCloakOutlineWindow by remember { mutableStateOf(false) }
    var showLogWindow by remember { mutableStateOf(false) }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFCDCDCD)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Combined VPN Client", style = MaterialTheme.typography.h5.copy(color = Color.White))
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { showOutlineWindow = true }) {
                    Text("Open Outline Control")
                }
                Button(onClick = { showCloakOutlineWindow = true }) {
                    Text("Open Combined Control (Cloak + Outline)")
                }
                Button(onClick = { showLogWindow = true }) {
                    Text("Show Logs")
                }
            }
        }
    }

    if (showOutlineWindow) {
        Window(
            onCloseRequest = { showOutlineWindow = false },
            title = "Outline Control",
            state = rememberWindowState(width = 400.dp, height = 600.dp)
        ) {
            OutlineWindow(onClose = { showOutlineWindow = false })
        }
    }

    if (showCloakOutlineWindow) {
        Window(
            onCloseRequest = { showCloakOutlineWindow = false },
            title = "Cloak + Outline Control",
            state = rememberWindowState(width = 400.dp, height = 600.dp)
        ) {
            CloakOutlineWindow(onClose = { showCloakOutlineWindow = false })
        }
    }

    if (showLogWindow) {
        Window(
            onCloseRequest = { showLogWindow = false },
            title = "Logs",
            state = rememberWindowState(width = 600.dp, height = 400.dp)
        ) {
            LogWindow(onClose = { showLogWindow = false })
        }
    }
}