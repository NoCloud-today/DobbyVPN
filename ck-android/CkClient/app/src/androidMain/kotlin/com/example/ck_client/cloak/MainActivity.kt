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
import androidx.lifecycle.lifecycleScope
import com.example.ck_client.ui.theme.CkClientTheme
import com.dobby.common.showToast
import com.dobby.util.Logger
import com.example.ck_client.VpnControlActivity
import com.example.ck_client.domain.CloakVpnConnectionInteractor
import com.example.ck_client.domain.ConnectResult
import com.example.ck_client.domain.DisconnectResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var vpnConfigRepository: CloakVpnConfigRepository
    private val connectionInteractor = CloakVpnConnectionInteractor()

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
        Logger.init(this)
        Logger.log("Connected")
        vpnConfigRepository.save(CloakVpnConfig(config, localHost, localPort))
        lifecycleScope.launch {
            val result = connectionInteractor.connect(
                localHost = localHost,
                localPort = localPort,
                config = config
            )
            withContext(Dispatchers.Main) {
                when (result) {
                    ConnectResult.Success -> {
                        showToast("Connected successfully", Toast.LENGTH_SHORT)
                    }

                    ConnectResult.AlreadyConnected -> {
                        showToast("Already connected", Toast.LENGTH_SHORT)
                    }

                    is ConnectResult.Error -> {
                        showToast(
                            "Error of launch: ${result.error.message}",
                            Toast.LENGTH_LONG
                        )
                    }
                    ConnectResult.ValidationError -> {
                        showToast("All fields need to be full", Toast.LENGTH_SHORT)
                    }
                }
            }
        }
    }

    private fun doOnDisconnect() {
        lifecycleScope.launch {
            val result = connectionInteractor.disconnect()
            withContext(Dispatchers.Main) {
                when (result) {
                    DisconnectResult.Success -> {
                        showToast("Disconnected!", Toast.LENGTH_SHORT)
                    }
                    DisconnectResult.AlreadyDisconnected -> {
                        showToast("Already disconnected", Toast.LENGTH_SHORT)
                    }
                    is DisconnectResult.Error -> {
                        showToast("Ошибка отключения: ${result.error.message}", Toast.LENGTH_LONG)
                    }
                }
            }
        }
    }
    // host
    // port 1984

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
