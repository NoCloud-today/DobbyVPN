package com.dobby.domain

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter

class FileLogsRepository(
    private val fileDirProvider: () -> File,
    private val logFileName: String = "app_logs.txt"
) {

    private val logFile: File
        get() = File(fileDirProvider(), logFileName)

    fun writeLog(log: String) {
        runCatching {
            FileWriter(logFile, true).use { writer ->
                writer.appendLine(log)
            }
        }.onFailure { it.printStackTrace() }
    }

    fun readLogs(): List<String> {
        val logs = mutableListOf<String>()
        runCatching {
            BufferedReader(FileReader(logFile)).use { reader ->
                var line: String? = reader.readLine()
                while(line != null) {
                    logs.add(line)
                    line = reader.readLine()
                }
            }
        }.onFailure { it.printStackTrace() }
        return logs
    }

    fun clearLogs() {
        if (logFile.exists()) logFile.delete()
    }
}
