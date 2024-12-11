package com.dobby.util

import android.content.Context
import com.dobby.logs.LogsRepository

object Logger {

    private lateinit var logsRepository: LogsRepository

    fun init(context: Context) {
        if (::logsRepository.isInitialized.not()) {
            logsRepository = LogsRepository(fileDirProvider = { context.filesDir })
        }
    }

    fun log(message: String) {
        logsRepository.writeLog(message)
    }
}
