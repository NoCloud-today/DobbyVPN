package com.example.ck_client

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.dobby.ui.LogScreen

class LogActivity : ComponentActivity() {

    companion object {

        fun createIntent(context: Context): Intent {
            return Intent(context, LogActivity::class.java)
        }
    }

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
                        LogScreen(
                            modifier = Modifier.padding(innerPadding),
                            logMessages = logMessages,
                            onCopyToClipBoard = {}
                        )
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

    override fun onDestroy() {
        super.onDestroy()
        clearLogs()
    }

    private fun clearLogs() {
        sharedPreferences.edit().clear().apply()
    }
}