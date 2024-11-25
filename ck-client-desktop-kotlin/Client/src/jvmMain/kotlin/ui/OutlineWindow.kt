package ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import interop.OutlineLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import utils.ConfigUtils

@Composable
fun OutlineWindow(onClose: () -> Unit) {
    var key by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("Not connected") }
    val coroutineScope = rememberCoroutineScope()

    // загрузка сохраненного ключа
    LaunchedEffect(Unit) {
        val savedConfig = ConfigUtils.loadConfig()
        key = savedConfig.key
    }

    Surface(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        color = Color(0xFFCDCDCD)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Outline Control", style = MaterialTheme.typography.h6)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Shadowsocks Key:")
            TextField(value = key, onValueChange = { key = it })
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Button(onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            OutlineLib.startOutline(key)
                            statusMessage = "Connected"
                            // Сохранение ключа
                            val configData = ConfigUtils.loadConfig().copy(key = key)
                            ConfigUtils.saveConfig(configData)
                        } catch (e: Exception) {
                            statusMessage = "Failed to connect: ${e.message}"
                        }
                    }
                }) {
                    Text("Connect")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            OutlineLib.stopOutline()
                            statusMessage = "Disconnected"
                        } catch (e: Exception) {
                            statusMessage = "Failed to disconnect: ${e.message}"
                        }
                    }
                }) {
                    Text("Disconnect")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Status: $statusMessage")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onClose) {
                Text("Close")
            }
        }
    }
}