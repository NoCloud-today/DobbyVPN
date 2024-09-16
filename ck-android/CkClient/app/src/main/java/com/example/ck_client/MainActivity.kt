package com.example.ck_client

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ck_client.ui.theme.CkClientTheme
import cloak_outline.*
import kotlinx.coroutines.*
import org.json.JSONObject

data class VpnConfig(
    val config: String,
    val localHost: String,
    val localPort: String
)

class MainActivity : ComponentActivity() {

    private var isConnected by mutableStateOf(false)
    private var connectionJob: Job? = null
    private var counter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val savedConfig = loadConfigFromSharedPreferences()
        println("Config loaded: $savedConfig")

        setContent {
            CkClientTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    content = { innerPadding ->
                        MainScreen(
                            modifier = Modifier.padding(innerPadding),
                            initialConfig = savedConfig?.config ?: "",
                            initialLocalHost = savedConfig?.localHost ?: "127.0.0.1",
                            initialLocalPort = savedConfig?.localPort ?: "1984"
                        )
                    }
                )
            }
        }
    }

    private fun saveConfig(vpnConfig: VpnConfig) {
        val sharedPref = getSharedPreferences("VpnPrefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            val json = JSONObject().apply {
                put("config", vpnConfig.config)
                put("localHost", vpnConfig.localHost)
                put("localPort", vpnConfig.localPort)
            }.toString()
            putString("vpnConfig", json)
            apply()
        }
    }

    private fun loadConfigFromSharedPreferences(): VpnConfig? {
        val sharedPref = getSharedPreferences("VpnPrefs", MODE_PRIVATE)
        val json = sharedPref.getString("vpnConfig", null)
        return json?.let {
            val jsonObject = JSONObject(it)
            VpnConfig(
                config = jsonObject.optString("config", ""),
                localHost = jsonObject.optString("localHost", "127.0.0.1"),
                localPort = jsonObject.optString("localPort", "1984")
            )
        }
    }

    @Composable
    fun MainScreen(
        modifier: Modifier = Modifier,
        initialConfig: String = "",
        initialLocalHost: String = "127.0.0.1",
        initialLocalPort: String = "1984"
    ) {
        var config by remember { mutableStateOf(initialConfig) }
        var localHost by remember { mutableStateOf(initialLocalHost) }
        var localPort by remember { mutableStateOf(initialLocalPort) }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            TextField(
                value = config,
                onValueChange = { config = it },
                label = { Text("Enter the config") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = localHost,
                onValueChange = { localHost = it },
                label = { Text("Lokal host") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = localPort,
                onValueChange = { localPort = it },
                label = { Text("Lokal port") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (config.isNotEmpty() && localHost.isNotEmpty() && localPort.isNotEmpty()) {
                        if (!isConnected) {
                            connectionJob = CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    withContext(Dispatchers.Main) {
                                        isConnected = true
                                    }
                                    saveConfig(VpnConfig(config, localHost, localPort))
                                    counter += 1
                                    if (counter > 1) {
                                        Cloak_outline.startAgain()
                                    } else {
                                        Cloak_outline.startCloakClient(localHost, localPort, config, false)
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@MainActivity, "Error of launch: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        } else {
                            Toast.makeText(this@MainActivity, "Already connected", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "All fields need to be full", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Connect")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (isConnected) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                Cloak_outline.stopCloak()
                                withContext(Dispatchers.Main) {
                                    isConnected = false
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, "Ошибка отключения: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Отключиться")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val intent = Intent(this@MainActivity, VpnControlActivity::class.java)
                    startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("VPN Service Control")
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun MainScreenPreview() {
        CkClientTheme {
            MainScreen()
        }
    }
}