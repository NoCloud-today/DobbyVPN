package com.dobby.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.dobby.common.showToast

actual class CopyLogsInteractor(
    private val context: Context
) {

    actual fun copy(logs: List<String>) {
        val joinedLogs = logs.joinToString("\n")
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("Log Messages", joinedLogs)
        clipboardManager.setPrimaryClip(clipData)
        context.showToast("Logs copied!")
    }
}
