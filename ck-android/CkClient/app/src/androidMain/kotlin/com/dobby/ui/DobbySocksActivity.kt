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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.dobby.common.showToast
import com.dobby.domain.ConnectionStateRepository
import com.dobby.domain.DobbyConfigsRepository
import com.dobby.domain.DobbyConfigsRepositoryImpl
import com.dobby.util.Logger
import com.example.ck_client.LogActivity
import com.example.ck_client.ConnectVpnServiceInteractor
import com.example.ck_client.ui.theme.CkClientTheme
import kotlinx.coroutines.launch

class DobbySocksActivity : ComponentActivity() {

    private lateinit var dobbyConfigsRepository: DobbyConfigsRepository

    private lateinit var requestVpnPermissionLauncher: ActivityResultLauncher<Intent>

    private lateinit var vpnServiceInteractor: ConnectVpnServiceInteractor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Logger.init(this)
        ConnectionStateRepository.init(false)

        dobbyConfigsRepository = DobbyConfigsRepositoryImpl(
            prefs = getSharedPreferences("DobbyPrefs", MODE_PRIVATE)
        )

        vpnServiceInteractor = ConnectVpnServiceInteractor()

        val cloakJson = dobbyConfigsRepository.getCloakConfig()
        val outlineKey = dobbyConfigsRepository.getOutlineKey()

        initVpnPermissionLauncher()

        setContent {
            CkClientTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    content = { innerPadding ->
                        DobbySocksScreen(
                            modifier = Modifier.padding(innerPadding),
                            isConnected = ConnectionStateRepository.observe().collectAsState(false),
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

    private fun handleConnectionButtonClick(
        cloakJson: String?,
        outlineKey: String,
        isCloakEnabled: Boolean
    ) {
        saveData(isCloakEnabled, cloakJson, outlineKey)
        lifecycleScope.launch {
            when (ConnectionStateRepository.get()) {
                true -> stopVpnService()
                false -> checkVpnPermissionAndStart()
            }
        }
    }

    private fun saveData(isCloakEnabled: Boolean, cloakJson: String?, outlineKey: String) {
        dobbyConfigsRepository.setOutlineKey(outlineKey)

        cloakJson?.let(dobbyConfigsRepository::setCloakConfig)
        dobbyConfigsRepository.setIsCloakEnabled(isCloakEnabled)
    }

    private fun checkVpnPermissionAndStart() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            requestVpnPermissionLauncher.launch(vpnIntent)
        } else {
            startVpnService()
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

    private fun startVpnService() {
        dobbyConfigsRepository.setIsOutlineEnabled(true)
        vpnServiceInteractor.start(context = this)
    }

    private fun stopVpnService() {
        dobbyConfigsRepository.setIsOutlineEnabled(false)
        vpnServiceInteractor.stop(context = this)
    }
}
