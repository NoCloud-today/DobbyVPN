package com.example.ck_client

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread

class LogService : Service() {

    private lateinit var sharedPreferences: SharedPreferences
    private var logcatProcess: Process? = null

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences("log_prefs", MODE_PRIVATE)
        startLogcatProcess()
    }

    private fun startLogcatProcess() {
        thread {
            try {
                logcatProcess = ProcessBuilder("logcat", "-s", "cloak").start()

                //Log.d("LogService", "Started logcat process with tag 'cloak'")

                val reader = BufferedReader(InputStreamReader(logcatProcess!!.inputStream))
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    if (line != null) {
                        handleLogMessage(removeDateFromLog(line!!))
                    }
                }
            } catch (e: Exception) {
                Log.e("LogService", "Error in logcat process", e)
            }
        }
    }

    private fun removeDateFromLog(logMessage: String): String {
        return if (logMessage.length > 6) {
            logMessage.substring(6)
        } else {
            logMessage
        }
    }

    private fun handleLogMessage(logMessage: String) {

        val logs = sharedPreferences.getString("logs", "") ?: ""
        val updatedLogs = if (logs.isEmpty()) {
            logMessage
        } else {
            "$logs\n$logMessage"
        }
        sharedPreferences.edit().putString("logs", updatedLogs).apply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val logMessage = intent?.getStringExtra("LOG_MESSAGE")
        logMessage?.let {
            handleLogMessage(it)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        logcatProcess?.destroy()
        clearLogs()
    }

    private fun clearLogs() {
        sharedPreferences.edit().clear().apply()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}