package com.example.ck_client

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import cloak_outline.Cloak_outline
import com.example.ck_client.ui.theme.CkClientTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CloakOutlineActivity : ComponentActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    private var isConnected by mutableStateOf(false)
    private var connectionJob: Job? = null
    private var counter = 0
    private lateinit var requestVpnPermissionLauncher: ActivityResultLauncher<Intent>
    private var isVpnRunning by mutableStateOf(false)
    private var apiKey by mutableStateOf("")
    private var config by mutableStateOf("")
    private var localHost by mutableStateOf("127.0.0.1")
    private var localPort by mutableStateOf("1984")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("cloak_outline_prefs", Context.MODE_PRIVATE)
        loadSavedData()

        requestVpnPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                startVpnService()
            } else {
                Toast.makeText(this, "VPN Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }

        setContent {
            CkClientTheme {
                CloakOutlineScreen()
            }
        }
    }

    private fun loadSavedData() {
        apiKey = sharedPreferences.getString("apiKey", "") ?: ""
        config = sharedPreferences.getString("config", "") ?: ""
        localHost = sharedPreferences.getString("localHost", "127.0.0.1") ?: "127.0.0.1"
        localPort = sharedPreferences.getString("localPort", "1984") ?: "1984"
        isVpnRunning = sharedPreferences.getBoolean("isVpnRunning", false)
    }

    private fun saveData() {
        with(sharedPreferences.edit()) {
            putString("apiKey", apiKey)
            putString("config", config)
            putString("localHost", localHost)
            putString("localPort", localPort)
            putBoolean("isVpnRunning", isVpnRunning)
            apply()
        }
    }

    @Composable
    fun CloakOutlineScreen(modifier: Modifier = Modifier) {
        val scrollState = rememberScrollState()

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.Top
        ) {
            TextField(
                value = config,
                onValueChange = {
                    config = it
                    saveData()
                },
                label = { Text("Enter the config") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = localHost,
                onValueChange = {
                    localHost = it
                    saveData()
                },
                label = { Text("Local host") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = localPort,
                onValueChange = {
                    localPort = it
                    saveData()
                },
                label = { Text("Local port") },
                modifier = Modifier.fillMaxWidth()
            )

            TextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    saveData()
                },
                label = { Text("Enter the API key") },
                placeholder = { Text("API key") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (!isVpnRunning) {
                        checkVpnPermissionAndStart()
                    } else {
                        stopVpnService()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isVpnRunning) "Disconnect VPN" else "Connect VPN")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val intent = Intent(this@CloakOutlineActivity, LogActivity::class.java)

                        launch(Dispatchers.Main) {
                            startActivity(intent)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Show Logs")
            }
        }
    }

    private fun checkVpnPermissionAndStart() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            requestVpnPermissionLauncher.launch(vpnIntent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        if (apiKey.isNotEmpty()) {

            val vpnServiceIntent = Intent(this, MyVpnService::class.java).apply {
                putExtra("API_KEY", apiKey)
            }
            startService(vpnServiceIntent)
            isVpnRunning = true
            saveData()

            if (counter == 0) {
                connectionJob = lifecycleScope.launch(Dispatchers.IO) {
                    Cloak_outline.startCloakClient(localHost, localPort, config, false)
                }
            }
            counter += 1
        } else {
            Toast.makeText(this, "Enter the API key", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopVpnService() {
        val vpnServiceIntent = Intent(this, MyVpnService::class.java).apply {
            putExtra("API_KEY", "Stop")
        }
        startService(vpnServiceIntent)
        stopService(vpnServiceIntent)
        isVpnRunning = false
        saveData()
    }

    @Preview(showBackground = true)
    @Composable
    fun MainScreenPreview() {
        CkClientTheme {
            CloakOutlineScreen()
        }
    }
}