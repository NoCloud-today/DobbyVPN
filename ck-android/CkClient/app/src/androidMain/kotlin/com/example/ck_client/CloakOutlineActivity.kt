package com.example.ck_client

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import cloak_outline.Cloak_outline
import com.dobby.common.showToast
import com.example.ck_client.domain.CloakOutlineConfigRepository
import com.example.ck_client.domain.CloakOutlineVpnConfig
import com.example.ck_client.ui.theme.CkClientTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CloakOutlineActivity : ComponentActivity() {

    companion object {

        fun createIntent(context: Context): Intent {
            return Intent(context, CloakOutlineActivity::class.java)
        }
    }

    private var isConnected by mutableStateOf(false)
    private var connectionJob: Job? = null
    private var counter = 0
    private lateinit var requestVpnPermissionLauncher: ActivityResultLauncher<Intent>
    private var isVpnRunning by mutableStateOf(false)
    private var apiKey by mutableStateOf("")
    private var config by mutableStateOf("")
    private var localHost by mutableStateOf("127.0.0.1")
    private var localPort by mutableStateOf("1984")

    private lateinit var configRepository: CloakOutlineConfigRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configRepository = CloakOutlineConfigRepository(
            prefs = getSharedPreferences("cloak_outline_prefs", Context.MODE_PRIVATE)
        )
        loadSavedData()
        initVpnPermissionLauncher()

        setContent {
            CkClientTheme {
                CloakOutlineScreen(
                    initialConfig = config,
                    initialLocalHost = localHost,
                    initialLocalPort = localPort,
                    initialApiKey = apiKey,
                    isVpnRunning = isVpnRunning,
                    doOnConnectionButtonClick = ::doOnConnectionClick,
                    doOnShowLogs = {
                        LogActivity.createIntent(context = this).run(::startActivity)
                    }
                )
            }
        }
    }

    private fun initVpnPermissionLauncher() {
        requestVpnPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                startVpnService()
            } else {
                showToast("VPN Permission Denied", Toast.LENGTH_SHORT)
            }
        }
    }

    private fun loadSavedData() {
        val vpnConfig: CloakOutlineVpnConfig = configRepository.get()
        apiKey = vpnConfig.apiKey
        config = vpnConfig.config
        localHost = vpnConfig.localHost
        localPort = vpnConfig.localPort
        isVpnRunning = vpnConfig.isVpnRunning
    }

    private fun saveData(
        apiKey: String,
        config: String,
        localHost: String,
        localPort: String,
        isConnected: Boolean
    ) {
        this.apiKey = apiKey
        this.config = config
        this.localHost = localHost
        this.localPort = localPort
        this.isConnected = isConnected
        saveData()
    }

    private fun saveData() {
        val vpnConfig = CloakOutlineVpnConfig(
            apiKey = apiKey,
            config = config,
            localHost = localHost,
            localPort = localPort,
            isVpnRunning = isVpnRunning
        )
        configRepository.save(vpnConfig)
    }

    private fun doOnConnectionClick(
        apiKey: String,
        config: String,
        localHost: String,
        localPort: String,
        isConnected: Boolean
    ) {
        saveData(apiKey, config, localHost, localPort, isConnected)
        if (!isVpnRunning) {
            checkVpnPermissionAndStart()
        } else {
            stopVpnService()
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
            showToast("Enter the API key", Toast.LENGTH_SHORT)
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