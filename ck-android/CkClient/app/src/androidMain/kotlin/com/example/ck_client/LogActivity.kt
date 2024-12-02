package com.example.ck_client

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import com.dobby.common.showToast
import com.dobby.domain.FileLogsRepository
import com.dobby.ui.LogScreen

class LogActivity : ComponentActivity() {

    companion object {

        fun createIntent(context: Context): Intent {
            return Intent(context, LogActivity::class.java)
        }
    }

    private lateinit var logsRepository: FileLogsRepository

    private var logs = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logsRepository = FileLogsRepository(fileDirProvider = { this.filesDir })

        logs.clear()
        logs.addAll(logsRepository.readLogs().filter { it.isNotEmpty() } )

        setContent {
            MaterialTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    content = { innerPadding ->
                        LogScreen(
                            modifier = Modifier.padding(innerPadding),
                            logMessages = logs,
                            onCopyToClipBoard = ::copyLogsToClipboard,
                            onClearLogs = ::clearLogs
                        )
                    }
                )
            }
        }
    }

    private fun copyLogsToClipboard(logs: List<String>) {
        val joinedLogs = logs.joinToString("\n")
        val clipboardManager = this.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("Log Messages", joinedLogs)
        clipboardManager.setPrimaryClip(clipData)
        showToast("Logs copied!")
    }

    private fun clearLogs() {
        logsRepository.clearLogs()
        logs.clear()
    }
}
