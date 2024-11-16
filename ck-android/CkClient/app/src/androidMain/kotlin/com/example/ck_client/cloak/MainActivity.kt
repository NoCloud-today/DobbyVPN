package com.example.ck_client.cloak

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.ck_client.ui.theme.CkClientTheme
import cloak_outline.Cloak_outline
import com.dobby.common.showToast
import com.example.ck_client.LogHelper
import com.example.ck_client.VpnControlActivity
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {

    private var isConnected by mutableStateOf(false)
    private var connectionJob: Job? = null
    private var counter = 0

    private lateinit var vpnConfigRepository: CloakVpnConfigRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences("VpnPrefs", MODE_PRIVATE)
        vpnConfigRepository = CloakVpnConfigRepositoryImpl(sharedPreferences)

        val savedConfig = vpnConfigRepository.get()
        println("Config loaded: $savedConfig")

        setContent {
            CkClientTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    content = { innerPadding ->
                        CloakScreen(
                            modifier = Modifier.padding(innerPadding),
                            initialConfig = savedConfig?.config ?: "",
                            initialLocalHost = savedConfig?.localHost ?: "127.0.0.1",
                            initialLocalPort = savedConfig?.localPort ?: "1984",
                            onConnect = ::doOnConnect,
                            onDisconnect = ::doOnDisconnect,
                            onVpnServiceControlClick = ::onVpnServiceControlClick
                        )
                    }
                )
            }
        }
    }

    private fun doOnConnect(
        config: String,
        localHost: String,
        localPort: String
    ) {
        LogHelper.log(this@MainActivity, "Connected")
        if (config.isNotEmpty() && localHost.isNotEmpty() && localPort.isNotEmpty()) {
            if (!isConnected) {
                connectionJob = CoroutineScope(Dispatchers.IO).launch {
                    try {
                        withContext(Dispatchers.Main) {
                            isConnected = true
                        }
                        vpnConfigRepository.save(CloakVpnConfig(config, localHost, localPort))
                        counter += 1
                        if (counter > 1) {
                            Cloak_outline.startAgain()
                        } else {
                            Cloak_outline.startCloakClient(localHost, localPort, config, false)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            showToast("Error of launch: ${e.message}", Toast.LENGTH_LONG)
                        }
                    }
                }
            } else {
                showToast("Already connected", Toast.LENGTH_SHORT)
            }
        } else {
            showToast("All fields need to be full", Toast.LENGTH_SHORT)
        }

    }

    private fun doOnDisconnect() {
        if (isConnected) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Cloak_outline.stopCloak()
                    withContext(Dispatchers.Main) {
                        isConnected = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showToast("Ошибка отключения: ${e.message}", Toast.LENGTH_LONG)
                    }
                }
            }
        }
    }

    private fun onVpnServiceControlClick() {
        VpnControlActivity
            .createIntent(context = this@MainActivity)
            .run(::startActivity)
    }

    @Preview(showBackground = true)
    @Composable
    fun MainScreenPreview() {
        CkClientTheme {
            CloakScreen()
        }
    }
}