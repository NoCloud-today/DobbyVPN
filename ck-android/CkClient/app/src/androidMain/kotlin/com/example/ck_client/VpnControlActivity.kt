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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dobby.common.showToast
import com.example.ck_client.MyVpnService.Companion.VPN_KEY_EXTRA
import com.example.ck_client.ui.theme.CkClientTheme
import org.json.JSONObject

class VpnControlActivity : ComponentActivity() {

    companion object {

        fun createIntent(context: Context): Intent {
            return Intent(context, VpnControlActivity::class.java)
        }
    }

    private lateinit var requestVpnPermissionLauncher: ActivityResultLauncher<Intent>
    private var isVpnRunning by mutableStateOf(false)
    private var apiKey by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        apiKey = loadApiKey() ?: ""

        requestVpnPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                startVpnService()
            } else {
                showToast("VPN Permission Denied", Toast.LENGTH_SHORT)
            }
        }

        setContent {
            CkClientTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    content = { innerPadding ->
                        VpnControlScreen(modifier = Modifier.padding(innerPadding))
                    }
                )
            }
        }
    }

    @Composable
    fun VpnControlScreen(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {

            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    saveApiKey(apiKey)
                },
                label = { Text("Enter API key") },
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
                    CloakOutlineActivity.createIntent(context = this@VpnControlActivity)
                        .run(::startActivity)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cloak-Outline Client")
            }
        }
    }

    private fun saveApiKey(apiKey: String) {
        val sharedPref = getSharedPreferences("VpnControlPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            val json = JSONObject().apply {
                put("apiKey", apiKey)
            }.toString()
            putString("vpnApiKey", json)
            apply()
        }
    }

    private fun loadApiKey(): String? {
        val sharedPref = getSharedPreferences("VpnControlPrefs", Context.MODE_PRIVATE)
        val json = sharedPref.getString("vpnApiKey", null)
        return json?.let {
            val jsonObject = JSONObject(it)
            jsonObject.optString("apiKey", "")
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
            MyVpnService.createIntent(context = this)
                .apply { putExtra(VPN_KEY_EXTRA, apiKey) }
                .let(::startService)
            isVpnRunning = true
        } else {
            showToast("Enter API key", Toast.LENGTH_SHORT)
        }
    }

    private fun stopVpnService() {
        val vpnServiceIntent = MyVpnService.createIntent(context = this).apply {
            putExtra(VPN_KEY_EXTRA, "Stop")
        }
        startService(vpnServiceIntent)
        stopService(vpnServiceIntent)
        isVpnRunning = false
    }

    @Preview(showBackground = true)
    @Composable
    fun VpnControlScreenPreview() {
        CkClientTheme {
            VpnControlScreen()
        }
    }
}