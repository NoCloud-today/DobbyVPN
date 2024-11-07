package com.example.ck_client.com.example.ck_client.cloak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CloakScreen(
    modifier: Modifier = Modifier,
    initialConfig: String = "",
    initialLocalHost: String = "127.0.0.1",
    initialLocalPort: String = "1984",
    onConnect: (String, String, String) -> Unit = { _, _, _ -> Unit },
    onDisconnect: () -> Unit = {},
    onVpnServiceControlClick: () -> Unit = {}
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

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onConnect.invoke(config, localHost, localPort) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Connect")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onDisconnect() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Disconnect")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onVpnServiceControlClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("VPN Service Control")
        }
    }
}
