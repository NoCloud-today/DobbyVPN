package com.example.ck_client.com.example.ck_client

import com.example.ck_client.MyVpnService

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ck_client.ui.theme.CkClientTheme

class VpnControlActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        var apiKey by remember { mutableStateOf("") }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Введите ключ API") },
                placeholder = { Text("API ключ") },
                modifier = Modifier
                    .fillMaxWidth()
                    //.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

//            Button(
//                onClick = {
//                    if (apiKey.isNotEmpty()) {
//                        startVpnService(apiKey)
//                    } else {
//                        Toast.makeText(this@VpnControlActivity, "Введите ключ API", Toast.LENGTH_SHORT).show()
//                    }
//                },
//                modifier = Modifier.fillMaxWidth()
//            ) {
//                Text("Запустить VPN Service")
//            }
//
//            Spacer(modifier = Modifier.height(16.dp))
//
//            Button(
//                onClick = {
//                    stopVpnService()
//                },
//                modifier = Modifier.fillMaxWidth()
//            ) {
//                Text("Остановить VPN Service")
//            }
        }
    }

    private fun startVpnService(apiKey: String) {
        val intent = Intent(this, MyVpnService::class.java).apply {
            putExtra("API_KEY", apiKey)
        }
        startService(intent)
        Toast.makeText(this, "VPN Service Запущен", Toast.LENGTH_SHORT).show()
    }

    private fun stopVpnService() {
        val intent = Intent(this, MyVpnService::class.java)
        stopService(intent)
        Toast.makeText(this, "VPN Service Остановлен", Toast.LENGTH_SHORT).show()
    }

    @Preview(showBackground = true)
    @Composable
    fun VpnControlScreenPreview() {
        CkClientTheme {
            VpnControlScreen()
        }
    }
}