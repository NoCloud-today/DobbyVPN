package com.example.ck_client

import android.content.Context
import android.content.Intent
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

object LogHelper {

    private const val LOG_TAG = "VPN_LOG"
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun log(context: Context, message: String) {
        val timestamp = dateFormat.format(Date())
        val formattedMessage = "$timestamp $LOG_TAG D $message"
        Log.d(LOG_TAG, message)
        sendLogToService(context, formattedMessage)
    }

    private fun sendLogToService(context: Context, message: String) {
        val intent = Intent(context, LogService::class.java)
        intent.putExtra("LOG_MESSAGE", message)
        context.startService(intent)
    }
}