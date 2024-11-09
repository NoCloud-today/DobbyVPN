package com.example.ck_client

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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

@Composable
fun CloakOutlineScreen(
    modifier: Modifier = Modifier,
    initialConfig: String = "",
    initialLocalHost: String = "",
    initialLocalPort: String = "",
    initialApiKey: String = "",
    isVpnRunning: Boolean = false,
    doOnConnectionButtonClick: (String, String, String, String, Boolean) -> Unit = { _, _, _, _, _ ->
        Unit
    },
    doOnShowLogs: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val isConnected: MutableState<Boolean> = remember { mutableStateOf(isVpnRunning) }
    var config by remember { mutableStateOf(initialConfig) }
    var localHost by remember { mutableStateOf(initialLocalHost) }
    var localPort by remember { mutableStateOf(initialLocalPort) }
    var apiKey by remember { mutableStateOf(initialApiKey) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Top
    ) {
        TextField(
            value = config,
            onValueChange = { config = it },
            label = { Text("Enter the config") },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = localHost,
            onValueChange = { localHost = it },
            label = { Text("Local host") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = localPort,
            onValueChange = { localPort = it },
            label = { Text("Local port") },
            modifier = Modifier.fillMaxWidth()
        )

        TextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
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
                doOnConnectionButtonClick(
                    apiKey,
                    config,
                    localHost,
                    localPort,
                    isConnected.value
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (isConnected.value) "Disconnect VPN" else "Connect VPN")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = doOnShowLogs,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Show Logs")
        }
    }
}