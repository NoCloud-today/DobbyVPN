package com.dobby.util

import android.content.Context
import com.dobby.domain.FileLogsRepository

object Logger {

    private lateinit var logsRepository: FileLogsRepository

    fun init(context: Context) {
        logsRepository = FileLogsRepository(fileDirProvider = { context.filesDir })
    }

    fun log(message: String) {
        logsRepository.writeLog(message)
    }
}
