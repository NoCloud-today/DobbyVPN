package com.example.ck_client

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class LogActivity : ComponentActivity() {

    private val logMessages = mutableStateListOf<String>()
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("log_prefs", MODE_PRIVATE)
        loadLogsFromStorage()

        setContent {
            MaterialTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    content = { innerPadding ->
                        LogScreen(modifier = Modifier.padding(innerPadding), logMessages = logMessages)
                    }
                )
            }
        }
    }

    private fun loadLogsFromStorage() {
        val logs = sharedPreferences.getString("logs", "") ?: ""
        logMessages.clear()
        logMessages.addAll(logs.split("\n"))
    }

    @Composable
    fun LogScreen(modifier: Modifier = Modifier, logMessages: List<String>) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(logMessages) { message ->
                    Text(text = message, modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
}