package com.dobby.ui

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.dobby.common.showToast
import com.dobby.domain.CloakConfigRepository
import com.dobby.util.Logger
import com.dobby.domain.OutlineKeyRepository
import com.example.ck_client.LogActivity
import com.example.ck_client.MyVpnServiceInteractor
import com.example.ck_client.domain.CloakVpnConnectionInteractor
import com.example.ck_client.domain.ConnectResult
import com.example.ck_client.domain.ConnectResult.AlreadyConnected
import com.example.ck_client.domain.DisconnectResult
import com.example.ck_client.ui.theme.CkClientTheme
import kotlinx.coroutines.launch

class DobbySocksActivity : ComponentActivity() {

    private lateinit var outlineKeyRepository: OutlineKeyRepository
    private lateinit var cloakConfigRepository: CloakConfigRepository
    private lateinit var requestVpnPermissionLauncher: ActivityResultLauncher<Intent>

    private val vpnServiceInteractor = MyVpnServiceInteractor()
    private val cloakConnectionInteractor by lazy {
        CloakVpnConnectionInteractor()
    }

    private var cloakJson: String? = null
    private var apiKey: String = ""

    private var isConnected by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Logger.init(this)

        val sharedPreferences = getSharedPreferences("DobbyPrefs", MODE_PRIVATE)
        outlineKeyRepository = OutlineKeyRepository(sharedPreferences)
        cloakConfigRepository = CloakConfigRepository(sharedPreferences)

        val outlineKey = outlineKeyRepository.get()
        val cloakJson = cloakConfigRepository.get()

        initVpnPermissionLauncher()

        setContent {
            CkClientTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    content = { innerPadding ->
                        DobbySocksScreen(
                            modifier = Modifier.padding(innerPadding),
                            isConnected = isConnected,
                            initialConfig = cloakJson,
                            initialKey = outlineKey,
                            onConnectionButtonClick = ::handleConnectionButtonClick,
                            onShowLogsClick = {
                                LogActivity
                                    .createIntent(context = this)
                                    .let(::startActivity)
                            }
                        )
                    }
                )
            }
        }
    }

    private fun handleConnectionButtonClick(cloakJson: String?, outlineKey: String) {
        saveData(cloakJson, outlineKey)
        if (isConnected) {
            stopVpnService()
        } else {
            checkVpnPermissionAndStart(cloakJson, outlineKey)
        }
    }

    private fun saveData(cloakJson: String?, outlineKey: String) {
        this.apiKey = outlineKey
        this.cloakJson = cloakJson
        outlineKeyRepository.save(apiKey)
        cloakJson?.let(cloakConfigRepository::save)
    }

    private fun checkVpnPermissionAndStart(cloakJson: String?, apiKey: String) {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            requestVpnPermissionLauncher.launch(vpnIntent)
        } else {
            startVpnService(cloakJson, apiKey)
        }
    }

    private fun initVpnPermissionLauncher() {
        requestVpnPermissionLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                startVpnService(cloakJson, apiKey)
            } else {
                showToast("VPN Permission Denied", Toast.LENGTH_SHORT)
            }
        }
    }

    private fun startVpnService(cloakJson: String?, apiKey: String) {
        if (apiKey.isNotEmpty()) {
            vpnServiceInteractor.start(context = this, apiKey)
            isConnected = true
        } else {
            showToast("Enter the API key")
        }
        if (cloakJson.isNullOrBlank().not()) {
            lifecycleScope.launch {
                cloakConnectionInteractor.connect(config = cloakJson ?: "").let { result ->
                    when (result) {
                        AlreadyConnected -> showToast("Already Connected!")
                        is ConnectResult.Error -> showToast("Connection Error!")
                        ConnectResult.Success -> showToast("Connected successfully!")
                        ConnectResult.ValidationError -> showToast("Validation error!")
                    }
                }
            }
        }
    }

    private fun stopVpnService() {
        if (apiKey.isNotEmpty()) {
            vpnServiceInteractor.stop(context = this)
            isConnected = false
        }
        lifecycleScope.launch {
            cloakConnectionInteractor.disconnect().let { result ->
                when (result) {
                    DisconnectResult.Success -> Unit // do nothing
                    DisconnectResult.AlreadyDisconnected -> Unit // do nothing
                    is DisconnectResult.Error -> showToast("Disconnection Error !)")
                }
            }
        }
    }
}
