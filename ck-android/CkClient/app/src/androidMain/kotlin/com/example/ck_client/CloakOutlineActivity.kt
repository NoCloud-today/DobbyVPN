package com.example.ck_client

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.dobby.common.showToast
import com.example.ck_client.domain.CloakOutlineConfigRepository
import com.example.ck_client.domain.CloakOutlineVpnConfig
import com.example.ck_client.domain.CloakVpnConnectionInteractor
import com.example.ck_client.ui.theme.CkClientTheme
import kotlinx.coroutines.launch

class CloakOutlineActivity : ComponentActivity() {

    companion object {

        fun createIntent(context: Context): Intent {
            return Intent(context, CloakOutlineActivity::class.java)
        }
    }

    private var isConnected by mutableStateOf(false)
    private lateinit var requestVpnPermissionLauncher: ActivityResultLauncher<Intent>
    private val connectionInteractor = CloakVpnConnectionInteractor()
    private var isVpnRunning by mutableStateOf(false)
    private var apiKey by mutableStateOf("")
    private var config by mutableStateOf("")
    private var localHost by mutableStateOf("127.0.0.1")
    private var localPort by mutableStateOf("1984")

    private lateinit var configRepository: CloakOutlineConfigRepository
    private val vpnServiceInteractor = MyVpnServiceInteractor()

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
            StartActivityForResult()
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
        CloakOutlineVpnConfig(
            apiKey = apiKey,
            config = config,
            localHost = localHost,
            localPort = localPort,
            isVpnRunning = isVpnRunning
        ).let(configRepository::save)
    }

    private fun doOnConnectionClick(
        apiKey: String,
        config: String,
        localHost: String,
        localPort: String,
        isConnected: Boolean
    ) {
        saveData(apiKey, config, localHost, localPort, isConnected)
        if (isVpnRunning) {
            stopVpnService()
        } else {
            checkVpnPermissionAndStart()
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
            vpnServiceInteractor.start(context = this, apiKey)
            isVpnRunning = true
            configRepository.updateVpnRunning(newValue = true)
            lifecycleScope.launch {
                connectionInteractor.connect(
                    localHost = localHost,
                    localPort = localPort,
                    config = config
                ) // TODO handle result
            }
        } else {
            showToast("Enter the API key", Toast.LENGTH_SHORT)
        }
    }

    private fun stopVpnService() {
        vpnServiceInteractor.stop(context = this)
        isVpnRunning = false
        configRepository.updateVpnRunning(newValue = false)
    }

    @Preview(showBackground = true)
    @Composable
    fun MainScreenPreview() {
        CkClientTheme {
            CloakOutlineScreen()
        }
    }
}