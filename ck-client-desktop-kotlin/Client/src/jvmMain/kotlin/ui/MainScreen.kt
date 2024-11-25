package ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog

//0xFFCDCDCD


@Composable
fun MainScreen() {
    var showOutlineWindow by remember { mutableStateOf(false) }
    var showCloakOutlineWindow by remember { mutableStateOf(false) }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFCDCDCD) // Серо-фиолетовый фон
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Левая часть: Основное содержимое
                Column(
                    modifier = Modifier.weight(1f).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Combined VPN Client",
                        style = MaterialTheme.typography.h5.copy(color = Color.White)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showOutlineWindow = true }) {
                        Text("Open Outline Control")
                    }
                    Button(onClick = { showCloakOutlineWindow = true }) {
                        Text("Open Combined Control (Cloak + Outline)")
                    }
                    // Можно добавить дополнительный контент здесь
                }

                // Правая часть: Окно логов
                Surface(
                    modifier = Modifier.width(400.dp).fillMaxHeight(),
                    color = Color(0xFF7C7C7C)
                ) {
                    LogWindow()
                }
            }
        }
    }

    if (showOutlineWindow) {
        Dialog(onCloseRequest = { showOutlineWindow = false }) {
            OutlineWindow(onClose = { showOutlineWindow = false })
        }
    }

    if (showCloakOutlineWindow) {
        Dialog(onCloseRequest = { showCloakOutlineWindow = false }) {
            CloakOutlineWindow(onClose = { showCloakOutlineWindow = false })
        }
    }
}