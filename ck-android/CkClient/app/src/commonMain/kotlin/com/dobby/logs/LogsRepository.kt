package com.dobby.logs

expect class LogsRepository {

    fun writeLog(log: String)

    fun readLogs(): List<String>

    fun clearLogs()
}
