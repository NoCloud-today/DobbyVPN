package ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import interop.CloakLib
import interop.OutlineLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import models.ConfigData
import utils.ConfigUtils

@Composable
fun CloakOutlineWindow(onClose: () -> Unit) {
    var localHost by remember { mutableStateOf("127.0.0.1") }
    var localPort by remember { mutableStateOf("1984") }
    var configText by remember { mutableStateOf("") }
    var udpEnabled by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("Not connected") }
    var counter by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    // Загрузка сохраненной конфигурации
    LaunchedEffect(Unit) {
        val savedConfig = ConfigUtils.loadConfig()
        localHost = savedConfig.localHost
        localPort = savedConfig.localPort
        configText = savedConfig.configText
        udpEnabled = savedConfig.udp
        key = savedConfig.key
    }

    Surface(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        color = Color(0xFFCDCDCD)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Combined Control (Cloak + Outline)", style = MaterialTheme.typography.h6)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Local Host:")
            TextField(value = localHost, onValueChange = { localHost = it })
            Text("Local Port:")
            TextField(value = localPort, onValueChange = { localPort = it })
            Text("Config:")
            TextField(
                value = configText,
                onValueChange = { configText = it },
                modifier = Modifier.height(150.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = udpEnabled, onCheckedChange = { udpEnabled = it })
                Text("UDP Mode")
            }
            Text("Shadowsocks Key:")
            TextField(value = key, onValueChange = { key = it })
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Button(onClick = {
                    // Сохранение конфигурации
                    val configData = ConfigData(
                        localHost = localHost,
                        localPort = localPort,
                        configText = configText,
                        udp = udpEnabled,
                        key = key
                    )
                    ConfigUtils.saveConfig(configData)

                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            OutlineLib.startOutline(key)
                            if (counter == 0) {
                                CloakLib.startCloakClient(localHost, localPort, configText, udpEnabled)
                            }
                            counter += 1
                            statusMessage = "Connected"
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
                            CloakLib.stopCloakClient()
                            OutlineLib.stopOutline()
                            statusMessage = "Disconnected"
                            counter = 0
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